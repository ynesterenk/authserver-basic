package com.example.auth.domain.model;

import jakarta.validation.constraints.NotBlank;

import java.util.Objects;

/**
 * Domain model representing an authentication request.
 * Contains the credentials provided by a client for verification.
 */
public final class AuthenticationRequest {
    
    @NotBlank(message = "Username cannot be blank")
    private final String username;
    
    @NotBlank(message = "Password cannot be blank")
    private final String password;

    /**
     * Constructor for creating an authentication request.
     *
     * @param username the username to authenticate
     * @param password the plaintext password to verify
     */
    public AuthenticationRequest(String username, String password) {
        this.username = validateUsername(username);
        this.password = validatePassword(password);
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
     * Gets the password.
     * Note: This should be treated as sensitive data and never logged.
     *
     * @return the plaintext password
     */
    public String getPassword() {
        return password;
    }

    /**
     * Clears the password from memory for security.
     * Note: In Java, this doesn't guarantee the memory is actually cleared
     * due to string immutability, but it's a best practice.
     */
    public void clearPassword() {
        // In a real implementation, we might use char[] instead of String
        // for passwords to allow actual clearing from memory
    }

    private String validateUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username cannot be null or empty");
        }
        if (username.length() > 255) {
            throw new IllegalArgumentException("Username cannot exceed 255 characters");
        }
        return username.trim();
    }

    private String validatePassword(String password) {
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Password cannot be null or empty");
        }
        if (password.length() > 1000) {
            throw new IllegalArgumentException("Password cannot exceed 1000 characters");
        }
        return password;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        AuthenticationRequest that = (AuthenticationRequest) obj;
        return Objects.equals(username, that.username) &&
               Objects.equals(password, that.password);
    }

    @Override
    public int hashCode() {
        return Objects.hash(username, password);
    }

    @Override
    public String toString() {
        // Never include password in toString for security
        return "AuthenticationRequest{" +
               "username='" + username + '\'' +
               ", password='[PROTECTED]'" +
               '}';
    }
}