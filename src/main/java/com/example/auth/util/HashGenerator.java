package com.example.auth.util;

import com.example.auth.domain.util.PasswordHasher;

/**
 * Utility class to generate password hashes for storing in Secrets Manager.
 */
public class HashGenerator {
    
    public static void main(String[] args) {
        PasswordHasher passwordHasher = new PasswordHasher();
        
        // Generate hash for testpass123
        String password = "testpass123";
        String hash = passwordHasher.hashPassword(password);
        
        System.out.println("Password: " + password);
        System.out.println("Argon2id Hash: " + hash);
        System.out.println();
        System.out.println("Secret JSON format:");
        System.out.println("{\"testuser\": \"" + hash + "\"}");
    }
} 