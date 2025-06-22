package com.example.auth.infrastructure.azure;

import com.example.auth.domain.model.User;
import com.example.auth.domain.model.UserStatus;
import com.example.auth.domain.port.UserRepository;
import com.example.auth.domain.util.PasswordHasher;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired; // Added import for Autowired
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
// import java.util.concurrent.ConcurrentHashMap; // Unused import

/**
 * Local file-based implementation of UserRepository for Azure Functions.
 * Used for local development and testing with Azure Functions.
 */
@Component
@Profile("azure")
public class LocalAzureUserRepository implements UserRepository {
    private static final Logger logger = LoggerFactory.getLogger(LocalAzureUserRepository.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final PasswordHasher passwordHasher;
    private final Map<String, User> usersByUsername = new HashMap<>();
    private final List<User> allUsers = new ArrayList<>(); // Retained for potential future use, though not directly used by current UserRepository methods
    private final RepositoryMetadata metadata;

    /**
     * CRITICAL: Constructor with PasswordHasher parameter as required by configuration.
     */
    @Autowired
    public LocalAzureUserRepository(PasswordHasher passwordHasher) {
        this(passwordHasher, loadUsersFromClasspath());
    }

    // New constructor for testability
    public LocalAzureUserRepository(PasswordHasher passwordHasher, InputStream usersInputStream) {
        this.passwordHasher = passwordHasher;
        
        // FIXED: Fail fast for null stream instead of creating empty repository
        if (usersInputStream == null) {
            throw new IllegalArgumentException("User data input stream cannot be null");
        }
        
        try {
            // Use the static objectMapper for deserialization
            List<UserDto> userDtos = objectMapper.readValue(usersInputStream, new TypeReference<List<UserDto>>() {});
            for (UserDto dto : userDtos) {
                // Hash password before storing if it's not already hashed
                // Assuming DTO contains plain password and it needs hashing during loading
                String hashedPassword = passwordHasher.hashPassword(dto.getPassword());
                User user = new User(
                        dto.getUsername(),
                        hashedPassword, // Store hashed password
                        UserStatus.valueOf(dto.getStatus().toUpperCase()),
                        dto.getRoles()
                );
                usersByUsername.put(user.getUsername().toLowerCase(), user);
                allUsers.add(user);
            }
            this.metadata = new RepositoryMetadata(allUsers.size(), "local-users.json-INPUTSTREAM");
        } catch (IOException e) {
            logger.error("Failed to load users from input stream", e);
            throw new IllegalStateException("Failed to load users from input stream", e);
        }
    }

    private static InputStream loadUsersFromClasspath() {
        try {
            Resource resource = new ClassPathResource("local-users.json");
            if (!resource.exists()) {
                logger.error("local-users.json not found on classpath. This is a critical configuration file.");
                // Throwing an exception here is appropriate as the repository cannot function without this file.
                throw new IllegalStateException("local-users.json not found on classpath. Please ensure it is present.");
            }
            return resource.getInputStream();
        } catch (IOException e) {
            logger.error("Failed to load local-users.json from classpath", e);
            throw new IllegalStateException("Failed to load local-users.json from classpath", e);
        }
    }

    @Override
    public Optional<User> findByUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            // Consider logging this as a warning or info, as it might indicate a programming error elsewhere.
            // throw new IllegalArgumentException("Username cannot be null or empty"); // Or return Optional.empty()
            logger.warn("Attempted to find user with null or empty username.");
            return Optional.empty();
        }

        String normalizedUsername = username.toLowerCase().trim();
        User user = usersByUsername.get(normalizedUsername);
        
        if (user != null) {
            logger.debug("User found: {}", maskUsername(normalizedUsername));
        } else {
            logger.debug("User not found: {}", maskUsername(normalizedUsername));
        }
        
        return Optional.ofNullable(user);
    }

    @Override
    public Map<String, User> getAllUsers() {
        // Return an unmodifiable map to prevent external modification
        return Collections.unmodifiableMap(new HashMap<>(usersByUsername));
    }

    // Additional methods for local repository functionality (consider if these should be part of the UserRepository interface)
    public List<User> findAll() {
        // Return an unmodifiable list
        return Collections.unmodifiableList(new ArrayList<>(usersByUsername.values()));
    }

    // This method is not part of UserRepository, consider its placement.
    // If it's for testing or admin purposes, it might be okay here.
    public void save(User user) {
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null");
        }
        if (user.getUsername() == null || user.getUsername().trim().isEmpty()) {
            throw new IllegalArgumentException("User username cannot be null or empty");
        }

        // Synchronize on a dedicated lock object or usersByUsername if mutations are frequent.
        // For this local repository, synchronizing on usersByUsername is likely fine.
        synchronized (usersByUsername) {
            String normalizedUsername = user.getUsername().toLowerCase().trim();
            usersByUsername.put(normalizedUsername, user);
            // Update allUsers list as well if it's meant to be a consistent view
            allUsers.removeIf(u -> u.getUsername().equalsIgnoreCase(normalizedUsername));
            allUsers.add(user);
            logger.info("User saved: {}", maskUsername(normalizedUsername));
        }
    }

    // Not part of UserRepository interface
    public void deleteByUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username cannot be null or empty for deletion");
        }

        synchronized (usersByUsername) {
            String normalizedUsername = username.toLowerCase().trim();
            User removedUser = usersByUsername.remove(normalizedUsername);
            if (removedUser != null) {
                allUsers.removeIf(u -> u.getUsername().equalsIgnoreCase(normalizedUsername));
                logger.info("User deleted: {}", maskUsername(normalizedUsername));
            } else {
                logger.warn("User not found for deletion: {}", maskUsername(normalizedUsername));
            }
        }
    }

    // FIXED: Renamed to match UserRepository interface
    @Override
    public boolean userExists(String username) {
        if (username == null || username.trim().isEmpty()) {
            return false;
        }
        
        String normalizedUsername = username.toLowerCase().trim();
        return usersByUsername.containsKey(normalizedUsername);
    }

    // FIXED: Renamed to match UserRepository interface
    @Override
    public long getUserCount() {
        return usersByUsername.size();
    }

    // FIXED: Added explicit implementation to override interface default
    @Override
    public boolean isHealthy() {
        try {
            return usersByUsername != null;
        } catch (Exception e) {
            logger.error("Repository health check failed", e);
            return false;
        }
    }

    /**
     * Masks username for logging (security).
     */
    private String maskUsername(String username) {
        if (username == null || username.length() <= 2) {
            return "***"; // Or return the original if too short to mask meaningfully
        }
        // More robust masking might be needed depending on username character sets
        return username.charAt(0) + "*".repeat(Math.max(0, username.length() - 2)) + username.charAt(username.length() - 1);
    }

    /**
     * Gets repository statistics for monitoring.
     * This is specific to this implementation and not part of the UserRepository interface.
     */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalUsers", usersByUsername.size());
        // Consider thread-safety if usersByUsername can be modified concurrently during stream processing.
        // For this local repo, it's likely okay if modifications are synchronized.
        long activeUsers = usersByUsername.values().stream()
                .filter(Objects::nonNull) // Add null check for robustness
                .filter(User::isActive) // Use method reference
                .count();
        stats.put("activeUsers", activeUsers);
        stats.put("inactiveUsers", usersByUsername.size() - activeUsers);
        stats.put("metadata", metadata); // Include metadata in stats
        
        return Collections.unmodifiableMap(stats);
    }

    // Inner DTO class for loading users from JSON
    // Simplified to match the core User domain model
    private static class UserDto {
        private String username;
        private String password; // Plain password from JSON - will be hashed
        private String status;
        private List<String> roles;

        // Getters - necessary for Jackson deserialization
        public String getUsername() { return username; }
        public String getPassword() { return password; }
        public String getStatus() { return status; }
        public List<String> getRoles() { return roles; }

        // Setters - needed for Jackson
        public void setUsername(String username) { this.username = username; }
        public void setPassword(String password) { this.password = password; }
        public void setStatus(String status) { this.status = status; }
        public void setRoles(List<String> roles) { this.roles = roles; }
    }

    // Inner class for repository metadata
    public static class RepositoryMetadata {
        private final int userCount;
        private final String source;

        public RepositoryMetadata(int userCount, String source) {
            this.userCount = userCount;
            this.source = source;
        }

        public int getUserCount() {
            return userCount;
        }

        public String getSource() {
            return source;
        }

        @Override
        public String toString() {
            return "RepositoryMetadata{" +
                    "userCount=" + userCount +
                    ", source=\'" + source + "\'" +
                    '}';
        }
    }
}