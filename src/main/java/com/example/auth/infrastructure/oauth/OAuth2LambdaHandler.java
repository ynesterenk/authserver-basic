package com.example.auth.infrastructure.oauth;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.example.auth.domain.model.oauth.TokenRequest;
import com.example.auth.domain.model.oauth.TokenResponse;
import com.example.auth.domain.model.oauth.OAuthError;
import com.example.auth.domain.service.oauth.OAuth2TokenService;
import com.example.auth.domain.service.oauth.ClientCredentialsService;
import com.example.auth.domain.util.oauth.OAuth2RequestParser;
import com.example.auth.infrastructure.oauth.model.OAuth2TokenResponse;
import com.example.auth.infrastructure.oauth.model.OAuth2ErrorResponse;
import com.example.auth.infrastructure.oauth.model.OAuth2IntrospectionResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import software.amazon.lambda.powertools.logging.Logging;
import software.amazon.lambda.powertools.metrics.Metrics;
import software.amazon.lambda.powertools.tracing.Tracing;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * AWS Lambda handler for OAuth 2.0 token and introspection endpoints.
 * 
 * This handler processes:
 * - POST /oauth/token - OAuth 2.0 token endpoint (Client Credentials Grant)
 * - POST /oauth/introspect - OAuth 2.0 token introspection endpoint
 */
public class OAuth2LambdaHandler implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {

    private static final Logger logger = LoggerFactory.getLogger(OAuth2LambdaHandler.class);
    
    private static final String CONTENT_TYPE = "content-type";
    private static final String APPLICATION_JSON = "application/json";
    private static final String FORM_URLENCODED = "application/x-www-form-urlencoded";
    
    private final OAuth2TokenService tokenService;
    private final ClientCredentialsService clientService;
    private final ObjectMapper objectMapper;
    
    // Lazy initialization for Lambda cold start optimization
    private static ApplicationContext applicationContext;
    
    public OAuth2LambdaHandler() {
        try {
            this.applicationContext = initializeApplicationContext();
            this.tokenService = applicationContext.getBean(OAuth2TokenService.class);
            this.clientService = applicationContext.getBean(ClientCredentialsService.class);
            this.objectMapper = applicationContext.getBean(ObjectMapper.class);
            
            logger.info("OAuth2LambdaHandler initialized successfully");
        } catch (Exception e) {
            logger.error("Failed to initialize OAuth2LambdaHandler", e);
            throw new RuntimeException("OAuth2 Lambda handler initialization failed", e);
        }
    }

    @Override
    @Logging(logEvent = true)
    @Metrics(namespace = "OAuth2Service")
    @Tracing
    public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent input, Context context) {
        long startTime = System.currentTimeMillis();
        
        try {
            logger.info("Processing OAuth2 request - RequestId: {}", context.getAwsRequestId());
            
            String httpMethod = input.getRequestContext().getHttp().getMethod();
            String path = input.getRequestContext().getHttp().getPath();
            
            logger.debug("Request: method={}, path={}", httpMethod, path);
            
            // Route to appropriate handler
            if ("POST".equalsIgnoreCase(httpMethod)) {
                if (path.endsWith("/oauth/token")) {
                    return handleTokenRequest(input, context);
                } else if (path.endsWith("/oauth/introspect")) {
                    return handleIntrospectionRequest(input, context);
                }
            }
            
            // Invalid endpoint or method
            logger.warn("Invalid request: method={}, path={}", httpMethod, path);
            if (path.endsWith("/oauth/token") || path.endsWith("/oauth/introspect")) {
                return createErrorResponse(405, OAuthError.invalidRequest("Only POST method is allowed for OAuth endpoints"));
            } else {
                return createErrorResponse(404, OAuthError.invalidRequest("Invalid endpoint"));
            }
            
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            logger.error("Error processing OAuth2 request - Duration: {}ms", duration, e);
            return createErrorResponse(500, OAuthError.serverError("Internal server error"));
        }
    }
    
    /**
     * Handles OAuth 2.0 token requests (Client Credentials Grant).
     */
    private APIGatewayV2HTTPResponse handleTokenRequest(APIGatewayV2HTTPEvent input, Context context) {
        try {
            // Log the raw request body for debugging
            String requestBody = input.getBody();
            Boolean isBase64Encoded = input.getIsBase64Encoded();
            logger.info("Raw request body: '{}', isBase64Encoded: {}", requestBody, isBase64Encoded);
            
            // Decode Base64 if needed
            if (Boolean.TRUE.equals(isBase64Encoded) && requestBody != null) {
                try {
                    byte[] decodedBytes = java.util.Base64.getDecoder().decode(requestBody);
                    requestBody = new String(decodedBytes, java.nio.charset.StandardCharsets.UTF_8);
                    logger.info("Decoded request body: '{}'", requestBody);
                } catch (Exception e) {
                    logger.error("Failed to decode Base64 request body", e);
                    return createErrorResponse(400, OAuthError.invalidRequest("Invalid request body encoding"));
                }
            }
            
            // Parse form parameters
            Map<String, String> formParams = parseFormParameters(requestBody);
            logger.info("Parsed form parameters: {}", formParams);
            
            // Extract authorization header
            String authHeader = extractAuthorizationHeader(input);
            logger.info("Authorization header: '{}'", authHeader);
            
            // Parse token request
            TokenRequest tokenRequest;
            try {
                tokenRequest = OAuth2RequestParser.parseTokenRequest(formParams, authHeader);
            } catch (ClientCredentialsService.OAuth2AuthenticationException e) {
                // This handles parsing-level OAuth errors (including unsupported_grant_type)
                return createErrorResponse(400, e.getOAuthError());
            }
            
            // Authenticate and generate token using domain service
            TokenResponse tokenResponse = clientService.authenticate(tokenRequest);
            
            // Convert to API response model
            OAuth2TokenResponse apiResponse = new OAuth2TokenResponse(
                tokenResponse.getAccessToken(),
                tokenResponse.getTokenType(),
                tokenResponse.getExpiresIn(),
                tokenResponse.getScope(),
                tokenResponse.getIssuedAt()
            );
            
            return createSuccessResponse(apiResponse);
            
        } catch (ClientCredentialsService.OAuth2AuthenticationException e) {
            logger.warn("OAuth2 authentication error: {}", e.getOAuthError().getErrorDescription());
            return createErrorResponse(400, e.getOAuthError());
        } catch (Exception e) {
            logger.error("Error processing token request", e);
            return createErrorResponse(500, OAuthError.serverError("Token generation failed"));
        }
    }
    
    /**
     * Handles OAuth 2.0 token introspection requests.
     */
    private APIGatewayV2HTTPResponse handleIntrospectionRequest(APIGatewayV2HTTPEvent input, Context context) {
        try {
            // Log the raw request body for debugging
            String requestBody = input.getBody();
            Boolean isBase64Encoded = input.getIsBase64Encoded();
            logger.info("Raw introspection request body: '{}', isBase64Encoded: {}", requestBody, isBase64Encoded);
            
            // Decode Base64 if needed
            if (Boolean.TRUE.equals(isBase64Encoded) && requestBody != null) {
                try {
                    byte[] decodedBytes = java.util.Base64.getDecoder().decode(requestBody);
                    requestBody = new String(decodedBytes, java.nio.charset.StandardCharsets.UTF_8);
                    logger.info("Decoded introspection request body: '{}'", requestBody);
                } catch (Exception e) {
                    logger.error("Failed to decode Base64 introspection request body", e);
                    return createErrorResponse(400, OAuthError.invalidRequest("Invalid request body encoding"));
                }
            }
            
            // Parse form parameters
            Map<String, String> formParams = parseFormParameters(requestBody);
            logger.info("Parsed introspection form parameters: {}", formParams);
            
            String token = formParams.get("token");
            if (token == null || token.trim().isEmpty()) {
                return createErrorResponse(400, OAuthError.invalidRequest("Missing token parameter"));
            }
            
            // Validate token and extract claims
            boolean isActive = tokenService.validateToken(token);
            OAuth2IntrospectionResponse response;
            
            if (isActive) {
                // Extract token claims
                Map<String, Object> claims = tokenService.extractClaims(token);
                
                // Handle potential Integer vs Long casting issues
                Object expObj = claims.get("exp");
                Object iatObj = claims.get("iat");
                
                Integer exp = null;
                Integer iat = null;
                
                if (expObj instanceof Number) {
                    exp = ((Number) expObj).intValue();
                }
                if (iatObj instanceof Number) {
                    iat = ((Number) iatObj).intValue();
                }
                
                response = new OAuth2IntrospectionResponse(
                    true,
                    (String) claims.get("client_id"),
                    (String) claims.get("scope"),
                    "Bearer",
                    exp,
                    iat
                );
            } else {
                response = new OAuth2IntrospectionResponse(false, null, null, null, null, null);
            }
            
            return createSuccessResponse(response);
            
        } catch (Exception e) {
            logger.error("Error processing introspection request", e);
            return createErrorResponse(500, OAuthError.serverError("Introspection failed"));
        }
    }
    
    /**
     * Initializes Spring application context.
     */
    private static ApplicationContext initializeApplicationContext() {
        try {
            AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
            context.scan("com.example.auth");
            context.refresh();
            
            logger.info("Spring ApplicationContext initialized successfully");
            return context;
            
        } catch (Exception e) {
            logger.error("Failed to initialize ApplicationContext", e);
            throw new RuntimeException("Application initialization failed", e);
        }
    }
    
    /**
     * Parses form-encoded request body into parameter map.
     */
    private Map<String, String> parseFormParameters(String body) {
        Map<String, String> params = new HashMap<>();
        
        if (body == null || body.trim().isEmpty()) {
            return params;
        }
        
        try {
            String[] pairs = body.split("&");
            for (String pair : pairs) {
                String[] keyValue = pair.split("=", 2);
                if (keyValue.length == 2) {
                    String key = URLDecoder.decode(keyValue[0], "UTF-8");
                    String value = URLDecoder.decode(keyValue[1], "UTF-8");
                    params.put(key, value);
                }
            }
        } catch (UnsupportedEncodingException e) {
            logger.error("Error parsing form parameters", e);
        }
        
        return params;
    }
    
    /**
     * Extracts Authorization header from API Gateway request.
     */
    private String extractAuthorizationHeader(APIGatewayV2HTTPEvent input) {
        Map<String, String> headers = input.getHeaders();
        if (headers == null) {
            return null;
        }
        
        // Check both Authorization and authorization (case-insensitive)
        String authHeader = headers.get("authorization");
        if (authHeader == null) {
            authHeader = headers.get("Authorization");
        }
        
        return authHeader;
    }
    
    /**
     * Creates success response with proper headers.
     */
    private APIGatewayV2HTTPResponse createSuccessResponse(Object responseBody) {
        try {
            String jsonBody = objectMapper.writeValueAsString(responseBody);
            
            Map<String, String> headers = new HashMap<>();
            headers.put(CONTENT_TYPE, APPLICATION_JSON);
            headers.put("Cache-Control", "no-cache, no-store, must-revalidate");
            headers.put("Pragma", "no-cache");
            headers.put("Expires", "0");
            
            return APIGatewayV2HTTPResponse.builder()
                    .withStatusCode(200)
                    .withHeaders(headers)
                    .withBody(jsonBody)
                    .build();
                    
        } catch (Exception e) {
            logger.error("Error creating success response", e);
            return createErrorResponse(500, OAuthError.serverError("Response generation failed"));
        }
    }
    
    /**
     * Creates error response with proper HTTP status code.
     */
    private APIGatewayV2HTTPResponse createErrorResponse(int statusCode, OAuthError error) {
        try {
            OAuth2ErrorResponse errorResponse = new OAuth2ErrorResponse(
                error.getError(),
                error.getErrorDescription(),
                error.getErrorUri()
            );
            
            String jsonBody = objectMapper.writeValueAsString(errorResponse);
            
            Map<String, String> headers = new HashMap<>();
            headers.put(CONTENT_TYPE, APPLICATION_JSON);
            
            return APIGatewayV2HTTPResponse.builder()
                    .withStatusCode(statusCode)
                    .withHeaders(headers)
                    .withBody(jsonBody)
                    .build();
                    
        } catch (Exception e) {
            logger.error("Error creating error response", e);
            // Fallback response
            return APIGatewayV2HTTPResponse.builder()
                    .withStatusCode(statusCode)
                    .withBody("{\"error\":\"server_error\",\"error_description\":\"Internal server error\"}")
                    .build();
        }
    }
} 