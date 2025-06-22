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
import static org.mockito.Mockito.when;

/**
 * Unit test for AzureFunctionConfiguration without Spring context.
 * Tests individual bean creation methods in isolation.
 */
@ExtendWith(MockitoExtension.class)
class AzureFunctionConfigurationUnitTest {

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
        // Set test properties
        ReflectionTestUtils.setField(configuration, "keyVaultUrl", "https://test-keyvault.vault.azure.net/");
        ReflectionTestUtils.setField(configuration, "cacheTtlMinutes", 5);
        ReflectionTestUtils.setField(configuration, "cacheMaxSize", 1000L);
    }

    @Test
    void testPasswordHasherBean() {
        PasswordHasher passwordHasher = configuration.passwordHasher();
        assertNotNull(passwordHasher, "PasswordHasher should be created");
    }

    @Test
    void testMetricsBean() {
        AzureFunctionConfiguration.AzureFunctionMetrics metrics = configuration.azureFunctionMetrics();
        assertNotNull(metrics, "AzureFunctionMetrics should be created");
        
        // Test metrics methods don't throw exceptions
        assertDoesNotThrow(() -> metrics.recordAuthenticationAttempt("success"));
        assertDoesNotThrow(() -> metrics.recordTokenGeneration("test-client"));
        assertDoesNotThrow(() -> metrics.recordIntrospection("test-token"));
    }

    @Test
    void testLocalUserRepositoryCreation() {
        // Test is now skipped since repository beans are created via @Component annotations
        // UserRepository userRepository = configuration.localUserRepository(passwordHasher);
        // assertNotNull(userRepository);
        
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
        
        // Instead, test that the repository can be created directly
        OAuthClientRepository clientRepository = new LocalAzureOAuthClientRepository();
        assertNotNull(clientRepository);
    }

    @Test
    void testAuthenticatorServiceBean() {
        PasswordHasher passwordHasher = new PasswordHasher();
        AuthenticatorService authenticatorService = configuration.authenticatorService(mockUserRepository, passwordHasher);
        assertNotNull(authenticatorService, "AuthenticatorService should be created");
    }

    @Test
    void testTokenServiceBean() {
        OAuth2TokenService tokenService = configuration.tokenService();
        assertNotNull(tokenService, "OAuth2TokenService should be created");
    }

    @Test
    void testClientCredentialsServiceBean() {
        ClientCredentialsService clientCredentialsService = configuration.clientCredentialsService();
        assertNotNull(clientCredentialsService, "ClientCredentialsService should be created");
    }
} 