package com.example.auth.domain.util.oauth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Utility class for OAuth 2.0 scope validation and management.
 * This class handles scope normalization, validation, and hierarchy management
 * according to OAuth 2.0 specifications.
 *
 * @author Auth Server Team
 * @version 1.0
 * @since 1.0
 */
public class ScopeValidator {

    private static final Logger logger = LoggerFactory.getLogger(ScopeValidator.class);

    // Common OAuth 2.0 scopes
    public static final String SCOPE_READ = "read";
    public static final String SCOPE_WRITE = "write";
    public static final String SCOPE_ADMIN = "admin";
    public static final String SCOPE_DELETE = "delete";

    // Scope separator as defined in RFC 6749
    private static final String SCOPE_SEPARATOR = " ";

    /**
     * Validates that all requested scopes are within the allowed scopes for a client.
     *
     * @param requestedScopes array of requested scopes
     * @param allowedScopes list of allowed scopes for the client
     * @return true if all requested scopes are allowed, false otherwise
     */
    public static boolean validateScopesAllowed(String[] requestedScopes, List<String> allowedScopes) {
        if (requestedScopes == null || requestedScopes.length == 0) {
            return true; // Empty request is always allowed
        }

        if (allowedScopes == null || allowedScopes.isEmpty()) {
            logger.debug("No allowed scopes configured for client");
            return false;
        }

        Set<String> allowedScopeSet = new HashSet<>(allowedScopes);

        for (String requestedScope : requestedScopes) {
            String trimmedScope = requestedScope.trim();
            if (!allowedScopeSet.contains(trimmedScope)) {
                logger.debug("Requested scope not allowed: {}", trimmedScope);
                return false;
            }
        }

        return true;
    }

    /**
     * Parses a scope string into an array of individual scopes.
     *
     * @param scope the scope string to parse
     * @return array of individual scopes, empty array if input is null/empty
     */
    public static String[] parseScopes(String scope) {
        if (scope == null || scope.trim().isEmpty()) {
            return new String[0];
        }

        return Arrays.stream(scope.trim().split("\\s+"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);
    }

    /**
     * Gets the default scope for a client when no specific scope is requested.
     *
     * @param allowedScopes list of allowed scopes for the client
     * @return the default scope, or null if no default can be determined
     */
    public static String getDefaultScope(List<String> allowedScopes) {
        if (allowedScopes == null || allowedScopes.isEmpty()) {
            return null;
        }

        // Return the first scope as default, preferring "read" if available
        if (allowedScopes.contains(SCOPE_READ)) {
            return SCOPE_READ;
        }

        return allowedScopes.get(0);
    }

    /**
     * Creates a scope string from an array of individual scopes.
     *
     * @param scopes array of individual scopes
     * @return scope string with scopes separated by spaces
     */
    public static String createScopeString(String[] scopes) {
        if (scopes == null || scopes.length == 0) {
            return null;
        }

        return Arrays.stream(scopes)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .sorted()
                .collect(Collectors.joining(SCOPE_SEPARATOR));
    }

    /**
     * Normalizes a scope string by removing duplicates, trimming whitespace,
     * and sorting scopes alphabetically.
     *
     * @param scope the scope string to normalize
     * @return normalized scope string or null if input is null/empty
     */
    public static String normalizeScope(String scope) {
        if (scope == null || scope.trim().isEmpty()) {
            return null;
        }

        String[] scopes = parseScopes(scope);
        return createScopeString(scopes);
    }

    /**
     * Validates a scope string format according to OAuth 2.0 specifications.
     *
     * @param scope the scope string to validate
     * @return true if the scope format is valid, false otherwise
     */
    public static boolean isValidScopeFormat(String scope) {
        if (scope == null || scope.trim().isEmpty()) {
            return true; // Empty scope is valid
        }

        String[] scopes = parseScopes(scope);
        for (String individualScope : scopes) {
            if (!isValidIndividualScope(individualScope)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Validates an individual scope name.
     *
     * @param scope the individual scope to validate
     * @return true if valid, false otherwise
     */
    private static boolean isValidIndividualScope(String scope) {
        if (scope == null || scope.trim().isEmpty()) {
            return false;
        }

        String trimmedScope = scope.trim();

        // Check length (reasonable limit)
        if (trimmedScope.length() > 50) {
            logger.debug("Individual scope too long: {}", trimmedScope);
            return false;
        }

        // Check for valid characters (alphanumeric, underscore, hyphen, colon, period)
        if (!trimmedScope.matches("^[a-zA-Z0-9._:-]+$")) {
            logger.debug("Invalid characters in scope: {}", trimmedScope);
            return false;
        }

        return true;
    }
} 