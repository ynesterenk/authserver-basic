package com.example.auth.infrastructure.azure.functions;

import com.example.auth.domain.service.oauth.OAuth2TokenService;
import com.example.auth.infrastructure.azure.config.AzureFunctionConfiguration;
import com.example.auth.infrastructure.oauth.model.OAuth2ErrorResponse;
import com.example.auth.infrastructure.oauth.model.OAuth2IntrospectionResponse;
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
import java.time.Instant;
import java.util.*;

/**
 * Azure Function for OAuth 2.0 Token Introspection endpoint.
 * Handles POST /api/oauth/introspect requests (RFC 7662).
 * Uses OAuth2TokenService methods to implement introspection functionality.
 */
@Component
public class OAuth2IntrospectFunction {

    private static final Logger logger = LoggerFactory.getLogger(OAuth2IntrospectFunction.class);
    private static final String BASIC_AUTH_PREFIX = "Basic ";
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private OAuth2TokenService tokenService;

    @Autowired
    private AzureFunctionConfiguration.AzureFunctionMetrics metrics;

    public OAuth2IntrospectFunction() {
        logger.info("OAuth2IntrospectFunction initialized");
    }

    /**
     * Azure Function endpoint for OAuth 2.0 token introspection
     * POST /api/oauth/introspect
     */
    @FunctionName("OAuth2Introspect")
    public HttpResponseMessage run(
            @HttpTrigger(
                name = "req",
                methods = {HttpMethod.POST},
                authLevel = AuthorizationLevel.ANONYMOUS,
                route = "oauth/introspect"
            ) HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {

        long startTime = System.currentTimeMillis();
        String requestId = context.getInvocationId();

        try {
            logger.info("Processing OAuth 2.0 introspection request - RequestId: {}", requestId);

            // Parse request parameters
            IntrospectionRequestData requestData = parseIntrospectionRequest(request);
            if (requestData == null) {
                return createErrorResponse(request, "invalid_request", "Failed to parse request parameters");
            }

            // Validate required parameters
            if (requestData.token == null || requestData.token.trim().isEmpty()) {
                logger.warn("Missing token parameter - RequestId: {}", requestId);
                return createErrorResponse(request, "invalid_request", "Missing token parameter");
            }

            // Perform token introspection using real domain service
            OAuth2IntrospectionResponse introspectionResponse = performIntrospection(requestData.token);

            long duration = System.currentTimeMillis() - startTime;
            logger.info("Token introspection completed in {}ms - RequestId: {}, Active: {}", 
                      duration, requestId, introspectionResponse.isActive());

            metrics.recordIntrospection(requestData.token);

            return createSuccessResponse(request, introspectionResponse, duration);

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            logger.error("Unexpected error during token introspection in {}ms - RequestId: {}", 
                        duration, requestId, e);
            
            return createErrorResponse(request, "server_error", "Internal server error");
        }
    }

    /**
     * Performs token introspection using OAuth2TokenService methods.
     */
    private OAuth2IntrospectionResponse performIntrospection(String token) {
        try {
            // Use real domain service to validate token
            boolean isValid = tokenService.validateToken(token);
            
            if (!isValid) {
                // Return inactive response for invalid tokens - use real constructor
                return new OAuth2IntrospectionResponse(false, null, null, null, null, null);
            }

            // Extract claims from valid token
            Map<String, Object> claims = tokenService.extractClaims(token);
            
            // Extract standard claims
            String clientId = tokenService.extractClientId(token);
            String scope = tokenService.extractScope(token);
            
            // Extract timing information
            Object expObj = claims.get("exp");
            Object iatObj = claims.get("iat");
            
            Integer exp = expObj != null ? ((Number) expObj).intValue() : null;
            Integer iat = iatObj != null ? ((Number) iatObj).intValue() : null;

            // Create active introspection response using real constructor
            return new OAuth2IntrospectionResponse(
                true,           // active
                clientId,       // client_id
                scope,          // scope
                "Bearer",       // token_type
                exp,            // exp
                iat             // iat
            );

        } catch (Exception e) {
            logger.debug("Token introspection failed: {}", e.getMessage());
            // Return inactive response for any errors - use real constructor
            return new OAuth2IntrospectionResponse(false, null, null, null, null, null);
        }
    }

    /**
     * Parses the introspection request from form data and Basic Auth header.
     */
    private IntrospectionRequestData parseIntrospectionRequest(HttpRequestMessage<Optional<String>> request) {
        try {
            // Initialize request data
            IntrospectionRequestData requestData = new IntrospectionRequestData();

            // Parse form data from request body
            String contentType = request.getHeaders().get("content-type");
            if (contentType != null && contentType.toLowerCase().contains("application/x-www-form-urlencoded")) {
                String body = request.getBody().orElse("");
                parseFormData(body, requestData);
            }

            // Parse Basic Auth header for client authentication
            String authHeader = request.getHeaders().get("authorization");
            if (authHeader == null) {
                authHeader = request.getHeaders().get("Authorization");
            }
            
            if (authHeader != null && authHeader.startsWith(BASIC_AUTH_PREFIX)) {
                parseBasicAuth(authHeader, requestData);
            }

            return requestData;

        } catch (Exception e) {
            logger.debug("Failed to parse introspection request", e);
            return null;
        }
    }

    /**
     * Parses form-encoded data from request body.
     */
    private void parseFormData(String body, IntrospectionRequestData requestData) {
        String[] pairs = body.split("&");
        for (String pair : pairs) {
            String[] keyValue = pair.split("=", 2);
            if (keyValue.length == 2) {
                String key = urlDecode(keyValue[0]);
                String value = urlDecode(keyValue[1]);
                
                switch (key) {
                    case "token":
                        requestData.token = value;
                        break;
                    case "token_type_hint":
                        requestData.tokenTypeHint = value;
                        break;
                    case "client_id":
                        if (requestData.clientId == null) { // Don't override Basic Auth
                            requestData.clientId = value;
                        }
                        break;
                }
            }
        }
    }

    /**
     * Parses Basic Authentication header for client authentication.
     */
    private void parseBasicAuth(String authHeader, IntrospectionRequestData requestData) {
        try {
            String encodedCredentials = authHeader.substring(BASIC_AUTH_PREFIX.length()).trim();
            String decodedCredentials = new String(
                Base64.getDecoder().decode(encodedCredentials), 
                StandardCharsets.UTF_8
            );

            int colonIndex = decodedCredentials.indexOf(':');
            if (colonIndex != -1) {
                requestData.clientId = decodedCredentials.substring(0, colonIndex);
                // Client secret not needed for introspection, but could be validated
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
     * Creates a successful introspection response.
     */
    private HttpResponseMessage createSuccessResponse(HttpRequestMessage<Optional<String>> request, 
                                                     OAuth2IntrospectionResponse introspectionResponse, 
                                                     long duration) {
        try {
            String responseBody = objectMapper.writeValueAsString(introspectionResponse);

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
                case "invalid_request" -> HttpStatus.BAD_REQUEST;
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
     * Simple data class for parsed introspection request parameters.
     */
    private static class IntrospectionRequestData {
        String token;
        String tokenTypeHint;
        String clientId;
    }
} 