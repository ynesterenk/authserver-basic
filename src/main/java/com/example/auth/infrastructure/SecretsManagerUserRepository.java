package com.example.auth.infrastructure;

import com.example.auth.domain.model.User;
import com.example.auth.domain.model.UserStatus;
import com.example.auth.domain.port.UserRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import software.amazon.awssdk.services.secretsmanager.model.ResourceNotFoundException;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * AWS Secrets Manager implementation of UserRepository with caching.
 * 
 * This implementation:
 * - Fetches user credentials from AWS Secrets Manager
 * - Implements a TTL-based cache to reduce API calls
 * - Provides retry logic with exponential backoff
 * - Handles AWS SDK exceptions gracefully
 * - Maintains security by never logging sensitive data
 */
@Component
@Profile("aws")
public class SecretsManagerUserRepository implements UserRepository {

    private static final Logger logger = LoggerFactory.getLogger(SecretsManagerUserRepository.class);
    
    private final SecretsManagerClient secretsClient;
    private final String secretArn;
    private final ObjectMapper objectMapper;
    private final Cache<String, Map<String, User>> userCache;
    private final int cacheTtlMinutes;
    
    // Metrics
    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);
    private final AtomicLong secretsManagerCalls = new AtomicLong(0);
    
    public SecretsManagerUserRepository(
            SecretsManagerClient secretsClient,
            @Value("${auth.credential-secret-arn:#{environment.CREDENTIAL_SECRET_ARN}}") String secretArn,
            @Value("${auth.cache-ttl-minutes:5}") int cacheTtlMinutes,
            ObjectMapper objectMapper) {
        
        this.secretsClient = secretsClient;
        this.secretArn = secretArn;
        this.cacheTtlMinutes = cacheTtlMinutes;
        this.objectMapper = objectMapper;
        
        // Initialize cache with TTL
        this.userCache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(cacheTtlMinutes))
                .maximumSize(1) // Only cache one entry (the users map)
                .recordStats()
                .build();
        
        logger.info("SecretsManagerUserRepository initialized with secret ARN: {} and cache TTL: {} minutes", 
                   secretArn != null ? secretArn.substring(0, Math.min(secretArn.length(), 50)) + "..." : "null", 
                   cacheTtlMinutes);
    }

    @Override
    public Optional<User> findByUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            logger.debug("Username is null or empty");
            return Optional.empty();
        }

        try {
            Map<String, User> users = getUsersFromCacheOrSecretsManager();
            User user = users.get(username);
            
            if (user != null) {
                logger.debug("User found for username: {}", maskUsername(username));
                return Optional.of(user);
            } else {
                logger.debug("User not found for username: {}", maskUsername(username));
                return Optional.empty();
            }
            
        } catch (Exception e) {
            logger.error("Error retrieving user for username: {} - {}", maskUsername(username), e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public Map<String, User> getAllUsers() {
        try {
            Map<String, User> users = getUsersFromCacheOrSecretsManager();
            logger.debug("Retrieved {} users from repository", users.size());
            return Collections.unmodifiableMap(users);
        } catch (Exception e) {
            logger.error("Error retrieving all users: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * Retrieves users from cache first, falls back to Secrets Manager if cache miss.
     */
    private Map<String, User> getUsersFromCacheOrSecretsManager() {
        // Try cache first
        Map<String, User> cachedUsers = userCache.getIfPresent("users");
        if (cachedUsers != null) {
            cacheHits.incrementAndGet();
            logger.debug("Cache hit - using cached user data");
            return cachedUsers;
        }
        
        // Cache miss - fetch from Secrets Manager
        cacheMisses.incrementAndGet();
        logger.debug("Cache miss - fetching from Secrets Manager");
        
        Map<String, User> users = fetchUsersFromSecretsManager();
        
        // Cache the result
        userCache.put("users", users);
        logger.debug("User data cached for {} minutes", cacheTtlMinutes);
        
        return users;
    }

    /**
     * Fetches users from AWS Secrets Manager with retry logic.
     */
    private Map<String, User> fetchUsersFromSecretsManager() {
        if (secretArn == null || secretArn.trim().isEmpty()) {
            logger.error("Secret ARN is not configured");
            return Collections.emptyMap();
        }

        int maxRetries = 3;
        int baseDelayMs = 100;
        
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                secretsManagerCalls.incrementAndGet();
                
                GetSecretValueRequest request = GetSecretValueRequest.builder()
                        .secretId(secretArn)
                        .build();
                
                GetSecretValueResponse response = secretsClient.getSecretValue(request);
                String secretString = response.secretString();
                
                if (secretString == null || secretString.trim().isEmpty()) {
                    logger.error("Secret value is empty or null");
                    return Collections.emptyMap();
                }
                
                Map<String, User> users = parseUsersFromSecret(secretString);
                logger.info("Successfully loaded {} users from Secrets Manager", users.size());
                return users;
                
            } catch (ResourceNotFoundException e) {
                logger.error("Secret not found: {} - {}", secretArn, e.getMessage());
                return Collections.emptyMap();
                
            } catch (SdkException e) {
                logger.warn("AWS SDK error on attempt {} of {}: {}", attempt, maxRetries, e.getMessage());
                
                if (attempt == maxRetries) {
                    logger.error("Failed to fetch secret after {} attempts", maxRetries);
                    return Collections.emptyMap();
                }
                
                // Exponential backoff
                try {
                    Thread.sleep(baseDelayMs * (long) Math.pow(2, attempt - 1));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    logger.error("Interrupted during retry delay");
                    return Collections.emptyMap();
                }
                
            } catch (Exception e) {
                logger.error("Unexpected error fetching secret: {}", e.getMessage());
                return Collections.emptyMap();
            }
        }
        
        return Collections.emptyMap();
    }

    /**
     * Parses user data from the JSON secret string.
     * Expected format:
     * {
     *   "alice": { "passwordHash": "xxx", "status": "ACTIVE", "roles": ["admin"] },
     *   "bob": { "passwordHash": "yyy", "status": "ACTIVE", "roles": [] }
     * }
     */
    private Map<String, User> parseUsersFromSecret(String secretString) {
        try {
            JsonNode rootNode = objectMapper.readTree(secretString);
            Map<String, User> users = new java.util.HashMap<>();
            
            rootNode.fields().forEachRemaining(entry -> {
                String username = entry.getKey();
                JsonNode userNode = entry.getValue();
                
                try {
                    String passwordHash = userNode.get("passwordHash").asText();
                    String statusStr = userNode.has("status") ? userNode.get("status").asText() : "ACTIVE";
                    
                    UserStatus status;
                    try {
                        status = UserStatus.valueOf(statusStr.toUpperCase());
                    } catch (IllegalArgumentException e) {
                        logger.warn("Invalid status '{}' for user '{}', defaulting to ACTIVE", statusStr, maskUsername(username));
                        status = UserStatus.ACTIVE;
                    }
                    
                    List<String> roles = Collections.emptyList();
                    if (userNode.has("roles") && userNode.get("roles").isArray()) {
                        roles = objectMapper.convertValue(userNode.get("roles"), new TypeReference<List<String>>() {});
                    }
                    
                    User user = new User(username, passwordHash, status, roles);
                    
                    users.put(username, user);
                    logger.debug("Parsed user: {}", maskUsername(username));
                    
                } catch (Exception e) {
                    logger.error("Error parsing user data for username '{}': {}", maskUsername(username), e.getMessage());
                }
            });
            
            return users;
            
        } catch (Exception e) {
            logger.error("Error parsing secret JSON: {}", e.getMessage());
            return Collections.emptyMap();
        }
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
     * Returns cache statistics for monitoring.
     */
    public CacheStats getCacheStats() {
        var stats = userCache.stats();
        return new CacheStats(
            cacheHits.get(),
            cacheMisses.get(),
            secretsManagerCalls.get(),
            stats.hitRate(),
            (double) stats.averageLoadPenalty()
        );
    }
    
    /**
     * Manually evicts cache (useful for testing or forced refresh).
     */
    public void evictCache() {
        userCache.invalidateAll();
        logger.info("User cache manually evicted");
    }
    
    /**
     * Cache statistics data class.
     */
    public record CacheStats(
        long cacheHits,
        long cacheMisses,
        long secretsManagerCalls,
        double hitRate,
        double averageLoadTimeNanos
    ) {}
} 