package com.example.auth.domain.model.oauth;

/**
 * Enumeration representing the possible states of an OAuth 2.0 client.
 * This follows security best practices by allowing granular control over client access.
 *
 * @author Auth Server Team
 * @version 1.0
 * @since 1.0
 */
public enum ClientStatus {
    
    /**
     * Client is active and can authenticate and receive tokens.
     */
    ACTIVE("Active", "Client is operational and can authenticate"),
    
    /**
     * Client is permanently disabled and cannot authenticate.
     */
    DISABLED("Disabled", "Client is permanently disabled"),
    
    /**
     * Client is temporarily suspended and cannot authenticate.
     * This status allows for potential reactivation.
     */
    SUSPENDED("Suspended", "Client is temporarily suspended");

    private final String displayName;
    private final String description;

    /**
     * Constructs a ClientStatus with the specified display name and description.
     *
     * @param displayName the human-readable name for this status
     * @param description a description of what this status means
     */
    ClientStatus(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    /**
     * Gets the human-readable display name for this status.
     *
     * @return the display name
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Gets the description of what this status means.
     *
     * @return the description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Checks if this status allows the client to authenticate.
     *
     * @return true if the client can authenticate in this status, false otherwise
     */
    public boolean canAuthenticate() {
        return this == ACTIVE;
    }

    /**
     * Checks if this status is a temporary state that might be changed.
     *
     * @return true if the status is temporary, false if permanent
     */
    public boolean isTemporary() {
        return this == SUSPENDED;
    }

    @Override
    public String toString() {
        return displayName;
    }
} 