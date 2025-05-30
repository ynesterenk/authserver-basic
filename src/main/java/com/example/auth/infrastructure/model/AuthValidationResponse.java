package com.example.auth.infrastructure.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response model for authentication validation API.
 * This class represents the JSON response returned by the Lambda function.
 */
public class AuthValidationResponse {
    
    @JsonProperty("allowed")
    private boolean allowed;
    
    @JsonProperty("message")
    private String message;
    
    @JsonProperty("timestamp")
    private long timestamp;

    /**
     * Default constructor for Jackson deserialization.
     */
    public AuthValidationResponse() {
        this.allowed = false;
        this.message = "";
        this.timestamp = 0;
    }

    /**
     * Constructor for creating an authentication validation response.
     *
     * @param allowed whether the authentication was successful
     * @param message descriptive message about the result
     * @param timestamp timestamp of the response
     */
    @JsonCreator
    public AuthValidationResponse(
            @JsonProperty("allowed") boolean allowed, 
            @JsonProperty("message") String message, 
            @JsonProperty("timestamp") long timestamp) {
        this.allowed = allowed;
        this.message = message;
        this.timestamp = timestamp;
    }

    public boolean isAllowed() {
        return allowed;
    }

    public String getMessage() {
        return message;
    }

    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return "AuthValidationResponse{" +
               "allowed=" + allowed +
               ", message='" + message + '\'' +
               ", timestamp=" + timestamp +
               '}';
    }
} 