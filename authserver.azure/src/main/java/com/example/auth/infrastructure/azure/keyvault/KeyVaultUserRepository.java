package com.example.auth.infrastructure.azure.keyvault;

import com.azure.core.exception.ResourceNotFoundException;
import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.models.KeyVaultSecret;
import com.example.auth.domain.model.User;
import com.example.auth.domain.model.UserStatus;
import com.example.auth.domain.port.UserRepository;
import com.example.auth.domain.util.PasswordHasher;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Azure Key Vault implementation of UserRepository.
 * Stores user data as JSON secrets in Azure Key Vault with caching.
 */
public class KeyVaultUserRepository implements UserRepository {

    private static final Logger logger = LoggerFactory.getLogger(KeyVaultUserRepository.class);
    private static final String USER_SECRET_PREFIX = "user-";
    private static final String USER_LIST_SECRET = "user-list";
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final SecretClient secretClient;
    private final PasswordHasher passwordHasher;
    private final Cache<String, Optional<User>> userCache;
    private final Cache<String, Set<String>> userListCache;

    public KeyVaultUserRepository(SecretClient secretClient, 
                                 PasswordHasher passwordHasher, 
                                 int cacheTtlMinutes, 
                                 long cacheMaxSize) {
        this.secretClient = secretClient;
        this.passwordHasher = passwordHasher;
        
        // Configure user cache
        this.userCache = Caffeine.newBuilder()
                .maximumSize(cacheMaxSize)
                .expireAfterWrite(Duration.ofMinutes(cacheTtlMinutes))
                .build();
        
        // Configure user list cache
        this.userListCache = Caffeine.newBuilder()
                .maximumSize(10) // Small cache for user lists
                .expireAfterWrite(Duration.ofMinutes(cacheTtlMinutes))
                .build();

        logger.info("KeyVaultUserRepository initialized with cache TTL: {} minutes, max size: {}", 
                   cacheTtlMinutes, cacheMaxSize);
    }

    @Override
    public Map<String, User> getAllUsers() {
        try {
            Map<String, User> allUsers = new HashMap<>();
            // Get all usernames from Key Vault
            // This would typically be done by listing secrets with a common prefix
            
            // For demonstration, we'll load from a known list
            // In practice, you'd query Key Vault for secrets with user prefix
            List<String> usernames = Arrays.asList("demo", "admin", "test");
            
            for (String username : usernames) {
                Optional<User> user = findByUsername(username);
                if (user.isPresent()) {
                    allUsers.put(username, user.get());
                }
            }
            
            return allUsers;
        } catch (Exception e) {
            logger.error("Failed to get all users from Key Vault", e);
            return new HashMap<>();
        }
    }

    @Override
    public Optional<User> findByUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username cannot be null or empty");
        }

        String normalizedUsername = username.toLowerCase().trim();
        
        try {
            // Check cache first
            Optional<User> cachedUser = userCache.getIfPresent(normalizedUsername);
            if (cachedUser != null) {
                logger.debug("User found in cache: {}", maskUsername(normalizedUsername));
                return cachedUser;
            }

            // Load from Key Vault
            String secretName = USER_SECRET_PREFIX + normalizedUsername;
            Optional<User> user = loadUserFromKeyVault(secretName, normalizedUsername);
            
            // Cache the result (including empty Optional)
            userCache.put(normalizedUsername, user);
            
            return user;

        } catch (Exception e) {
            logger.error("Error finding user '{}': {}", maskUsername(normalizedUsername), e.getMessage());
            return Optional.empty();
        }
    }

    // Additional methods for Key Vault repository functionality
    public List<User> findAll() {
        try {
            // Get list of all usernames
            Set<String> usernames = getUsernameList();
            List<User> users = new ArrayList<>();

            // CRITICAL: Avoid lambda expressions with loop variables
            for (String username : usernames) {
                Optional<User> user = findByUsername(username);
                if (user.isPresent()) {
                    users.add(user.get());
                }
            }

            logger.debug("Loaded {} users from Key Vault", users.size());
            return users;

        } catch (Exception e) {
            logger.error("Error loading all users: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    public void save(User user) {
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null");
        }

        String normalizedUsername = user.getUsername().toLowerCase().trim();
        
        try {
            // Convert user to JSON
            Map<String, Object> userData = new HashMap<>();
            userData.put("username", user.getUsername());
            userData.put("passwordHash", user.getPasswordHash());
            userData.put("status", user.getStatus().name());
            userData.put("roles", user.getRoles());
            userData.put("lastModified", System.currentTimeMillis());

            String userJson = objectMapper.writeValueAsString(userData);
            String secretName = USER_SECRET_PREFIX + normalizedUsername;

            // Save to Key Vault
            secretClient.setSecret(secretName, userJson);
            
            // Update user list
            updateUserList(normalizedUsername, true);
            
            // Update cache
            userCache.put(normalizedUsername, Optional.of(user));
            
            logger.info("User saved successfully: {}", maskUsername(normalizedUsername));

        } catch (Exception e) {
            logger.error("Error saving user '{}': {}", maskUsername(normalizedUsername), e.getMessage());
            throw new RuntimeException("Failed to save user to Key Vault", e);
        }
    }

    public void deleteByUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username cannot be null or empty");
        }

        String normalizedUsername = username.toLowerCase().trim();
        
        try {
            String secretName = USER_SECRET_PREFIX + normalizedUsername;
            
            // Delete from Key Vault
            secretClient.beginDeleteSecret(secretName);
            
            // Update user list
            updateUserList(normalizedUsername, false);
            
            // Remove from cache
            userCache.invalidate(normalizedUsername);
            
            logger.info("User deleted successfully: {}", maskUsername(normalizedUsername));

        } catch (ResourceNotFoundException e) {
            logger.warn("User not found for deletion: {}", maskUsername(normalizedUsername));
        } catch (Exception e) {
            logger.error("Error deleting user '{}': {}", maskUsername(normalizedUsername), e.getMessage());
            throw new RuntimeException("Failed to delete user from Key Vault", e);
        }
    }

    public boolean existsByUsername(String username) {
        return findByUsername(username).isPresent();
    }

    public long count() {
        try {
            return getUsernameList().size();
        } catch (Exception e) {
            logger.error("Error counting users: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * Loads a user from Key Vault by secret name.
     */
    private Optional<User> loadUserFromKeyVault(String secretName, String username) {
        try {
            KeyVaultSecret secret = secretClient.getSecret(secretName);
            if (secret == null || secret.getValue() == null) {
                logger.debug("User not found in Key Vault: {}", maskUsername(username));
                return Optional.empty();
            }

            // Parse JSON data
            Map<String, Object> userData = objectMapper.readValue(
                secret.getValue(), 
                new TypeReference<Map<String, Object>>() {}
            );

            // Extract user data
            String storedUsername = (String) userData.get("username");
            String plainPassword = (String) userData.get("password");
            String preHashedPassword = (String) userData.get("passwordHash");
            String statusString = (String) userData.get("status");
            
            // Handle both plain text passwords and pre-hashed passwords
            String passwordHash;
            if (preHashedPassword != null && !preHashedPassword.trim().isEmpty()) {
                // Use pre-hashed password if available
                passwordHash = preHashedPassword;
            } else if (plainPassword != null && !plainPassword.trim().isEmpty()) {
                // Hash plain text password
                passwordHash = passwordHasher.hashPassword(plainPassword);
            } else {
                // No valid password found
                passwordHash = null;
            }
            
            @SuppressWarnings("unchecked")
            List<String> roles = (List<String>) userData.get("roles");
            if (roles == null) {
                roles = new ArrayList<>();
            }

            // Parse status
            UserStatus status;
            try {
                status = UserStatus.valueOf(statusString);
            } catch (Exception e) {
                logger.warn("Invalid user status '{}' for user '{}', defaulting to DISABLED", 
                           statusString, maskUsername(username));
                status = UserStatus.DISABLED;
            }

            // CRITICAL: Use correct User constructor - new User(username, passwordHash, status, roles)
            User user = new User(storedUsername, passwordHash, status, roles);

            logger.debug("User loaded from Key Vault: {}", maskUsername(username));
            return Optional.of(user);

        } catch (ResourceNotFoundException e) {
            logger.debug("User not found in Key Vault: {}", maskUsername(username));
            return Optional.empty();
        } catch (Exception e) {
            logger.error("Error loading user '{}' from Key Vault: {}", maskUsername(username), e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Gets the list of all usernames from Key Vault.
     */
    private Set<String> getUsernameList() {
        try {
            // Check cache first
            Set<String> cachedList = userListCache.getIfPresent(USER_LIST_SECRET);
            if (cachedList != null) {
                return cachedList;
            }

            // Load from Key Vault
            try {
                KeyVaultSecret secret = secretClient.getSecret(USER_LIST_SECRET);
                if (secret != null && secret.getValue() != null) {
                    @SuppressWarnings("unchecked")
                    List<String> usernameList = objectMapper.readValue(
                        secret.getValue(), 
                        new TypeReference<List<String>>() {}
                    );
                    Set<String> usernameSet = new HashSet<>(usernameList);
                    userListCache.put(USER_LIST_SECRET, usernameSet);
                    return usernameSet;
                }
            } catch (ResourceNotFoundException e) {
                logger.debug("User list not found in Key Vault, returning empty set");
            }

            // Return empty set if not found
            Set<String> emptySet = new HashSet<>();
            userListCache.put(USER_LIST_SECRET, emptySet);
            return emptySet;

        } catch (Exception e) {
            logger.error("Error loading username list: {}", e.getMessage());
            return new HashSet<>();
        }
    }

    /**
     * Updates the username list in Key Vault.
     */
    private void updateUserList(String username, boolean add) {
        try {
            Set<String> usernames = new HashSet<>(getUsernameList());
            
            if (add) {
                usernames.add(username);
            } else {
                usernames.remove(username);
            }

            // Save updated list
            List<String> usernameList = new ArrayList<>(usernames);
            String listJson = objectMapper.writeValueAsString(usernameList);
            secretClient.setSecret(USER_LIST_SECRET, listJson);
            
            // Update cache
            userListCache.put(USER_LIST_SECRET, usernames);

        } catch (Exception e) {
            logger.error("Error updating username list: {}", e.getMessage());
            // Don't throw exception here as it's not critical for the main operation
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
     * Clears all caches.
     */
    public void clearCache() {
        userCache.invalidateAll();
        userListCache.invalidateAll();
        logger.info("KeyVaultUserRepository cache cleared");
    }

    /**
     * Gets cache statistics for monitoring.
     */
    public Map<String, Object> getCacheStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("userCacheSize", userCache.estimatedSize());
        stats.put("userListCacheSize", userListCache.estimatedSize());
        return stats;
    }
} 