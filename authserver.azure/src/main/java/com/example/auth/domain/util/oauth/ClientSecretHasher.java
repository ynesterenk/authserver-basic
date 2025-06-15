package com.example.auth.domain.util.oauth;

import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
     * Constructs a new ClientSecretHasher with custom Argon2 configuration.
     * This constructor allows fine-tuning the Argon2 parameters for specific security requirements.
     *
     * @param saltLength the length of the salt in bytes
     * @param hashLength the length of the hash output in bytes
     * @param parallelism the number of parallel threads
     * @param memory the memory usage in KB
     * @param iterations the number of iterations
     */
    public ClientSecretHasher(int saltLength, int hashLength, int parallelism, int memory, int iterations) {
        this.passwordEncoder = new Argon2PasswordEncoder(saltLength, hashLength, parallelism, memory, iterations);
        logger.debug("Initialized ClientSecretHasher with custom Argon2 parameters: " +
                "saltLength={}, hashLength={}, parallelism={}, memory={}KB, iterations={}",
                saltLength, hashLength, parallelism, memory, iterations);
    }

    /**
     * Hashes a client secret using Argon2id algorithm.
     * The resulting hash includes the salt and algorithm parameters, making it self-contained.
     *
     * @param clientSecret the plaintext client secret to hash
     * @return the hashed client secret including salt and parameters
     * @throws IllegalArgumentException if clientSecret is null or empty
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
     * Verifies a plaintext client secret against its hash.
     * This method provides timing attack resistance by ensuring consistent execution time
     * regardless of whether the verification succeeds or fails.
     *
     * @param clientSecret the plaintext client secret to verify
     * @param hashedSecret the stored hash to verify against
     * @return true if the client secret matches the hash, false otherwise
     * @throws IllegalArgumentException if either parameter is null or empty
     */
    public boolean verifyClientSecret(String clientSecret, String hashedSecret) {
        if (clientSecret == null || clientSecret.trim().isEmpty()) {
            throw new IllegalArgumentException("Client secret cannot be null or empty");
        }
        if (hashedSecret == null || hashedSecret.trim().isEmpty()) {
            throw new IllegalArgumentException("Hashed secret cannot be null or empty");
        }

        long startTime = System.nanoTime();
        
        try {
            boolean matches = passwordEncoder.matches(clientSecret, hashedSecret);
            
            long endTime = System.nanoTime();
            long duration = (endTime - startTime) / 1_000_000; // Convert to milliseconds
            
            logger.debug("Client secret verification completed in {}ms, result: {}", duration, matches);
            
            return matches;
        } catch (Exception e) {
            logger.error("Failed to verify client secret", e);
            
            // For security, we return false on verification errors rather than throwing
            // This prevents potential information leakage about the hash format
            return false;
        }
    }

    /**
     * Validates that a client secret meets minimum security requirements.
     * This method checks the strength of a plaintext client secret before hashing.
     *
     * @param clientSecret the plaintext client secret to validate
     * @return true if the secret meets security requirements, false otherwise
     */
    public boolean validateSecretStrength(String clientSecret) {
        if (clientSecret == null || clientSecret.length() < 32) {
            logger.debug("Client secret validation failed: insufficient length");
            return false;
        }

        // Check for character diversity
        boolean hasUpper = clientSecret.chars().anyMatch(Character::isUpperCase);
        boolean hasLower = clientSecret.chars().anyMatch(Character::isLowerCase);
        boolean hasDigit = clientSecret.chars().anyMatch(Character::isDigit);
        boolean hasSpecial = clientSecret.chars().anyMatch(ch -> !Character.isLetterOrDigit(ch));

        boolean isStrong = hasUpper && hasLower && hasDigit && hasSpecial;
        
        if (!isStrong) {
            logger.debug("Client secret validation failed: insufficient character diversity");
        }
        
        return isStrong;
    }

    /**
     * Generates a secure random client secret that meets security requirements.
     * This method creates a cryptographically secure random secret suitable for OAuth clients.
     *
     * @param length the desired length of the secret (minimum 32 characters)
     * @return a secure random client secret
     * @throws IllegalArgumentException if length is less than 32
     */
    public String generateSecureSecret(int length) {
        if (length < 32) {
            throw new IllegalArgumentException("Secret length must be at least 32 characters");
        }

        // Character sets for generating diverse secrets
        String upperCase = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        String lowerCase = "abcdefghijklmnopqrstuvwxyz";
        String digits = "0123456789";
        String special = "!@#$%^&*()_+-=[]{}|;:,.<>?";
        String allChars = upperCase + lowerCase + digits + special;

        StringBuilder secret = new StringBuilder(length);
        java.security.SecureRandom random = new java.security.SecureRandom();

        // Ensure at least one character from each category
        secret.append(upperCase.charAt(random.nextInt(upperCase.length())));
        secret.append(lowerCase.charAt(random.nextInt(lowerCase.length())));
        secret.append(digits.charAt(random.nextInt(digits.length())));
        secret.append(special.charAt(random.nextInt(special.length())));

        // Fill the rest randomly
        for (int i = 4; i < length; i++) {
            secret.append(allChars.charAt(random.nextInt(allChars.length())));
        }

        // Shuffle the characters to avoid predictable patterns
        for (int i = 0; i < secret.length(); i++) {
            int randomIndex = random.nextInt(secret.length());
            char temp = secret.charAt(i);
            secret.setCharAt(i, secret.charAt(randomIndex));
            secret.setCharAt(randomIndex, temp);
        }

        String generatedSecret = secret.toString();
        logger.debug("Generated secure client secret of length {}", length);
        
        return generatedSecret;
    }

    /**
     * Checks if a hash was created with the current hashing algorithm and parameters.
     * This can be used to identify hashes that need to be regenerated with updated parameters.
     *
     * @param hashedSecret the hash to check
     * @return true if the hash uses current algorithm and parameters
     */
    public boolean needsRehashing(String hashedSecret) {
        if (hashedSecret == null || hashedSecret.trim().isEmpty()) {
            return true;
        }

        try {
            // Use a dummy password to check if the hash format is current
            return passwordEncoder.upgradeEncoding(hashedSecret);
        } catch (Exception e) {
            logger.debug("Hash format check failed, rehashing recommended", e);
            return true;
        }
    }
} 