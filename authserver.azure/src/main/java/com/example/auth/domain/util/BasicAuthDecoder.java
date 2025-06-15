package com.example.auth.domain.util;

import com.example.auth.domain.model.AuthenticationRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Utility class for decoding HTTP Basic Authentication headers.
 * Safely extracts username and password from Authorization headers.
 */
@Component
public class BasicAuthDecoder {

    private static final Logger log = LoggerFactory.getLogger(BasicAuthDecoder.class);
    
    private static final String BASIC_PREFIX = "Basic ";
    private static final String BEARER_PREFIX = "Bearer ";

    /**
     * Decodes a Basic Authentication header and creates an AuthenticationRequest.
     *
     * @param authorizationHeader the full Authorization header value
     * @return an AuthenticationRequest containing the decoded credentials
     * @throws IllegalArgumentException if the header is invalid or malformed
     */
    public AuthenticationRequest decodeBasicAuth(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.trim().isEmpty()) {
            throw new IllegalArgumentException("Authorization header cannot be null or empty");
        }

        String trimmedHeader = authorizationHeader.trim();
        
        // Check if it's a Basic auth header
        if (!trimmedHeader.startsWith(BASIC_PREFIX)) {
            if (trimmedHeader.startsWith(BEARER_PREFIX)) {
                throw new IllegalArgumentException("Bearer token authentication is not supported, use Basic authentication");
            }
            throw new IllegalArgumentException("Authorization header must start with 'Basic '");
        }

        // Extract the base64 encoded credentials
        String encodedCredentials = trimmedHeader.substring(BASIC_PREFIX.length()).trim();
        
        if (encodedCredentials.isEmpty()) {
            throw new IllegalArgumentException("No credentials found in Authorization header");
        }

        try {
            // Decode base64
            byte[] decodedBytes = Base64.getDecoder().decode(encodedCredentials);
            String credentials = new String(decodedBytes, StandardCharsets.UTF_8);

            // Split username:password
            return parseCredentials(credentials);

        } catch (IllegalArgumentException e) {
            log.warn("Failed to decode basic auth header: invalid base64 encoding");
            throw new IllegalArgumentException("Invalid base64 encoding in Authorization header");
        } catch (Exception e) {
            log.warn("Unexpected error decoding basic auth header", e);
            throw new IllegalArgumentException("Failed to decode Authorization header");
        }
    }

    /**
     * Validates that an Authorization header is properly formatted for Basic auth.
     *
     * @param authorizationHeader the header to validate
     * @return true if the header appears to be valid Basic auth format
     */
    public boolean isValidBasicAuthHeader(String authorizationHeader) {
        try {
            decodeBasicAuth(authorizationHeader);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Extracts just the base64-encoded credentials portion from a Basic auth header.
     *
     * @param authorizationHeader the full Authorization header
     * @return the base64-encoded credentials string
     * @throws IllegalArgumentException if the header is invalid
     */
    public String extractEncodedCredentials(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.trim().startsWith(BASIC_PREFIX)) {
            throw new IllegalArgumentException("Invalid Basic auth header format");
        }

        return authorizationHeader.trim().substring(BASIC_PREFIX.length()).trim();
    }

    /**
     * Parses the decoded credentials string (username:password format).
     *
     * @param credentials the decoded credentials string
     * @return an AuthenticationRequest with username and password
     * @throws IllegalArgumentException if the format is invalid
     */
    private AuthenticationRequest parseCredentials(String credentials) {
        if (credentials == null || credentials.isEmpty()) {
            throw new IllegalArgumentException("Credentials cannot be empty");
        }

        // Find the first colon separator
        int colonIndex = credentials.indexOf(':');
        
        if (colonIndex == -1) {
            throw new IllegalArgumentException("Invalid credentials format: missing colon separator");
        }

        if (colonIndex == 0) {
            throw new IllegalArgumentException("Invalid credentials format: username cannot be empty");
        }

        String username = credentials.substring(0, colonIndex);
        String password = credentials.substring(colonIndex + 1);

        // Validate extracted components
        validateUsername(username);
        validatePassword(password);

        return new AuthenticationRequest(username, password);
    }

    /**
     * Validates the extracted username.
     */
    private void validateUsername(String username) {
        if (username.isEmpty()) {
            throw new IllegalArgumentException("Username cannot be empty");
        }

        if (username.length() > 255) {
            throw new IllegalArgumentException("Username exceeds maximum length of 255 characters");
        }

        // Check for control characters that might indicate an attack
        if (containsControlCharacters(username)) {
            throw new IllegalArgumentException("Username contains invalid control characters");
        }
    }

    /**
     * Validates the extracted password.
     */
    private void validatePassword(String password) {
        // Note: Password can be empty (though not recommended)
        if (password.length() > 1000) {
            throw new IllegalArgumentException("Password exceeds maximum length of 1000 characters");
        }

        // Check for null bytes that might indicate an attack
        if (password.contains("\0")) {
            throw new IllegalArgumentException("Password contains null bytes");
        }
    }

    /**
     * Checks if a string contains control characters that might indicate an attack.
     */
    private boolean containsControlCharacters(String input) {
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            // Allow common printable characters and common international characters
            // Reject control characters (except tab, newline, carriage return)
            if (Character.isISOControl(c) && c != '\t' && c != '\n' && c != '\r') {
                return true;
            }
        }
        return false;
    }

    /**
     * Creates a Basic auth header value from username and password.
     * This method is primarily used for testing purposes.
     *
     * @param username the username
     * @param password the password
     * @return the Basic auth header value
     */
    public String createBasicAuthHeader(String username, String password) {
        if (username == null || password == null) {
            throw new IllegalArgumentException("Username and password cannot be null");
        }

        String credentials = username + ":" + password;
        String encodedCredentials = Base64.getEncoder().encodeToString(
                credentials.getBytes(StandardCharsets.UTF_8));

        return BASIC_PREFIX + encodedCredentials;
    }
} 