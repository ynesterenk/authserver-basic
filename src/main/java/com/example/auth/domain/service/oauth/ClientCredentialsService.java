package com.example.auth.domain.service.oauth;

import com.example.auth.domain.model.oauth.OAuthError;
import com.example.auth.domain.model.oauth.TokenRequest;
import com.example.auth.domain.model.oauth.TokenResponse;

/**
 * Service interface for OAuth 2.0 Client Credentials Grant authentication.
 * This service handles the complete client credentials flow including client
 * authentication, scope validation, and token generation.
 *
 * @author Auth Server Team
 * @version 1.0
 * @since 1.0
 */
public interface ClientCredentialsService {

    /**
     * Authenticates a client using the Client Credentials Grant flow and generates an access token.
     * This method performs the complete OAuth 2.0 client credentials authentication including:
     * - Grant type validation (must be "client_credentials")
     * - Client authentication using client_id and client_secret
     * - Client status validation (must be ACTIVE)
     * - Scope validation against allowed scopes
     * - Access token generation with appropriate claims
     *
     * @param request the token request containing grant type, client credentials, and scope
     * @return a TokenResponse containing the generated access token and metadata
     * @throws OAuth2AuthenticationException if authentication fails with specific error details
     * @throws IllegalArgumentException if the request is null or invalid
     */
    TokenResponse authenticate(TokenRequest request);

    /**
     * Validates client credentials (client_id and client_secret) without generating a token.
     * This method can be used for client authentication verification in isolation.
     *
     * @param clientId the client identifier
     * @param clientSecret the client secret (plaintext)
     * @return true if the credentials are valid and client is active, false otherwise
     * @throws IllegalArgumentException if clientId or clientSecret is null or empty
     */
    boolean validateClientCredentials(String clientId, String clientSecret);

    /**
     * Validates if a client is authorized to request the specified scopes.
     * This method checks if all requested scopes are within the client's allowed scopes.
     *
     * @param clientId the client identifier
     * @param requestedScopes array of requested scope strings
     * @return true if all requested scopes are allowed for the client
     * @throws IllegalArgumentException if clientId is null or empty
     */
    boolean validateClientScopes(String clientId, String[] requestedScopes);

    /**
     * Checks if a client exists and is active (can authenticate).
     *
     * @param clientId the client identifier to check
     * @return true if the client exists and has ACTIVE status
     * @throws IllegalArgumentException if clientId is null or empty
     */
    boolean isClientActive(String clientId);

    /**
     * Gets the effective token expiration time for a client, considering both
     * the client's default expiration and any maximum system limits.
     *
     * @param clientId the client identifier
     * @param requestedExpiration the requested expiration time (optional)
     * @return the effective expiration time in seconds
     * @throws IllegalArgumentException if clientId is null or empty
     */
    int getEffectiveTokenExpiration(String clientId, Integer requestedExpiration);

    /**
     * Exception class for OAuth 2.0 authentication failures.
     * This exception carries OAuth 2.0 specific error information that can be
     * returned to the client according to RFC 6749 specifications.
     */
    class OAuth2AuthenticationException extends RuntimeException {
        
        private final OAuthError oauthError;

        /**
         * Constructs a new OAuth2AuthenticationException with the specified OAuth error.
         *
         * @param oauthError the OAuth 2.0 error details
         */
        public OAuth2AuthenticationException(OAuthError oauthError) {
            super(oauthError.getErrorDescription());
            this.oauthError = oauthError;
        }

        /**
         * Constructs a new OAuth2AuthenticationException with OAuth error and cause.
         *
         * @param oauthError the OAuth 2.0 error details
         * @param cause the underlying cause
         */
        public OAuth2AuthenticationException(OAuthError oauthError, Throwable cause) {
            super(oauthError.getErrorDescription(), cause);
            this.oauthError = oauthError;
        }

        /**
         * Gets the OAuth 2.0 error details.
         *
         * @return the OAuth error
         */
        public OAuthError getOAuthError() {
            return oauthError;
        }
    }
} 