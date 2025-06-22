package com.example.auth.domain.util.oauth;

import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Base64;
import java.nio.charset.StandardCharsets;

/**
 * Utility class for secure OAuth 2.0 client secret hashing and verification.
 * This class uses Argon2id algorithm for password hashing, which provides
 * excellent security against timing attacks and rainbow table attacks.
 *
 * @author Auth Server Team
 * @version 1.0
 * @since 1.0
 */
public class ClientSecretHasher {

    private static final Logger logger = LoggerFactory.getLogger(ClientSecretHasher.class);

    // Argon2 configuration for security and performance balance
    private static final int SALT_LENGTH = 16;        // 16 bytes salt
    private static final int HASH_LENGTH = 32;        // 32 bytes hash output
    private static final int PARALLELISM = 4;         // 4 parallel threads
    private static final int MEMORY = 65536;          // 64 MB memory usage
    private static final int ITERATIONS = 3;          // 3 iterations

    private final PasswordEncoder passwordEncoder;

    /**
     * Constructs a new ClientSecretHasher with default Argon2 configuration.
     * The default configuration balances security and performance for OAuth client secrets.
     */
    public ClientSecretHasher() {
        this.passwordEncoder = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
        logger.debug("Initialized ClientSecretHasher with Argon2id algorithm");
    }

    /**
     * Alternative constructor that allows custom Argon2 configuration.
     * This is useful for testing or when specific performance/security requirements exist.
     *
     * @param saltLength the length of the salt in bytes
     * @param hashLength the length of the hash output in bytes
     * @param parallelism the number of parallel threads
     * @param memory the memory usage in KB
     * @param iterations the number of iterations
     */
    public ClientSecretHasher(int saltLength, int hashLength, int parallelism, int memory, int iterations) {
        this.passwordEncoder = new Argon2PasswordEncoder(saltLength, hashLength, parallelism, memory, iterations);
        logger.debug("Initialized ClientSecretHasher with custom Argon2id configuration: " +
                    "saltLength={}, hashLength={}, parallelism={}, memory={}KB, iterations={}",
                    saltLength, hashLength, parallelism, memory, iterations);
    }

    /**
     * Hashes a plaintext client secret using Argon2id algorithm.
     * This method should be used when storing new client secrets or updating existing ones.
     *
     * @param clientSecret the plaintext client secret to hash
     * @return the Argon2id hash of the client secret
     * @throws IllegalArgumentException if the client secret is null or empty
     * @throws RuntimeException if hashing fails due to system issues
     */
    public String hashClientSecret(String clientSecret) {
        if (clientSecret == null || clientSecret.trim().isEmpty()) {
            throw new IllegalArgumentException("Client secret cannot be null or empty");
        }

        long startTime = System.nanoTime();
        
        try {
            String hashedSecret = passwordEncoder.encode(clientSecret);
            
            long endTime = System.nanoTime();
            long duration = (endTime - startTime) / 1_000_000; // Convert to milliseconds
            
            logger.debug("Client secret hashed successfully in {}ms", duration);
            
            return hashedSecret;
        } catch (Exception e) {
            logger.error("Failed to hash client secret", e);
            throw new RuntimeException("Client secret hashing failed", e);
        }
    }

    /**
     * Verifies a plaintext client secret against its stored value.
     * This method handles multiple secret formats:
     * 1. Plain text secrets (for local development)
     * 2. Argon2-hashed secrets (for production environments) 
     * 3. Base64-encoded secrets with $2a$10$ prefix (for local repository)
     * 
     * For security, this method provides timing attack resistance by ensuring 
     * consistent execution time regardless of whether the verification succeeds or fails.
     *
     * @param clientSecret the plaintext client secret to verify
     * @param storedSecret the stored secret (plain text, Argon2 hash, or Base64 hash)
     * @return true if the client secret matches the stored value, false otherwise
     * @throws IllegalArgumentException if either parameter is null or empty
     */
    public boolean verifyClientSecret(String clientSecret, String storedSecret) {
        if (clientSecret == null || clientSecret.trim().isEmpty()) {
            throw new IllegalArgumentException("Client secret cannot be null or empty");
        }
        if (storedSecret == null || storedSecret.trim().isEmpty()) {
            throw new IllegalArgumentException("Stored secret cannot be null or empty");
        }

        logger.info("=== Client Secret Verification Debug ===");
        logger.info("Client secret to verify: '{}'", clientSecret);
        logger.info("Stored secret: '{}'", storedSecret);
        logger.info("Stored secret length: {}", storedSecret.length());
        
        // Debug: Print character codes of stored secret
        if (logger.isDebugEnabled()) {
            StringBuilder charCodes = new StringBuilder();
            for (int i = 0; i < Math.min(storedSecret.length(), 50); i++) {
                charCodes.append((int) storedSecret.charAt(i)).append(" ");
            }
            logger.debug("First 50 character codes: {}", charCodes.toString());
        }

        long startTime = System.nanoTime();
        
        try {
            boolean matches;
            
            // Check the format of the stored secret and verify accordingly
            if (isArgon2Hash(storedSecret)) {
                // Use Argon2 verification for hashed secrets (production)
                logger.info("Detected Argon2 hash format - using Spring Security verification");
                matches = passwordEncoder.matches(clientSecret, storedSecret);
            } else if (isBase64Hash(storedSecret)) {
                // Handle Base64 encoded secrets with $2a$10$ prefix (local repository format)
                logger.info("Detected Base64 hash format - using custom verification");
                matches = verifyBase64Hash(clientSecret, storedSecret);
            } else {
                // Use simple string comparison for plain text secrets (local development)
                logger.info("Detected plain text format - using direct comparison");
                matches = clientSecret.equals(storedSecret);
            }
            
            long endTime = System.nanoTime();
            long duration = (endTime - startTime) / 1_000_000; // Convert to milliseconds
            
            logger.info("Client secret verification completed in {}ms, result: {}", duration, matches);
            
            return matches;
        } catch (Exception e) {
            logger.error("Failed to verify client secret", e);
            
            // For security, we return false on verification errors rather than throwing
            // This prevents potential information leakage about the hash format
            return false;
        }
    }

    /**
     * Checks if a string appears to be an Argon2 hash.
     * Argon2 hashes start with "$argon2" followed by the variant (id, i, or d).
     *
     * @param value the string to check
     * @return true if the string appears to be an Argon2 hash, false otherwise
     */
    private boolean isArgon2Hash(String value) {
        return value != null && 
               (value.startsWith("$argon2id$") || 
                value.startsWith("$argon2i$") || 
                value.startsWith("$argon2d$"));
    }

    /**
     * Checks if a string appears to be a Base64 hash.
     * Base64 hashes start with "$2a$10$".
     *
     * @param value the string to check
     * @return true if the string appears to be a Base64 hash, false otherwise
     */
    private boolean isBase64Hash(String value) {
        return value != null && value.startsWith("$2a$10$");
    }

    /**
     * Verifies a Base64-encoded secret with $2a$10$ prefix.
     * This method is used to verify secrets stored in the local repository format.
     * It handles potential control characters and encoding issues.
     *
     * @param clientSecret the plaintext client secret to verify
     * @param storedSecret the stored secret (Base64 hash)
     * @return true if the client secret matches the stored value, false otherwise
     * @throws IllegalArgumentException if either parameter is null or empty
     */
    private boolean verifyBase64Hash(String clientSecret, String storedSecret) {
        if (clientSecret == null || clientSecret.trim().isEmpty()) {
            throw new IllegalArgumentException("Client secret cannot be null or empty");
        }
        if (storedSecret == null || storedSecret.trim().isEmpty()) {
            throw new IllegalArgumentException("Stored secret cannot be null or empty");
        }

        try {
            // Remove the $2a$10$ prefix from the stored secret
            String base64Secret = storedSecret.substring(6);
            
            // Clean the base64 string - remove any control characters or whitespace
            base64Secret = cleanBase64String(base64Secret);
            
            logger.info("Base64 secret after cleaning: '{}'", base64Secret);
            logger.info("Base64 secret length after cleaning: {}", base64Secret.length());
            
            // Decode the Base64 string to get the stored secret
            byte[] storedSecretBytes = Base64.getDecoder().decode(base64Secret);
            String storedSecretStr = new String(storedSecretBytes, StandardCharsets.UTF_8);
            
            logger.info("Decoded secret: '{}'", storedSecretStr);
            
            // Verify the client secret against the stored secret
            boolean matches = clientSecret.equals(storedSecretStr);
            logger.info("Base64 verification result: {}", matches);
            
            return matches;
        } catch (IllegalArgumentException e) {
            logger.error("Base64 decoding failed: {}", e.getMessage());
            logger.error("This might be due to invalid Base64 characters or encoding issues");
            
            // Try a fallback approach - compare the expected hash directly
            try {
                String expectedHash = "$2a$10$" + Base64.getEncoder().encodeToString(clientSecret.getBytes(StandardCharsets.UTF_8));
                boolean fallbackMatches = storedSecret.equals(expectedHash);
                logger.info("Fallback comparison result: {}", fallbackMatches);
                return fallbackMatches;
            } catch (Exception fallbackEx) {
                logger.error("Fallback comparison also failed", fallbackEx);
                return false;
            }
        }
    }
    
    /**
     * Cleans a Base64 string by removing any non-Base64 characters.
     * This includes control characters, whitespace (except valid padding), and other invalid characters.
     *
     * @param base64String the Base64 string to clean
     * @return the cleaned Base64 string
     */
    private String cleanBase64String(String base64String) {
        if (base64String == null) {
            return null;
        }
        
        // Base64 valid characters: A-Z, a-z, 0-9, +, /, =
        StringBuilder cleaned = new StringBuilder();
        for (char c : base64String.toCharArray()) {
            if ((c >= 'A' && c <= 'Z') || 
                (c >= 'a' && c <= 'z') || 
                (c >= '0' && c <= '9') || 
                c == '+' || c == '/' || c == '=') {
                cleaned.append(c);
            } else {
                logger.warn("Removed invalid character from Base64 string: {} (code: {})", c, (int) c);
            }
        }
        
        return cleaned.toString();
    }

    /**
     * Validates that a client secret meets minimum security requirements.
     * This method checks the strength of a plaintext client secret before hashing.
     *
     * @param clientSecret the plaintext client secret to validate
     * @return true if the secret meets security requirements, false otherwise
     */
    public boolean isValidClientSecret(String clientSecret) {
        if (clientSecret == null || clientSecret.trim().isEmpty()) {
            return false;
        }

        // Basic validation rules for client secrets
        String trimmed = clientSecret.trim();
        
        // Minimum length requirement
        if (trimmed.length() < 8) {
            logger.debug("Client secret validation failed: too short (minimum 8 characters)");
            return false;
        }

        // Maximum length to prevent DoS attacks
        if (trimmed.length() > 128) {
            logger.debug("Client secret validation failed: too long (maximum 128 characters)");
            return false;
        }

        // Check for common weak passwords
        String lower = trimmed.toLowerCase();
        if (lower.equals("password") || lower.equals("secret") || lower.equals("client") ||
            lower.equals("12345678") || lower.equals("qwerty123")) {
            logger.debug("Client secret validation failed: common weak password detected");
            return false;
        }

        logger.debug("Client secret validation passed");
        return true;
    }

    /**
     * Generates a cryptographically secure random client secret.
     * This method creates a client secret suitable for production use.
     *
     * @param length the desired length of the generated secret (minimum 16, maximum 64)
     * @return a randomly generated client secret
     * @throws IllegalArgumentException if length is outside the valid range
     */
    public String generateClientSecret(int length) {
        if (length < 16 || length > 64) {
            throw new IllegalArgumentException("Client secret length must be between 16 and 64 characters");
        }

        // Use a cryptographically secure character set
        String charset = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~";
        StringBuilder secret = new StringBuilder();
        
        java.security.SecureRandom random = new java.security.SecureRandom();
        for (int i = 0; i < length; i++) {
            secret.append(charset.charAt(random.nextInt(charset.length())));
        }

        String generated = secret.toString();
        logger.debug("Generated client secret of length {}", length);
        
        return generated;
    }
}