package com.example.auth.infrastructure.oauth.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * OAuth 2.0 Introspection Response model for API layer.
 * This class is used for JSON serialization of token introspection responses.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OAuth2IntrospectionResponse {

    @JsonProperty("active")
    private boolean active;

    @JsonProperty("client_id")
    private String clientId;

    @JsonProperty("scope")
    private String scope;

    @JsonProperty("token_type")
    private String tokenType;

    @JsonProperty("exp")
    private Integer exp;

    @JsonProperty("iat")
    private Integer iat;

    public OAuth2IntrospectionResponse(boolean active, String clientId, String scope, String tokenType, Integer exp, Integer iat) {
        this.active = active;
        this.clientId = clientId;
        this.scope = scope;
        this.tokenType = tokenType;
        this.exp = exp;
        this.iat = iat;
    }

    // Default constructor for Jackson deserialization
    public OAuth2IntrospectionResponse() {
        this.active = false;
        this.clientId = null;
        this.scope = null;
        this.tokenType = null;
        this.exp = null;
        this.iat = null;
    }

    public boolean isActive() {
        return active;
    }

    public String getClientId() {
        return clientId;
    }

    public String getScope() {
        return scope;
    }

    public String getTokenType() {
        return tokenType;
    }

    public Integer getExp() {
        return exp;
    }

    public Integer getIat() {
        return iat;
    }

    // Setters for Jackson deserialization
    public void setActive(boolean active) {
        this.active = active;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }

    public void setExp(Integer exp) {
        this.exp = exp;
    }

    public void setIat(Integer iat) {
        this.iat = iat;
    }
} 