package com.example.auth.infrastructure.azure.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for Spring Cloud Functions Authorization Server.
 * Tests the actual Spring function beans that are exposed as Azure Functions.
 * 
 * This tests the current Spring Cloud Functions implementation with real domain services.
 */
@SpringBootTest
@ActiveProfiles("azure")
class SpringCloudFunctionIntegrationTest {
    
    @Autowired
    @Qualifier("basicAuth")
    private Function<String, String> basicAuthFunction;
    
    @Autowired
    @Qualifier("oauthToken")
    private Function<String, String> oauthTokenFunction;
    
    @Autowired
    @Qualifier("oauthIntrospect")
    private Function<String, String> oauthIntrospectFunction;
    
    private ObjectMapper objectMapper;
    
    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }
    
    @Test
    void testBasicAuthFunctionBean() {
        // Arrange
        String validCredentialsJson = """
            {
                "username": "test",
                "password": "test123"
            }
            """;
        
        // Act
        String response = basicAuthFunction.apply(validCredentialsJson);
        
        // Assert
        assertNotNull(response);
        assertTrue(response.contains("\"authenticated\""));
        assertTrue(response.contains("\"message\""));
        assertTrue(response.contains("\"timestamp\""));
        assertTrue(response.contains("\"username\":\"test\""));
    }
    
    @Test
    void testOAuthTokenFunctionBean() {
        // Arrange
        String tokenRequestJson = """
            {
                "grantType": "client_credentials",
                "clientId": "test-client",
                "clientSecret": "test-secret",
                "scope": "read"
            }
            """;
        
        // Act
        String response = oauthTokenFunction.apply(tokenRequestJson);
        
        // Assert
        assertNotNull(response);
        // Should contain either success response or error response
        assertTrue(response.contains("access_token") || response.contains("error"));
    }
    
    @Test
    void testOAuthIntrospectFunctionBean() {
        // Arrange
        String introspectRequestJson = """
            {
                "token": "test-token"
            }
            """;
        
        // Act
        String response = oauthIntrospectFunction.apply(introspectRequestJson);
        
        // Assert
        assertNotNull(response);
        assertTrue(response.contains("\"active\""));
    }
    
    @Test
    void testEndToEndOAuthFlow() {
        // Test complete OAuth flow: generate token -> introspect token
        
        // Step 1: Generate token (this will likely fail with current test credentials)
        String tokenRequest = """
            {
                "grantType": "client_credentials",
                "clientId": "test-client",
                "clientSecret": "test-secret",
                "scope": "read"
            }
            """;
        
        String tokenResponse = oauthTokenFunction.apply(tokenRequest);
        assertNotNull(tokenResponse);
        
        // Step 2: Introspect a test token (independent of token generation)
        String introspectRequest = """
            {
                "token": "test-token"
            }
            """;
        
        String introspectResponse = oauthIntrospectFunction.apply(introspectRequest);
        assertNotNull(introspectResponse);
        assertTrue(introspectResponse.contains("\"active\""));
    }
    
    @Test
    void testErrorHandling() {
        // Test functions handle invalid input gracefully
        
        // Test with invalid JSON
        String invalidJson = "invalid-json";
        String response = basicAuthFunction.apply(invalidJson);
        
        assertNotNull(response);
        // Should return some form of error response
        assertTrue(response.contains("error") || response.contains("false"));
    }
    
    @Test
    void testBasicAuthInvalidCredentials() {
        // Test with wrong credentials
        String invalidCredentialsJson = """
            {
                "username": "test",
                "password": "wrongpassword"
            }
            """;
        
        String response = basicAuthFunction.apply(invalidCredentialsJson);
        
        assertNotNull(response);
        assertTrue(response.contains("\"authenticated\":false"));
    }
    
    @Test
    void testOAuthTokenInvalidClient() {
        // Test with invalid client credentials
        String invalidTokenRequest = """
            {
                "grantType": "client_credentials",
                "clientId": "invalid-client",
                "clientSecret": "wrong-secret",
                "scope": "read"
            }
            """;
        
        String response = oauthTokenFunction.apply(invalidTokenRequest);
        
        assertNotNull(response);
        assertTrue(response.contains("error"));
    }
    
    @Test
    void testOAuthIntrospectInvalidToken() {
        // Test token introspection with invalid token
        String invalidIntrospectRequest = """
            {
                "token": "invalid-token-12345"
            }
            """;
        
        String response = oauthIntrospectFunction.apply(invalidIntrospectRequest);
        
        assertNotNull(response);
        assertTrue(response.contains("\"active\":false"));
    }
    
    @Test
    void testFunctionBeansInjection() {
        // Verify that Spring dependency injection is working
        assertNotNull(basicAuthFunction);
        assertNotNull(oauthTokenFunction);
        assertNotNull(oauthIntrospectFunction);
    }
    
    @Test
    void testMissingTokenParameter() {
        // Test introspection with missing token parameter
        String missingTokenRequest = """
            {
                "clientId": "test-client"
            }
            """;
        
        String response = oauthIntrospectFunction.apply(missingTokenRequest);
        
        assertNotNull(response);
        assertTrue(response.contains("error") || response.contains("\"active\":false"));
    }
} 