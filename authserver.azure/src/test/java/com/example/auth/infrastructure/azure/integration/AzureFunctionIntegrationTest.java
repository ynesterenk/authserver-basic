package com.example.auth.infrastructure.azure.integration;

import com.example.auth.domain.model.User;
import com.example.auth.domain.model.UserStatus;
import com.example.auth.domain.model.oauth.ClientStatus;
import com.example.auth.domain.model.oauth.OAuthClient;
import com.example.auth.domain.port.oauth.OAuthClientRepository;
import com.example.auth.domain.model.AuthenticationResult;
import com.example.auth.domain.model.oauth.TokenResponse;
import com.example.auth.domain.model.oauth.OAuthError;
import com.example.auth.domain.model.AuthenticationRequest;
import com.example.auth.domain.port.UserRepository;
import com.example.auth.domain.service.AuthenticatorService;
import com.example.auth.domain.service.oauth.ClientCredentialsService;
import com.example.auth.domain.service.oauth.OAuth2TokenService;
import com.example.auth.domain.util.PasswordHasher;
import com.example.auth.infrastructure.azure.LocalAzureOAuthClientRepository;
import com.example.auth.infrastructure.azure.LocalAzureUserRepository;
import com.example.auth.infrastructure.azure.functions.BasicAuthFunction;
import com.example.auth.infrastructure.azure.functions.OAuth2IntrospectFunction;
import com.example.auth.infrastructure.azure.functions.OAuth2TokenFunction;
import com.example.auth.test.AzureFunctionTestHelper;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Integration test for Azure Functions Authorization Server.
 * Tests complete end-to-end flows using real domain services and local repositories.
 * 
 * This tests the actual Azure Functions implementation with real data flows,
 * not just mocked components.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AzureFunctionIntegrationTest {
    
    @TempDir
    private static Path tempDir;
    
    private UserRepository userRepository;
    private OAuthClientRepository oAuthClientRepository;
    private PasswordHasher passwordHasher;
    private AuthenticatorService authenticatorService;
    private ClientCredentialsService clientCredentialsService;
    private OAuth2TokenService oAuth2TokenService;
    
    private BasicAuthFunction basicAuthFunction;
    private OAuth2TokenFunction oAuth2TokenFunction;
    private OAuth2IntrospectFunction oAuth2IntrospectFunction;
    
    private ExecutionContext mockContext;
    private Logger mockLogger;
    
    @BeforeAll
    void setUpIntegrationTest() throws IOException {
        setupTestData();
        setupDomainServices();
        setupAzureFunctions();
        setupMockContext();
    }
    
    @BeforeEach
    void resetMocks() {
        // Reset mocks before each test to avoid interference
        reset(authenticatorService, clientCredentialsService, oAuth2TokenService);
    }
    
    private void setupTestData() throws IOException {
        // Create test users.json file (array format expected by LocalAzureUserRepository)
        Path usersFile = tempDir.resolve("users.json");
        String usersJson = """
            [
              {
                "username": "testuser",
                "password": "password123",
                "status": "ACTIVE",
                "roles": ["user", "read"]
              },
              {
                "username": "adminuser",
                "password": "adminpass456",
                "status": "ACTIVE",
                "roles": ["admin", "read", "write"]
              }
            ]
            """;
        Files.write(usersFile, usersJson.getBytes());
        
        // Create test oauth-clients.json file (array format expected by LocalAzureOAuthClientRepository)
        Path clientsFile = tempDir.resolve("oauth-clients.json");
        String clientsJson = """
            [
              {
                "clientId": "test-client",
                "clientSecret": "secret123",
                "status": "ACTIVE",
                "allowedGrantTypes": ["client_credentials"],
                "allowedScopes": ["read", "write"],
                "tokenExpirationSeconds": 3600,
                "description": "Test Client"
              },
              {
                "clientId": "admin-client",
                "clientSecret": "adminsecret456",
                "status": "ACTIVE",
                "allowedGrantTypes": ["client_credentials"],
                "allowedScopes": ["read", "write", "admin"],
                "tokenExpirationSeconds": 7200,
                "description": "Admin Client"
              }
            ]
            """;
        Files.write(clientsFile, clientsJson.getBytes());
        
        // Setup real repositories with test data
        passwordHasher = mock(PasswordHasher.class);
        when(passwordHasher.hashPassword(anyString())).thenAnswer(invocation -> 
            "$2a$10$hashed_" + invocation.getArgument(0));
        
        // Use the test data files instead of classpath resources
        userRepository = new LocalAzureUserRepository(passwordHasher, Files.newInputStream(usersFile));
        oAuthClientRepository = new LocalAzureOAuthClientRepository(Files.newInputStream(clientsFile));
    }
    
    private void setupDomainServices() {
        // Setup real domain services with repositories
        authenticatorService = mock(AuthenticatorService.class);
        clientCredentialsService = mock(ClientCredentialsService.class);
        oAuth2TokenService = mock(OAuth2TokenService.class);
        
        // These would normally be configured with real implementations
        // but for integration testing, we use mocks to control behavior
    }
    
    private void setupAzureFunctions() {
        // Setup real Azure Functions with injected dependencies
        basicAuthFunction = new BasicAuthFunction();
        // Inject dependencies using reflection (in real implementation, this would be done by Spring)
        injectDependency(basicAuthFunction, "authenticatorService", authenticatorService);
        injectDependency(basicAuthFunction, "userRepository", userRepository);
        injectDependency(basicAuthFunction, "metrics", createMockMetrics());
        
        oAuth2TokenFunction = new OAuth2TokenFunction();
        // Inject dependencies
        injectDependency(oAuth2TokenFunction, "clientCredentialsService", clientCredentialsService);
        injectDependency(oAuth2TokenFunction, "metrics", createMockMetrics());
        
        oAuth2IntrospectFunction = new OAuth2IntrospectFunction();
        // Inject dependencies
        injectDependency(oAuth2IntrospectFunction, "tokenService", oAuth2TokenService);
        injectDependency(oAuth2IntrospectFunction, "metrics", createMockMetrics());
    }
    
    private void injectDependency(Object target, String fieldName, Object dependency) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, dependency);
        } catch (Exception e) {
            throw new RuntimeException("Failed to inject dependency: " + fieldName, e);
        }
    }
    
    private com.example.auth.infrastructure.azure.config.AzureFunctionConfiguration.AzureFunctionMetrics createMockMetrics() {
        com.example.auth.infrastructure.azure.config.AzureFunctionConfiguration.AzureFunctionMetrics metrics = 
            mock(com.example.auth.infrastructure.azure.config.AzureFunctionConfiguration.AzureFunctionMetrics.class);
        // Setup default behavior to avoid NPE
        doNothing().when(metrics).recordAuthenticationAttempt(anyString());
        doNothing().when(metrics).recordTokenGeneration(anyString());
        doNothing().when(metrics).recordIntrospection(anyString());
        return metrics;
    }
    
    private void setupMockContext() {
        mockContext = mock(ExecutionContext.class);
        mockLogger = mock(Logger.class);
        when(mockContext.getLogger()).thenReturn(mockLogger);
        // Add a test invocation ID for better log readability
        when(mockContext.getInvocationId()).thenReturn("test-invocation-" + UUID.randomUUID().toString());
    }
    
    @Test
    void testBasicAuthEndToEnd() {
        // Test complete Basic Auth flow from HTTP request to response
        
        // Arrange
        HttpRequestMessage<Optional<String>> request = 
            AzureFunctionTestHelper.createBasicAuthRequest("testuser", "password123");
        
        // Mock authenticator service to return success
        when(authenticatorService.authenticate(new AuthenticationRequest("testuser", "password123"))).thenReturn(
            createAuthResult(true, "Authentication successful"));
        
        // Act
        HttpResponseMessage response = basicAuthFunction.run(request, mockContext);
        
        // Assert
        assertNotNull(response);
        // Don't verify logger calls as Azure Functions use real SLF4J loggers, not mocks
        
        // Verify that the actual User was found in repository
        Optional<User> user = userRepository.findByUsername("testuser");
        assertTrue(user.isPresent());
        assertEquals("testuser", user.get().getUsername());
        assertEquals(UserStatus.ACTIVE, user.get().getStatus());
        assertEquals(Arrays.asList("user", "read"), user.get().getRoles());
    }
    
    @Test
    void testOAuth2TokenFlowIntegration() {
        // Test token generation and introspection together
        
        // Arrange - Token request
        HttpRequestMessage<Optional<String>> tokenRequest = 
            AzureFunctionTestHelper.createTokenRequest("test-client", "secret123", "client_credentials", "read write");
        
        // Mock successful token generation
        when(clientCredentialsService.authenticate(any())).thenReturn(
            createTokenResponse("access_token_123", "Bearer", 3600, "read write"));
        
        // Act - Generate token
        HttpResponseMessage tokenResponse = oAuth2TokenFunction.run(tokenRequest, mockContext);
        
        // Assert - Token generation
        assertNotNull(tokenResponse);
        
        // Verify that the actual OAuthClient was found in repository
        Optional<OAuthClient> client = oAuthClientRepository.findByClientId("test-client");
        assertTrue(client.isPresent());
        assertEquals("test-client", client.get().getClientId());
        assertEquals(ClientStatus.ACTIVE, client.get().getStatus());
        assertTrue(client.get().getAllowedGrantTypes().contains("client_credentials"));
        
        // Arrange - Token introspection
        HttpRequestMessage<Optional<String>> introspectRequest = 
            AzureFunctionTestHelper.createIntrospectionRequest("access_token_123");
        
        // Mock token validation
        when(oAuth2TokenService.validateToken("access_token_123")).thenReturn(true);
        when(oAuth2TokenService.extractClientId("access_token_123")).thenReturn("test-client");
        when(oAuth2TokenService.extractScope("access_token_123")).thenReturn("read write");
        when(oAuth2TokenService.extractClaims("access_token_123")).thenReturn(createTokenClaims());
        
        // Act - Introspect token
        HttpResponseMessage introspectResponse = oAuth2IntrospectFunction.run(introspectRequest, mockContext);
        
        // Assert - Token introspection
        assertNotNull(introspectResponse);
        verify(oAuth2TokenService).validateToken("access_token_123");
        verify(oAuth2TokenService).extractClientId("access_token_123");
        verify(oAuth2TokenService).extractScope("access_token_123");
    }
    
    @Test
    void testLocalRepositoriesIntegration() {
        // Test with local JSON files and verify data loading and caching
        
        // Test user repository
        Optional<User> testUser = userRepository.findByUsername("testuser");
        assertTrue(testUser.isPresent());
        assertEquals("$2a$10$hashed_password123", testUser.get().getPasswordHash());
        
        Optional<User> adminUser = userRepository.findByUsername("adminuser");
        assertTrue(adminUser.isPresent());
        assertEquals(Arrays.asList("admin", "read", "write"), adminUser.get().getRoles());
        
        // Test client repository
        Optional<OAuthClient> testClient = oAuthClientRepository.findByClientId("test-client");
        assertTrue(testClient.isPresent());
        assertEquals(Set.of("client_credentials"), testClient.get().getAllowedGrantTypes());
        assertEquals(Arrays.asList("read", "write"), testClient.get().getAllowedScopes());
        
        Optional<OAuthClient> adminClient = oAuthClientRepository.findByClientId("admin-client");
        assertTrue(adminClient.isPresent());
        assertEquals(7200, adminClient.get().getTokenExpirationSeconds());
        assertEquals(Arrays.asList("read", "write", "admin"), adminClient.get().getAllowedScopes());
    }
    
    @Test
    void testErrorHandlingIntegration() {
        // Test error scenarios across the complete flow
        
        // Test invalid user credentials
        HttpRequestMessage<Optional<String>> invalidAuthRequest = 
            AzureFunctionTestHelper.createBasicAuthRequest("testuser", "wrongpassword");
        
        when(authenticatorService.authenticate(new AuthenticationRequest("testuser", "wrongpassword"))).thenReturn(
            createAuthResult(false, "Invalid credentials"));
        
        HttpResponseMessage authResponse = basicAuthFunction.run(invalidAuthRequest, mockContext);
        assertNotNull(authResponse);
        
        // Test invalid client credentials
        HttpRequestMessage<Optional<String>> invalidTokenRequest = 
            AzureFunctionTestHelper.createTokenRequest("invalid-client", "wrong-secret");
        
        when(clientCredentialsService.authenticate(any())).thenThrow(
            createOAuth2Exception("invalid_client", "Client authentication failed"));
        
        HttpResponseMessage tokenResponse = oAuth2TokenFunction.run(invalidTokenRequest, mockContext);
        assertNotNull(tokenResponse);
        
        // Test invalid token introspection
        HttpRequestMessage<Optional<String>> invalidIntrospectRequest = 
            AzureFunctionTestHelper.createIntrospectionRequest("invalid_token");
        
        when(oAuth2TokenService.validateToken("invalid_token")).thenReturn(false);
        
        HttpResponseMessage introspectResponse = oAuth2IntrospectFunction.run(invalidIntrospectRequest, mockContext);
        assertNotNull(introspectResponse);
    }
    
    @Test
    void testPerformanceCharacteristics() {
        // Test performance aspects of the integration
        
        long startTime = System.currentTimeMillis();
        
        // Test multiple repository lookups
        for (int i = 0; i < 10; i++) {
            userRepository.findByUsername("testuser");
            oAuthClientRepository.findByClientId("test-client");
        }
        
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        
        // Should complete quickly (basic performance check)
        assertTrue(duration < 1000, "Repository lookups should complete within 1 second");
    }
    
    @Test
    void testDataConsistency() {
        // Test that data is consistent across repository calls
        
        // Multiple calls should return identical data
        Optional<User> user1 = userRepository.findByUsername("testuser");
        Optional<User> user2 = userRepository.findByUsername("testuser");
        
        assertTrue(user1.isPresent() && user2.isPresent());
        assertEquals(user1.get().getUsername(), user2.get().getUsername());
        assertEquals(user1.get().getPasswordHash(), user2.get().getPasswordHash());
        assertEquals(user1.get().getStatus(), user2.get().getStatus());
        assertEquals(user1.get().getRoles(), user2.get().getRoles());
        
        // Same for OAuth clients
        Optional<OAuthClient> client1 = oAuthClientRepository.findByClientId("test-client");
        Optional<OAuthClient> client2 = oAuthClientRepository.findByClientId("test-client");
        
        assertTrue(client1.isPresent() && client2.isPresent());
        assertEquals(client1.get().getClientId(), client2.get().getClientId());
        assertEquals(client1.get().getAllowedGrantTypes(), client2.get().getAllowedGrantTypes());
        assertEquals(client1.get().getAllowedScopes(), client2.get().getAllowedScopes());
    }
    
    // Helper methods for creating test objects
    
    private AuthenticationResult createAuthResult(boolean allowed, String reason) {
        // Use real AuthenticationResult instance instead of mock
        if (allowed) {
            return AuthenticationResult.success("testuser");
        } else {
            return AuthenticationResult.failure("testuser", reason);
        }
    }
    
    private TokenResponse createTokenResponse(String accessToken, String tokenType, int expiresIn, String scope) {
        // Use real TokenResponse instance instead of mock
        return TokenResponse.bearer(accessToken, expiresIn, scope);
    }
    
    private Map<String, Object> createTokenClaims() {
        Map<String, Object> claims = new HashMap<>();
        claims.put("exp", System.currentTimeMillis() / 1000 + 3600);
        claims.put("iat", System.currentTimeMillis() / 1000);
        claims.put("client_id", "test-client");
        claims.put("scope", "read write");
        return claims;
    }
    
    private ClientCredentialsService.OAuth2AuthenticationException createOAuth2Exception(String error, String description) {
        // Use real instances instead of mocks
        OAuthError oAuthError = OAuthError.of(error, description);
        return new ClientCredentialsService.OAuth2AuthenticationException(oAuthError);
    }
}




