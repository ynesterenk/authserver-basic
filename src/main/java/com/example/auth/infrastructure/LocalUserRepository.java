package com.example.auth.infrastructure;

import com.example.auth.domain.model.User;
import com.example.auth.domain.model.UserStatus;
import com.example.auth.domain.port.UserRepository;
import com.example.auth.domain.util.PasswordHasher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Local implementation of UserRepository for testing and development.
 * Uses in-memory storage with test users and real password hashing.
 */
@Component
@Profile("local")
public class LocalUserRepository implements UserRepository {

    private static final Logger logger = LoggerFactory.getLogger(LocalUserRepository.class);
    
    private final Map<String, User> users;
    
    public LocalUserRepository(PasswordHasher passwordHasher) {
        this.users = createTestUsers(passwordHasher);
        logger.info("LocalUserRepository initialized with {} test users", users.size());
    }

    @Override
    public Optional<User> findByUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            logger.debug("Username is null or empty");
            return Optional.empty();
        }
        
        User user = users.get(username);
        if (user != null) {
            logger.debug("User found for username: {}", maskUsername(username));
            return Optional.of(user);
        } else {
            logger.debug("User not found for username: {}", maskUsername(username));
            return Optional.empty();
        }
    }

    @Override
    public Map<String, User> getAllUsers() {
        logger.debug("Retrieved {} users from local repository", users.size());
        return Collections.unmodifiableMap(users);
    }

    /**
     * Creates test users with proper password hashing for local testing.
     */
    private Map<String, User> createTestUsers(PasswordHasher passwordHasher) {
        Map<String, User> testUsers = new HashMap<>();
        
        try {
            // Alice - Admin user
            testUsers.put("alice", new User(
                "alice",
                passwordHasher.hashPassword("password123"),
                UserStatus.ACTIVE,
                List.of("admin", "user")
            ));
            
            // Admin - Another admin user
            testUsers.put("admin", new User(
                "admin",
                passwordHasher.hashPassword("admin123"),
                UserStatus.ACTIVE,
                List.of("admin")
            ));
            
            // Bob - Disabled user for testing
            testUsers.put("bob", new User(
                "bob",
                passwordHasher.hashPassword("password456"),
                UserStatus.DISABLED,
                List.of("user")
            ));
            
            // Charlie - Regular user
            testUsers.put("charlie", new User(
                "charlie",
                passwordHasher.hashPassword("charlie789"),
                UserStatus.ACTIVE,
                List.of("user")
            ));
            
            // Test user for integration tests
            testUsers.put("testuser", new User(
                "testuser",
                passwordHasher.hashPassword("testpass"),
                UserStatus.ACTIVE,
                List.of("test")
            ));
            
            logger.info("Created {} test users with real password hashing", testUsers.size());
            
        } catch (Exception e) {
            logger.error("Error creating test users", e);
        }
        
        return testUsers;
    }

    /**
     * Masks username for logging (security best practice).
     */
    private String maskUsername(String username) {
        if (username == null || username.length() <= 2) {
            return "***";
        }
        return username.charAt(0) + "***" + username.charAt(username.length() - 1);
    }
    
    /**
     * Adds a user to the local repository (for testing purposes).
     */
    public void addUser(User user) {
        users.put(user.getUsername(), user);
        logger.debug("Added user to local repository: {}", maskUsername(user.getUsername()));
    }
    
    /**
     * Removes a user from the local repository (for testing purposes).
     */
    public void removeUser(String username) {
        users.remove(username);
        logger.debug("Removed user from local repository: {}", maskUsername(username));
    }
    
    /**
     * Clears all users (for testing purposes).
     */
    public void clearUsers() {
        users.clear();
        logger.debug("Cleared all users from local repository");
    }
} 