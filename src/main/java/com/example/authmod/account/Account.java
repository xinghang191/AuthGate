package com.example.authmod.account;

public record Account(
    String username,
    String salt,
    String hash,
    int iterations,
    String algorithm,
    long createdAt,
    long lastLogin,
    int failedAttempts,
    long lockedUntil
) {
    public Account withLastLogin(long timestamp) {
        return new Account(username, salt, hash, iterations, algorithm, createdAt, timestamp, 0, 0);
    }

    public Account withFailure(int attempts, long newLockedUntil) {
        return new Account(username, salt, hash, iterations, algorithm, createdAt, lastLogin, attempts, newLockedUntil);
    }
}
