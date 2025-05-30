package com.example.auth.util;

import com.example.auth.domain.util.PasswordHasher;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test to generate password hashes for Secrets Manager.
 */
class HashGeneratorTest {

    @Test
    void generateAndVerifyPasswordHash() {
        PasswordHasher passwordHasher = new PasswordHasher();
        
        String password = "testpass123";
        String hash = passwordHasher.hashPassword(password);
        
        System.out.println("=== HASH GENERATION AND VERIFICATION ===");
        System.out.println("Password: " + password);
        System.out.println("Generated Hash: " + hash);
        
        // Verify the hash immediately
        boolean isValid = passwordHasher.verifyPassword(password, hash);
        System.out.println("Hash verification result: " + isValid);
        assertTrue(isValid, "Generated hash should verify correctly");
        
        System.out.println("JSON for Secrets Manager:");
        System.out.println("{\"testuser\": \"" + hash + "\"}");
        System.out.println("=== END ===");
    }
} 