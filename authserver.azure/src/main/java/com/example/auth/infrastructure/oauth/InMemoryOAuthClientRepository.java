package com.example.auth.infrastructure.oauth;

import com.example.auth.domain.model.oauth.OAuthClient;
import com.example.auth.domain.model.oauth.ClientStatus;
import com.example.auth.domain.port.oauth.OAuthClientRepository;
import com.example.auth.domain.util.oauth.ClientSecretHasher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;

/**
 * In-memory implementation of OAuthClientRepository for testing and local development.
 * This implementation stores OAuth clients in memory with pre-configured test clients.
 */
public class InMemoryOAuthClientRepository implements OAuthClientRepository {

    private static final Logger logger = LoggerFactory.getLogger(InMemoryOAuthClientRepository.class);
    
    private final Map<String, OAuthClient> clients = new HashMap<>();
    private final ClientSecretHasher secretHasher;

    public InMemoryOAuthClientRepository(ClientSecretHasher secretHasher) {
        this.secretHasher = secretHasher;
        initializeTestClients();
        logger.info("InMemoryOAuthClientRepository initialized with {} test clients", clients.size());
    }

    @Override
    public Optional<OAuthClient> findByClientId(String clientId) {
        OAuthClient client = clients.get(clientId);
        return Optional.ofNullable(client);
    }

    @Override
    public Map<String, OAuthClient> getAllClients() {
        return Map.copyOf(clients);
    }

    @Override
    public boolean existsByClientId(String clientId) {
        return clients.containsKey(clientId);
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
        return Map.copyOf(result);
    }

    @Override
    public boolean isRepositoryHealthy() {
        return true; // In-memory repository is always healthy
    }

    @Override
    public Map<String, Object> getRepositoryMetadata() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("type", "in-memory");
        metadata.put("totalClients", clients.size());
        metadata.put("activeClients", getActiveClientCount());
        metadata.put("lastUpdated", Instant.now().toString());
        metadata.put("healthy", true);
        return Map.copyOf(metadata);
    }

    /**
     * Initializes test OAuth clients for development and testing.
     */
    private void initializeTestClients() {
        // Test client 1 - Full permissions
        String client1Secret = secretHasher.hashClientSecret("test-client-1-secret");
        OAuthClient client1 = new OAuthClient(
            "test-client-1",
            client1Secret,
            ClientStatus.ACTIVE,
            List.of("read", "write", "admin"),
            Set.of("client_credentials"),
            3600, // default token expiration
            "Test Client 1 - Full permissions"
        );
        clients.put(client1.getClientId(), client1);

        // Test client 2 - Read only
        String client2Secret = secretHasher.hashClientSecret("test-client-2-secret");
        OAuthClient client2 = new OAuthClient(
            "test-client-2",
            client2Secret,
            ClientStatus.ACTIVE,
            List.of("read"),
            Set.of("client_credentials"),
            1800, // 30 minutes default
            "Test Client 2 - Read only permissions"
        );
        clients.put(client2.getClientId(), client2);

        // Test client 3 - Disabled
        String client3Secret = secretHasher.hashClientSecret("test-client-3-secret");
        OAuthClient client3 = new OAuthClient(
            "test-client-3",
            client3Secret,
            ClientStatus.DISABLED,
            List.of("read", "write"),
            Set.of("client_credentials"),
            3600,
            "Test Client 3 - Disabled client"
        );
        clients.put(client3.getClientId(), client3);

        logger.info("Initialized {} test OAuth clients", clients.size());
        logger.debug("Test clients: test-client-1 (active, read/write/admin), test-client-2 (active, read only), test-client-3 (disabled)");
    }
}