package com.example.auth.config;

import com.example.auth.domain.util.oauth.ClientSecretHasher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Spring configuration for OAuth 2.0 components.
 * This class configures beans and settings specific to OAuth 2.0 functionality.
 *
 * @author Auth Server Team
 * @version 1.0
 * @since 1.0
 */
@Configuration
public class OAuth2Config {

    private static final Logger logger = LoggerFactory.getLogger(OAuth2Config.class);

    @Value("${oauth2.security.client-secret.salt-length:16}")
    private int clientSecretSaltLength;

    @Value("${oauth2.security.client-secret.hash-length:32}")
    private int clientSecretHashLength;

    @Value("${oauth2.security.client-secret.parallelism:4}")
    private int clientSecretParallelism;

    @Value("${oauth2.security.client-secret.memory:65536}")
    private int clientSecretMemory;

    @Value("${oauth2.security.client-secret.iterations:3}")
    private int clientSecretIterations;

    /**
     * Creates a ClientSecretHasher bean with configured security parameters.
     * Uses Argon2id for secure client secret hashing.
     *
     * @return configured ClientSecretHasher instance
     */
    @Bean
    public ClientSecretHasher clientSecretHasher() {
        logger.info("Configuring ClientSecretHasher with security parameters: " +
                   "saltLength={}, hashLength={}, parallelism={}, memory={}KB, iterations={}",
                   clientSecretSaltLength, clientSecretHashLength, clientSecretParallelism,
                   clientSecretMemory, clientSecretIterations);

        return new ClientSecretHasher(
            clientSecretSaltLength,
            clientSecretHashLength,
            clientSecretParallelism,
            clientSecretMemory,
            clientSecretIterations
        );
    }

    /**
     * Creates a default ClientSecretHasher bean for development/testing.
     * Uses standard security parameters that balance security and performance.
     *
     * @return default ClientSecretHasher instance
     */
    @Bean
    @Profile({"dev", "test"})
    public ClientSecretHasher defaultClientSecretHasher() {
        logger.info("Configuring default ClientSecretHasher for development/testing");
        return new ClientSecretHasher();
    }

    /**
     * Configuration properties for OAuth 2.0 token settings.
     * These can be overridden via application properties.
     */
    public static class TokenConfig {
        
        @Value("${oauth2.token.default-expiration-seconds:3600}")
        private int defaultExpirationSeconds;

        @Value("${oauth2.token.max-expiration-seconds:7200}")
        private int maxExpirationSeconds;

        @Value("${oauth2.token.min-expiration-seconds:300}")
        private int minExpirationSeconds;

        public int getDefaultExpirationSeconds() {
            return defaultExpirationSeconds;
        }

        public int getMaxExpirationSeconds() {
            return maxExpirationSeconds;
        }

        public int getMinExpirationSeconds() {
            return minExpirationSeconds;
        }
    }

    /**
     * Configuration properties for OAuth 2.0 JWT settings.
     */
    public static class JwtConfig {
        
        @Value("${oauth2.jwt.issuer:https://auth.example.com}")
        private String issuer;

        @Value("${oauth2.jwt.audience:https://api.example.com}")
        private String audience;

        @Value("${oauth2.jwt.algorithm:HS256}")
        private String algorithm;

        @Value("${oauth2.jwt.secret:mySecretKeyForJWTTokenGenerationThatIsAtLeast256BitsLong}")
        private String secret;

        public String getIssuer() {
            return issuer;
        }

        public String getAudience() {
            return audience;
        }

        public String getAlgorithm() {
            return algorithm;
        }

        public String getSecret() {
            return secret;
        }
    }

    /**
     * Configuration properties for OAuth 2.0 scope settings.
     */
    public static class ScopeConfig {
        
        @Value("${oauth2.scopes.default:read}")
        private String defaultScope;

        @Value("${oauth2.scopes.available:read,write,admin,delete}")
        private String availableScopes;

        @Value("${oauth2.scopes.separator: }")
        private String scopeSeparator;

        public String getDefaultScope() {
            return defaultScope;
        }

        public String[] getAvailableScopes() {
            return availableScopes.split(",");
        }

        public String getScopeSeparator() {
            return scopeSeparator;
        }
    }

    /**
     * Configuration properties for OAuth 2.0 security settings.
     */
    public static class SecurityConfig {
        
        @Value("${oauth2.security.enable-client-status-check:true}")
        private boolean enableClientStatusCheck;

        @Value("${oauth2.security.require-https:true}")
        private boolean requireHttps;

        @Value("${oauth2.security.allow-insecure-connections:false}")
        private boolean allowInsecureConnections;

        @Value("${oauth2.security.max-token-requests-per-minute:100}")
        private int maxTokenRequestsPerMinute;

        public boolean isEnableClientStatusCheck() {
            return enableClientStatusCheck;
        }

        public boolean isRequireHttps() {
            return requireHttps;
        }

        public boolean isAllowInsecureConnections() {
            return allowInsecureConnections;
        }

        public int getMaxTokenRequestsPerMinute() {
            return maxTokenRequestsPerMinute;
        }
    }

    @Bean
    public TokenConfig tokenConfig() {
        return new TokenConfig();
    }

    @Bean
    public JwtConfig jwtConfig() {
        return new JwtConfig();
    }

    @Bean
    public ScopeConfig scopeConfig() {
        return new ScopeConfig();
    }

    @Bean
    public SecurityConfig securityConfig() {
        return new SecurityConfig();
    }
} 