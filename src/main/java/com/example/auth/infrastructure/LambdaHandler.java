package com.example.auth.infrastructure;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.example.auth.domain.model.AuthenticationRequest;
import com.example.auth.domain.model.AuthenticationResult;
import com.example.auth.domain.service.AuthenticatorService;
import com.example.auth.infrastructure.model.AuthValidationResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import software.amazon.lambda.powertools.logging.Logging;
import software.amazon.lambda.powertools.metrics.Metrics;
import software.amazon.lambda.powertools.tracing.Tracing;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * AWS Lambda handler for processing authentication requests.
 * 
 * This handler:
 * - Processes API Gateway HTTP requests
 * - Extracts Basic Authentication credentials
 * - Validates credentials using the domain service
 * - Returns appropriate HTTP responses with proper status codes
 * - Implements structured logging and metrics
 */
public class LambdaHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final Logger logger = LoggerFactory.getLogger(LambdaHandler.class);
    
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BASIC_PREFIX = "Basic ";
    private static final String CONTENT_TYPE = "Content-Type";
    private static final String APPLICATION_JSON = "application/json";
    
    private final AuthenticatorService authenticatorService;
    private final ObjectMapper objectMapper;
    
    // Lazy initialization for Lambda cold start optimization
    private static ApplicationContext applicationContext;
    
    public LambdaHandler() {
        this.applicationContext = initializeApplicationContext();
        this.authenticatorService = applicationContext.getBean(AuthenticatorService.class);
        this.objectMapper = applicationContext.getBean(ObjectMapper.class);
        
        logger.info("LambdaHandler initialized successfully");
    }

    @Override
    @Logging(logEvent = true)
    @Metrics(namespace = "AuthService")
    @Tracing
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        long startTime = System.currentTimeMillis();
        
        try {
            logger.info("Processing authentication request - RequestId: {}", context.getAwsRequestId());
            
            // Validate HTTP method
            if (!"POST".equalsIgnoreCase(input.getHttpMethod())) {
                logger.warn("Invalid HTTP method: {}", input.getHttpMethod());
                return createErrorResponse(405, "Method Not Allowed", "Only POST method is supported");
            }
            
            // Extract Authorization header
            String authHeader = extractAuthorizationHeader(input);
            if (authHeader == null) {
                logger.warn("Missing Authorization header");
                return createErrorResponse(400, "Bad Request", "Missing Authorization header");
            }
            
            // Parse Basic Auth credentials
            AuthenticationRequest authRequest = parseBasicAuthHeader(authHeader);
            if (authRequest == null) {
                logger.warn("Invalid Authorization header format");
                return createErrorResponse(400, "Bad Request", "Invalid Authorization header format");
            }
            
            // Perform authentication
            AuthenticationResult result = authenticatorService.authenticate(authRequest);
            
            // Create response
            APIGatewayProxyResponseEvent response = createAuthResponse(result);
            
            long duration = System.currentTimeMillis() - startTime;
            logger.info("Authentication completed - Success: {}, Duration: {}ms", 
                       result.isAllowed(), duration);
            
            // Add custom metrics
            addMetrics(result, duration);
            
            return response;
            
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            logger.error("Error processing authentication request - Duration: {}ms", duration, e);
            return createErrorResponse(500, "Internal Server Error", "Authentication service temporarily unavailable");
        }
    }
    
    /**
     * Initializes Spring application context with AWS configuration.
     */
    private static ApplicationContext initializeApplicationContext() {
        try {
            AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
            context.register(LambdaConfiguration.class);
            context.refresh();
            
            logger.info("Spring ApplicationContext initialized successfully");
            return context;
            
        } catch (Exception e) {
            logger.error("Failed to initialize ApplicationContext", e);
            throw new RuntimeException("Application initialization failed", e);
        }
    }
    
    /**
     * Extracts Authorization header from API Gateway request.
     */
    private String extractAuthorizationHeader(APIGatewayProxyRequestEvent input) {
        Map<String, String> headers = input.getHeaders();
        if (headers == null) {
            return null;
        }
        
        // Check both Authorization and authorization (case-insensitive)
        String authHeader = headers.get(AUTHORIZATION_HEADER);
        if (authHeader == null) {
            authHeader = headers.get(AUTHORIZATION_HEADER.toLowerCase());
        }
        
        return authHeader;
    }
    
    /**
     * Parses Basic Authentication header and creates AuthenticationRequest.
     */
    private AuthenticationRequest parseBasicAuthHeader(String authHeader) {
        try {
            if (!authHeader.startsWith(BASIC_PREFIX)) {
                logger.debug("Authorization header does not start with 'Basic '");
                return null;
            }
            
            String encodedCredentials = authHeader.substring(BASIC_PREFIX.length()).trim();
            String credentials = new String(Base64.getDecoder().decode(encodedCredentials));
            
            int colonIndex = credentials.indexOf(':');
            if (colonIndex == -1) {
                logger.debug("Invalid Basic Auth format - missing colon separator");
                return null;
            }
            
            String username = credentials.substring(0, colonIndex);
            String password = credentials.substring(colonIndex + 1);
            
            if (username.isEmpty()) {
                logger.debug("Empty username in Basic Auth");
                return null;
            }
            
            return new AuthenticationRequest(username, password);
            
        } catch (IllegalArgumentException e) {
            logger.debug("Invalid Base64 encoding in Authorization header: {}", e.getMessage());
            return null;
        } catch (Exception e) {
            logger.debug("Error parsing Authorization header: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Creates HTTP response for authentication result.
     */
    private APIGatewayProxyResponseEvent createAuthResponse(AuthenticationResult result) {
        try {
            AuthValidationResponse responseBody = new AuthValidationResponse(
                result.isAllowed(),
                result.getReason(),
                System.currentTimeMillis()
            );
            
            String jsonBody = objectMapper.writeValueAsString(responseBody);
            
            Map<String, String> headers = new HashMap<>();
            headers.put(CONTENT_TYPE, APPLICATION_JSON);
            headers.put("Cache-Control", "no-cache, no-store, must-revalidate");
            headers.put("Pragma", "no-cache");
            headers.put("Expires", "0");
            
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
                    .withHeaders(headers)
                    .withBody(jsonBody);
                    
        } catch (Exception e) {
            logger.error("Error creating authentication response", e);
            return createErrorResponse(500, "Internal Server Error", "Error processing response");
        }
    }
    
    /**
     * Creates error response with proper HTTP status code.
     */
    private APIGatewayProxyResponseEvent createErrorResponse(int statusCode, String error, String message) {
        try {
            AuthValidationResponse errorBody = new AuthValidationResponse(
                false,
                message,
                System.currentTimeMillis()
            );
            
            String jsonBody = objectMapper.writeValueAsString(errorBody);
            
            Map<String, String> headers = new HashMap<>();
            headers.put(CONTENT_TYPE, APPLICATION_JSON);
            
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(statusCode)
                    .withHeaders(headers)
                    .withBody(jsonBody);
                    
        } catch (Exception e) {
            logger.error("Error creating error response", e);
            // Fallback to plain text response
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(statusCode)
                    .withBody("{\"allowed\":false,\"message\":\"" + message + "\"}");
        }
    }
    
    /**
     * Adds custom CloudWatch metrics for monitoring.
     */
    private void addMetrics(AuthenticationResult result, long durationMs) {
        try {
            // Note: In a real implementation, you would use Lambda Powertools Metrics
            // This is a placeholder for the metrics that would be emitted
            if (result.isAllowed()) {
                logger.info("METRIC: AuthSuccess=1, Duration={}ms", durationMs);
            } else {
                logger.info("METRIC: AuthFailure=1, Duration={}ms", durationMs);
            }
            
            if (durationMs > 100) {
                logger.info("METRIC: SlowAuth=1, Duration={}ms", durationMs);
            }
            
        } catch (Exception e) {
            logger.warn("Error adding metrics: {}", e.getMessage());
        }
    }
} 