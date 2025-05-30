package com.example.auth.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Domain model representing the result of an authentication attempt.
 * Contains the outcome and additional metadata about the authentication.
 */
public final class AuthenticationResult {
    
    private final boolean allowed;
    private final String reason;
    private final String username;
    private final Instant timestamp;
    private final Map<String, Object> metadata;

    /**
     * Constructor for creating an authentication result.
     *
     * @param allowed whether authentication was successful
     * @param reason the reason for the authentication outcome
     * @param username the username that was authenticated (for logging/audit)
     */
    @JsonCreator
    public AuthenticationResult(@JsonProperty("allowed") boolean allowed,
                                @JsonProperty("reason") String reason,
                                @JsonProperty("username") String username) {
        this.allowed = allowed;
        this.reason = reason != null ? reason : (allowed ? "Authentication successful" : "Authentication failed");
        this.username = username;
        this.timestamp = Instant.now();
        this.metadata = new HashMap<>();
    }

    /**
     * Constructor with additional metadata.
     *
     * @param allowed whether authentication was successful
     * @param reason the reason for the authentication outcome
     * @param username the username that was authenticated
     * @param metadata additional metadata about the authentication
     */
    public AuthenticationResult(boolean allowed, String reason, String username, Map<String, Object> metadata) {
        this.allowed = allowed;
        this.reason = reason != null ? reason : (allowed ? "Authentication successful" : "Authentication failed");
        this.username = username;
        this.timestamp = Instant.now();
        this.metadata = metadata != null ? new HashMap<>(metadata) : new HashMap<>();
    }

    /**
     * Creates a successful authentication result.
     *
     * @param username the authenticated username
     * @return a successful AuthenticationResult
     */
    public static AuthenticationResult success(String username) {
        return new AuthenticationResult(true, "Authentication successful", username);
    }

    /**
     * Creates a successful authentication result with metadata.
     *
     * @param username the authenticated username
     * @param metadata additional metadata
     * @return a successful AuthenticationResult
     */
    public static AuthenticationResult success(String username, Map<String, Object> metadata) {
        return new AuthenticationResult(true, "Authentication successful", username, metadata);
    }

    /**
     * Creates a failed authentication result.
     *
     * @param username the username that failed authentication
     * @param reason the reason for failure
     * @return a failed AuthenticationResult
     */
    public static AuthenticationResult failure(String username, String reason) {
        return new AuthenticationResult(false, reason, username);
    }

    /**
     * Creates a failed authentication result with metadata.
     *
     * @param username the username that failed authentication
     * @param reason the reason for failure
     * @param metadata additional metadata
     * @return a failed AuthenticationResult
     */
    public static AuthenticationResult failure(String username, String reason, Map<String, Object> metadata) {
        return new AuthenticationResult(false, reason, username, metadata);
    }

    /**
     * Gets whether authentication was allowed.
     *
     * @return true if authentication was successful
     */
    public boolean isAllowed() {
        return allowed;
    }

    /**
     * Gets the reason for the authentication outcome.
     *
     * @return the reason string
     */
    public String getReason() {
        return reason;
    }

    /**
     * Gets the username that was authenticated.
     *
     * @return the username
     */
    public String getUsername() {
        return username;
    }

    /**
     * Gets the timestamp when the result was created.
     *
     * @return the timestamp
     */
    public Instant getTimestamp() {
        return timestamp;
    }

    /**
     * Gets the metadata associated with this result.
     *
     * @return a copy of the metadata map
     */
    public Map<String, Object> getMetadata() {
        return new HashMap<>(metadata);
    }

    /**
     * Gets a specific metadata value.
     *
     * @param key the metadata key
     * @return the metadata value, or null if not found
     */
    public Object getMetadata(String key) {
        return metadata.get(key);
    }

    /**
     * Creates a new result with additional metadata.
     *
     * @param key the metadata key
     * @param value the metadata value
     * @return a new AuthenticationResult with the additional metadata
     */
    public AuthenticationResult withMetadata(String key, Object value) {
        Map<String, Object> newMetadata = new HashMap<>(this.metadata);
        newMetadata.put(key, value);
        return new AuthenticationResult(this.allowed, this.reason, this.username, newMetadata);
    }

    /**
     * Gets the duration since the result was created.
     *
     * @return duration in milliseconds
     */
    public long getAgeMillis() {
        return Instant.now().toEpochMilli() - timestamp.toEpochMilli();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        AuthenticationResult that = (AuthenticationResult) obj;
        return allowed == that.allowed &&
               Objects.equals(reason, that.reason) &&
               Objects.equals(username, that.username) &&
               Objects.equals(timestamp, that.timestamp) &&
               Objects.equals(metadata, that.metadata);
    }

    @Override
    public int hashCode() {
        return Objects.hash(allowed, reason, username, timestamp, metadata);
    }

    @Override
    public String toString() {
        return "AuthenticationResult{" +
               "allowed=" + allowed +
               ", reason='" + reason + '\'' +
               ", username='" + username + '\'' +
               ", timestamp=" + timestamp +
               ", metadata=" + metadata +
               '}';
    }
} 