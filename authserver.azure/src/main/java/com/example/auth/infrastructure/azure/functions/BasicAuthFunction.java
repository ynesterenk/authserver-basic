package com.example.auth.infrastructure.azure.functions;

import com.example.auth.domain.model.AuthenticationRequest;
import com.example.auth.domain.model.AuthenticationResult;
import com.example.auth.domain.model.User;
import com.example.auth.domain.port.UserRepository;
import com.example.auth.domain.service.AuthenticatorService;
import com.example.auth.infrastructure.azure.config.AzureFunctionConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.net.ssl.HttpsURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Azure Function for Basic Authentication validation.
 * Handles POST /api/auth/validate requests.
 */
@Component
public class BasicAuthFunction {

    private static final Logger logger = LoggerFactory.getLogger(BasicAuthFunction.class);
    private static final String BASIC_AUTH_PREFIX = "Basic ";
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private AuthenticatorService authenticatorService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AzureFunctionConfiguration.AzureFunctionMetrics metrics;

    public BasicAuthFunction() {
        logger.info("BasicAuthFunction initialized");
    }

    /**
     * Azure Function endpoint for Basic Authentication validation
     * POST /api/auth/validate
     */
    @FunctionName("BasicAuth")
    public HttpResponseMessage run(
            @HttpTrigger(
                name = "req",
                methods = {HttpMethod.POST},
                authLevel = AuthorizationLevel.ANONYMOUS,
                route = "auth/validate"
            ) HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {

        long startTime = System.currentTimeMillis();
        String requestId = context.getInvocationId();

        try {
            logger.info("Processing Basic Auth request - RequestId: {}", requestId);

            // Extract Authorization header
            String authHeader = request.getHeaders().get("authorization");
            if (authHeader == null) {
                authHeader = request.getHeaders().get("Authorization");
            }

            if (authHeader == null || !authHeader.startsWith(BASIC_AUTH_PREFIX)) {
                logger.warn("Missing or invalid Authorization header - RequestId: {}", requestId);
                return createUnauthorizedResponse(request, "Missing or invalid Authorization header");
            }

            // Parse Basic Auth credentials
            BasicAuthCredentials credentials = parseBasicAuth(authHeader);
            if (credentials == null) {
                logger.warn("Failed to parse Basic Auth credentials - RequestId: {}", requestId);
                return createUnauthorizedResponse(request, "Invalid Authorization header format");
            }

            // Create authentication request
            AuthenticationRequest authRequest = new AuthenticationRequest(
                credentials.username, 
                credentials.password
            );

            // Perform authentication using domain service
            AuthenticationResult result = authenticatorService.authenticate(authRequest);

            long duration = System.currentTimeMillis() - startTime;

            // CRITICAL: Use result.isAllowed() NOT isAuthenticated()
            if (result.isAllowed()) {
                logger.info("Authentication successful for user '{}' in {}ms - RequestId: {}", 
                          maskUsername(credentials.username), duration, requestId);
                
                metrics.recordAuthenticationAttempt("success");
                
                // CRITICAL: Get roles by looking up user
                List<String> roles = getUserRoles(credentials.username);
                
                return createSuccessResponse(request, credentials.username, roles, duration);
                
            } else {
                // CRITICAL: Use result.getReason() NOT getFailureReason()
                logger.warn("Authentication failed for user '{}': {} in {}ms - RequestId: {}", 
                          maskUsername(credentials.username), result.getReason(), duration, requestId);
                
                metrics.recordAuthenticationAttempt("failure");
                
                return createUnauthorizedResponse(request, result.getReason());
            }

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            logger.error("Unexpected error during authentication in {}ms - RequestId: {}", 
                        duration, requestId, e);
            
            metrics.recordAuthenticationAttempt("error");
            
            return createInternalErrorResponse(request, "Internal authentication error");
        }
    }

    /**
     * CRITICAL: Get roles by looking up user in repository
     */
    private List<String> getUserRoles(String username) {
        try {
            return userRepository.findByUsername(username)
                    .map(User::getRoles)
                    .orElse(Collections.emptyList());
        } catch (Exception e) {
            logger.warn("Failed to retrieve roles for user '{}': {}", maskUsername(username), e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Parses Basic Authentication header to extract credentials.
     */
    private BasicAuthCredentials parseBasicAuth(String authHeader) {
        try {
            String encodedCredentials = authHeader.substring(BASIC_AUTH_PREFIX.length()).trim();
            String decodedCredentials = new String(
                Base64.getDecoder().decode(encodedCredentials), 
                StandardCharsets.UTF_8
            );

            int colonIndex = decodedCredentials.indexOf(':');
            if (colonIndex == -1) {
                return null;
            }

            String username = decodedCredentials.substring(0, colonIndex);
            String password = decodedCredentials.substring(colonIndex + 1);

            if (username.isEmpty()) {
                return null;
            }

            return new BasicAuthCredentials(username, password);

        } catch (Exception e) {
            logger.debug("Failed to parse Basic Auth header", e);
            return null;
        }
    }

    /**
     * Creates a successful authentication response.
     */
    private HttpResponseMessage createSuccessResponse(HttpRequestMessage<Optional<String>> request, 
                                                     String username, 
                                                     List<String> roles, 
                                                     long duration) {
        try {
            Map<String, Object> response = new HashMap<>();
            response.put("authenticated", true);
            response.put("username", username);
            response.put("roles", roles);
            response.put("message", "Authentication successful");
            
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("duration", duration);
            metadata.put("timestamp", System.currentTimeMillis());
            response.put("metadata", metadata);

            String responseBody = objectMapper.writeValueAsString(response);

            return request.createResponseBuilder(HttpStatus.OK)
                    .header("Content-Type", "application/json")
                    .header("Cache-Control", "no-cache, no-store, must-revalidate")
                    .header("Pragma", "no-cache")
                    .header("Expires", "0")
                    .body(responseBody)
                    .build();

        } catch (Exception e) {
            logger.error("Failed to create success response", e);
            return createInternalErrorResponse(request, "Failed to create response");
        }
    }

    /**
     * Creates an unauthorized response.
     */
    private HttpResponseMessage createUnauthorizedResponse(HttpRequestMessage<Optional<String>> request, 
                                                          String reason) {
        try {
            Map<String, Object> response = new HashMap<>();
            response.put("authenticated", false);
            response.put("message", reason != null ? reason : "Authentication failed");
            response.put("timestamp", System.currentTimeMillis());

            String responseBody = objectMapper.writeValueAsString(response);

            return request.createResponseBuilder(HttpStatus.UNAUTHORIZED)
                    .header("Content-Type", "application/json")
                    .header("WWW-Authenticate", "Basic realm=\"Authorization Required\"")
                    .header("Cache-Control", "no-cache, no-store, must-revalidate")
                    .header("Pragma", "no-cache")
                    .header("Expires", "0")
                    .body(responseBody)
                    .build();

        } catch (Exception e) {
            logger.error("Failed to create unauthorized response", e);
            return request.createResponseBuilder(HttpStatus.UNAUTHORIZED)
                    .header("Content-Type", "application/json")
                    .body("{\"authenticated\":false,\"message\":\"Authentication failed\"}")
                    .build();
        }
    }

    /**
     * Creates an internal server error response.
     */
    private HttpResponseMessage createInternalErrorResponse(HttpRequestMessage<Optional<String>> request, 
                                                           String message) {
        try {
            Map<String, Object> response = new HashMap<>();
            response.put("error", "internal_error");
            response.put("message", message != null ? message : "Internal server error");
            response.put("timestamp", System.currentTimeMillis());

            String responseBody = objectMapper.writeValueAsString(response);

            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .header("Content-Type", "application/json")
                    .header("Cache-Control", "no-cache, no-store, must-revalidate")
                    .body(responseBody)
                    .build();

        } catch (Exception e) {
            logger.error("Failed to create error response", e);
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .header("Content-Type", "application/json")
                    .body("{\"error\":\"internal_error\",\"message\":\"Internal server error\"}")
                    .build();
        }
    }

    /**
     * Masks username for logging (security).
     */
    private String maskUsername(String username) {
        if (username == null || username.length() <= 2) {
            return "***";
        }
        return username.charAt(0) + "*".repeat(username.length() - 2) + username.charAt(username.length() - 1);
    }

    /**
     * Simple data class for Basic Auth credentials.
     */
    private static class BasicAuthCredentials {
        final String username;
        final String password;

        BasicAuthCredentials(String username, String password) {
            this.username = username;
            this.password = password;
        }
    }
} 