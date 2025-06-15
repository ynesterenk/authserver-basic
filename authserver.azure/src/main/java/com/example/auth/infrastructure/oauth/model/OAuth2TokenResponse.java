package com.example.auth.infrastructure.oauth.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * OAuth 2.0 Token Response model for API layer.
 * This class is used for JSON serialization of successful token responses.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OAuth2TokenResponse {

    @JsonProperty("access_token")
    private String accessToken;

    @JsonProperty("token_type")
    private String tokenType;

    @JsonProperty("expires_in")
    private Integer expiresIn;

    @JsonProperty("scope")
    private String scope;

    @JsonProperty("issued_at")
    private Long issuedAt;

    public OAuth2TokenResponse(String accessToken, String tokenType, Integer expiresIn, String scope, Long issuedAt) {
        this.accessToken = accessToken;
        this.tokenType = tokenType;
        this.expiresIn = expiresIn;
        this.scope = scope;
        this.issuedAt = issuedAt;
    }

    // Default constructor for Jackson deserialization
    public OAuth2TokenResponse() {
        this.accessToken = null;
        this.tokenType = null;
        this.expiresIn = null;
        this.scope = null;
        this.issuedAt = null;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getTokenType() {
        return tokenType;
    }

    public Integer getExpiresIn() {
        return expiresIn;
    }

    public String getScope() {
        return scope;
    }

    public Long getIssuedAt() {
        return issuedAt;
    }

    // Setters for Jackson deserialization
    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }

    public void setExpiresIn(Integer expiresIn) {
        this.expiresIn = expiresIn;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public void setIssuedAt(Long issuedAt) {
        this.issuedAt = issuedAt;
    }
} 