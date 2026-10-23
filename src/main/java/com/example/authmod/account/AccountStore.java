package com.example.authmod.account;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AccountStore {
    private static final Logger LOGGER = LoggerFactory.getLogger(AccountStore.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path file;
    private final Path temporaryFile;
    private final Path corruptBackup;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private Map<String, Account> accounts = new LinkedHashMap<>();

    public AccountStore(MinecraftServer server) {
        Path directory = server.getWorldPath(LevelResource.ROOT).resolve("authmod");
        this.file = directory.resolve("accounts.json");
        this.temporaryFile = directory.resolve("accounts.json.tmp");
        this.corruptBackup = directory.resolve("accounts.json.bak-corrupt");
        load();
    }

    public Optional<Account> find(String key) {
        lock.readLock().lock();
        try {
            return Optional.ofNullable(accounts.get(key));
        } finally {
            lock.readLock().unlock();
        }
    }

    public Map<String, Account> snapshot() {
        lock.readLock().lock();
        try {
            return Map.copyOf(accounts);
        } finally {
            lock.readLock().unlock();
        }
    }

    public boolean contains(String key) {
        lock.readLock().lock();
        try {
            return accounts.containsKey(key);
        } finally {
            lock.readLock().unlock();
        }
    }

    public void put(String key, Account account) throws IOException {
        lock.writeLock().lock();
        try {
            accounts.put(key, account);
            writeLocked();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean remove(String key) throws IOException {
        lock.writeLock().lock();
        try {
            if (accounts.remove(key) == null) {
                return false;
            }
            writeLocked();
            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void load() {
        lock.writeLock().lock();
        try {
            Files.createDirectories(file.getParent());
            if (!Files.exists(file)) {
                accounts = new LinkedHashMap<>();
                return;
            }

            JsonElement parsed = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
            if (!parsed.isJsonObject()) {
                throw new JsonParseException("Account root is not an object");
            }
            JsonObject root = parsed.getAsJsonObject();
            if (!root.has("version") || root.get("version").getAsInt() != 1
                || !root.has("accounts") || !root.get("accounts").isJsonObject()) {
                throw new JsonParseException("Unsupported account store structure");
            }

            Map<String, Account> loaded = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("accounts").entrySet()) {
                Account account = GSON.fromJson(entry.getValue(), Account.class);
                if (account == null || account.username() == null || account.salt() == null || account.hash() == null
                    || account.iterations() != PasswordHasher.ITERATIONS
                    || !PasswordHasher.ALGORITHM.equals(account.algorithm())) {
                    throw new JsonParseException("Invalid account record");
                }
                loaded.put(entry.getKey(), account);
            }
            accounts = loaded;
        } catch (IOException | RuntimeException exception) {
            backupCorruptFile(exception);
            accounts = new LinkedHashMap<>();
            try {
                writeLocked();
            } catch (IOException writeException) {
                LOGGER.error("Could not rebuild account store at {} ({})", file, writeException.getClass().getSimpleName());
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void backupCorruptFile(Exception cause) {
        LOGGER.error("Account store at {} is corrupt ({})", file, cause.getClass().getSimpleName());
        try {
            if (Files.exists(file)) {
                Files.move(file, corruptBackup, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException backupException) {
            LOGGER.error("Could not back up corrupt account store at {} ({})", file, backupException.getClass().getSimpleName());
        }
    }

    private void writeLocked() throws IOException {
        Files.createDirectories(file.getParent());
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        JsonObject accountObject = new JsonObject();
        accounts.forEach((key, account) -> accountObject.add(key, GSON.toJsonTree(account)));
        root.add("accounts", accountObject);
        Files.writeString(temporaryFile, GSON.toJson(root), StandardCharsets.UTF_8);
        try {
            Files.move(temporaryFile, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporaryFile, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
