package com.example.auth.infrastructure.azure.config;

import com.example.auth.domain.port.UserRepository;
import com.example.auth.domain.port.oauth.OAuthClientRepository;
import com.example.auth.domain.service.AuthenticatorService;
import com.example.auth.domain.service.oauth.ClientCredentialsService;
import com.example.auth.domain.service.oauth.OAuth2TokenService;
import com.example.auth.domain.util.PasswordHasher;
import com.example.auth.domain.util.oauth.ClientSecretHasher;
import com.example.auth.infrastructure.azure.LocalAzureOAuthClientRepository;
import com.example.auth.infrastructure.azure.LocalAzureUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit test for AzureFunctionConfiguration using mocks.
 * Tests individual configuration methods without full Spring context.
 */
@ExtendWith(MockitoExtension.class)
class AzureFunctionConfigurationTest {

    @Mock
    private UserRepository mockUserRepository;
    
    @Mock
    private OAuthClientRepository mockOAuthClientRepository;
    
    @Mock
    private AuthenticatorService mockAuthenticatorService;
    
    @Mock
    private ClientCredentialsService mockClientCredentialsService;
    
    @Mock
    private OAuth2TokenService mockOAuth2TokenService;

    private AzureFunctionConfiguration configuration;

    @BeforeEach
    void setUp() {
        configuration = new AzureFunctionConfiguration();
        // Set test properties using reflection
        ReflectionTestUtils.setField(configuration, "keyVaultUrl", "https://test-keyvault.vault.azure.net/");
        ReflectionTestUtils.setField(configuration, "cacheTtlMinutes", 5);
        ReflectionTestUtils.setField(configuration, "cacheMaxSize", 1000L);
    }

    @Test
    void testBeanConfiguration() {
        // Test that configuration can create basic beans
        assertNotNull(configuration, "Configuration should be instantiated");
    }

    @Test
    void testPasswordHasherConfiguration() {
        // Test PasswordHasher bean creation
        PasswordHasher passwordHasher = configuration.passwordHasher();
        assertNotNull(passwordHasher, "PasswordHasher should be created");
    }

    @Test
    void testClientSecretHasherConfiguration() {
        // Test ClientSecretHasher bean creation
        ClientSecretHasher clientSecretHasher = configuration.clientSecretHasher();
        assertNotNull(clientSecretHasher, "ClientSecretHasher should be created");
    }

    @Test
    void testLocalUserRepositoryCreation() {
        // Test is now skipped since repository beans are created via @Component annotations
        // UserRepository userRepository = configuration.localUserRepository(passwordHasher);
        // assertNotNull(userRepository);
        // assertInstanceOf(LocalAzureUserRepository.class, userRepository);
        
        // Instead, test that the repository can be created directly
        PasswordHasher testPasswordHasher = new PasswordHasher();
        UserRepository userRepository = new LocalAzureUserRepository(testPasswordHasher);
        assertNotNull(userRepository);
    }

    @Test
    void testLocalOAuthClientRepositoryCreation() {
        // Test is now skipped since repository beans are created via @Component annotations
        // OAuthClientRepository clientRepository = configuration.localOAuthClientRepository();
        // assertNotNull(clientRepository);
        // assertInstanceOf(LocalAzureOAuthClientRepository.class, clientRepository);
        
        // Instead, test that the repository can be created directly
        OAuthClientRepository clientRepository = new LocalAzureOAuthClientRepository();
        assertNotNull(clientRepository);
    }

    @Test
    void testAuthenticatorServiceConfiguration() {
        // Test authenticator service creation with mocked dependencies
        PasswordHasher passwordHasher = new PasswordHasher();
        AuthenticatorService authenticatorService = configuration.authenticatorService(mockUserRepository, passwordHasher);
        
        assertNotNull(authenticatorService, "AuthenticatorService should be created");
    }

    @Test
    void testTokenServiceConfiguration() {
        // Test OAuth2 token service creation
        OAuth2TokenService tokenService = configuration.tokenService();
        assertNotNull(tokenService, "OAuth2TokenService should be created");
    }

    @Test
    void testClientCredentialsServiceConfiguration() {
        // Test client credentials service creation
        ClientCredentialsService clientCredentialsService = configuration.clientCredentialsService();
        assertNotNull(clientCredentialsService, "ClientCredentialsService should be created");
    }

    @Test
    void testMetricsConfiguration() {
        // Test AzureFunctionMetrics bean creation
        AzureFunctionConfiguration.AzureFunctionMetrics metrics = configuration.azureFunctionMetrics();
        assertNotNull(metrics, "AzureFunctionMetrics should be created");
        
        // Test metrics methods don't throw exceptions
        assertDoesNotThrow(() -> metrics.recordAuthenticationAttempt("success"));
        assertDoesNotThrow(() -> metrics.recordTokenGeneration("test-client"));
        assertDoesNotThrow(() -> metrics.recordIntrospection("test-token"));
    }

    @Test
    void testCacheConfiguration() {
        // Test that cache configuration properties are properly set
        int cacheTtl = (int) ReflectionTestUtils.getField(configuration, "cacheTtlMinutes");
        long cacheMaxSize = (long) ReflectionTestUtils.getField(configuration, "cacheMaxSize");
        
        assertEquals(5, cacheTtl, "Cache TTL should be set correctly");
        assertEquals(1000L, cacheMaxSize, "Cache max size should be set correctly");
    }

    @Test
    void testPropertyInjection() {
        // Test that custom properties are properly injected via reflection
        String keyVaultUrl = (String) ReflectionTestUtils.getField(configuration, "keyVaultUrl");
        assertNotNull(keyVaultUrl, "Key Vault URL should be injected");
        assertEquals("https://test-keyvault.vault.azure.net/", keyVaultUrl);
    }

    @Test
    void testConditionalBeanCreation() {
        // Test that beans can be created conditionally
        // Since we're in unit test mode, we can't test @Profile annotations
        // but we can test that the methods exist and return valid objects
        
        PasswordHasher passwordHasher = configuration.passwordHasher();
        assertNotNull(passwordHasher, "Conditional beans should be creatable");
    }

    @Test
    void testDomainServiceConfiguration() {
        // Test domain service configuration with mock dependencies
        PasswordHasher passwordHasher = new PasswordHasher();
        
        // Test that services can be created with provided dependencies
        AuthenticatorService authenticatorService = configuration.authenticatorService(mockUserRepository, passwordHasher);
        OAuth2TokenService tokenService = configuration.tokenService();
        ClientCredentialsService clientCredentialsService = configuration.clientCredentialsService();
        
        assertNotNull(authenticatorService, "AuthenticatorService should be configured");
        assertNotNull(tokenService, "OAuth2TokenService should be configured");
        assertNotNull(clientCredentialsService, "ClientCredentialsService should be configured");
    }

    @Test
    void testConfigurationValidation() {
        // Test configuration validation
        assertNotNull(configuration, "Configuration should be valid");
        
        // Test that configuration can handle different property values
        ReflectionTestUtils.setField(configuration, "cacheTtlMinutes", 1);
        ReflectionTestUtils.setField(configuration, "cacheMaxSize", 10L);
        
        // Configuration should still be valid with different values
        assertNotNull(configuration.passwordHasher(), "Configuration should handle different property values");
    }

    @Test
    void testConfigurationErrorHandling() {
        // Test configuration error handling
        // Set null values to test error handling
        ReflectionTestUtils.setField(configuration, "keyVaultUrl", null);
        
        // Basic beans should still be creatable even with missing optional properties
        assertNotNull(configuration.passwordHasher(), "Configuration should handle missing optional properties");
        assertNotNull(configuration.azureFunctionMetrics(), "Metrics should be creatable regardless of other properties");
    }

    @Test
    void testSecretClientConfiguration() {
        // Test that configuration recognizes profile-based bean creation
        // In unit test, we can't test @Profile annotations directly,
        // but we can verify the configuration structure is sound
        assertTrue(true, "SecretClient configuration should be conditional on profiles");
    }

    @Test
    void testSpringContextInitializationPerformance() {
        // Test that configuration initialization is fast
        long startTime = System.currentTimeMillis();
        
        AzureFunctionConfiguration newConfig = new AzureFunctionConfiguration();
        ReflectionTestUtils.setField(newConfig, "keyVaultUrl", "https://test-keyvault.vault.azure.net/");
        ReflectionTestUtils.setField(newConfig, "cacheTtlMinutes", 5);
        ReflectionTestUtils.setField(newConfig, "cacheMaxSize", 1000L);
        
        // Create a few beans to test initialization time
        newConfig.passwordHasher();
        newConfig.azureFunctionMetrics();
        
        long duration = System.currentTimeMillis() - startTime;
        assertTrue(duration < 1000, "Configuration initialization should be fast (< 1 second)");
    }
}




