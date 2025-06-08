package com.example.auth.infrastructure;

import com.example.auth.domain.port.UserRepository;
import com.example.auth.domain.port.oauth.OAuthClientRepository;
import com.example.auth.domain.util.PasswordHasher;
import com.example.auth.domain.util.oauth.ClientSecretHasher;
import com.example.auth.infrastructure.oauth.InMemoryOAuthClientRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;

/**
 * Spring configuration for AWS Lambda deployment.
 * This configuration sets up all necessary beans for the Lambda function.
 */
@Configuration
@ComponentScan(basePackages = "com.example.auth")
public class LambdaConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(LambdaConfiguration.class);

    /**
     * Creates the AWS Secrets Manager client for AWS environment.
     */
    @Bean
    @Profile("aws")
    public SecretsManagerClient secretsManagerClient() {
        logger.info("Creating AWS Secrets Manager client for region: {}", getAwsRegion());
        
        return SecretsManagerClient.builder()
                .region(Region.of(getAwsRegion()))
                .build();
    }

    /**
     * Creates the password hasher utility.
     */
    @Bean
    public PasswordHasher passwordHasher() {
        logger.info("Creating PasswordHasher bean");
        return new PasswordHasher();
    }

    /**
     * Creates the Jackson ObjectMapper for JSON processing.
     */
    @Bean
    public ObjectMapper objectMapper() {
        logger.info("Creating ObjectMapper bean");
        return new ObjectMapper();
    }

    /**
     * Creates the UserRepository implementation for AWS environment.
     * This will be the SecretsManagerUserRepository in AWS environment.
     */
    @Bean
    @Primary
    @Profile("aws")
    public UserRepository secretsManagerUserRepository(SecretsManagerClient secretsManagerClient, ObjectMapper objectMapper) {
        logger.info("Creating SecretsManagerUserRepository bean for AWS environment");
        
        String secretArn = System.getenv("CREDENTIAL_SECRET_ARN");
        if (secretArn == null || secretArn.trim().isEmpty()) {
            logger.warn("CREDENTIAL_SECRET_ARN environment variable not set");
        }
        
        String cacheTtlStr = System.getenv("CACHE_TTL_MINUTES");
        int cacheTtlMinutes = 5; // default
        if (cacheTtlStr != null && !cacheTtlStr.trim().isEmpty()) {
            try {
                cacheTtlMinutes = Integer.parseInt(cacheTtlStr.trim());
            } catch (NumberFormatException e) {
                logger.warn("Invalid CACHE_TTL_MINUTES value: {}, using default: {}", cacheTtlStr, cacheTtlMinutes);
            }
        }
        
        return new SecretsManagerUserRepository(secretsManagerClient, secretArn, cacheTtlMinutes, objectMapper);
    }

    /**
     * Creates the UserRepository implementation for local development.
     * This will be the LocalUserRepository in local environment.
     */
    @Bean
    @Primary
    @Profile("local")
    public UserRepository localUserRepository(PasswordHasher passwordHasher) {
        logger.info("Creating LocalUserRepository bean for local development");
        return new LocalUserRepository(passwordHasher);
    }

    /**
     * Creates the ClientSecretHasher utility for OAuth2 client secret hashing.
     */
    @Bean
    public ClientSecretHasher clientSecretHasher() {
        logger.info("Creating ClientSecretHasher bean");
        return new ClientSecretHasher();
    }

    /**
     * Creates the OAuthClientRepository implementation for local development and testing.
     */
    @Bean
    @Primary
    @Profile({"local", "test"})
    public OAuthClientRepository inMemoryOAuthClientRepository(ClientSecretHasher clientSecretHasher) {
        logger.info("Creating InMemoryOAuthClientRepository bean for local/test environment");
        return new InMemoryOAuthClientRepository(clientSecretHasher);
    }

    /**
     * Gets the AWS region from environment variables.
     */
    private String getAwsRegion() {
        String region = System.getenv("AWS_REGION");
        if (region == null || region.trim().isEmpty()) {
            region = System.getenv("AWS_DEFAULT_REGION");
        }
        if (region == null || region.trim().isEmpty()) {
            region = "us-east-1"; // fallback
            logger.warn("AWS region not found in environment variables, using fallback: {}", region);
        }
        return region;
    }
} 