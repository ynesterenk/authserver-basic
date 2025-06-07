package com.example.auth.domain.util.oauth;

import com.example.auth.domain.model.oauth.OAuthError;
import com.example.auth.domain.model.oauth.TokenRequest;
import com.example.auth.domain.service.oauth.ClientCredentialsService.OAuth2AuthenticationException;
import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Utility class for parsing OAuth 2.0 token requests from HTTP requests.
 * This class handles both Basic Authentication and form-encoded client credentials
 * according to RFC 6749 specifications.
 *
 * @author Auth Server Team
 * @version 1.0
 * @since 1.0
 */
public class OAuth2RequestParser {

    private static final Logger logger = LoggerFactory.getLogger(OAuth2RequestParser.class);

    // OAuth 2.0 request parameters
    private static final String PARAM_GRANT_TYPE = "grant_type";
    private static final String PARAM_CLIENT_ID = "client_id";
    private static final String PARAM_CLIENT_SECRET = "client_secret";
    private static final String PARAM_SCOPE = "scope";

    // HTTP headers
    private static final String HEADER_AUTHORIZATION = "Authorization";
    private static final String BASIC_AUTH_PREFIX = "Basic ";

    /**
     * Parses an OAuth 2.0 token request from form parameters and HTTP headers.
     * Supports both Basic Authentication and form-encoded client credentials.
     *
     * @param formParameters the form parameters from the HTTP request
     * @param authorizationHeader the Authorization header value (may be null)
     * @return a TokenRequest object with parsed parameters
     * @throws OAuth2AuthenticationException if the request is invalid
     */
    public static TokenRequest parseTokenRequest(Map<String, String> formParameters, String authorizationHeader) {
        if (formParameters == null) {
            throw new OAuth2AuthenticationException(
                OAuthError.invalidRequest("Form parameters cannot be null")
            );
        }

        try {
            // Extract grant type (required)
            String grantType = formParameters.get(PARAM_GRANT_TYPE);
            if (grantType == null || grantType.trim().isEmpty()) {
                throw new OAuth2AuthenticationException(
                    OAuthError.invalidRequest("Missing grant_type parameter")
                );
            }

            // Extract client credentials
            ClientCredentials credentials = extractClientCredentials(formParameters, authorizationHeader);

            // Extract scope (optional)
            String scope = formParameters.get(PARAM_SCOPE);

            // Create and validate token request
            TokenRequest tokenRequest = new TokenRequest(
                grantType.trim(),
                credentials.clientId,
                credentials.clientSecret,
                scope != null ? scope.trim() : null
            );

            logger.debug("Successfully parsed OAuth 2.0 token request for client: {}", credentials.clientId);
            return tokenRequest;

        } catch (OAuth2AuthenticationException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error parsing OAuth 2.0 token request", e);
            throw new OAuth2AuthenticationException(
                OAuthError.invalidRequest("Invalid request format"), e
            );
        }
    }

    /**
     * Extracts client credentials from either Basic Authentication header or form parameters.
     * RFC 6749 allows both methods, with Basic Auth taking precedence.
     *
     * @param formParameters the form parameters
     * @param authorizationHeader the Authorization header
     * @return ClientCredentials object containing client ID and secret
     * @throws OAuth2AuthenticationException if credentials cannot be extracted
     */
    private static ClientCredentials extractClientCredentials(Map<String, String> formParameters, 
                                                            String authorizationHeader) {
        // Try Basic Authentication first
        if (authorizationHeader != null && authorizationHeader.startsWith(BASIC_AUTH_PREFIX)) {
            try {
                return extractBasicAuthCredentials(authorizationHeader);
            } catch (Exception e) {
                logger.debug("Failed to extract Basic Auth credentials", e);
                throw new OAuth2AuthenticationException(
                    OAuthError.invalidClient("Invalid Basic Authentication credentials")
                );
            }
        }

        // Fall back to form parameters
        String clientId = formParameters.get(PARAM_CLIENT_ID);
        String clientSecret = formParameters.get(PARAM_CLIENT_SECRET);

        if (clientId == null || clientId.trim().isEmpty()) {
            throw new OAuth2AuthenticationException(
                OAuthError.invalidRequest("Missing client_id parameter")
            );
        }

        if (clientSecret == null || clientSecret.trim().isEmpty()) {
            throw new OAuth2AuthenticationException(
                OAuthError.invalidRequest("Missing client_secret parameter")
            );
        }

        return new ClientCredentials(clientId.trim(), clientSecret);
    }

    /**
     * Extracts client credentials from Basic Authentication header.
     *
     * @param authorizationHeader the Authorization header value
     * @return ClientCredentials object
     * @throws IllegalArgumentException if the header is malformed
     */
    private static ClientCredentials extractBasicAuthCredentials(String authorizationHeader) {
        // Remove "Basic " prefix
        String encodedCredentials = authorizationHeader.substring(BASIC_AUTH_PREFIX.length()).trim();

        if (encodedCredentials.isEmpty()) {
            throw new IllegalArgumentException("Empty Basic Auth credentials");
        }

        // Decode Base64
        byte[] decodedBytes;
        try {
            decodedBytes = Base64.decodeBase64(encodedCredentials);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid Base64 encoding in Basic Auth", e);
        }

        String credentials = new String(decodedBytes, StandardCharsets.UTF_8);

        // Split on first colon
        int colonIndex = credentials.indexOf(':');
        if (colonIndex == -1) {
            throw new IllegalArgumentException("Basic Auth credentials missing colon separator");
        }

        String clientId = credentials.substring(0, colonIndex);
        String clientSecret = credentials.substring(colonIndex + 1);

        if (clientId.isEmpty()) {
            throw new IllegalArgumentException("Empty client ID in Basic Auth");
        }

        return new ClientCredentials(clientId, clientSecret);
    }

    /**
     * Validates the format of OAuth 2.0 request parameters.
     *
     * @param formParameters the form parameters to validate
     * @throws OAuth2AuthenticationException if validation fails
     */
    public static void validateRequestFormat(Map<String, String> formParameters) {
        if (formParameters == null) {
            throw new OAuth2AuthenticationException(
                OAuthError.invalidRequest("Request parameters cannot be null")
            );
        }

        // Check for required grant_type parameter
        String grantType = formParameters.get(PARAM_GRANT_TYPE);
        if (grantType == null || grantType.trim().isEmpty()) {
            throw new OAuth2AuthenticationException(
                OAuthError.invalidRequest("Missing required parameter: grant_type")
            );
        }

        // Validate scope format if present
        String scope = formParameters.get(PARAM_SCOPE);
        if (scope != null && !ScopeValidator.isValidScopeFormat(scope)) {
            throw new OAuth2AuthenticationException(
                OAuthError.invalidScope("Invalid scope format")
            );
        }

        logger.debug("OAuth 2.0 request format validation passed");
    }

    /**
     * Checks if the request contains client credentials in form parameters.
     *
     * @param formParameters the form parameters to check
     * @return true if both client_id and client_secret are present
     */
    public static boolean hasFormCredentials(Map<String, String> formParameters) {
        if (formParameters == null) {
            return false;
        }

        String clientId = formParameters.get(PARAM_CLIENT_ID);
        String clientSecret = formParameters.get(PARAM_CLIENT_SECRET);

        return clientId != null && !clientId.trim().isEmpty() &&
               clientSecret != null && !clientSecret.trim().isEmpty();
    }

    /**
     * Checks if the Authorization header contains Basic Authentication credentials.
     *
     * @param authorizationHeader the Authorization header value
     * @return true if it's a valid Basic Auth header
     */
    public static boolean hasBasicAuthCredentials(String authorizationHeader) {
        return authorizationHeader != null && 
               authorizationHeader.startsWith(BASIC_AUTH_PREFIX) &&
               authorizationHeader.length() > BASIC_AUTH_PREFIX.length();
    }

    /**
     * Simple data class to hold client credentials.
     */
    private static class ClientCredentials {
        final String clientId;
        final String clientSecret;

        ClientCredentials(String clientId, String clientSecret) {
            this.clientId = clientId;
            this.clientSecret = clientSecret;
        }
    }
} 