package com.example.auth.domain.model.oauth;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import java.util.Objects;

/**
 * OAuth 2.0 Error Response domain model representing an error response.
 * This follows RFC 6749 Section 5.2 specification for error responses.
 *
 * @author Auth Server Team
 * @version 1.0
 * @since 1.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OAuthError {

    // Standard OAuth 2.0 error codes from RFC 6749
    public static final String INVALID_REQUEST = "invalid_request";
    public static final String INVALID_CLIENT = "invalid_client";
    public static final String INVALID_GRANT = "invalid_grant";
    public static final String UNAUTHORIZED_CLIENT = "unauthorized_client";
    public static final String UNSUPPORTED_GRANT_TYPE = "unsupported_grant_type";
    public static final String INVALID_SCOPE = "invalid_scope";
    public static final String ACCESS_DENIED = "access_denied";
    public static final String SERVER_ERROR = "server_error";
    public static final String TEMPORARILY_UNAVAILABLE = "temporarily_unavailable";

    @JsonProperty("error")
    @NotBlank(message = "Error code cannot be blank")
    private final String error;

    @JsonProperty("error_description")
    private final String errorDescription;

    @JsonProperty("error_uri")
    private final String errorUri;

    /**
     * Constructs a new OAuthError with the specified parameters.
     *
     * @param error the OAuth 2.0 error code
     * @param errorDescription a human-readable description of the error
     * @param errorUri a URI to a page with more information about the error
     * @throws IllegalArgumentException if error code is blank
     */
    public OAuthError(String error, String errorDescription, String errorUri) {
        this.error = validateNotBlank(error, "Error code cannot be blank");
        this.errorDescription = errorDescription;
        this.errorUri = errorUri;
    }

    /**
     * Creates an OAuth error with just an error code.
     *
     * @param error the OAuth 2.0 error code
     * @return a new OAuthError instance
     */
    public static OAuthError of(String error) {
        return new OAuthError(error, null, null);
    }

    /**
     * Creates an OAuth error with error code and description.
     *
     * @param error the OAuth 2.0 error code
     * @param errorDescription a human-readable description
     * @return a new OAuthError instance
     */
    public static OAuthError of(String error, String errorDescription) {
        return new OAuthError(error, errorDescription, null);
    }

    /**
     * Creates an invalid_request error.
     *
     * @param description specific description of what was invalid
     * @return a new OAuthError instance
     */
    public static OAuthError invalidRequest(String description) {
        return new OAuthError(INVALID_REQUEST, description, null);
    }

    /**
     * Creates an invalid_client error.
     *
     * @param description specific description of the client error
     * @return a new OAuthError instance
     */
    public static OAuthError invalidClient(String description) {
        return new OAuthError(INVALID_CLIENT, description, null);
    }

    /**
     * Creates an invalid_grant error.
     *
     * @param description specific description of the grant error
     * @return a new OAuthError instance
     */
    public static OAuthError invalidGrant(String description) {
        return new OAuthError(INVALID_GRANT, description, null);
    }

    /**
     * Creates an unauthorized_client error.
     *
     * @param description specific description of why client is unauthorized
     * @return a new OAuthError instance
     */
    public static OAuthError unauthorizedClient(String description) {
        return new OAuthError(UNAUTHORIZED_CLIENT, description, null);
    }

    /**
     * Creates an unsupported_grant_type error.
     *
     * @param grantType the unsupported grant type
     * @return a new OAuthError instance
     */
    public static OAuthError unsupportedGrantType(String grantType) {
        String description = "Grant type '" + grantType + "' is not supported";
        return new OAuthError(UNSUPPORTED_GRANT_TYPE, description, null);
    }

    /**
     * Creates an invalid_scope error.
     *
     * @param requestedScope the invalid scope that was requested
     * @return a new OAuthError instance
     */
    public static OAuthError invalidScope(String requestedScope) {
        String description = "Requested scope '" + requestedScope + "' is invalid or not allowed";
        return new OAuthError(INVALID_SCOPE, description, null);
    }

    /**
     * Creates an access_denied error.
     *
     * @param reason the reason access was denied
     * @return a new OAuthError instance
     */
    public static OAuthError accessDenied(String reason) {
        return new OAuthError(ACCESS_DENIED, reason, null);
    }

    /**
     * Creates a server_error error.
     *
     * @param description description of the server error
     * @return a new OAuthError instance
     */
    public static OAuthError serverError(String description) {
        return new OAuthError(SERVER_ERROR, description, null);
    }

    /**
     * Creates a temporarily_unavailable error.
     *
     * @param description description of why the service is unavailable
     * @return a new OAuthError instance
     */
    public static OAuthError temporarilyUnavailable(String description) {
        return new OAuthError(TEMPORARILY_UNAVAILABLE, description, null);
    }

    /**
     * Checks if this error indicates a client authentication failure.
     *
     * @return true if this is an invalid_client error
     */
    public boolean isClientAuthenticationError() {
        return INVALID_CLIENT.equals(error);
    }

    /**
     * Checks if this error indicates a server-side problem.
     *
     * @return true if this is a server_error or temporarily_unavailable error
     */
    public boolean isServerError() {
        return SERVER_ERROR.equals(error) || TEMPORARILY_UNAVAILABLE.equals(error);
    }

    /**
     * Checks if this error indicates a client configuration problem.
     *
     * @return true if this is a client configuration error
     */
    public boolean isClientConfigurationError() {
        return UNAUTHORIZED_CLIENT.equals(error) || 
               UNSUPPORTED_GRANT_TYPE.equals(error) || 
               INVALID_SCOPE.equals(error);
    }

    // Getters
    public String getError() {
        return error;
    }

    public String getErrorDescription() {
        return errorDescription;
    }

    public String getErrorUri() {
        return errorUri;
    }

    // Validation helper method
    private String validateNotBlank(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OAuthError that = (OAuthError) o;
        return Objects.equals(error, that.error) &&
                Objects.equals(errorDescription, that.errorDescription) &&
                Objects.equals(errorUri, that.errorUri);
    }

    @Override
    public int hashCode() {
        return Objects.hash(error, errorDescription, errorUri);
    }

    @Override
    public String toString() {
        return "OAuthError{" +
                "error='" + error + '\'' +
                ", errorDescription='" + errorDescription + '\'' +
                ", errorUri='" + errorUri + '\'' +
                '}';
    }
} 