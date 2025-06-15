package com.example.auth.infrastructure.azure;

import com.example.auth.domain.model.User;
import com.example.auth.domain.model.UserStatus;
import com.example.auth.domain.port.UserRepository;
import com.example.auth.domain.util.PasswordHasher;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Local file-based implementation of UserRepository for Azure Functions development.
 * Loads user data from a JSON file in the classpath.
 */
public class LocalAzureUserRepository implements UserRepository {

    private static final Logger logger = LoggerFactory.getLogger(LocalAzureUserRepository.class);
    private static final String DEFAULT_USERS_FILE = "local-users.json";
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final PasswordHasher passwordHasher;
    private final Map<String, User> users;
    private final Object lock = new Object();

    /**
     * CRITICAL: Constructor with PasswordHasher parameter as required by configuration.
     */
    public LocalAzureUserRepository(PasswordHasher passwordHasher) {
        this.passwordHasher = passwordHasher;
        this.users = new ConcurrentHashMap<>();
        loadUsersFromFile();
        logger.info("LocalAzureUserRepository initialized with {} users", users.size());
    }

    @Override
    public Optional<User> findByUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username cannot be null or empty");
        }

        String normalizedUsername = username.toLowerCase().trim();
        User user = users.get(normalizedUsername);
        
        if (user != null) {
            logger.debug("User found: {}", maskUsername(normalizedUsername));
        } else {
            logger.debug("User not found: {}", maskUsername(normalizedUsername));
        }
        
        return Optional.ofNullable(user);
    }

    @Override
    public Map<String, User> getAllUsers() {
        return new HashMap<>(users);
    }

    // Additional methods for local repository functionality
    public List<User> findAll() {
        return new ArrayList<>(users.values());
    }

    public void save(User user) {
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null");
        }

        synchronized (lock) {
            String normalizedUsername = user.getUsername().toLowerCase().trim();
            users.put(normalizedUsername, user);
            logger.info("User saved: {}", maskUsername(normalizedUsername));
        }
    }

    public void deleteByUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username cannot be null or empty");
        }

        synchronized (lock) {
            String normalizedUsername = username.toLowerCase().trim();
            User removedUser = users.remove(normalizedUsername);
            if (removedUser != null) {
                logger.info("User deleted: {}", maskUsername(normalizedUsername));
            } else {
                logger.warn("User not found for deletion: {}", maskUsername(normalizedUsername));
            }
        }
    }

    public boolean existsByUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            return false;
        }
        
        String normalizedUsername = username.toLowerCase().trim();
        return users.containsKey(normalizedUsername);
    }

    public long count() {
        return users.size();
    }

    /**
     * Loads users from the local JSON file.
     */
    private void loadUsersFromFile() {
        try {
            ClassPathResource resource = new ClassPathResource(DEFAULT_USERS_FILE);
            if (!resource.exists()) {
                logger.warn("Local users file not found: {}, creating default users", DEFAULT_USERS_FILE);
                createDefaultUsers();
                return;
            }

            try (InputStream inputStream = resource.getInputStream()) {
                List<Map<String, Object>> userDataList = objectMapper.readValue(
                    inputStream, 
                    new TypeReference<List<Map<String, Object>>>() {}
                );

                int loadedCount = 0;
                for (Map<String, Object> userData : userDataList) {
                    try {
                        User user = parseUserFromData(userData);
                        if (user != null) {
                            String normalizedUsername = user.getUsername().toLowerCase().trim();
                            users.put(normalizedUsername, user);
                            loadedCount++;
                        }
                    } catch (Exception e) {
                        logger.warn("Failed to parse user data: {}", userData, e);
                    }
                }

                logger.info("Loaded {} users from {}", loadedCount, DEFAULT_USERS_FILE);
            }

        } catch (Exception e) {
            logger.error("Failed to load users from file: {}", DEFAULT_USERS_FILE, e);
            createDefaultUsers();
        }
    }

    /**
     * Parses a User object from JSON data.
     */
    private User parseUserFromData(Map<String, Object> userData) {
        String username = (String) userData.get("username");
        String password = (String) userData.get("password");
        String passwordHash = (String) userData.get("passwordHash");
        String statusString = (String) userData.get("status");
        
        @SuppressWarnings("unchecked")
        List<String> roles = (List<String>) userData.get("roles");
        if (roles == null) {
            roles = new ArrayList<>();
        }

        if (username == null || username.trim().isEmpty()) {
            logger.warn("Invalid user data: missing username");
            return null;
        }

        // Handle password hashing if plain password is provided
        if (passwordHash == null && password != null) {
            passwordHash = passwordHasher.hashPassword(password);
            logger.debug("Generated password hash for user: {}", maskUsername(username));
        }

        if (passwordHash == null) {
            logger.warn("Invalid user data: missing password or passwordHash for user {}", maskUsername(username));
            return null;
        }

        // Parse status
        UserStatus status = UserStatus.ACTIVE; // Default
        if (statusString != null) {
            try {
                status = UserStatus.valueOf(statusString.toUpperCase());
            } catch (IllegalArgumentException e) {
                logger.warn("Invalid user status '{}' for user '{}', using ACTIVE", 
                           statusString, maskUsername(username));
            }
        }

        // CRITICAL: Use correct User constructor - new User(username, passwordHash, status, roles)
        return new User(username, passwordHash, status, roles);
    }

    /**
     * Creates default users for development.
     */
    private void createDefaultUsers() {
        logger.info("Creating default users for local development");

        try {
            // Create demo user
            String demoPasswordHash = passwordHasher.hashPassword("demo123");
            User demoUser = new User("demo", demoPasswordHash, UserStatus.ACTIVE, 
                                   Arrays.asList("user", "read"));
            
            // Create admin user
            String adminPasswordHash = passwordHasher.hashPassword("admin123");
            User adminUser = new User("admin", adminPasswordHash, UserStatus.ACTIVE, 
                                    Arrays.asList("admin", "user", "read", "write"));

            // Create test user (disabled)
            String testPasswordHash = passwordHasher.hashPassword("test123");
            User testUser = new User("test", testPasswordHash, UserStatus.DISABLED, 
                                   Arrays.asList("user"));

            users.put("demo", demoUser);
            users.put("admin", adminUser);
            users.put("test", testUser);

            logger.info("Created {} default users", users.size());

        } catch (Exception e) {
            logger.error("Failed to create default users", e);
        }
    }

    /**
     * Masks username for logging (security).
     */
    private String maskUsername(String username) {
        if (username == null || username.length() <= 2) {
            return "***";
        }
        return username.charAt(0) + "*".repeat(username.length() - 2) + username.charAt(username.length() - 1);
    }

    /**
     * Gets repository statistics for monitoring.
     */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalUsers", users.size());
        
        long activeUsers = users.values().stream()
                .mapToLong(user -> user.isActive() ? 1 : 0)
                .sum();
        stats.put("activeUsers", activeUsers);
        stats.put("inactiveUsers", users.size() - activeUsers);
        
        return stats;
    }
} 