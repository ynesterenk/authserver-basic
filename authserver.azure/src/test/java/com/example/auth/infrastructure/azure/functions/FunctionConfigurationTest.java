package com.example.auth.infrastructure.azure.functions;

import com.example.auth.domain.model.AuthenticationRequest;
import com.example.auth.domain.model.AuthenticationResult;
import com.example.auth.domain.port.UserRepository;
import com.example.auth.domain.port.oauth.OAuthClientRepository;
import com.example.auth.domain.service.AuthenticatorService;
import com.example.auth.domain.service.oauth.ClientCredentialsService;
import com.example.auth.domain.service.oauth.OAuth2TokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Optional;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit test for FunctionConfiguration using mocks.
 * Tests Spring Cloud Function definitions in isolation.
 */
@ExtendWith(MockitoExtension.class)
class FunctionConfigurationTest {

    @Mock
    private AuthenticatorService mockAuthenticatorService;
    
    @Mock
    private UserRepository mockUserRepository;
    
    @Mock
    private ClientCredentialsService mockClientCredentialsService;
    
    @Mock
    private OAuth2TokenService mockOAuth2TokenService;

    private FunctionConfiguration functionConfiguration;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        functionConfiguration = new FunctionConfiguration();
        objectMapper = new ObjectMapper();
    }

    @Test
    void testBasicAuthFunction() {
        // Mock dependencies - only stub what's actually used
        AuthenticationResult mockResult = mock(AuthenticationResult.class);
        when(mockResult.isAllowed()).thenReturn(true);
        // Don't stub getReason() since it's not used when isAllowed() is true
        
        when(mockAuthenticatorService.authenticate(any(AuthenticationRequest.class))).thenReturn(mockResult);
        when(mockUserRepository.findByUsername("test")).thenReturn(Optional.empty());

        // Create the function
        Function<String, String> basicAuthFunction = functionConfiguration.basicAuth(mockAuthenticatorService, mockUserRepository);
        
        assertNotNull(basicAuthFunction, "basicAuth function should be created");
        
        // Test with valid input that matches AuthenticationRequest JSON structure
        String input = "{\"username\":\"test\",\"password\":\"test123\"}";
        String result = basicAuthFunction.apply(input);
        
        assertNotNull(result, "Function should return a result");
        assertTrue(result.contains("authenticated"), "Result should contain authentication status");
        assertTrue(result.contains("true"), "Authentication should be successful");
        
        // Verify service was called
        verify(mockAuthenticatorService).authenticate(any(AuthenticationRequest.class));
        verify(mockUserRepository).findByUsername("test");
    }

    @Test
    void testBasicAuthFunctionWithFailedAuthentication() {
        // Mock failed authentication - now we DO need getReason()
        AuthenticationResult mockResult = mock(AuthenticationResult.class);
        when(mockResult.isAllowed()).thenReturn(false);
        when(mockResult.getReason()).thenReturn("Invalid credentials");
        when(mockAuthenticatorService.authenticate(any(AuthenticationRequest.class))).thenReturn(mockResult);

        Function<String, String> basicAuthFunction = functionConfiguration.basicAuth(mockAuthenticatorService, mockUserRepository);
        
        String input = "{\"username\":\"test\",\"password\":\"wrong\"}";
        String result = basicAuthFunction.apply(input);
        
        assertNotNull(result, "Function should return a result");
        assertTrue(result.contains("authenticated"), "Result should contain authentication status");
        assertTrue(result.contains("false"), "Authentication should fail");
        assertTrue(result.contains("Invalid credentials"), "Should contain failure reason");
        
        verify(mockAuthenticatorService).authenticate(any(AuthenticationRequest.class));
        // When authentication fails, user lookup is not performed
    }

    @Test
    void testOAuthTokenFunction() {
        // Create the function
        Function<String, String> oAuthTokenFunction = functionConfiguration.oauthToken(mockClientCredentialsService);
        
        assertNotNull(oAuthTokenFunction, "oauthToken function should be created");
        
        // Test that function can handle input (will likely fail due to mock, but should not throw)
        String input = "{\"grantType\":\"client_credentials\",\"clientId\":\"test\",\"clientSecret\":\"test\",\"scope\":\"read\"}";
        
        assertDoesNotThrow(() -> {
            String result = oAuthTokenFunction.apply(input);
            assertNotNull(result, "Function should return a result");
            // Result might be an error due to mock behavior, but function should execute
        }, "Function should not throw during execution");
    }

    @Test
    void testOAuthIntrospectFunction() {
        // Mock token service
        when(mockOAuth2TokenService.validateToken("test-token")).thenReturn(false);
        
        // Create the function
        Function<String, String> oAuthIntrospectFunction = functionConfiguration.oauthIntrospect(mockOAuth2TokenService);
        
        assertNotNull(oAuthIntrospectFunction, "oauthIntrospect function should be created");
        
        // Test with token input
        String input = "{\"token\":\"test-token\"}";
        String result = oAuthIntrospectFunction.apply(input);
        
        assertNotNull(result, "Function should return a result");
        assertTrue(result.contains("active"), "Result should contain active status");
        assertTrue(result.contains("false"), "Token should be inactive");
        
        // Verify service was called
        verify(mockOAuth2TokenService).validateToken("test-token");
    }

    @Test
    void testBasicAuthFunctionWithInvalidInput() {
        // Create the function
        Function<String, String> basicAuthFunction = functionConfiguration.basicAuth(mockAuthenticatorService, mockUserRepository);
        
        // Test with invalid JSON
        String result = basicAuthFunction.apply("invalid-json");
        
        assertNotNull(result, "Function should handle invalid input gracefully");
        assertTrue(result.contains("authenticated") && result.contains("false"), 
            "Result should indicate authentication failure");
        
        // With invalid JSON, authenticate should not be called
        verify(mockAuthenticatorService, never()).authenticate(any());
        verify(mockUserRepository, never()).findByUsername(anyString());
    }

    @Test
    void testOAuthTokenFunctionWithInvalidInput() {
        // Create the function
        Function<String, String> oAuthTokenFunction = functionConfiguration.oauthToken(mockClientCredentialsService);
        
        // Test with invalid JSON
        String result = oAuthTokenFunction.apply("invalid-json");
        
        assertNotNull(result, "Function should handle invalid input gracefully");
        assertTrue(result.contains("error"), "Result should contain error information");
        
        // With invalid JSON, service should not be called
        verify(mockClientCredentialsService, never()).authenticate(any());
    }

    @Test
    void testOAuthIntrospectFunctionWithInvalidInput() {
        // Create the function
        Function<String, String> oAuthIntrospectFunction = functionConfiguration.oauthIntrospect(mockOAuth2TokenService);
        
        // Test with invalid JSON
        String result = oAuthIntrospectFunction.apply("invalid-json");
        
        assertNotNull(result, "Function should handle invalid input gracefully");
        assertTrue(result.contains("active") && result.contains("false"), 
            "Result should indicate inactive token");
        
        // With invalid JSON, service should not be called
        verify(mockOAuth2TokenService, never()).validateToken(anyString());
    }

    @Test
    void testOAuthIntrospectFunctionWithMissingToken() {
        // Create the function
        Function<String, String> oAuthIntrospectFunction = functionConfiguration.oauthIntrospect(mockOAuth2TokenService);
        
        // Test with missing token
        String input = "{\"nottoken\":\"value\"}";
        String result = oAuthIntrospectFunction.apply(input);
        
        assertNotNull(result, "Function should handle missing token gracefully");
        assertTrue(result.contains("error"), "Result should contain error for missing token");
        
        // With missing token, service should not be called
        verify(mockOAuth2TokenService, never()).validateToken(anyString());
    }

    @Test
    void testFunctionConfigurationInstantiation() {
        // Test that configuration can be instantiated
        FunctionConfiguration config = new FunctionConfiguration();
        assertNotNull(config, "FunctionConfiguration should be instantiable");
    }

    @Test
    void testAllFunctionsCanBeCreated() {
        // Test that all functions can be created without throwing exceptions
        assertDoesNotThrow(() -> {
            Function<String, String> basicAuth = functionConfiguration.basicAuth(mockAuthenticatorService, mockUserRepository);
            Function<String, String> oauthToken = functionConfiguration.oauthToken(mockClientCredentialsService);
            Function<String, String> oauthIntrospect = functionConfiguration.oauthIntrospect(mockOAuth2TokenService);
            
            assertNotNull(basicAuth, "basicAuth function should be created");
            assertNotNull(oauthToken, "oauthToken function should be created");
            assertNotNull(oauthIntrospect, "oauthIntrospect function should be created");
        }, "All functions should be creatable without exceptions");
    }

    @Test
    void testBasicAuthFunctionErrorHandling() {
        // Test error handling when authenticate throws exception
        when(mockAuthenticatorService.authenticate(any(AuthenticationRequest.class)))
            .thenThrow(new RuntimeException("Authentication service error"));
        
        Function<String, String> basicAuthFunction = functionConfiguration.basicAuth(mockAuthenticatorService, mockUserRepository);
        
        // Even with a valid-looking input, if service throws exception, should get error response
        String input = "{\"username\":\"test\",\"password\":\"test123\"}";
        String result = basicAuthFunction.apply(input);
        
        assertNotNull(result, "Function should return error response");
        assertTrue(result.contains("authenticated") && result.contains("false"), 
            "Should return authentication failure on service error");
        
        // Verify the service was called (and threw the exception)
        verify(mockAuthenticatorService).authenticate(any(AuthenticationRequest.class));
        // Repository should not be called when authentication throws exception
        verify(mockUserRepository, never()).findByUsername(anyString());
    }

    @Test
    void testOAuthIntrospectFunctionWithValidToken() {
        // Test with valid token
        when(mockOAuth2TokenService.validateToken("valid-token")).thenReturn(true);
        when(mockOAuth2TokenService.extractClientId("valid-token")).thenReturn("test-client");
        when(mockOAuth2TokenService.extractScope("valid-token")).thenReturn("read write");
        when(mockOAuth2TokenService.extractClaims("valid-token")).thenReturn(
            java.util.Map.of("exp", 1234567890, "iat", 1234567000)
        );
        
        Function<String, String> oAuthIntrospectFunction = functionConfiguration.oauthIntrospect(mockOAuth2TokenService);
        
        String input = "{\"token\":\"valid-token\"}";
        String result = oAuthIntrospectFunction.apply(input);
        
        assertNotNull(result, "Function should return a result");
        assertTrue(result.contains("active"), "Result should contain active status");
        assertTrue(result.contains("true"), "Token should be active");
        assertTrue(result.contains("test-client"), "Should contain client ID");
        
        // Verify all service methods were called
        verify(mockOAuth2TokenService).validateToken("valid-token");
        verify(mockOAuth2TokenService).extractClientId("valid-token");
        verify(mockOAuth2TokenService).extractScope("valid-token");
        verify(mockOAuth2TokenService).extractClaims("valid-token");
    }
} 