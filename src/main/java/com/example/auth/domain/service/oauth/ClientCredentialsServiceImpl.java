package com.example.auth.domain.service.oauth;

import com.example.auth.domain.model.oauth.*;
import com.example.auth.domain.port.oauth.OAuthClientRepository;
import com.example.auth.domain.util.oauth.ClientSecretHasher;
import com.example.auth.domain.util.oauth.ScopeValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Implementation of the ClientCredentialsService for OAuth 2.0 Client Credentials Grant flow.
 * This service orchestrates the complete authentication process including client validation,
 * scope checking, and token generation.
 *
 * @author Auth Server Team
 * @version 1.0
 * @since 1.0
 */
@Service
public class ClientCredentialsServiceImpl implements ClientCredentialsService {

    private static final Logger logger = LoggerFactory.getLogger(ClientCredentialsServiceImpl.class);

    @Autowired
    private OAuthClientRepository clientRepository;

    @Autowired
    private OAuth2TokenService tokenService;

    @Autowired
    private ClientSecretHasher secretHasher;

    @Value("${oauth2.token.max-expiration-seconds:7200}")
    private int maxExpirationSeconds;

    @Value("${oauth2.security.enable-client-status-check:true}")
    private boolean enableClientStatusCheck;

    public ClientCredentialsServiceImpl() {
        logger.info("ClientCredentialsServiceImpl initialized");
    }

    @Override
    public TokenResponse authenticate(TokenRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Token request cannot be null");
        }

        long startTime = System.nanoTime();
        String clientId = request.getClientId();

        try {
            logger.debug("Starting OAuth 2.0 client credentials authentication for client: {}", clientId);

            // Step 1: Validate grant type
            if (!request.isClientCredentialsGrant()) {
                throw new OAuth2AuthenticationException(
                    OAuthError.unsupportedGrantType(request.getGrantType())
                );
            }

            // Step 2: Authenticate client credentials
            OAuthClient client = authenticateClient(request.getClientId(), request.getClientSecret());

            // Step 3: Validate client status
            if (enableClientStatusCheck && !client.isActive()) {
                throw new OAuth2AuthenticationException(
                    OAuthError.accessDenied("Client is not active: " + client.getStatus())
                );
            }

            // Step 4: Validate requested scopes
            String[] requestedScopes = request.getRequestedScopes();
            if (!ScopeValidator.validateScopesAllowed(requestedScopes, client.getAllowedScopes())) {
                String requestedScopeString = ScopeValidator.createScopeString(requestedScopes);
                throw new OAuth2AuthenticationException(
                    OAuthError.invalidScope(requestedScopeString)
                );
            }

            // Step 5: Determine effective scope
            String effectiveScope = determineEffectiveScope(request.getScope(), client);

            // Step 6: Create token request with validated parameters
            TokenRequest validatedRequest = new TokenRequest(
                request.getGrantType(),
                request.getClientId(),
                request.getClientSecret(),
                effectiveScope
            );

            // Step 7: Generate access token
            TokenResponse tokenResponse = tokenService.generateToken(validatedRequest);

            long endTime = System.nanoTime();
            long duration = (endTime - startTime) / 1_000_000; // Convert to milliseconds

            logger.info("OAuth 2.0 authentication successful for client '{}' in {}ms", clientId, duration);

            return tokenResponse;

        } catch (OAuth2AuthenticationException e) {
            long endTime = System.nanoTime();
            long duration = (endTime - startTime) / 1_000_000;
            
            logger.warn("OAuth 2.0 authentication failed for client '{}' in {}ms: {}", 
                       clientId, duration, e.getOAuthError().getError());
            throw e;

        } catch (Exception e) {
            long endTime = System.nanoTime();
            long duration = (endTime - startTime) / 1_000_000;
            
            logger.error("Unexpected error during OAuth 2.0 authentication for client '{}' in {}ms", 
                        clientId, duration, e);
            throw new OAuth2AuthenticationException(
                OAuthError.serverError("Internal authentication error"), e
            );
        }
    }

    @Override
    public boolean validateClientCredentials(String clientId, String clientSecret) {
        if (clientId == null || clientId.trim().isEmpty()) {
            throw new IllegalArgumentException("Client ID cannot be null or empty");
        }
        if (clientSecret == null || clientSecret.trim().isEmpty()) {
            throw new IllegalArgumentException("Client secret cannot be null or empty");
        }

        try {
            OAuthClient client = authenticateClient(clientId, clientSecret);
            return client.isActive();
        } catch (Exception e) {
            logger.debug("Client credential validation failed for client '{}': {}", clientId, e.getMessage());
            return false;
        }
    }

    @Override
    public boolean validateClientScopes(String clientId, String[] requestedScopes) {
        if (clientId == null || clientId.trim().isEmpty()) {
            throw new IllegalArgumentException("Client ID cannot be null or empty");
        }

        try {
            Optional<OAuthClient> clientOpt = clientRepository.findByClientId(clientId);
            if (clientOpt.isEmpty()) {
                logger.debug("Client not found for scope validation: {}", clientId);
                return false;
            }

            OAuthClient client = clientOpt.get();
            return ScopeValidator.validateScopesAllowed(requestedScopes, client.getAllowedScopes());

        } catch (Exception e) {
            logger.error("Error validating client scopes for client '{}': {}", clientId, e.getMessage());
            return false;
        }
    }

    @Override
    public boolean isClientActive(String clientId) {
        if (clientId == null || clientId.trim().isEmpty()) {
            throw new IllegalArgumentException("Client ID cannot be null or empty");
        }

        try {
            Optional<OAuthClient> clientOpt = clientRepository.findByClientId(clientId);
            return clientOpt.map(OAuthClient::isActive).orElse(false);
        } catch (Exception e) {
            logger.error("Error checking client status for client '{}': {}", clientId, e.getMessage());
            return false;
        }
    }

    @Override
    public int getEffectiveTokenExpiration(String clientId, Integer requestedExpiration) {
        if (clientId == null || clientId.trim().isEmpty()) {
            throw new IllegalArgumentException("Client ID cannot be null or empty");
        }

        try {
            Optional<OAuthClient> clientOpt = clientRepository.findByClientId(clientId);
            if (clientOpt.isEmpty()) {
                return Math.min(requestedExpiration != null ? requestedExpiration : 3600, maxExpirationSeconds);
            }

            OAuthClient client = clientOpt.get();
            return client.getEffectiveTokenExpiration(requestedExpiration, maxExpirationSeconds);

        } catch (Exception e) {
            logger.error("Error calculating effective token expiration for client '{}': {}", clientId, e.getMessage());
            return Math.min(requestedExpiration != null ? requestedExpiration : 3600, maxExpirationSeconds);
        }
    }

    /**
     * Authenticates a client using client ID and secret.
     * 
     * @param clientId the client identifier
     * @param clientSecret the client secret (plaintext)
     * @return the authenticated OAuth client
     * @throws OAuth2AuthenticationException if authentication fails
     */
    private OAuthClient authenticateClient(String clientId, String clientSecret) {
        // Find client by ID
        Optional<OAuthClient> clientOpt = clientRepository.findByClientId(clientId);
        if (clientOpt.isEmpty()) {
            logger.debug("Client not found: {}", clientId);
            throw new OAuth2AuthenticationException(
                OAuthError.invalidClient("Client not found")
            );
        }

        OAuthClient client = clientOpt.get();

        // Verify client secret
        if (!secretHasher.verifyClientSecret(clientSecret, client.getClientSecretHash())) {
            logger.debug("Invalid client secret for client: {}", clientId);
            throw new OAuth2AuthenticationException(
                OAuthError.invalidClient("Invalid client credentials")
            );
        }

        logger.debug("Client authenticated successfully: {}", clientId);
        return client;
    }

    /**
     * Determines the effective scope for the token based on the request and client configuration.
     * 
     * @param requestedScope the scope requested by the client
     * @param client the authenticated OAuth client
     * @return the effective scope string
     */
    private String determineEffectiveScope(String requestedScope, OAuthClient client) {
        if (requestedScope == null || requestedScope.trim().isEmpty()) {
            // Use default scope if none requested
            String defaultScope = ScopeValidator.getDefaultScope(client.getAllowedScopes());
            logger.debug("Using default scope '{}' for client '{}'", defaultScope, client.getClientId());
            return defaultScope;
        }

        // Use requested scope (already validated)
        logger.debug("Using requested scope '{}' for client '{}'", requestedScope, client.getClientId());
        return requestedScope.trim();
    }
} 