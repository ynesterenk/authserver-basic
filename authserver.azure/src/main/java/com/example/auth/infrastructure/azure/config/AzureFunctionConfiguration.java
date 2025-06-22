package com.example.auth.infrastructure.azure.config;

import com.azure.identity.ManagedIdentityCredential;
import com.azure.identity.ManagedIdentityCredentialBuilder;
import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.SecretClientBuilder;
import com.example.auth.domain.port.UserRepository;
import com.example.auth.domain.port.oauth.OAuthClientRepository;
import com.example.auth.domain.service.AuthenticatorService;
import com.example.auth.domain.service.BasicAuthenticatorService;
import com.example.auth.domain.service.oauth.ClientCredentialsService;
import com.example.auth.domain.service.oauth.ClientCredentialsServiceImpl;
import com.example.auth.domain.service.oauth.JwtTokenService;
import com.example.auth.domain.service.oauth.OAuth2TokenService;
import com.example.auth.domain.util.PasswordHasher;
import com.example.auth.domain.util.oauth.ClientSecretHasher;
import com.example.auth.infrastructure.azure.LocalAzureOAuthClientRepository;
import com.example.auth.infrastructure.azure.LocalAzureUserRepository;
import com.example.auth.infrastructure.azure.keyvault.KeyVaultOAuthClientRepository;
import com.example.auth.infrastructure.azure.keyvault.KeyVaultUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

/**
 * Spring configuration for Azure Functions Authorization Server.
 * Configures beans for different environments (local, dev, prod).
 */
@Configuration
public class AzureFunctionConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(AzureFunctionConfiguration.class);

    @Value("${azure.keyvault.url:}")
    private String keyVaultUrl;

    @Value("${cache.ttl.minutes:5}")
    private int cacheTtlMinutes;

    @Value("${cache.max.size:1000}")
    private long cacheMaxSize;

    public AzureFunctionConfiguration() {
        logger.info("AzureFunctionConfiguration initialized");
    }

    // ========== Azure Key Vault Configuration ==========

    /**
     * Creates a SecretClient for Azure Key Vault using Managed Identity.
     * Only available in deployed Azure environments (dev, prod).
     */
    @Bean
    @Profile({"dev", "prod"})
    public SecretClient secretClient() {
        logger.info("Creating Azure Key Vault SecretClient for URL: {}", keyVaultUrl);
        
        if (keyVaultUrl == null || keyVaultUrl.trim().isEmpty()) {
            throw new IllegalStateException("Azure Key Vault URL is required for dev/prod profiles");
        }

        ManagedIdentityCredential managedIdentityCredential = new ManagedIdentityCredentialBuilder()
                .build();

        return new SecretClientBuilder()
                .vaultUrl(keyVaultUrl)
                .credential(managedIdentityCredential)
                .buildClient();
    }

    // ========== Password Hashing Configuration ==========

    /**
     * Creates the password hasher for user authentication.
     */
    @Bean
    @Primary
    public PasswordHasher passwordHasher() {
        logger.info("Creating PasswordHasher bean");
        return new PasswordHasher();
    }

    /**
     * Creates the client secret hasher for OAuth client authentication.
     */
    @Bean
    @Primary
    public ClientSecretHasher clientSecretHasher() {
        logger.info("Creating ClientSecretHasher bean");
        return new ClientSecretHasher();
    }

    // ========== Repository Configuration - Local Profile ==========
    // NOTE: Repository beans are now defined using @Component annotations on the classes themselves
    // These bean definitions are commented out to avoid conflicts

    /**
     * Local file-based user repository for development.
     */
    // @Bean
    // @Primary
    // @Profile("local")
    // public UserRepository localUserRepository(PasswordHasher passwordHasher) {
    //     logger.info("Creating LocalAzureUserRepository for local development");
    //     return new LocalAzureUserRepository(passwordHasher);
    // }

    /**
     * Local file-based OAuth client repository for development.
     */
    // @Bean
    // @Primary
    // @Profile("local")
    // public OAuthClientRepository localOAuthClientRepository() {
    //     logger.info("Creating LocalAzureOAuthClientRepository for local development");
    //     return new LocalAzureOAuthClientRepository();
    // }

    // ========== Repository Configuration - Azure Profiles ==========
    // NOTE: Repository beans are now defined using @Component annotations on the classes themselves
    // These bean definitions are commented out to avoid conflicts

    /**
     * Azure Key Vault user repository for dev/prod environments.
     */
    // @Bean
    // @Primary
    // @Profile({"dev", "prod"})
    // public UserRepository keyVaultUserRepository(SecretClient secretClient, PasswordHasher passwordHasher) {
    //     logger.info("Creating KeyVaultUserRepository for Azure environment");
    //     return new KeyVaultUserRepository(secretClient, passwordHasher, cacheTtlMinutes, cacheMaxSize);
    // }

    /**
     * Azure Key Vault OAuth client repository for dev/prod environments.
     */
    // @Bean
    // @Primary
    // @Profile({"dev", "prod"})
    // public OAuthClientRepository keyVaultOAuthClientRepository(SecretClient secretClient) {
    //     logger.info("Creating KeyVaultOAuthClientRepository for Azure environment");
    //     return new KeyVaultOAuthClientRepository(secretClient, cacheTtlMinutes, cacheMaxSize);
    // }

    // ========== Service Configuration ==========

    /**
     * Basic authenticator service with constructor injection.
     * Uses UserRepository and PasswordHasher.
     */
    @Bean
    @Primary
    public AuthenticatorService authenticatorService(UserRepository userRepository, 
                                                    PasswordHasher passwordHasher) {
        logger.info("Creating BasicAuthenticatorService with constructor injection");
        return new BasicAuthenticatorService(userRepository, passwordHasher);
    }

    /**
     * JWT token service for OAuth2 token generation.
     */
    @Bean
    @Primary
    public OAuth2TokenService tokenService() {
        logger.info("Creating JwtTokenService");
        return new JwtTokenService();
    }

    /**
     * Client credentials service implementation.
     * Uses @Autowired field injection - Spring will handle dependency injection.
     */
    @Bean
    @Primary
    public ClientCredentialsService clientCredentialsService() {
        logger.info("Creating ClientCredentialsServiceImpl with @Autowired field injection");
        return new ClientCredentialsServiceImpl();
    }

    // ========== Utility Beans ==========

    /**
     * Application metrics and monitoring bean.
     */
    @Bean
    @Primary
    public AzureFunctionMetrics azureFunctionMetrics() {
        logger.info("Creating AzureFunctionMetrics bean");
        return new AzureFunctionMetrics();
    }

    /**
     * Simple metrics collector for Azure Functions.
     */
    public static class AzureFunctionMetrics {
        private static final Logger log = LoggerFactory.getLogger(AzureFunctionMetrics.class);
        
        public void recordAuthenticationAttempt(String result) {
            log.info("Authentication attempt recorded: {}", result);
        }
        
        public void recordTokenGeneration(String clientId) {
            log.info("Token generation recorded for client: {}", clientId != null ? maskClientId(clientId) : "null");
        }
        
        public void recordIntrospection(String tokenId) {
            log.info("Token introspection recorded for token: {}", tokenId != null ? maskToken(tokenId) : "null");
        }
        
        private String maskClientId(String clientId) {
            if (clientId == null || clientId.length() <= 4) {
                return "****";
            }
            return clientId.substring(0, 2) + "****" + clientId.substring(clientId.length() - 2);
        }
        
        private String maskToken(String token) {
            if (token == null || token.length() <= 8) {
                return "********";
            }
            return token.substring(0, 4) + "****" + token.substring(token.length() - 4);
        }
    }
} 