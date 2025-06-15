package com.example.auth.infrastructure.oauth.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * OAuth 2.0 Error Response model for API layer.
 * This class is used for JSON serialization of error responses.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OAuth2ErrorResponse {

    @JsonProperty("error")
    private String error;

    @JsonProperty("error_description")
    private String errorDescription;

    @JsonProperty("error_uri")
    private String errorUri;

    public OAuth2ErrorResponse(String error, String errorDescription, String errorUri) {
        this.error = error;
        this.errorDescription = errorDescription;
        this.errorUri = errorUri;
    }

    // Default constructor for Jackson deserialization
    public OAuth2ErrorResponse() {
        this.error = null;
        this.errorDescription = null;
        this.errorUri = null;
    }

    public String getError() {
        return error;
    }

    public String getErrorDescription() {
        return errorDescription;
    }

    public String getErrorUri() {
        return errorUri;
    }

    // Setters for Jackson deserialization
    public void setError(String error) {
        this.error = error;
    }

    public void setErrorDescription(String errorDescription) {
        this.errorDescription = errorDescription;
    }

    public void setErrorUri(String errorUri) {
        this.errorUri = errorUri;
    }
} 