package com.example.authmod.account;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public final class PasswordHasher {
    public static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    public static final int SALT_BYTES = 16;
    public static final int ITERATIONS = 120_000;
    private static final int HASH_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordHasher() {
    }

    public static Hash create(String password) {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        byte[] derived = derive(password, salt, ITERATIONS);
        return new Hash(Base64.getEncoder().encodeToString(salt), Base64.getEncoder().encodeToString(derived), ITERATIONS);
    }

    public static boolean verify(String password, String encodedSalt, String encodedHash, int iterations) {
        if (password == null || encodedSalt == null || encodedHash == null || iterations < 1) {
            return false;
        }
        try {
            byte[] salt = Base64.getDecoder().decode(encodedSalt);
            byte[] expected = Base64.getDecoder().decode(encodedHash);
            if (salt.length != SALT_BYTES || expected.length != HASH_BYTES) {
                return false;
            }
            byte[] actual = derive(password, salt, iterations);
            return MessageDigest.isEqual(expected, actual);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static byte[] derive(String password, byte[] salt, int iterations) {
        char[] chars = password.toCharArray();
        PBEKeySpec spec = new PBEKeySpec(chars, salt, iterations, HASH_BYTES * Byte.SIZE);
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Required password hashing algorithm is unavailable", exception);
        } finally {
            spec.clearPassword();
            java.util.Arrays.fill(chars, '\0');
        }
    }

    public record Hash(String saltBase64, String hashBase64, int iterations) {
    }
}
