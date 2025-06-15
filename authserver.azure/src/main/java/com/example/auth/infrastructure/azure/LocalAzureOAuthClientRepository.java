package com.example.auth.infrastructure.azure;

import com.example.auth.domain.model.oauth.OAuthClient;
import com.example.auth.domain.model.oauth.ClientStatus;
import com.example.auth.domain.port.oauth.OAuthClientRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Local file-based implementation of OAuthClientRepository for Azure Functions development.
 * Loads OAuth client data from a JSON file in the classpath.
 */
public class LocalAzureOAuthClientRepository implements OAuthClientRepository {

    private static final Logger logger = LoggerFactory.getLogger(LocalAzureOAuthClientRepository.class);
    private static final String DEFAULT_CLIENTS_FILE = "local-oauth-clients.json";
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final Map<String, OAuthClient> clients;
    private final Object lock = new Object();

    public LocalAzureOAuthClientRepository() {
        this.clients = new ConcurrentHashMap<>();
        loadClientsFromFile();
        logger.info("LocalAzureOAuthClientRepository initialized with {} clients", clients.size());
    }

    @Override
    public Map<String, OAuthClient> getAllClients() {
        return new HashMap<>(clients);
    }

    @Override
    public boolean existsByClientId(String clientId) {
        if (clientId == null || clientId.trim().isEmpty()) {
            return false;
        }
        String normalizedClientId = clientId.toLowerCase().trim();
        return clients.containsKey(normalizedClientId);
    }

    @Override
    public long getActiveClientCount() {
        return clients.values().stream()
                .mapToLong(client -> client.isActive() ? 1 : 0)
                .sum();
    }

    @Override
    public Map<String, OAuthClient> findClientsByAllowedScope(String scope) {
        if (scope == null || scope.trim().isEmpty()) {
            throw new IllegalArgumentException("Scope cannot be null or empty");
        }
        
        Map<String, OAuthClient> result = new HashMap<>();
        for (Map.Entry<String, OAuthClient> entry : clients.entrySet()) {
            if (entry.getValue().isScopeAllowed(scope)) {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }

    @Override
    public boolean isRepositoryHealthy() {
        try {
            getAllClients();
            return true;
        } catch (Exception e) {
            logger.error("Repository health check failed", e);
            return false;
        }
    }

    @Override
    public Map<String, Object> getRepositoryMetadata() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("type", "LocalAzureOAuthClientRepository");
        metadata.put("totalClients", clients.size());
        metadata.put("activeClients", getActiveClientCount());
        metadata.put("lastLoaded", System.currentTimeMillis());
        return metadata;
    }

    @Override
    public Optional<OAuthClient> findByClientId(String clientId) {
        if (clientId == null || clientId.trim().isEmpty()) {
            throw new IllegalArgumentException("Client ID cannot be null or empty");
        }

        String normalizedClientId = clientId.toLowerCase().trim();
        OAuthClient client = clients.get(normalizedClientId);
        
        if (client != null) {
            logger.debug("OAuth client found: {}", maskClientId(normalizedClientId));
        } else {
            logger.debug("OAuth client not found: {}", maskClientId(normalizedClientId));
        }
        
        return Optional.ofNullable(client);
    }

    // Additional methods for local repository functionality
    public List<OAuthClient> findAll() {
        return new ArrayList<>(clients.values());
    }

    public void save(OAuthClient client) {
        if (client == null) {
            throw new IllegalArgumentException("OAuth client cannot be null");
        }

        synchronized (lock) {
            String normalizedClientId = client.getClientId().toLowerCase().trim();
            clients.put(normalizedClientId, client);
            logger.info("OAuth client saved: {}", maskClientId(normalizedClientId));
        }
    }

    public void deleteByClientId(String clientId) {
        if (clientId == null || clientId.trim().isEmpty()) {
            throw new IllegalArgumentException("Client ID cannot be null or empty");
        }

        synchronized (lock) {
            String normalizedClientId = clientId.toLowerCase().trim();
            OAuthClient removedClient = clients.remove(normalizedClientId);
            if (removedClient != null) {
                logger.info("OAuth client deleted: {}", maskClientId(normalizedClientId));
            } else {
                logger.warn("OAuth client not found for deletion: {}", maskClientId(normalizedClientId));
            }
        }
    }

    public long count() {
        return clients.size();
    }

    /**
     * Loads OAuth clients from the local JSON file.
     */
    private void loadClientsFromFile() {
        try {
            ClassPathResource resource = new ClassPathResource(DEFAULT_CLIENTS_FILE);
            if (!resource.exists()) {
                logger.warn("Local OAuth clients file not found: {}, creating default clients", DEFAULT_CLIENTS_FILE);
                createDefaultClients();
                return;
            }

            try (InputStream inputStream = resource.getInputStream()) {
                List<Map<String, Object>> clientDataList = objectMapper.readValue(
                    inputStream, 
                    new TypeReference<List<Map<String, Object>>>() {}
                );

                int loadedCount = 0;
                for (Map<String, Object> clientData : clientDataList) {
                    try {
                        OAuthClient client = parseClientFromData(clientData);
                        if (client != null) {
                            String normalizedClientId = client.getClientId().toLowerCase().trim();
                            clients.put(normalizedClientId, client);
                            loadedCount++;
                        }
                    } catch (Exception e) {
                        logger.warn("Failed to parse OAuth client data: {}", clientData, e);
                    }
                }

                logger.info("Loaded {} OAuth clients from {}", loadedCount, DEFAULT_CLIENTS_FILE);
            }

        } catch (Exception e) {
            logger.error("Failed to load OAuth clients from file: {}", DEFAULT_CLIENTS_FILE, e);
            createDefaultClients();
        }
    }

    /**
     * Parses an OAuthClient object from JSON data.
     * Note: This method is only used during initialization and not called elsewhere.
     */
    private OAuthClient parseClientFromData(Map<String, Object> clientData) {
        String clientId = (String) clientData.get("clientId");
        String clientSecret = (String) clientData.get("clientSecret");
        String clientSecretHash = (String) clientData.get("clientSecretHash");
        String description = (String) clientData.get("description");
        String statusString = (String) clientData.get("status");
        Integer tokenExpirationSeconds = (Integer) clientData.get("tokenExpirationSeconds");
        
        @SuppressWarnings("unchecked")
        List<String> allowedGrantTypesStr = (List<String>) clientData.get("allowedGrantTypes");
        @SuppressWarnings("unchecked")
        List<String> allowedScopes = (List<String>) clientData.get("allowedScopes");

        if (clientId == null || clientId.trim().isEmpty()) {
            logger.warn("Invalid OAuth client data: missing clientId");
            return null;
        }

        // Handle client secret hashing if plain secret is provided
        if (clientSecretHash == null && clientSecret != null) {
            // For local development, we'll use a simple hash (in production, use proper hashing)
            clientSecretHash = hashClientSecret(clientSecret);
            logger.debug("Generated client secret hash for client: {}", maskClientId(clientId));
        }

        if (clientSecretHash == null) {
            logger.warn("Invalid OAuth client data: missing clientSecret or clientSecretHash for client {}", 
                       maskClientId(clientId));
            return null;
        }

        // Parse grant types - use String set as per real domain model
        Set<String> allowedGrantTypes = new HashSet<>();
        if (allowedGrantTypesStr != null) {
            for (String grantTypeStr : allowedGrantTypesStr) {
                // Validate known grant types
                if (isValidGrantType(grantTypeStr)) {
                    allowedGrantTypes.add(grantTypeStr);
                } else {
                    logger.warn("Invalid grant type '{}' for client '{}', skipping", 
                               grantTypeStr, maskClientId(clientId));
                }
            }
        }

        // Default to CLIENT_CREDENTIALS if no grant types specified
        if (allowedGrantTypes.isEmpty()) {
            allowedGrantTypes.add("client_credentials");
        }

        // Parse status - use real ClientStatus enum
        ClientStatus status = ClientStatus.ACTIVE; // Default
        if (statusString != null) {
            try {
                status = ClientStatus.valueOf(statusString.toUpperCase());
            } catch (IllegalArgumentException e) {
                logger.warn("Invalid client status '{}' for client '{}', using ACTIVE", 
                           statusString, maskClientId(clientId));
            }
        }

        // Set default values
        if (allowedScopes == null) {
            allowedScopes = Arrays.asList("read", "write");
        }
        if (tokenExpirationSeconds == null) {
            tokenExpirationSeconds = 3600; // Default 1 hour
        }
        if (description == null) {
            description = clientId + " client";
        }

        // Use real OAuthClient constructor
        return new OAuthClient(
            clientId,
            clientSecretHash,
            status,
            allowedScopes,
            allowedGrantTypes,
            tokenExpirationSeconds,
            description
        );
    }

    /**
     * Validates if a grant type string is supported.
     */
    private boolean isValidGrantType(String grantType) {
        // List of supported grant types as per OAuth 2.0 specification
        return grantType != null && (
            "client_credentials".equals(grantType) ||
            "authorization_code".equals(grantType) ||
            "refresh_token".equals(grantType)
        );
    }

    /**
     * Creates default OAuth clients for development.
     */
    private void createDefaultClients() {
        logger.info("Creating default OAuth clients for local development");

        try {
            // Create demo client
            OAuthClient demoClient = new OAuthClient(
                "demo-client",
                hashClientSecret("demo-secret"),
                ClientStatus.ACTIVE,
                Arrays.asList("read", "write"),
                Set.of("client_credentials"),
                3600,
                "Demo Client"
            );

            // Create test client
            OAuthClient testClient = new OAuthClient(
                "test-client",
                hashClientSecret("test-secret"),
                ClientStatus.ACTIVE,
                Arrays.asList("read"),
                Set.of("client_credentials"),
                1800,
                "Test Client"
            );

            // Create admin client
            OAuthClient adminClient = new OAuthClient(
                "admin-client",
                hashClientSecret("admin-secret"),
                ClientStatus.ACTIVE,
                Arrays.asList("read", "write", "admin"),
                Set.of("client_credentials"),
                7200,
                "Admin Client"
            );

            clients.put("demo-client", demoClient);
            clients.put("test-client", testClient);
            clients.put("admin-client", adminClient);

            logger.info("Created {} default OAuth clients", clients.size());

        } catch (Exception e) {
            logger.error("Failed to create default OAuth clients", e);
        }
    }

    /**
     * Simple client secret hashing for local development.
     * In production, use proper cryptographic hashing.
     */
    private String hashClientSecret(String clientSecret) {
        // For local development, use a simple hash
        // In production, this should use a proper password hashing library
        return "$2a$10$" + Base64.getEncoder().encodeToString(clientSecret.getBytes());
    }

    /**
     * Masks client ID for logging (security).
     */
    private String maskClientId(String clientId) {
        if (clientId == null || clientId.length() <= 4) {
            return "****";
        }
        return clientId.substring(0, 2) + "****" + clientId.substring(clientId.length() - 2);
    }

    /**
     * Gets repository statistics for monitoring.
     */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalClients", clients.size());
        
        long activeClients = clients.values().stream()
                .mapToLong(client -> client.isActive() ? 1 : 0)
                .sum();
        stats.put("activeClients", activeClients);
        stats.put("inactiveClients", clients.size() - activeClients);
        
        return stats;
    }
} 