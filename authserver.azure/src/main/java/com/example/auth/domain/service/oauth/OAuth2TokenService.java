package com.example.auth.domain.service.oauth;

import com.example.auth.domain.model.oauth.TokenRequest;
import com.example.auth.domain.model.oauth.TokenResponse;
import java.util.Map;

/**
 * Service interface for OAuth 2.0 JWT token operations.
 * This service handles the generation, validation, and parsing of JWT tokens
 * for the OAuth 2.0 Client Credentials Grant flow.
 *
 * @author Auth Server Team
 * @version 1.0
 * @since 1.0
 */
public interface OAuth2TokenService {

    /**
     * Generates a new JWT access token based on the provided token request.
     * The token will be signed using a secure signing algorithm (RS256) and 
     * include standard JWT claims as well as OAuth 2.0 specific claims.
     *
     * @param request the token request containing client and scope information
     * @return a TokenResponse containing the generated access token
     * @throws IllegalArgumentException if the request is invalid
     * @throws RuntimeException if token generation fails
     */
    TokenResponse generateToken(TokenRequest request);

    /**
     * Validates a JWT token's signature, expiration, and structure.
     * This method performs comprehensive validation including:
     * - Signature verification
     * - Expiration time check
     * - Token structure validation
     * - Issuer and audience validation
     *
     * @param token the JWT token to validate
     * @return true if the token is valid, false otherwise
     * @throws IllegalArgumentException if the token is null or empty
     */
    boolean validateToken(String token);

    /**
     * Extracts and returns the claims from a JWT token.
     * This method assumes the token has already been validated and will
     * throw an exception if the token is invalid or malformed.
     *
     * @param token the JWT token to parse
     * @return a Map containing all token claims
     * @throws IllegalArgumentException if the token is null or empty
     * @throws RuntimeException if the token cannot be parsed
     */
    Map<String, Object> extractClaims(String token);

    /**
     * Extracts the client ID from a JWT token.
     * This is a convenience method that extracts the 'client_id' claim.
     *
     * @param token the JWT token to parse
     * @return the client ID or null if not present
     * @throws IllegalArgumentException if the token is null or empty
     * @throws RuntimeException if the token cannot be parsed
     */
    String extractClientId(String token);

    /**
     * Extracts the scope from a JWT token.
     * This is a convenience method that extracts the 'scope' claim.
     *
     * @param token the JWT token to parse
     * @return the scope string or null if not present
     * @throws IllegalArgumentException if the token is null or empty
     * @throws RuntimeException if the token cannot be parsed
     */
    String extractScope(String token);

    /**
     * Checks if a JWT token has expired.
     * This method parses the token and checks the 'exp' claim against current time.
     *
     * @param token the JWT token to check
     * @return true if the token has expired, false otherwise
     * @throws IllegalArgumentException if the token is null or empty
     * @throws RuntimeException if the token cannot be parsed
     */
    boolean isTokenExpired(String token);

    /**
     * Gets the remaining lifetime of a JWT token in seconds.
     *
     * @param token the JWT token to check
     * @return the remaining lifetime in seconds, or 0 if expired
     * @throws IllegalArgumentException if the token is null or empty
     * @throws RuntimeException if the token cannot be parsed
     */
    long getTokenRemainingLifetime(String token);
} 