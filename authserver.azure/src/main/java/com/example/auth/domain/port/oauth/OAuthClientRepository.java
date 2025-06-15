package com.example.auth.domain.port.oauth;

import com.example.auth.domain.model.oauth.OAuthClient;
import java.util.Map;
import java.util.Optional;

/**
 * Repository port interface for OAuth 2.0 client data access.
 * This follows the hexagonal architecture pattern by defining the port
 * for client data access without specifying the implementation details.
 *
 * @author Auth Server Team
 * @version 1.0
 * @since 1.0
 */
public interface OAuthClientRepository {

    /**
     * Finds an OAuth client by its client ID.
     * This method should return an empty Optional if the client is not found,
     * rather than throwing an exception.
     *
     * @param clientId the unique client identifier
     * @return an Optional containing the OAuthClient if found, empty otherwise
     * @throws IllegalArgumentException if clientId is null or empty
     */
    Optional<OAuthClient> findByClientId(String clientId);

    /**
     * Retrieves all OAuth clients in the system.
     * This method returns a map for efficient client lookup by client ID.
     * The returned map should be immutable or a copy to prevent external modification.
     *
     * @return an immutable map of client ID to OAuthClient
     */
    Map<String, OAuthClient> getAllClients();

    /**
     * Checks if a client with the specified client ID exists.
     * This is a convenience method that can be more efficient than findByClientId
     * when only existence needs to be verified.
     *
     * @param clientId the client identifier to check
     * @return true if a client with the given ID exists, false otherwise
     * @throws IllegalArgumentException if clientId is null or empty
     */
    boolean existsByClientId(String clientId);

    /**
     * Gets the count of active OAuth clients in the system.
     * This method counts only clients with ACTIVE status.
     *
     * @return the number of active clients
     */
    long getActiveClientCount();

    /**
     * Finds all clients that have the specified scope in their allowed scopes.
     * This can be useful for administrative purposes or scope analysis.
     *
     * @param scope the scope to search for
     * @return a map of client ID to OAuthClient for clients that allow the scope
     * @throws IllegalArgumentException if scope is null or empty
     */
    Map<String, OAuthClient> findClientsByAllowedScope(String scope);

    /**
     * Validates that a client repository is properly configured and accessible.
     * This method can be used for health checks and startup validation.
     *
     * @return true if the repository is accessible and properly configured
     */
    boolean isRepositoryHealthy();

    /**
     * Gets metadata about the client repository such as the number of clients,
     * last update time, etc. This is useful for monitoring and diagnostics.
     *
     * @return a map containing repository metadata
     */
    Map<String, Object> getRepositoryMetadata();
} 