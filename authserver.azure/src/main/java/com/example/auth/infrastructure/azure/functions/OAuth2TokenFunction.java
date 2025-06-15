package com.example.auth.infrastructure.azure.functions;

import com.example.auth.domain.model.oauth.*;
import com.example.auth.domain.service.oauth.ClientCredentialsService;
import com.example.auth.infrastructure.azure.config.AzureFunctionConfiguration;
import com.example.auth.infrastructure.oauth.model.OAuth2ErrorResponse;
import com.example.auth.infrastructure.oauth.model.OAuth2TokenResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Azure Function for OAuth 2.0 Token endpoint.
 * Handles POST /api/oauth/token requests for Client Credentials Grant.
 */
@Component
public class OAuth2TokenFunction {

    private static final Logger logger = LoggerFactory.getLogger(OAuth2TokenFunction.class);
    private static final String BASIC_AUTH_PREFIX = "Basic ";
    private static final String GRANT_TYPE_CLIENT_CREDENTIALS = "client_credentials";
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private ClientCredentialsService clientCredentialsService;

    @Autowired
    private AzureFunctionConfiguration.AzureFunctionMetrics metrics;

    public OAuth2TokenFunction() {
        logger.info("OAuth2TokenFunction initialized");
    }

    /**
     * Azure Function endpoint for OAuth 2.0 token generation
     * POST /api/oauth/token
     */
    @FunctionName("OAuth2Token")
    public HttpResponseMessage run(
            @HttpTrigger(
                name = "req",
                methods = {HttpMethod.POST},
                authLevel = AuthorizationLevel.ANONYMOUS,
                route = "oauth/token"
            ) HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {

        long startTime = System.currentTimeMillis();
        String requestId = context.getInvocationId();

        try {
            logger.info("Processing OAuth 2.0 token request - RequestId: {}", requestId);

            // Parse request parameters
            TokenRequestData requestData = parseTokenRequest(request);
            if (requestData == null) {
                return createErrorResponse(request, "invalid_request", "Failed to parse request parameters");
            }

            // Validate grant type
            if (!GRANT_TYPE_CLIENT_CREDENTIALS.equals(requestData.grantType)) {
                logger.warn("Unsupported grant type: {} - RequestId: {}", requestData.grantType, requestId);
                return createErrorResponse(request, "unsupported_grant_type", 
                    "Grant type '" + requestData.grantType + "' is not supported");
            }

            // Create domain token request
            TokenRequest domainRequest = new TokenRequest(
                requestData.grantType,
                requestData.clientId,
                requestData.clientSecret,
                requestData.scope
            );

            // CRITICAL: Use authenticate() method - the real domain API
            TokenResponse tokenResponse = clientCredentialsService.authenticate(domainRequest);

            long duration = System.currentTimeMillis() - startTime;
            logger.info("Token generated successfully for client '{}' in {}ms - RequestId: {}", 
                      maskClientId(requestData.clientId), duration, requestId);

            metrics.recordTokenGeneration(requestData.clientId);

            return createSuccessResponse(request, tokenResponse, duration);

        } catch (ClientCredentialsService.OAuth2AuthenticationException e) {
            long duration = System.currentTimeMillis() - startTime;
            logger.warn("OAuth 2.0 authentication failed in {}ms - RequestId: {}: {}", 
                       duration, requestId, e.getOAuthError().getError());
            
            return createErrorResponse(request, 
                e.getOAuthError().getError(), 
                e.getOAuthError().getErrorDescription());

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            logger.error("Unexpected error during token generation in {}ms - RequestId: {}", 
                        duration, requestId, e);
            
            return createErrorResponse(request, "server_error", "Internal server error");
        }
    }

    /**
     * Parses the token request from form data or Basic Auth header.
     */
    private TokenRequestData parseTokenRequest(HttpRequestMessage<Optional<String>> request) {
        try {
            // Initialize request data
            TokenRequestData requestData = new TokenRequestData();

            // Parse form data from request body
            String contentType = request.getHeaders().get("content-type");
            if (contentType != null && contentType.toLowerCase().contains("application/x-www-form-urlencoded")) {
                String body = request.getBody().orElse("");
                parseFormData(body, requestData);
            }

            // Parse Basic Auth header (client credentials can be in header or form)
            String authHeader = request.getHeaders().get("authorization");
            if (authHeader == null) {
                authHeader = request.getHeaders().get("Authorization");
            }
            
            if (authHeader != null && authHeader.startsWith(BASIC_AUTH_PREFIX)) {
                parseBasicAuth(authHeader, requestData);
            }

            // Validate required parameters
            if (requestData.grantType == null || requestData.clientId == null || requestData.clientSecret == null) {
                logger.debug("Missing required parameters - grant_type: {}, client_id: {}, client_secret: {}", 
                           requestData.grantType, 
                           requestData.clientId != null ? maskClientId(requestData.clientId) : "null",
                           requestData.clientSecret != null ? "***" : "null");
                return null;
            }

            return requestData;

        } catch (Exception e) {
            logger.debug("Failed to parse token request", e);
            return null;
        }
    }

    /**
     * Parses form-encoded data from request body.
     */
    private void parseFormData(String body, TokenRequestData requestData) {
        String[] pairs = body.split("&");
        for (String pair : pairs) {
            String[] keyValue = pair.split("=", 2);
            if (keyValue.length == 2) {
                String key = urlDecode(keyValue[0]);
                String value = urlDecode(keyValue[1]);
                
                switch (key) {
                    case "grant_type":
                        requestData.grantType = value;
                        break;
                    case "client_id":
                        if (requestData.clientId == null) { // Don't override Basic Auth
                            requestData.clientId = value;
                        }
                        break;
                    case "client_secret":
                        if (requestData.clientSecret == null) { // Don't override Basic Auth
                            requestData.clientSecret = value;
                        }
                        break;
                    case "scope":
                        requestData.scope = value;
                        break;
                }
            }
        }
    }

    /**
     * Parses Basic Authentication header for client credentials.
     */
    private void parseBasicAuth(String authHeader, TokenRequestData requestData) {
        try {
            String encodedCredentials = authHeader.substring(BASIC_AUTH_PREFIX.length()).trim();
            String decodedCredentials = new String(
                Base64.getDecoder().decode(encodedCredentials), 
                StandardCharsets.UTF_8
            );

            int colonIndex = decodedCredentials.indexOf(':');
            if (colonIndex != -1) {
                requestData.clientId = decodedCredentials.substring(0, colonIndex);
                requestData.clientSecret = decodedCredentials.substring(colonIndex + 1);
            }
        } catch (Exception e) {
            logger.debug("Failed to parse Basic Auth header", e);
        }
    }

    /**
     * Simple URL decoding for form parameters.
     */
    private String urlDecode(String value) {
        try {
            return java.net.URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return value;
        }
    }

    /**
     * Creates a successful token response.
     */
    private HttpResponseMessage createSuccessResponse(HttpRequestMessage<Optional<String>> request, 
                                                     TokenResponse tokenResponse, 
                                                     long duration) {
        try {
            OAuth2TokenResponse response = new OAuth2TokenResponse(
                tokenResponse.getAccessToken(),
                tokenResponse.getTokenType(), 
                tokenResponse.getExpiresIn(),
                tokenResponse.getScope(),
                System.currentTimeMillis() / 1000L // issuedAt in seconds
            );

            String responseBody = objectMapper.writeValueAsString(response);

            return request.createResponseBuilder(HttpStatus.OK)
                    .header("Content-Type", "application/json")
                    .header("Cache-Control", "no-store")
                    .header("Pragma", "no-cache")
                    .body(responseBody)
                    .build();

        } catch (Exception e) {
            logger.error("Failed to create success response", e);
            return createErrorResponse(request, "server_error", "Failed to create response");
        }
    }

    /**
     * Creates an OAuth 2.0 error response.
     */
    private HttpResponseMessage createErrorResponse(HttpRequestMessage<Optional<String>> request, 
                                                   String error, 
                                                   String errorDescription) {
        try {
            OAuth2ErrorResponse response = new OAuth2ErrorResponse(error, errorDescription, null);
            String responseBody = objectMapper.writeValueAsString(response);

            // Determine HTTP status based on error type
            HttpStatus status = switch (error) {
                case "invalid_client" -> HttpStatus.UNAUTHORIZED;
                case "invalid_request", "unsupported_grant_type", "invalid_scope" -> HttpStatus.BAD_REQUEST;
                default -> HttpStatus.INTERNAL_SERVER_ERROR;
            };

            return request.createResponseBuilder(status)
                    .header("Content-Type", "application/json")
                    .header("Cache-Control", "no-store")
                    .header("Pragma", "no-cache")
                    .body(responseBody)
                    .build();

        } catch (Exception e) {
            logger.error("Failed to create error response", e);
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .header("Content-Type", "application/json")
                    .body("{\"error\":\"server_error\",\"error_description\":\"Internal server error\"}")
                    .build();
        }
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
     * Simple data class for parsed token request parameters.
     */
    private static class TokenRequestData {
        String grantType;
        String clientId;
        String clientSecret;
        String scope;
    }
} 