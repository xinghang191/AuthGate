package com.example.authmod;

import com.example.authmod.account.Account;
import com.example.authmod.account.AccountStore;
import com.example.authmod.account.PasswordHasher;
import com.example.authmod.network.C2SChangePasswordPacket;
import com.example.authmod.network.C2SLoginPacket;
import com.example.authmod.network.C2SRegisterPacket;
import com.example.authmod.network.ModNetwork;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class AuthManager implements ModNetwork.ServerHandler {
    private static final AuthManager INSTANCE = new AuthManager();
    private static final long SUBMISSION_INTERVAL_MILLIS = 500L;

    private final Map<UUID, PlayerAuthState> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastSubmissionAt = new ConcurrentHashMap<>();
    private volatile MinecraftServer server;
    private volatile AccountStore accountStore;
    private long sessionSequence;

    private AuthManager() {
    }

    public static AuthManager getInstance() {
        return INSTANCE;
    }

    public synchronized void start(MinecraftServer minecraftServer) {
        server = minecraftServer;
        accountStore = new AccountStore(minecraftServer);
        sessions.clear();
        lastSubmissionAt.clear();
        sessionSequence = 0L;
    }

    public synchronized void stop() {
        sessions.clear();
        lastSubmissionAt.clear();
        accountStore = null;
        server = null;
    }

    public PlayerAuthState onLogin(ServerPlayer player) {
        AccountStore store = requireStore();
        String key = accountKey(player);
        Optional<Account> account = store.find(key);
        PlayerAuthState.State initial = account.isPresent() ? PlayerAuthState.State.KNOWN : PlayerAuthState.State.NEW;
        PlayerAuthState state = new PlayerAuthState(player, nextSessionId(), initial);
        if (player.server.isSingleplayer() && !AuthConfig.REQUIRE_LOGIN_IN_SINGLE_PLAYER.get()) {
            state.authenticate(player);
        } else {
            state.setAttempts(account.map(Account::failedAttempts).orElse(0));
            state.setLockedUntil(account.map(Account::lockedUntil).orElse(0L));
        }
        sessions.put(player.getUUID(), state);
        return state;
    }

    public void onLogout(ServerPlayer player) {
        sessions.remove(player.getUUID());
        lastSubmissionAt.remove(player.getUUID());
    }

    public PlayerAuthState getState(ServerPlayer player) {
        return sessions.get(player.getUUID());
    }

    public boolean isLocked(ServerPlayer player) {
        PlayerAuthState state = getState(player);
        return state != null && state.locked();
    }

    public boolean isAuthenticated(ServerPlayer player) {
        PlayerAuthState state = getState(player);
        return state != null && state.state() == PlayerAuthState.State.AUTHENTICATED && !state.locked();
    }

    public boolean hasTimedOut(ServerPlayer player, long nowMillis) {
        int timeout = AuthConfig.AUTH_TIMEOUT_SECONDS.get();
        PlayerAuthState state = getState(player);
        return timeout > 0 && state != null && state.locked()
            && nowMillis - state.joinedAtMillis() >= timeout * 1_000L;
    }

    public int modeFor(ServerPlayer player) {
        return requireStore().contains(accountKey(player)) ? ModNetwork.MODE_LOGIN : ModNetwork.MODE_REGISTER;
    }

    public void openAuthScreen(ServerPlayer player) {
        if (isLocked(player)) {
            ModNetwork.sendOpenAuth(player, modeFor(player), player.getGameProfile().getName(), AuthConfig.MAX_ATTEMPTS.get());
        }
    }

    public boolean openChangePassword(ServerPlayer player) {
        if (!isAuthenticated(player)) {
            return false;
        }
        ModNetwork.sendOpenAuth(player, ModNetwork.MODE_CHANGE_PASSWORD,
            player.getGameProfile().getName(), AuthConfig.MAX_ATTEMPTS.get());
        return true;
    }

    @Override
    public void register(ServerPlayer player, C2SRegisterPacket packet) {
        if (!allowSubmission(player, ModNetwork.MODE_REGISTER)) {
            return;
        }
        PlayerAuthState state = getState(player);
        if (state == null || !state.locked() || state.state() != PlayerAuthState.State.NEW) {
            reply(player, false, ModNetwork.MODE_REGISTER, "authmod.msg.already_registered");
            return;
        }
        if (!validPassword(packet.password()) || !validPassword(packet.confirm())) {
            reply(player, false, ModNetwork.MODE_REGISTER, "authmod.gui.hint.too_short");
            return;
        }
        if (!packet.password().equals(packet.confirm())) {
            reply(player, false, ModNetwork.MODE_REGISTER, "authmod.gui.hint.mismatch");
            return;
        }

        String key = accountKey(player);
        AccountStore store = requireStore();
        if (store.contains(key)) {
            state.setLocked(player, PlayerAuthState.State.KNOWN);
            reply(player, false, ModNetwork.MODE_REGISTER, "authmod.msg.already_registered");
            openAuthScreen(player);
            return;
        }

        long now = nowSeconds();
        PasswordHasher.Hash passwordHash = PasswordHasher.create(packet.password());
        Account account = new Account(player.getGameProfile().getName(), passwordHash.saltBase64(),
            passwordHash.hashBase64(), passwordHash.iterations(), PasswordHasher.ALGORITHM,
            now, now, 0, 0L);
        persistOrFail(player, ModNetwork.MODE_REGISTER, key, account);
    }

    @Override
    public void login(ServerPlayer player, C2SLoginPacket packet) {
        if (!allowSubmission(player, ModNetwork.MODE_LOGIN)) {
            return;
        }
        PlayerAuthState state = getState(player);
        if (state == null || !state.locked()
            || (state.state() != PlayerAuthState.State.KNOWN && state.state() != PlayerAuthState.State.LOCKED)) {
            reply(player, false, ModNetwork.MODE_LOGIN, "authmod.msg.no_account");
            return;
        }
        if (!validPassword(packet.password())) {
            reply(player, false, ModNetwork.MODE_LOGIN, "authmod.gui.hint.too_short");
            return;
        }

        String key = accountKey(player);
        AccountStore store = requireStore();
        Optional<Account> accountValue = store.find(key);
        if (accountValue.isEmpty()) {
            state.setLocked(player, PlayerAuthState.State.NEW);
            reply(player, false, ModNetwork.MODE_LOGIN, "authmod.msg.no_account");
            openAuthScreen(player);
            return;
        }
        Account account = accountValue.get();
        if (!PasswordHasher.ALGORITHM.equals(account.algorithm())
            || account.iterations() != PasswordHasher.ITERATIONS) {
            reply(player, false, ModNetwork.MODE_LOGIN, "authmod.msg.internal_error");
            return;
        }
        long remainingLock = account.lockedUntil() - nowSeconds();
        if (remainingLock > 0L) {
            reply(player, false, ModNetwork.MODE_LOGIN, "authmod.msg.locked_out", Long.toString(remainingLock));
            return;
        }

        if (!PasswordHasher.verify(packet.password(), account.salt(), account.hash(), account.iterations())) {
            recordFailure(player, key, account, state);
            return;
        }

        Account updated = account.withLastLogin(nowSeconds());
        if (!storeAndReplyFailure(player, key, updated, ModNetwork.MODE_LOGIN)) {
            return;
        }
        authenticate(player, key, state, ModNetwork.MODE_LOGIN, "authmod.msg.login_ok");
    }

    @Override
    public void changePassword(ServerPlayer player, C2SChangePasswordPacket packet) {
        if (!allowSubmission(player, ModNetwork.MODE_CHANGE_PASSWORD)) {
            return;
        }
        if (!isAuthenticated(player)) {
            reply(player, false, ModNetwork.MODE_CHANGE_PASSWORD, "authmod.kick.relogin");
            return;
        }
        if (!validPassword(packet.oldPw()) || !validPassword(packet.newPw()) || !validPassword(packet.confirm())) {
            reply(player, false, ModNetwork.MODE_CHANGE_PASSWORD, "authmod.gui.hint.too_short");
            return;
        }
        if (!packet.newPw().equals(packet.confirm())) {
            reply(player, false, ModNetwork.MODE_CHANGE_PASSWORD, "authmod.gui.hint.mismatch");
            return;
        }

        String key = accountKey(player);
        AccountStore store = requireStore();
        Optional<Account> accountValue = store.find(key);
        if (accountValue.isEmpty()) {
            reply(player, false, ModNetwork.MODE_CHANGE_PASSWORD, "authmod.msg.no_account");
            return;
        }
        Account account = accountValue.get();
        if (!PasswordHasher.verify(packet.oldPw(), account.salt(), account.hash(), account.iterations())) {
            reply(player, false, ModNetwork.MODE_CHANGE_PASSWORD, "authmod.msg.wrong_password");
            return;
        }

        PasswordHasher.Hash passwordHash = PasswordHasher.create(packet.newPw());
        Account replacement = new Account(account.username(), passwordHash.saltBase64(), passwordHash.hashBase64(),
            passwordHash.iterations(), PasswordHasher.ALGORITHM, account.createdAt(), nowSeconds(), 0, 0L);
        if (storeAndReplyFailure(player, key, replacement, ModNetwork.MODE_CHANGE_PASSWORD)) {
            reply(player, true, ModNetwork.MODE_CHANGE_PASSWORD, "authmod.msg.change_ok");
            kickOtherSessions(key, player);
        }
    }

    public void onRespawn(ServerPlayer player) {
        PlayerAuthState state = getState(player);
        if (state == null) {
            return;
        }
        if (state.locked()) {
            state.setLocked(player, state.state());
        } else if (AuthConfig.RELOCK_ON_DEATH.get()) {
            state.setLocked(player, PlayerAuthState.State.LOCKED);
        } else {
            state.setAnchor(player);
        }
    }

    public void onDimensionChange(ServerPlayer player) {
        PlayerAuthState state = getState(player);
        if (state == null) {
            return;
        }
        if (state.locked()) {
            state.setLocked(player, state.state());
        } else if (AuthConfig.RELOCK_ON_DIMENSION_CHANGE.get()) {
            state.setLocked(player, PlayerAuthState.State.LOCKED);
        } else {
            state.setAnchor(player);
        }
    }

    public boolean resetAccount(String username) {
        AccountStore store = requireStore();
        Optional<Map.Entry<String, Account>> entry = findAccount(username);
        if (entry.isEmpty()) {
            return false;
        }
        String key = entry.get().getKey();
        try {
            store.remove(key);
        } catch (IOException exception) {
            logStoreFailure(exception);
            return false;
        }
        ServerPlayer online = findOnlinePlayer(username);
        if (online != null) {
            PlayerAuthState state = sessions.get(online.getUUID());
            if (state == null) {
                state = new PlayerAuthState(online, nextSessionId(), PlayerAuthState.State.NEW);
                sessions.put(online.getUUID(), state);
            } else {
                state.setLocked(online, PlayerAuthState.State.NEW);
                state.resetTimeoutClock();
            }
            openAuthScreen(online);
        }
        return true;
    }

    public boolean unlockAccount(String username) {
        AccountStore store = requireStore();
        Optional<Map.Entry<String, Account>> entry = findAccount(username);
        if (entry.isEmpty()) {
            return false;
        }
        Map.Entry<String, Account> found = entry.get();
        Account account = found.getValue().withFailure(0, 0L);
        if (!storeAndReplyFailure(null, found.getKey(), account, ModNetwork.MODE_LOGIN)) {
            return false;
        }
        ServerPlayer online = findOnlinePlayer(username);
        if (online != null) {
            PlayerAuthState state = sessions.get(online.getUUID());
            if (state != null) {
                state.setAttempts(0);
                state.setLockedUntil(0L);
            }
        }
        return true;
    }

    public AccountInfo getInfo(String username) {
        Optional<Map.Entry<String, Account>> entry = findAccount(username);
        if (entry.isPresent()) {
            Account account = entry.get().getValue();
            return new AccountInfo(account.failedAttempts(), account.lockedUntil());
        }
        ServerPlayer online = findOnlinePlayer(username);
        PlayerAuthState state = online == null ? null : sessions.get(online.getUUID());
        return state == null ? null : new AccountInfo(state.attempts(), state.lockedUntil());
    }

    private boolean allowSubmission(ServerPlayer player, int mode) {
        if (player == null || player.connection == null || accountStore == null || server == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        Long previous = lastSubmissionAt.put(player.getUUID(), now);
        if (previous != null && now - previous < SUBMISSION_INTERVAL_MILLIS) {
            reply(player, false, mode, "authmod.msg.too_fast");
            return false;
        }
        return true;
    }

    private boolean validPassword(String value) {
        if (value == null || value.length() < 6 || value.length() > 64) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (Character.isWhitespace(character) || Character.isISOControl(character)) {
                return false;
            }
        }
        return true;
    }

    private void recordFailure(ServerPlayer player, String key, Account account, PlayerAuthState state) {
        int attempts = Math.max(account.failedAttempts(), state.attempts()) + 1;
        long lockedUntil = 0L;
        boolean lockedOut = attempts >= AuthConfig.MAX_ATTEMPTS.get();
        if (lockedOut && AuthConfig.LOCKOUT_SECONDS.get() > 0) {
            lockedUntil = nowSeconds() + AuthConfig.LOCKOUT_SECONDS.get();
        }
        Account failed = account.withFailure(attempts, lockedUntil);
        if (!storeAndReplyFailure(player, key, failed, ModNetwork.MODE_LOGIN)) {
            return;
        }
        state.setAttempts(attempts);
        state.setLockedUntil(lockedUntil);
        if (lockedOut) {
            kick(player, "authmod.kick.too_many_attempts");
        } else {
            int remaining = Math.max(0, AuthConfig.MAX_ATTEMPTS.get() - attempts);
            reply(player, false, ModNetwork.MODE_LOGIN, "authmod.msg.attempts_left", Integer.toString(remaining));
        }
    }

    private void authenticate(ServerPlayer player, String key, PlayerAuthState state, int mode, String messageKey) {
        state.authenticate(player);
        kickOtherSessions(key, player);
        reply(player, true, mode, messageKey);
        player.sendSystemMessage(Component.translatable(messageKey));
    }

    private void persistOrFail(ServerPlayer player, int mode, String key, Account account) {
        if (!storeAndReplyFailure(player, key, account, mode)) {
            return;
        }
        PlayerAuthState state = getState(player);
        if (state != null) {
            authenticate(player, key, state, mode, "authmod.msg.register_ok");
        }
    }

    private boolean storeAndReplyFailure(ServerPlayer player, String key, Account account, int mode) {
        try {
            requireStore().put(key, account);
            return true;
        } catch (IOException exception) {
            logStoreFailure(exception);
            if (player != null) {
                reply(player, false, mode, "authmod.msg.internal_error");
            }
            return false;
        }
    }

    private void kickOtherSessions(String key, ServerPlayer current) {
        MinecraftServer activeServer = server;
        if (activeServer == null) {
            return;
        }
        for (ServerPlayer other : activeServer.getPlayerList().getPlayers()) {
            if (other != current && accountKey(other).equals(key)) {
                kick(other, "authmod.kick.relogin");
            }
        }
    }

    private Optional<Map.Entry<String, Account>> findAccount(String username) {
        if (username == null || accountStore == null) {
            return Optional.empty();
        }
        String normalized = username.toLowerCase(Locale.ROOT);
        return accountStore.snapshot().entrySet().stream()
            .filter(entry -> entry.getValue().username().toLowerCase(Locale.ROOT).equals(normalized))
            .findFirst();
    }

    private ServerPlayer findOnlinePlayer(String username) {
        MinecraftServer activeServer = server;
        if (activeServer == null) {
            return null;
        }
        return activeServer.getPlayerList().getPlayers().stream()
            .filter(player -> player.getGameProfile().getName().equalsIgnoreCase(username))
            .findFirst().orElse(null);
    }

    private String accountKey(ServerPlayer player) {
        String bindBy = AuthConfig.BIND_BY.get();
        return "NAME".equalsIgnoreCase(bindBy)
            ? "name:" + player.getGameProfile().getName().toLowerCase(Locale.ROOT)
            : player.getUUID().toString();
    }

    private void reply(ServerPlayer player, boolean success, int mode, String key, String... args) {
        ModNetwork.sendAuthResult(player, success, mode, key, List.of(args));
    }

    private void kick(ServerPlayer player, String key) {
        player.connection.disconnect(Component.translatable(key));
    }

    private AccountStore requireStore() {
        AccountStore store = accountStore;
        if (store == null) {
            throw new IllegalStateException("Authentication account store is not initialized");
        }
        return store;
    }

    private synchronized long nextSessionId() {
        return ++sessionSequence;
    }

    private static long nowSeconds() {
        return System.currentTimeMillis() / 1_000L;
    }

    private static void logStoreFailure(IOException exception) {
        org.slf4j.LoggerFactory.getLogger(AuthManager.class)
            .error("Account store operation failed ({})", exception.getClass().getSimpleName());
    }

    public record AccountInfo(int failedAttempts, long lockedUntil) {
    }
}
