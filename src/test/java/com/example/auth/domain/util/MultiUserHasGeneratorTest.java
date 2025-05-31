package com.example.auth.domain.util;


import org.junit.jupiter.api.Test;

/**
 * Test to generate password hashes for multiple users in Secrets Manager.
 */
class MultiUserHashGeneratorTest {

    @Test
    void generateHashesForMultipleUsers() {
        PasswordHasher passwordHasher = new PasswordHasher();
        
        // Define users and their passwords
        String[][] users = {
            {"alice", "password123"},
            {"bob", "bobpassword"},
            {"charlie", "charlie789"}, 
            {"admin", "admin123"},
            {"developer", "dev456"}
        };
        
        System.out.println("=== MULTI-USER HASH GENERATION ===");
        System.out.println("Copy this JSON to your Secrets Manager:");
        System.out.println("{");
        
        // Keep existing testuser
        System.out.println("  \"testuser\": {");
        System.out.println("    \"passwordHash\": \"$argon2id$v=19$m=65536,t=3,p=1$tAfnCOGvfoqtpA8fdehxjQ$xeVLzYR+9PcmvjOfYBvblNEIUlVSV4s/PeRKvNU3HGY\",");
        System.out.println("    \"status\": \"ACTIVE\",");
        System.out.println("    \"roles\": [\"user\"]");
        System.out.println("  },");
        
        // Generate new users
        for (int i = 0; i < users.length; i++) {
            String username = users[i][0];
            String password = users[i][1];
            String hash = passwordHasher.hashPassword(password);
            
            System.out.println("  \"" + username + "\": {");
            System.out.println("    \"passwordHash\": \"" + hash + "\",");
            if (username!="charlie") {    
            System.out.println("    \"status\": \"ACTIVE\",");
            } else {
                System.out.println("    \"status\": \"DISABLED\",");
            }
            // Set roles based on username
            if (username.equals("admin")) {
                System.out.println("    \"roles\": [\"admin\", \"user\"]");
            } else if (username.equals("charlie")) {
                System.out.println("    \"roles\": [\"user\"]");
            } else if (username.equals("developer")) {
                System.out.println("    \"roles\": [\"developer\", \"user\"]");
            } else {
                System.out.println("    \"roles\": [\"user\"]");
            }
            
            if (i < users.length - 1) {
                System.out.println("  },");
            } else {
                System.out.println("  }");
            }
        }
        
        System.out.println("}");
        System.out.println();
        
        // Print individual user details for testing
        System.out.println("=== USER CREDENTIALS FOR TESTING ===");
        System.out.println("testuser:testpass (Base64: " + java.util.Base64.getEncoder().encodeToString("testuser:testpass".getBytes()) + ")");
        for (String[] user : users) {
            String credentials = user[0] + ":" + user[1];
            String base64 = java.util.Base64.getEncoder().encodeToString(credentials.getBytes());
            System.out.println(user[0] + ":" + user[1] + " (Base64: " + base64 + ")");
        }
        System.out.println("=== END ===");
    }
}