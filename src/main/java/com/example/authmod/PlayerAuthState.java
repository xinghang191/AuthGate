package com.example.authmod;

import java.util.UUID;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.level.ServerPlayer;

public final class PlayerAuthState {
    private final long sessionId;
    private boolean locked;
    private Vec3 anchorPos;
    private float anchorYaw;
    private float anchorPitch;
    private int attempts;
    private long lockedUntil;
    private long joinedAtMillis;
    private State state;

    public PlayerAuthState(ServerPlayer player, long sessionId, State state) {
        this.sessionId = sessionId;
        this.state = state;
        this.locked = state != State.AUTHENTICATED;
        this.attempts = 0;
        this.lockedUntil = 0L;
        this.joinedAtMillis = System.currentTimeMillis();
        setAnchor(player);
    }

    public long sessionId() {
        return sessionId;
    }

    public boolean locked() {
        return locked;
    }

    public Vec3 anchorPos() {
        return anchorPos;
    }

    public float anchorYaw() {
        return anchorYaw;
    }

    public float anchorPitch() {
        return anchorPitch;
    }

    public int attempts() {
        return attempts;
    }

    public long lockedUntil() {
        return lockedUntil;
    }

    public long joinedAtMillis() {
        return joinedAtMillis;
    }

    public State state() {
        return state;
    }

    public void setAnchor(ServerPlayer player) {
        this.anchorPos = player.position();
        this.anchorYaw = player.getYRot();
        this.anchorPitch = player.getXRot();
    }

    public void setLocked(ServerPlayer player, State newState) {
        this.locked = true;
        this.state = newState;
        setAnchor(player);
    }

    public void authenticate(ServerPlayer player) {
        this.locked = false;
        this.state = State.AUTHENTICATED;
        this.attempts = 0;
        this.lockedUntil = 0L;
        setAnchor(player);
    }

    public void setAttempts(int attempts) {
        this.attempts = Math.max(0, attempts);
    }

    public void setLockedUntil(long lockedUntil) {
        this.lockedUntil = Math.max(0L, lockedUntil);
    }

    public void resetTimeoutClock() {
        this.joinedAtMillis = System.currentTimeMillis();
    }

    public enum State {
        NEW,
        KNOWN,
        LOCKED,
        AUTHENTICATED
    }
}
