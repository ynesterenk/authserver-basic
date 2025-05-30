package com.example.auth.config;

import com.example.auth.domain.service.AuthenticatorService;
import com.example.auth.domain.service.BasicAuthenticatorService;
import com.example.auth.domain.port.UserRepository;
import com.example.auth.domain.util.PasswordHasher;
import com.example.auth.domain.util.BasicAuthDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration class for the authentication domain layer.
 * Configures beans and services required for authentication operations.
 */
@Configuration
@ComponentScan(basePackages = "com.example.auth.domain")
public class AuthConfig {

    private static final Logger log = LoggerFactory.getLogger(AuthConfig.class);

    /**
     * Configures the password hasher bean.
     * Uses Argon2id for secure password hashing.
     */
    @Bean
    @ConditionalOnMissingBean
    public PasswordHasher passwordHasher() {
        log.info("Configuring PasswordHasher with Argon2id");
        return new PasswordHasher();
    }

    /**
     * Configures the Basic Auth decoder bean.
     * Handles parsing of HTTP Basic Authentication headers.
     */
    @Bean
    @ConditionalOnMissingBean
    public BasicAuthDecoder basicAuthDecoder() {
        log.info("Configuring BasicAuthDecoder");
        return new BasicAuthDecoder();
    }

    /**
     * Configures the main authentication service bean.
     * This is the core service that orchestrates authentication logic.
     */
    @Bean
    @ConditionalOnMissingBean
    public AuthenticatorService authenticatorService(UserRepository userRepository, 
                                                     PasswordHasher passwordHasher) {
        log.info("Configuring BasicAuthenticatorService");
        return new BasicAuthenticatorService(userRepository, passwordHasher);
    }

    /**
     * Configuration properties for authentication settings.
     * These can be overridden via application properties.
     */
    @Bean
    public AuthenticationProperties authenticationProperties() {
        return new AuthenticationProperties();
    }

    /**
     * Properties class for authentication configuration.
     */
    public static class AuthenticationProperties {
        private boolean timingAttackPrevention = true;
        private boolean detailedLogging = false;
        private int maxUsernameLength = 255;
        private int maxPasswordLength = 1000;

        public boolean isTimingAttackPrevention() {
            return timingAttackPrevention;
        }

        public void setTimingAttackPrevention(boolean timingAttackPrevention) {
            this.timingAttackPrevention = timingAttackPrevention;
        }

        public boolean isDetailedLogging() {
            return detailedLogging;
        }

        public void setDetailedLogging(boolean detailedLogging) {
            this.detailedLogging = detailedLogging;
        }

        public int getMaxUsernameLength() {
            return maxUsernameLength;
        }

        public void setMaxUsernameLength(int maxUsernameLength) {
            this.maxUsernameLength = maxUsernameLength;
        }

        public int getMaxPasswordLength() {
            return maxPasswordLength;
        }

        public void setMaxPasswordLength(int maxPasswordLength) {
            this.maxPasswordLength = maxPasswordLength;
        }
    }
} 