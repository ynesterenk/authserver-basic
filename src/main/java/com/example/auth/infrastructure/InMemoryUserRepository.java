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
 * In-memory implementation of UserRepository for unit testing.
 * This implementation is only active when neither 'local' nor 'aws' profiles are active.
 */
@Component
@Profile("!local & !aws")
public class InMemoryUserRepository implements UserRepository {

    private static final Logger logger = LoggerFactory.getLogger(InMemoryUserRepository.class);
    
    private final Map<String, User> users;
    
    public InMemoryUserRepository(PasswordHasher passwordHasher) {
        this.users = createTestUsers(passwordHasher);
        logger.info("InMemoryUserRepository initialized with {} test users", users.size());
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
        logger.debug("Retrieved {} users from in-memory repository", users.size());
        return Collections.unmodifiableMap(users);
    }

    /**
     * Creates test users with proper password hashing.
     */
    private Map<String, User> createTestUsers(PasswordHasher passwordHasher) {
        Map<String, User> testUsers = new HashMap<>();
        
        try {
            // Alice - Admin user
            testUsers.put("alice", new User(
                "alice",
                passwordHasher.hashPassword("password123"),
                UserStatus.ACTIVE,
                List.of("user")
            ));
            
            // Admin - Another admin user
            testUsers.put("admin", new User(
                "admin",
                passwordHasher.hashPassword("admin123"),
                UserStatus.ACTIVE,
                List.of("admin", "user")
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
                List.of()
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
} 