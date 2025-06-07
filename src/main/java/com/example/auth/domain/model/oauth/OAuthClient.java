package com.example.auth.domain.model.oauth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * OAuth 2.0 Client domain model representing a registered client application
 * that can authenticate using the Client Credentials Grant flow.
 *
 * @author Auth Server Team
 * @version 1.0
 * @since 1.0
 */
public class OAuthClient {

    @NotBlank(message = "Client ID cannot be blank")
    private final String clientId;

    @NotBlank(message = "Client secret hash cannot be blank")
    private final String clientSecretHash;

    @NotNull(message = "Client status cannot be null")
    private final ClientStatus status;

    @NotNull(message = "Allowed scopes cannot be null")
    private final List<String> allowedScopes;

    @NotNull(message = "Allowed grant types cannot be null")
    private final Set<String> allowedGrantTypes;

    @Positive(message = "Token expiration must be positive")
    private final Integer tokenExpirationSeconds;

    private final String description;

    /**
     * Constructs a new OAuthClient with the specified parameters.
     *
     * @param clientId the unique identifier for the client
     * @param clientSecretHash the hashed client secret
     * @param status the current status of the client
     * @param allowedScopes the list of scopes this client is allowed to request
     * @param allowedGrantTypes the set of grant types this client can use
     * @param tokenExpirationSeconds the default token expiration time in seconds
     * @param description a human-readable description of the client
     * @throws IllegalArgumentException if any validation fails
     */
    public OAuthClient(String clientId, 
                      String clientSecretHash, 
                      ClientStatus status,
                      List<String> allowedScopes, 
                      Set<String> allowedGrantTypes,
                      Integer tokenExpirationSeconds, 
                      String description) {
        this.clientId = validateNotBlank(clientId, "Client ID cannot be blank");
        this.clientSecretHash = validateNotBlank(clientSecretHash, "Client secret hash cannot be blank");
        this.status = Objects.requireNonNull(status, "Client status cannot be null");
        this.allowedScopes = Objects.requireNonNull(allowedScopes, "Allowed scopes cannot be null");
        this.allowedGrantTypes = Objects.requireNonNull(allowedGrantTypes, "Allowed grant types cannot be null");
        this.tokenExpirationSeconds = validatePositive(tokenExpirationSeconds, "Token expiration must be positive");
        this.description = description;

        // Validate that client credentials grant type is included
        if (!allowedGrantTypes.contains("client_credentials")) {
            throw new IllegalArgumentException("Client must support client_credentials grant type");
        }
    }

    /**
     * Checks if this client is active and can authenticate.
     *
     * @return true if the client status is ACTIVE, false otherwise
     */
    public boolean isActive() {
        return status == ClientStatus.ACTIVE;
    }

    /**
     * Checks if this client is allowed to request the specified scope.
     *
     * @param scope the scope to check
     * @return true if the scope is allowed, false otherwise
     */
    public boolean isScopeAllowed(String scope) {
        if (scope == null || scope.trim().isEmpty()) {
            return true; // Empty scope is always allowed
        }
        return allowedScopes.contains(scope.trim());
    }

    /**
     * Checks if this client supports the specified grant type.
     *
     * @param grantType the grant type to check
     * @return true if the grant type is supported, false otherwise
     */
    public boolean isGrantTypeSupported(String grantType) {
        return allowedGrantTypes.contains(grantType);
    }

    /**
     * Gets the effective token expiration time, ensuring it doesn't exceed the maximum allowed.
     *
     * @param requestedExpiration the requested expiration time (optional)
     * @param maxAllowedExpiration the maximum allowed expiration time
     * @return the effective expiration time in seconds
     */
    public int getEffectiveTokenExpiration(Integer requestedExpiration, int maxAllowedExpiration) {
        int effectiveExpiration = (requestedExpiration != null) ? requestedExpiration : tokenExpirationSeconds;
        return Math.min(effectiveExpiration, maxAllowedExpiration);
    }

    // Getters
    public String getClientId() {
        return clientId;
    }

    public String getClientSecretHash() {
        return clientSecretHash;
    }

    public ClientStatus getStatus() {
        return status;
    }

    public List<String> getAllowedScopes() {
        return List.copyOf(allowedScopes); // Return immutable copy
    }

    public Set<String> getAllowedGrantTypes() {
        return Set.copyOf(allowedGrantTypes); // Return immutable copy
    }

    public Integer getTokenExpirationSeconds() {
        return tokenExpirationSeconds;
    }

    public String getDescription() {
        return description;
    }

    // Validation helper methods
    private String validateNotBlank(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private Integer validatePositive(Integer value, String message) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OAuthClient that = (OAuthClient) o;
        return Objects.equals(clientId, that.clientId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(clientId);
    }

    @Override
    public String toString() {
        return "OAuthClient{" +
                "clientId='" + clientId + '\'' +
                ", status=" + status +
                ", allowedScopes=" + allowedScopes +
                ", allowedGrantTypes=" + allowedGrantTypes +
                ", tokenExpirationSeconds=" + tokenExpirationSeconds +
                ", description='" + description + '\'' +
                '}';
    }
} 