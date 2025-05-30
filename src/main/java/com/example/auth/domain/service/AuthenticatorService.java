package com.example.auth.domain.service;

import com.example.auth.domain.model.AuthenticationRequest;
import com.example.auth.domain.model.AuthenticationResult;

/**
 * Core service interface for authentication operations.
 * This interface defines the contract for authentication services
 * in the domain layer.
 */
public interface AuthenticatorService {

    /**
     * Authenticates a user based on the provided credentials.
     * This method performs the core authentication logic including:
     * - User lookup
     * - Password verification
     * - User status validation
     * - Audit logging
     *
     * @param request the authentication request containing username and password
     * @return the authentication result indicating success or failure with details
     * @throws IllegalArgumentException if the request is null or invalid
     */
    AuthenticationResult authenticate(AuthenticationRequest request);

    /**
     * Checks if the authentication service is healthy and operational.
     * This method can be used for health checks and monitoring.
     *
     * @return true if the service is healthy and can process authentication requests
     */
    default boolean isHealthy() {
        return true;
    }

    /**
     * Gets service statistics for monitoring purposes.
     * Returns basic metrics about the authentication service.
     *
     * @return a string containing service statistics
     */
    default String getServiceStats() {
        return "AuthenticatorService is operational";
    }
} 