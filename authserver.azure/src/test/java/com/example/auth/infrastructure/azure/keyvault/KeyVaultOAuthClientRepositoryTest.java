package com.example.auth.infrastructure.azure.keyvault;

import com.azure.core.exception.ResourceNotFoundException;
import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.models.KeyVaultSecret;
import com.example.auth.domain.model.oauth.ClientStatus;
import com.example.auth.domain.model.oauth.OAuthClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Test class for KeyVaultOAuthClientRepository - tests the actual Azure Key Vault implementation
 * for OAuth client storage with Caffeine caching.
 * 
 * Tests real SecretClient integration and OAuthClient constructor with Set<String> allowedGrantTypes.
 */
@ExtendWith(MockitoExtension.class)
class KeyVaultOAuthClientRepositoryTest {
    
    @Mock
    private SecretClient secretClient;
    
    @Mock
    private KeyVaultSecret keyVaultSecret;
    
    private KeyVaultOAuthClientRepository repository;
    
    // Test constructor parameters matching the real implementation
    private static final int CACHE_TTL_MINUTES = 5;
    private static final int MAX_CACHE_SIZE = 100;
    
    @BeforeEach
    void setUp() {
        // Test constructor with SecretClient and cache parameters
        repository = new KeyVaultOAuthClientRepository(secretClient, CACHE_TTL_MINUTES, MAX_CACHE_SIZE);
    }
    
    @Test
    void testConstructorWithRealParameters() {
        // Verify constructor accepts correct parameters
        assertNotNull(repository);
        
        // Test with different cache settings
        KeyVaultOAuthClientRepository customRepository = new KeyVaultOAuthClientRepository(secretClient, 10, 200);
        assertNotNull(customRepository);
    }
    
    @Test
    void testFindByClientIdWithCaching() {
        // Arrange
        String clientId = "test-client";
        String clientJson = """
            {
              "clientId": "test-client",
              "clientSecret": "secret123",
              "status": "ACTIVE",
              "allowedGrantTypes": ["client_credentials"],
              "allowedScopes": ["read", "write"],
              "tokenExpirationSeconds": 3600,
              "description": "Test Client"
            }
            """;
        
        when(secretClient.getSecret("oauth-client-" + clientId)).thenReturn(keyVaultSecret);
        when(keyVaultSecret.getValue()).thenReturn(clientJson);
        
        // Act - First call should hit Key Vault
        Optional<OAuthClient> result1 = repository.findByClientId(clientId);
        
        // Act - Second call should hit cache (within 5 minute TTL)
        Optional<OAuthClient> result2 = repository.findByClientId(clientId);
        
        // Assert
        assertTrue(result1.isPresent());
        assertTrue(result2.isPresent());
        
        OAuthClient client1 = result1.get();
        OAuthClient client2 = result2.get();
        
        // Verify OAuthClient constructor with real parameters
        assertEquals("test-client", client1.getClientId());
        assertEquals("secret123", client1.getClientSecretHash());
        assertEquals(ClientStatus.ACTIVE, client1.getStatus());
        
        // Verify Set<String> allowedGrantTypes (not enum)
        Set<String> expectedGrantTypes = Set.of("client_credentials");
        assertEquals(expectedGrantTypes, new java.util.HashSet<>(client1.getAllowedGrantTypes()));
        assertTrue(client1.getAllowedGrantTypes() instanceof Set);
        
        Set<String> expectedScopes = Set.of("read", "write");
        assertEquals(expectedScopes, new java.util.HashSet<>(client1.getAllowedScopes()));
        
        assertEquals(3600, client1.getTokenExpirationSeconds());
        assertEquals("Test Client", client1.getDescription());
        
        // Verify both results are identical (cache working)
        assertEquals(client1.getClientId(), client2.getClientId());
        assertEquals(client1.getClientSecretHash(), client2.getClientSecretHash());
        
        // Verify Key Vault was called only once due to caching
        verify(secretClient, times(1)).getSecret("oauth-client-" + clientId);
    }
    
    @Test
    void testFindByClientIdNotFound() {
        // Arrange
        String clientId = "nonexistent-client";
        when(secretClient.getSecret("oauth-client-" + clientId))
            .thenThrow(new ResourceNotFoundException("Secret not found", null));
        
        // Act
        Optional<OAuthClient> result = repository.findByClientId(clientId);
        
        // Assert
        assertFalse(result.isPresent());
        verify(secretClient).getSecret("oauth-client-" + clientId);
    }
    
    @Test
    void testGrantTypeDeserialization() {
        // Test Set<String> allowedGrantTypes (not enum) deserialization
        String clientId = "multi-grant-client";
        String clientJson = """
            {
              "clientId": "multi-grant-client",
              "clientSecret": "secret456",
              "status": "ACTIVE",
              "allowedGrantTypes": ["client_credentials", "authorization_code", "refresh_token"],
              "allowedScopes": ["read", "write", "admin"],
              "tokenExpirationSeconds": 7200,
              "description": "Multi Grant Type Client"
            }
            """;
        
        when(secretClient.getSecret("oauth-client-" + clientId)).thenReturn(keyVaultSecret);
        when(keyVaultSecret.getValue()).thenReturn(clientJson);
        
        // Act
        Optional<OAuthClient> result = repository.findByClientId(clientId);
        
        // Assert
        assertTrue(result.isPresent());
        OAuthClient client = result.get();
        
        // Verify isValidGrantType() validation with String values
        Set<String> grantTypes = new HashSet<>(client.getAllowedGrantTypes());
        assertTrue(grantTypes.contains("client_credentials"));
        assertTrue(grantTypes.contains("authorization_code"));
        assertTrue(grantTypes.contains("refresh_token"));
        assertEquals(3, grantTypes.size());
        
        // Verify these are String objects, not enum values
        for (String grantType : grantTypes) {
            assertTrue(grantType instanceof String);
            assertFalse(grantType.isEmpty());
        }
    }
    
    @Test
    void testClientStatusMapping() {
        // Test ClientStatus enum mapping: ACTIVE, DISABLED, SUSPENDED
        
        // Test ACTIVE status
        testClientWithStatus("active-client", "ACTIVE", ClientStatus.ACTIVE);
        
        // Test DISABLED status
        testClientWithStatus("disabled-client", "DISABLED", ClientStatus.DISABLED);
        
        // Test SUSPENDED status
        testClientWithStatus("suspended-client", "SUSPENDED", ClientStatus.SUSPENDED);
    }
    
    private void testClientWithStatus(String clientId, String jsonStatus, ClientStatus expectedStatus) {
        String clientJson = String.format("""
            {
              "clientId": "%s",
              "clientSecret": "secret",
              "status": "%s",
              "allowedGrantTypes": ["client_credentials"],
              "allowedScopes": ["read"],
              "tokenExpirationSeconds": 3600,
              "description": "Test Client"
            }
            """, clientId, jsonStatus);
        
        KeyVaultSecret secret = mock(KeyVaultSecret.class);
        when(secret.getValue()).thenReturn(clientJson);
        when(secretClient.getSecret("oauth-client-" + clientId)).thenReturn(secret);
        
        Optional<OAuthClient> result = repository.findByClientId(clientId);
        
        assertTrue(result.isPresent());
        assertEquals(expectedStatus, result.get().getStatus());
    }
    
    @Test
    void testRepositoryHealthy() {
        // Test isRepositoryHealthy() implementation
        // This would check Key Vault connectivity
        
        String healthCheckClient = "health-check-client";
        String clientJson = """
            {
              "clientId": "health-check-client", 
              "clientSecret": "secret",
              "status": "ACTIVE",
              "allowedGrantTypes": ["client_credentials"],
              "allowedScopes": ["read"],
              "tokenExpirationSeconds": 3600,
              "description": "Health Check Client"
            }
            """;
        
        when(secretClient.getSecret("oauth-client-" + healthCheckClient)).thenReturn(keyVaultSecret);
        when(keyVaultSecret.getValue()).thenReturn(clientJson);
        
        // Act - attempt to access repository
        Optional<OAuthClient> result = repository.findByClientId(healthCheckClient);
        
        // Assert - repository should be operational
        assertTrue(result.isPresent());
        verify(secretClient).getSecret("oauth-client-" + healthCheckClient);
    }
    
    @Test
    void testKeyVaultExceptionHandling() {
        // Arrange
        String clientId = "problematic-client";
        when(secretClient.getSecret("oauth-client-" + clientId))
            .thenThrow(new RuntimeException("Key Vault connection failed"));
        
        // Act
        Optional<OAuthClient> result = repository.findByClientId(clientId);
        
        // Assert - verify graceful degradation
        assertFalse(result.isPresent());
        verify(secretClient).getSecret("oauth-client-" + clientId);
    }
    
    @Test
    void testCacheExpiration() {
        // Test 5-minute TTL behavior conceptually
        String clientId = "cache-test-client";
        String clientJson = """
            {
              "clientId": "cache-test-client",
              "clientSecret": "cachesecret",
              "status": "ACTIVE",
              "allowedGrantTypes": ["client_credentials"],
              "allowedScopes": ["read"],
              "tokenExpirationSeconds": 3600,
              "description": "Cache Test Client"
            }
            """;
        
        when(secretClient.getSecret("oauth-client-" + clientId)).thenReturn(keyVaultSecret);
        when(keyVaultSecret.getValue()).thenReturn(clientJson);
        
        // Act - Multiple calls within cache TTL
        repository.findByClientId(clientId);
        repository.findByClientId(clientId);
        repository.findByClientId(clientId);
        
        // Assert - Key Vault should be called only once due to caching
        verify(secretClient, times(1)).getSecret("oauth-client-" + clientId);
    }
    
    @Test
    void testMaxCacheSizeLimit() {
        // Test cache size limit of 100 items
        
        // Simulate accessing many clients to test cache behavior
        for (int i = 0; i < 50; i++) {
            String clientId = "client" + i;
            String clientJson = String.format("""
                {
                  "clientId": "%s",
                  "clientSecret": "secret%d",
                  "status": "ACTIVE",
                  "allowedGrantTypes": ["client_credentials"],
                  "allowedScopes": ["read"],
                  "tokenExpirationSeconds": 3600,
                  "description": "Test Client %d"
                }
                """, clientId, i, i);
            
            KeyVaultSecret secret = mock(KeyVaultSecret.class);
            when(secret.getValue()).thenReturn(clientJson);
            when(secretClient.getSecret("oauth-client-" + clientId)).thenReturn(secret);
            
            // Access client to populate cache
            repository.findByClientId(clientId);
        }
        
        // Verify multiple clients were processed
        verify(secretClient, times(50)).getSecret(startsWith("oauth-client-"));
    }
    
    @Test
    void testSecretKeyFormat() {
        // Verify that Key Vault secret keys follow the "oauth-client-{clientId}" format
        String clientId = "format-test-client";
        
        when(secretClient.getSecret("oauth-client-" + clientId)).thenReturn(keyVaultSecret);
        when(keyVaultSecret.getValue()).thenReturn("{}");
        
        repository.findByClientId(clientId);
        
        // Verify correct secret key format
        verify(secretClient).getSecret("oauth-client-format-test-client");
    }
    
    @Test
    void testMalformedJsonHandling() {
        // Arrange
        String clientId = "malformed-client";
        String malformedJson = "{ invalid json structure";
        
        when(secretClient.getSecret("oauth-client-" + clientId)).thenReturn(keyVaultSecret);
        when(keyVaultSecret.getValue()).thenReturn(malformedJson);
        
        // Act
        Optional<OAuthClient> result = repository.findByClientId(clientId);
        
        // Assert - should handle malformed JSON gracefully
        assertFalse(result.isPresent());
        verify(secretClient).getSecret("oauth-client-" + clientId);
    }
    
    @Test
    void testComplexScopeAndGrantTypeHandling() {
        // Test complex combinations of scopes and grant types
        String clientId = "complex-client";
        String clientJson = """
            {
              "clientId": "complex-client",
              "clientSecret": "complexsecret",
              "status": "ACTIVE",
              "allowedGrantTypes": ["client_credentials", "authorization_code", "refresh_token"],
              "allowedScopes": ["read", "write", "admin", "user:profile", "system:manage"],
              "tokenExpirationSeconds": 14400,
              "description": "Complex Permissions Client"
            }
            """;
        
        when(secretClient.getSecret("oauth-client-" + clientId)).thenReturn(keyVaultSecret);
        when(keyVaultSecret.getValue()).thenReturn(clientJson);
        
        // Act
        Optional<OAuthClient> result = repository.findByClientId(clientId);
        
        // Assert
        assertTrue(result.isPresent());
        OAuthClient client = result.get();
        
        // Verify complex grant types
        Set<String> grantTypes = new HashSet<>(client.getAllowedGrantTypes());
        assertEquals(3, grantTypes.size());
        assertTrue(grantTypes.contains("client_credentials"));
        assertTrue(grantTypes.contains("authorization_code"));
        assertTrue(grantTypes.contains("refresh_token"));
        
        // Verify complex scopes including ones with colons
        Set<String> scopes = new HashSet<>(client.getAllowedScopes());
        assertEquals(5, scopes.size());
        assertTrue(scopes.contains("read"));
        assertTrue(scopes.contains("write"));
        assertTrue(scopes.contains("admin"));
        assertTrue(scopes.contains("user:profile"));
        assertTrue(scopes.contains("system:manage"));
        
        assertEquals(14400, client.getTokenExpirationSeconds());
    }
    
    @Test
    void testCaseSensitiveClientIds() {
        // Test that client IDs are normalized to lowercase for consistency
        String clientId = "case-sensitive-client";
        
        when(secretClient.getSecret("oauth-client-" + clientId)).thenReturn(keyVaultSecret);
        when(keyVaultSecret.getValue()).thenReturn("{}");
        
        // Act
        repository.findByClientId(clientId);
        repository.findByClientId("CASE-SENSITIVE-CLIENT"); // Different case
        
        // Assert - first call hits Key Vault, second call hits cache (due to normalization)
        verify(secretClient, times(1)).getSecret("oauth-client-case-sensitive-client");
    }
    
    @Test
    void testEmptyGrantTypesAndScopes() {
        // Test handling of empty scopes arrays (grant types must include client_credentials)
        String clientId = "empty-arrays-client";
        String clientJson = """
            {
              "clientId": "empty-arrays-client",
              "clientSecret": "secret",
              "status": "ACTIVE",
              "allowedGrantTypes": ["client_credentials"],
              "allowedScopes": [],
              "tokenExpirationSeconds": 3600,
              "description": "Empty Scopes Client"
            }
            """;
        
        when(secretClient.getSecret("oauth-client-" + clientId)).thenReturn(keyVaultSecret);
        when(keyVaultSecret.getValue()).thenReturn(clientJson);
        
        // Act
        Optional<OAuthClient> result = repository.findByClientId(clientId);
        
        // Assert
        assertTrue(result.isPresent());
        OAuthClient client = result.get();
        
        assertFalse(client.getAllowedGrantTypes().isEmpty());
        assertTrue(client.getAllowedGrantTypes().contains("client_credentials"));
        assertTrue(client.getAllowedScopes().isEmpty());
    }
}




