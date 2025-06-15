package com.example.auth.domain.model.oauth;

import jakarta.validation.constraints.NotBlank;
import java.util.Objects;

/**
 * OAuth 2.0 Token Request domain model representing a client credentials grant request.
 * This follows RFC 6749 Section 4.4 specification.
 *
 * @author Auth Server Team
 * @version 1.0
 * @since 1.0
 */
public class TokenRequest {

    @NotBlank(message = "Grant type cannot be blank")
    private final String grantType;

    @NotBlank(message = "Client ID cannot be blank")
    private final String clientId;

    @NotBlank(message = "Client secret cannot be blank")
    private final String clientSecret;

    private final String scope;

    /**
     * Constructs a new TokenRequest with the specified parameters.
     *
     * @param grantType the OAuth 2.0 grant type (must be "client_credentials")
     * @param clientId the client identifier
     * @param clientSecret the client secret
     * @param scope the requested scope (optional)
     * @throws IllegalArgumentException if validation fails
     */
    public TokenRequest(String grantType, String clientId, String clientSecret, String scope) {
        this.grantType = validateNotBlank(grantType, "Grant type cannot be blank");
        this.clientId = validateNotBlank(clientId, "Client ID cannot be blank");
        this.clientSecret = validateNotBlank(clientSecret, "Client secret cannot be blank");
        this.scope = scope;

        // Validate grant type is client_credentials
        if (!"client_credentials".equals(this.grantType)) {
            throw new IllegalArgumentException("Only client_credentials grant type is supported, got: " + this.grantType);
        }
    }

    /**
     * Checks if this is a valid client credentials request.
     *
     * @return true if the grant type is client_credentials
     */
    public boolean isClientCredentialsGrant() {
        return "client_credentials".equals(grantType);
    }

    /**
     * Gets the requested scopes as an array, splitting on whitespace.
     * Returns empty array if no scope is specified.
     *
     * @return array of requested scopes
     */
    public String[] getRequestedScopes() {
        if (scope == null || scope.trim().isEmpty()) {
            return new String[0];
        }
        return scope.trim().split("\\s+");
    }

    /**
     * Checks if a specific scope is requested.
     *
     * @param targetScope the scope to check for
     * @return true if the scope is requested, false otherwise
     */
    public boolean hasScopeRequested(String targetScope) {
        if (targetScope == null || scope == null) {
            return false;
        }
        String[] requestedScopes = getRequestedScopes();
        for (String requestedScope : requestedScopes) {
            if (targetScope.equals(requestedScope)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Gets a normalized scope string with consistent formatting.
     *
     * @return normalized scope string or null if no scope
     */
    public String getNormalizedScope() {
        if (scope == null || scope.trim().isEmpty()) {
            return null;
        }
        return String.join(" ", getRequestedScopes());
    }

    // Getters
    public String getGrantType() {
        return grantType;
    }

    public String getClientId() {
        return clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public String getScope() {
        return scope;
    }

    // Validation helper method
    private String validateNotBlank(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TokenRequest that = (TokenRequest) o;
        return Objects.equals(grantType, that.grantType) &&
                Objects.equals(clientId, that.clientId) &&
                Objects.equals(clientSecret, that.clientSecret) &&
                Objects.equals(scope, that.scope);
    }

    @Override
    public int hashCode() {
        return Objects.hash(grantType, clientId, clientSecret, scope);
    }

    @Override
    public String toString() {
        return "TokenRequest{" +
                "grantType='" + grantType + '\'' +
                ", clientId='" + clientId + '\'' +
                ", clientSecret='[PROTECTED]'" +
                ", scope='" + scope + '\'' +
                '}';
    }
} 