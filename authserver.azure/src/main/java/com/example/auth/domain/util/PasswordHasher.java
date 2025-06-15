package com.example.auth.domain.util;

import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

/**
 * Utility class for secure password hashing and verification using Argon2id.
 * This class provides methods for hashing passwords and verifying them against stored hashes.
 * 
 * Argon2id is the recommended password hashing algorithm as of 2023, providing
 * resistance against both side-channel and GPU-based attacks.
 */
@Component
public class PasswordHasher {

    private static final Logger log = LoggerFactory.getLogger(PasswordHasher.class);

    // Argon2 parameters - tuned for security vs performance balance
    private static final int SALT_LENGTH = 16; // 128 bits
    private static final int HASH_LENGTH = 32; // 256 bits
    private static final int ITERATIONS = 3;   // Time cost
    private static final int MEMORY_KB = 65536; // 64 MB memory cost
    private static final int PARALLELISM = 1;   // Single-threaded for Lambda

    private final SecureRandom secureRandom;

    public PasswordHasher() {
        this.secureRandom = new SecureRandom();
    }

    /**
     * Hashes a plaintext password using Argon2id.
     *
     * @param password the plaintext password to hash
     * @return the hashed password in PHC string format
     * @throws IllegalArgumentException if password is null or empty
     */
    public String hashPassword(String password) {
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Password cannot be null or empty");
        }

        long startTime = System.nanoTime();
        
        try {
            // Generate random salt
            byte[] salt = new byte[SALT_LENGTH];
            secureRandom.nextBytes(salt);

            // Generate hash
            byte[] hash = generateArgon2Hash(password, salt);

            // Format as PHC string: $argon2id$v=19$m=65536,t=3,p=1$salt$hash
            String saltBase64 = Base64.getEncoder().withoutPadding().encodeToString(salt);
            String hashBase64 = Base64.getEncoder().withoutPadding().encodeToString(hash);

            String result = String.format("$argon2id$v=19$m=%d,t=%d,p=%d$%s$%s",
                    MEMORY_KB, ITERATIONS, PARALLELISM, saltBase64, hashBase64);

            long duration = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
            log.debug("Password hashing completed in {}ms", duration);

            return result;

        } catch (Exception e) {
            log.error("Failed to hash password", e);
            throw new RuntimeException("Password hashing failed", e);
        }
    }

    /**
     * Verifies a plaintext password against a stored hash.
     * This method is designed to be constant-time to prevent timing attacks.
     *
     * @param password the plaintext password to verify
     * @param storedHash the stored hash to verify against
     * @return true if the password matches the hash
     * @throws IllegalArgumentException if either parameter is null or empty
     */
    public boolean verifyPassword(String password, String storedHash) {
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Password cannot be null or empty");
        }
        if (storedHash == null || storedHash.isEmpty()) {
            throw new IllegalArgumentException("Stored hash cannot be null or empty");
        }

        long startTime = System.nanoTime();

        try {
            // Parse the stored hash
            ParsedHash parsed = parseHash(storedHash);
            
            // Generate hash with same parameters
            byte[] candidateHash = generateArgon2Hash(password, parsed.salt);
            
            // Constant-time comparison
            boolean matches = constantTimeEquals(candidateHash, parsed.hash);

            long duration = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
            log.debug("Password verification completed in {}ms, result: {}", duration, matches);

            return matches;

        } catch (Exception e) {
            log.warn("Password verification failed due to error", e);
            // Return false on any error to prevent information leakage
            return false;
        }
    }

    /**
     * Validates that a hash string is in the correct Argon2id format.
     *
     * @param hash the hash to validate
     * @return true if the hash is in valid Argon2id format
     */
    public boolean isValidHashFormat(String hash) {
        try {
            if (hash == null || !hash.startsWith("$argon2id$")) {
                return false;
            }
            parseHash(hash);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Generates an Argon2id hash for the given password and salt.
     */
    private byte[] generateArgon2Hash(String password, byte[] salt) {
        Argon2Parameters params = new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withSalt(salt)
                .withMemoryAsKB(MEMORY_KB)
                .withIterations(ITERATIONS)
                .withParallelism(PARALLELISM)
                .build();

        Argon2BytesGenerator generator = new Argon2BytesGenerator();
        generator.init(params);

        byte[] hash = new byte[HASH_LENGTH];
        generator.generateBytes(password.getBytes(StandardCharsets.UTF_8), hash);

        return hash;
    }

    /**
     * Parses an Argon2id hash string into its components.
     */
    private ParsedHash parseHash(String hash) {
        // Expected format: $argon2id$v=19$m=65536,t=3,p=1$salt$hash
        String[] parts = hash.split("\\$");
        
        if (parts.length != 6) {
            throw new IllegalArgumentException("Invalid hash format: incorrect number of segments");
        }

        if (!"argon2id".equals(parts[1])) {
            throw new IllegalArgumentException("Invalid hash format: not Argon2id");
        }

        if (!"v=19".equals(parts[2])) {
            throw new IllegalArgumentException("Invalid hash format: unsupported version");
        }

        // Parse parameters (m=memory,t=time,p=parallelism)
        String[] paramParts = parts[3].split(",");
        if (paramParts.length != 3) {
            throw new IllegalArgumentException("Invalid hash format: incorrect parameters");
        }

        byte[] salt = Base64.getDecoder().decode(parts[4]);
        byte[] hashBytes = Base64.getDecoder().decode(parts[5]);

        return new ParsedHash(salt, hashBytes);
    }

    /**
     * Performs constant-time comparison of two byte arrays to prevent timing attacks.
     */
    private boolean constantTimeEquals(byte[] a, byte[] b) {
        if (a.length != b.length) {
            return false;
        }

        int result = 0;
        for (int i = 0; i < a.length; i++) {
            result |= a[i] ^ b[i];
        }

        return result == 0;
    }

    /**
     * Helper class to hold parsed hash components.
     */
    private static class ParsedHash {
        final byte[] salt;
        final byte[] hash;

        ParsedHash(byte[] salt, byte[] hash) {
            this.salt = salt;
            this.hash = hash;
        }
    }
} 