package com.example.auth;

import com.example.auth.infrastructure.azure.config.AzureFunctionConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;

/**
 * Main application class for Azure Functions Authorization Server.
 * Provides Spring Boot application context for Azure Functions.
 */
@SpringBootApplication
@Import(AzureFunctionConfiguration.class)
public class AzureApplication {

    private static final Logger logger = LoggerFactory.getLogger(AzureApplication.class);
    private static ApplicationContext applicationContext;

    public static void main(String[] args) {
        logger.info("Starting Azure Functions Authorization Server...");
        
        try {
            applicationContext = SpringApplication.run(AzureApplication.class, args);
            logger.info("Azure Functions Authorization Server started successfully");
            
            // Log active profiles
            String[] activeProfiles = applicationContext.getEnvironment().getActiveProfiles();
            if (activeProfiles.length > 0) {
                logger.info("Active profiles: {}", String.join(", ", activeProfiles));
            } else {
                logger.info("No active profiles set, using default");
            }
            
        } catch (Exception e) {
            logger.error("Failed to start Azure Functions Authorization Server", e);
            System.exit(1);
        }
    }

    /**
     * Gets the Spring application context for use by Azure Functions.
     * This allows Azure Functions to access Spring-managed beans.
     */
    public static ApplicationContext getApplicationContext() {
        return applicationContext;
    }

    /**
     * Gets a Spring bean by type from the application context.
     */
    public static <T> T getBean(Class<T> beanType) {
        if (applicationContext == null) {
            throw new IllegalStateException("Application context not initialized");
        }
        return applicationContext.getBean(beanType);
    }

    /**
     * Gets a Spring bean by name from the application context.
     */
    public static Object getBean(String beanName) {
        if (applicationContext == null) {
            throw new IllegalStateException("Application context not initialized");
        }
        return applicationContext.getBean(beanName);
    }

    /**
     * Checks if the application context is initialized.
     */
    public static boolean isInitialized() {
        return applicationContext != null;
    }
} 