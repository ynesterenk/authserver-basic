package com.example.auth.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Domain model representing a user in the authentication system.
 * Immutable object containing user credentials and metadata.
 */
public final class User {
    
    @NotBlank(message = "Username cannot be blank")
    private final String username;
    
    @NotBlank(message = "Password hash cannot be blank")
    private final String passwordHash;
    
    @NotNull(message = "User status cannot be null")
    private final UserStatus status;
    
    private final List<String> roles;

    /**
     * Constructor for creating a User instance.
     *
     * @param username the unique username (case-sensitive)
     * @param passwordHash the Argon2id hashed password
     * @param status the current status of the user account
     * @param roles list of roles assigned to the user (for future RBAC)
     */
    @JsonCreator
    public User(@JsonProperty("username") String username,
                @JsonProperty("passwordHash") String passwordHash,
                @JsonProperty("status") UserStatus status,
                @JsonProperty("roles") List<String> roles) {
        this.username = validateUsername(username);
        this.passwordHash = validatePasswordHash(passwordHash);
        this.status = Objects.requireNonNull(status, "User status cannot be null");
        this.roles = roles != null ? new ArrayList<>(roles) : new ArrayList<>();
    }

    /**
     * Constructor with default empty roles.
     */
    public User(String username, String passwordHash, UserStatus status) {
        this(username, passwordHash, status, Collections.emptyList());
    }

    /**
     * Gets the username.
     *
     * @return the username
     */
    public String getUsername() {
        return username;
    }

    /**
     * Gets the password hash.
     * Note: This should only be used for verification, never logged or exposed.
     *
     * @return the Argon2id password hash
     */
    public String getPasswordHash() {
        return passwordHash;
    }

    /**
     * Gets the user status.
     *
     * @return the current user status
     */
    public UserStatus getStatus() {
        return status;
    }

    /**
     * Gets the list of roles assigned to the user.
     *
     * @return immutable copy of the roles list
     */
    public List<String> getRoles() {
        return Collections.unmodifiableList(roles);
    }

    /**
     * Checks if the user account is active and can authenticate.
     *
     * @return true if the user status is ACTIVE
     */
    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }

    /**
     * Checks if the user has a specific role.
     *
     * @param role the role to check
     * @return true if the user has the specified role
     */
    public boolean hasRole(String role) {
        return roles.contains(role);
    }

    /**
     * Creates a new User with updated status.
     *
     * @param newStatus the new status
     * @return a new User instance with the updated status
     */
    public User withStatus(UserStatus newStatus) {
        return new User(this.username, this.passwordHash, newStatus, this.roles);
    }

    /**
     * Creates a new User with additional role.
     *
     * @param role the role to add
     * @return a new User instance with the additional role
     */
    public User withRole(String role) {
        List<String> newRoles = new ArrayList<>(this.roles);
        if (!newRoles.contains(role)) {
            newRoles.add(role);
        }
        return new User(this.username, this.passwordHash, this.status, newRoles);
    }

    private String validateUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username cannot be null or empty");
        }
        if (username.length() > 255) {
            throw new IllegalArgumentException("Username cannot exceed 255 characters");
        }
        // Basic validation - alphanumeric, underscore, hyphen, dot
        if (!username.matches("^[a-zA-Z0-9._-]+$")) {
            throw new IllegalArgumentException("Username contains invalid characters");
        }
        return username;
    }

    private String validatePasswordHash(String passwordHash) {
        if (passwordHash == null || passwordHash.trim().isEmpty()) {
            throw new IllegalArgumentException("Password hash cannot be null or empty");
        }
        // Basic validation for Argon2 hash format
        if (!passwordHash.startsWith("$argon2") && !passwordHash.startsWith("$2")) {
            throw new IllegalArgumentException("Invalid password hash format");
        }
        return passwordHash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        User user = (User) obj;
        return Objects.equals(username, user.username) &&
               Objects.equals(passwordHash, user.passwordHash) &&
               status == user.status &&
               Objects.equals(roles, user.roles);
    }

    @Override
    public int hashCode() {
        return Objects.hash(username, passwordHash, status, roles);
    }

    @Override
    public String toString() {
        // Never include password hash in toString for security
        return "User{" +
               "username='" + username + '\'' +
               ", status=" + status +
               ", roles=" + roles +
               '}';
    }
} 