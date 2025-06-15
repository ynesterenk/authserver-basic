package com.example.auth.domain.model;

/**
 * Enumeration representing the status of a user account.
 * Controls whether a user is allowed to authenticate.
 */
public enum UserStatus {
    /**
     * User account is active and can authenticate
     */
    ACTIVE,
    
    /**
     * User account is disabled and cannot authenticate
     */
    DISABLED
} 