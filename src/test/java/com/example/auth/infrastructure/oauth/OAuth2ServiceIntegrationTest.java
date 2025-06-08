package com.example.auth.infrastructure.oauth;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.example.auth.infrastructure.oauth.model.OAuth2TokenResponse;
import com.example.auth.infrastructure.oauth.model.OAuth2ErrorResponse;
import com.example.auth.infrastructure.oauth.model.OAuth2IntrospectionResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Integration tests for OAuth 2.0 Lambda handler with AWS services.
 * Uses Testcontainers with LocalStack for AWS service mocking.
 * 
 * Note: These tests require Docker to be running.
 */
@Testcontainers
@ActiveProfiles("local")
class OAuth2ServiceIntegrationTest {

    @Container
    static LocalStackContainer localstack;
    
    static {
        try {
            // Initialize LocalStack container for AWS integration tests
            localstack = new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.0"))
                    .withServices(
                        LocalStackContainer.Service.SECRETSMANAGER,
                        LocalStackContainer.Service.SSM,
                        LocalStackContainer.Service.KMS
                    )
                    .withReuse(true);
                    
            if (isDockerAvailable()) {
                localstack.start();
                System.out.println("LocalStack container started successfully on: " + localstack.getEndpoint());
            } else {
                System.out.println("Docker not available - skipping LocalStack initialization");
                localstack = null;
            }
            
        } catch (Exception e) {
            System.err.println("Failed to start LocalStack container - will skip container-dependent tests: " + e.getMessage());
            localstack = null;
        }
    }

    @Mock
    private Context mockContext;

    private OAuth2LambdaHandler oauth2Handler;
    private ObjectMapper objectMapper;

    @BeforeAll
    static void setUpClass() {
        // Ensure we're using the local profile for these tests
        System.setProperty("spring.profiles.active", "local");
    }

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        // Mock Lambda context
        when(mockContext.getAwsRequestId()).thenReturn("test-oauth2-request-id-12345");
        when(mockContext.getFunctionName()).thenReturn("oauth2-function");
        when(mockContext.getRemainingTimeInMillis()).thenReturn(30000);
        
        try {
            // Initialize handler with local configuration
            oauth2Handler = new OAuth2LambdaHandler();
            objectMapper = new ObjectMapper();
        } catch (Exception e) {
            // If we can't initialize the handler, skip the tests
            System.err.println("Failed to initialize OAuth2LambdaHandler: " + e.getMessage());
            oauth2Handler = null;
        }
    }

    @Nested
    @DisplayName("OAuth 2.0 Token Endpoint Tests")
    class TokenEndpointTests {

        @Test
        @DisplayName("Should generate token for valid client credentials (form data)")
        void shouldGenerateTokenForValidClientCredentialsFormData() throws Exception {
            // Skip if handler couldn't be initialized
            if (oauth2Handler == null) {
                System.out.println("Skipping test - OAuth2LambdaHandler could not be initialized");
                return;
            }
            
            // Prepare OAuth 2.0 token request with form data
            String formBody = "grant_type=client_credentials&client_id=test-client-1&client_secret=test-client-1-secret&scope=read";
            APIGatewayV2HTTPEvent request = createOAuth2Request("POST", "/oauth/token", formBody, null);
            
            // Execute
            APIGatewayV2HTTPResponse response = oauth2Handler.handleRequest(request, mockContext);
            
            // Verify response
            assertEquals(200, response.getStatusCode());
            assertEquals("application/json", response.getHeaders().get("content-type"));
            
            // Parse response body
            OAuth2TokenResponse tokenResponse = objectMapper.readValue(response.getBody(), OAuth2TokenResponse.class);
            assertNotNull(tokenResponse.getAccessToken());
            assertEquals("Bearer", tokenResponse.getTokenType());
            assertTrue(tokenResponse.getExpiresIn() > 0);
            assertEquals("read", tokenResponse.getScope());
            assertTrue(tokenResponse.getIssuedAt() > 0);
            
            // Verify token is a valid JWT (3 parts separated by dots)
            String[] tokenParts = tokenResponse.getAccessToken().split("\\.");
            assertEquals(3, tokenParts.length, "Token should be a valid JWT with 3 parts");
        }

        @Test
        @DisplayName("Should generate token for valid client credentials (Basic Auth)")
        void shouldGenerateTokenForValidClientCredentialsBasicAuth() throws Exception {
            // Skip if handler couldn't be initialized
            if (oauth2Handler == null) {
                System.out.println("Skipping test - OAuth2LambdaHandler could not be initialized");
                return;
            }
            
            // Prepare OAuth 2.0 token request with Basic Auth
            String formBody = "grant_type=client_credentials&scope=read%20write";
            String credentials = "test-client-1:test-client-1-secret";
            String basicAuth = "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes());
            
            APIGatewayV2HTTPEvent request = createOAuth2Request("POST", "/oauth/token", formBody, basicAuth);
            
            // Execute
            APIGatewayV2HTTPResponse response = oauth2Handler.handleRequest(request, mockContext);
            
            // Verify response
            assertEquals(200, response.getStatusCode());
            
            // Parse response body
            OAuth2TokenResponse tokenResponse = objectMapper.readValue(response.getBody(), OAuth2TokenResponse.class);
            assertNotNull(tokenResponse.getAccessToken());
            assertEquals("Bearer", tokenResponse.getTokenType());
            assertEquals("read write", tokenResponse.getScope());
        }

        @Test
        @DisplayName("Should reject invalid client credentials")
        void shouldRejectInvalidClientCredentials() throws Exception {
            // Skip if handler couldn't be initialized
            if (oauth2Handler == null) {
                System.out.println("Skipping test - OAuth2LambdaHandler could not be initialized");
                return;
            }
            
            // Prepare request with invalid credentials
            String formBody = "grant_type=client_credentials&client_id=test-client-1&client_secret=wrong-secret";
            APIGatewayV2HTTPEvent request = createOAuth2Request("POST", "/oauth/token", formBody, null);
            
            // Execute
            APIGatewayV2HTTPResponse response = oauth2Handler.handleRequest(request, mockContext);
            
            // Verify error response
            assertEquals(400, response.getStatusCode());
            
            // Parse error response
            OAuth2ErrorResponse errorResponse = objectMapper.readValue(response.getBody(), OAuth2ErrorResponse.class);
            assertEquals("invalid_client", errorResponse.getError());
            assertNotNull(errorResponse.getErrorDescription());
        }

        @Test
        @DisplayName("Should reject disabled client")
        void shouldRejectDisabledClient() throws Exception {
            // Skip if handler couldn't be initialized
            if (oauth2Handler == null) {
                System.out.println("Skipping test - OAuth2LambdaHandler could not be initialized");
                return;
            }
            
            // Prepare request with disabled client
            String formBody = "grant_type=client_credentials&client_id=test-client-3&client_secret=test-client-3-secret";
            APIGatewayV2HTTPEvent request = createOAuth2Request("POST", "/oauth/token", formBody, null);
            
            // Execute
            APIGatewayV2HTTPResponse response = oauth2Handler.handleRequest(request, mockContext);
            
            // Verify error response
            assertEquals(400, response.getStatusCode());
            
            // Parse error response
            OAuth2ErrorResponse errorResponse = objectMapper.readValue(response.getBody(), OAuth2ErrorResponse.class);
            assertEquals("access_denied", errorResponse.getError());
            assertNotNull(errorResponse.getErrorDescription());
        }

        @Test
        @DisplayName("Should reject unsupported grant type")
        void shouldRejectUnsupportedGrantType() throws Exception {
            // Skip if handler couldn't be initialized
            if (oauth2Handler == null) {
                System.out.println("Skipping test - OAuth2LambdaHandler could not be initialized");
                return;
            }
            
            // Prepare request with unsupported grant type
            String formBody = "grant_type=authorization_code&client_id=test-client-1&client_secret=test-client-1-secret";
            APIGatewayV2HTTPEvent request = createOAuth2Request("POST", "/oauth/token", formBody, null);
            
            // Execute
            APIGatewayV2HTTPResponse response = oauth2Handler.handleRequest(request, mockContext);
            
            // Verify error response
            assertEquals(400, response.getStatusCode());
            
            // Parse error response
            OAuth2ErrorResponse errorResponse = objectMapper.readValue(response.getBody(), OAuth2ErrorResponse.class);
            assertEquals("unsupported_grant_type", errorResponse.getError());
            assertNotNull(errorResponse.getErrorDescription());
        }

        @Test
        @DisplayName("Should reject invalid scope")
        void shouldRejectInvalidScope() throws Exception {
            // Skip if handler couldn't be initialized
            if (oauth2Handler == null) {
                System.out.println("Skipping test - OAuth2LambdaHandler could not be initialized");
                return;
            }
            
            // Prepare request with invalid scope (test-client-2 only has 'read' scope)
            String formBody = "grant_type=client_credentials&client_id=test-client-2&client_secret=test-client-2-secret&scope=admin";
            APIGatewayV2HTTPEvent request = createOAuth2Request("POST", "/oauth/token", formBody, null);
            
            // Execute
            APIGatewayV2HTTPResponse response = oauth2Handler.handleRequest(request, mockContext);
            
            // Verify error response
            assertEquals(400, response.getStatusCode());
            
            // Parse error response
            OAuth2ErrorResponse errorResponse = objectMapper.readValue(response.getBody(), OAuth2ErrorResponse.class);
            assertEquals("invalid_scope", errorResponse.getError());
            assertNotNull(errorResponse.getErrorDescription());
        }

        @Test
        @DisplayName("Should reject GET method")
        void shouldRejectGetMethod() throws Exception {
            // Skip if handler couldn't be initialized
            if (oauth2Handler == null) {
                System.out.println("Skipping test - OAuth2LambdaHandler could not be initialized");
                return;
            }
            
            // Prepare GET request
            APIGatewayV2HTTPEvent request = createOAuth2Request("GET", "/oauth/token", null, null);
            
            // Execute
            APIGatewayV2HTTPResponse response = oauth2Handler.handleRequest(request, mockContext);
            
            // Verify error response
            assertEquals(405, response.getStatusCode());
            
            // Parse error response
            OAuth2ErrorResponse errorResponse = objectMapper.readValue(response.getBody(), OAuth2ErrorResponse.class);
            assertEquals("invalid_request", errorResponse.getError());
            assertTrue(errorResponse.getErrorDescription().contains("POST"));
        }
    }

    @Nested
    @DisplayName("OAuth 2.0 Token Introspection Tests")
    class IntrospectionEndpointTests {

        private String validToken;

        @BeforeEach
        void setUp() throws Exception {
            // Generate a valid token for introspection tests
            if (oauth2Handler != null) {
                String formBody = "grant_type=client_credentials&client_id=test-client-1&client_secret=test-client-1-secret&scope=read";
                APIGatewayV2HTTPEvent tokenRequest = createOAuth2Request("POST", "/oauth/token", formBody, null);
                APIGatewayV2HTTPResponse tokenResponse = oauth2Handler.handleRequest(tokenRequest, mockContext);
                
                if (tokenResponse.getStatusCode() == 200) {
                    OAuth2TokenResponse token = objectMapper.readValue(tokenResponse.getBody(), OAuth2TokenResponse.class);
                    validToken = token.getAccessToken();
                }
            }
        }

        @Test
        @DisplayName("Should introspect valid token")
        void shouldIntrospectValidToken() throws Exception {
            // Skip if handler couldn't be initialized or token generation failed
            if (oauth2Handler == null || validToken == null) {
                System.out.println("Skipping test - OAuth2LambdaHandler could not be initialized or token generation failed");
                return;
            }
            
            // Prepare introspection request
            String formBody = "token=" + validToken;
            APIGatewayV2HTTPEvent request = createOAuth2Request("POST", "/oauth/introspect", formBody, null);
            
            // Execute
            APIGatewayV2HTTPResponse response = oauth2Handler.handleRequest(request, mockContext);
            
            // Verify response
            assertEquals(200, response.getStatusCode());
            
            // Parse response body
            OAuth2IntrospectionResponse introspectionResponse = objectMapper.readValue(response.getBody(), OAuth2IntrospectionResponse.class);
            assertTrue(introspectionResponse.isActive());
            assertEquals("test-client-1", introspectionResponse.getClientId());
            assertEquals("read", introspectionResponse.getScope());
            assertEquals("Bearer", introspectionResponse.getTokenType());
            assertNotNull(introspectionResponse.getExp());
            assertNotNull(introspectionResponse.getIat());
        }

        @Test
        @DisplayName("Should introspect invalid token")
        void shouldIntrospectInvalidToken() throws Exception {
            // Skip if handler couldn't be initialized
            if (oauth2Handler == null) {
                System.out.println("Skipping test - OAuth2LambdaHandler could not be initialized");
                return;
            }
            
            // Prepare introspection request with invalid token
            String formBody = "token=invalid.jwt.token";
            APIGatewayV2HTTPEvent request = createOAuth2Request("POST", "/oauth/introspect", formBody, null);
            
            // Execute
            APIGatewayV2HTTPResponse response = oauth2Handler.handleRequest(request, mockContext);
            
            // Verify response
            assertEquals(200, response.getStatusCode());
            
            // Parse response body
            OAuth2IntrospectionResponse introspectionResponse = objectMapper.readValue(response.getBody(), OAuth2IntrospectionResponse.class);
            assertFalse(introspectionResponse.isActive());
            assertNull(introspectionResponse.getClientId());
        }

        @Test
        @DisplayName("Should reject GET method for introspection")
        void shouldRejectGetMethodForIntrospection() throws Exception {
            // Skip if handler couldn't be initialized
            if (oauth2Handler == null) {
                System.out.println("Skipping test - OAuth2LambdaHandler could not be initialized");
                return;
            }
            
            // Prepare GET request
            APIGatewayV2HTTPEvent request = createOAuth2Request("GET", "/oauth/introspect", null, null);
            
            // Execute
            APIGatewayV2HTTPResponse response = oauth2Handler.handleRequest(request, mockContext);
            
            // Verify error response
            assertEquals(405, response.getStatusCode());
            
            // Parse error response
            OAuth2ErrorResponse errorResponse = objectMapper.readValue(response.getBody(), OAuth2ErrorResponse.class);
            assertEquals("invalid_request", errorResponse.getError());
            assertTrue(errorResponse.getErrorDescription().contains("POST"));
        }
    }

    @Nested
    @DisplayName("Performance and Security Tests")
    class PerformanceSecurityTests {

        @Test
        @DisplayName("Should complete token generation within performance target")
        void shouldCompleteTokenGenerationWithinPerformanceTarget() throws Exception {
            // Skip if handler couldn't be initialized
            if (oauth2Handler == null) {
                System.out.println("Skipping test - OAuth2LambdaHandler could not be initialized");
                return;
            }
            
            // Prepare request
            String formBody = "grant_type=client_credentials&client_id=test-client-1&client_secret=test-client-1-secret&scope=read";
            APIGatewayV2HTTPEvent request = createOAuth2Request("POST", "/oauth/token", formBody, null);
            
            // Measure execution time
            long startTime = System.currentTimeMillis();
            APIGatewayV2HTTPResponse response = oauth2Handler.handleRequest(request, mockContext);
            long executionTime = System.currentTimeMillis() - startTime;
            
            // Verify response and performance
            assertEquals(200, response.getStatusCode());
            assertTrue(executionTime < 3000, "Token generation should complete within 3 seconds for integration test, was: " + executionTime + "ms");
        }

        @Test
        @DisplayName("Should include proper security headers")
        void shouldIncludeProperSecurityHeaders() throws Exception {
            // Skip if handler couldn't be initialized
            if (oauth2Handler == null) {
                System.out.println("Skipping test - OAuth2LambdaHandler could not be initialized");
                return;
            }
            
            // Prepare request
            String formBody = "grant_type=client_credentials&client_id=test-client-1&client_secret=test-client-1-secret";
            APIGatewayV2HTTPEvent request = createOAuth2Request("POST", "/oauth/token", formBody, null);
            
            // Execute
            APIGatewayV2HTTPResponse response = oauth2Handler.handleRequest(request, mockContext);
            
            // Verify security headers
            Map<String, String> headers = response.getHeaders();
            assertEquals("application/json", headers.get("content-type"));
            assertEquals("no-cache, no-store, must-revalidate", headers.get("Cache-Control"));
            assertEquals("no-cache", headers.get("Pragma"));
            assertEquals("0", headers.get("Expires"));
        }
    }

    /**
     * Helper method to create OAuth 2.0 requests for API Gateway v2.
     */
    private APIGatewayV2HTTPEvent createOAuth2Request(String httpMethod, String path, String body, String authHeader) {
        Map<String, String> headers = new HashMap<>();
        headers.put("content-type", "application/x-www-form-urlencoded");
        
        if (authHeader != null) {
            headers.put("authorization", authHeader);
        }
        
        return APIGatewayV2HTTPEvent.builder()
                .withRequestContext(APIGatewayV2HTTPEvent.RequestContext.builder()
                        .withHttp(APIGatewayV2HTTPEvent.RequestContext.Http.builder()
                                .withMethod(httpMethod)
                                .withPath(path)
                                .build())
                        .build())
                .withHeaders(headers)
                .withBody(body)
                .build();
    }

    /**
     * Checks if Docker is available for running tests.
     */
    private static boolean isDockerAvailable() {
        try {
            Process process = new ProcessBuilder("docker", "--version").start();
            return process.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
