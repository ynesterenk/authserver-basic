package com.example.auth.domain.model.oauth;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Instant;
import java.util.Objects;

/**
 * OAuth 2.0 Token Response domain model representing a successful token response.
 * This follows RFC 6749 Section 5.1 specification.
 *
 * @author Auth Server Team
 * @version 1.0
 * @since 1.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TokenResponse {

    @JsonProperty("access_token")
    @NotBlank(message = "Access token cannot be blank")
    private final String accessToken;

    @JsonProperty("token_type")
    @NotBlank(message = "Token type cannot be blank")
    private final String tokenType;

    @JsonProperty("expires_in")
    @Positive(message = "Expires in must be positive")
    private final Integer expiresIn;

    @JsonProperty("scope")
    private final String scope;

    @JsonProperty("issued_at")
    @NotNull(message = "Issued at cannot be null")
    private final Long issuedAt;

    /**
     * Constructs a new TokenResponse with the specified parameters.
     *
     * @param accessToken the OAuth 2.0 access token
     * @param tokenType the token type (typically "Bearer")
     * @param expiresIn the token lifetime in seconds
     * @param scope the granted scope
     * @param issuedAt the timestamp when the token was issued (Unix epoch seconds)
     * @throws IllegalArgumentException if validation fails
     */
    public TokenResponse(String accessToken, String tokenType, Integer expiresIn, String scope, Long issuedAt) {
        this.accessToken = validateNotBlank(accessToken, "Access token cannot be blank");
        this.tokenType = validateNotBlank(tokenType, "Token type cannot be blank");
        this.expiresIn = validatePositive(expiresIn, "Expires in must be positive");
        this.scope = scope;
        this.issuedAt = Objects.requireNonNull(issuedAt, "Issued at cannot be null");
    }

    /**
     * Creates a new TokenResponse with Bearer token type.
     *
     * @param accessToken the OAuth 2.0 access token
     * @param expiresIn the token lifetime in seconds
     * @param scope the granted scope
     * @return a new TokenResponse instance
     */
    public static TokenResponse bearer(String accessToken, Integer expiresIn, String scope) {
        return new TokenResponse(accessToken, "Bearer", expiresIn, scope, Instant.now().getEpochSecond());
    }

    /**
     * Creates a new TokenResponse with Bearer token type and current timestamp.
     *
     * @param accessToken the OAuth 2.0 access token
     * @param expiresIn the token lifetime in seconds
     * @param scope the granted scope
     * @param issuedAt the timestamp when the token was issued
     * @return a new TokenResponse instance
     */
    public static TokenResponse bearer(String accessToken, Integer expiresIn, String scope, Long issuedAt) {
        return new TokenResponse(accessToken, "Bearer", expiresIn, scope, issuedAt);
    }

    /**
     * Checks if this token has expired based on the current time.
     *
     * @return true if the token has expired, false otherwise
     */
    public boolean isExpired() {
        long currentTime = Instant.now().getEpochSecond();
        return currentTime >= (issuedAt + expiresIn);
    }

    /**
     * Gets the absolute expiration time as Unix epoch seconds.
     *
     * @return the expiration timestamp
     */
    public long getExpirationTime() {
        return issuedAt + expiresIn;
    }

    /**
     * Gets the remaining lifetime of the token in seconds.
     *
     * @return remaining seconds until expiration, or 0 if expired
     */
    public long getRemainingLifetime() {
        long currentTime = Instant.now().getEpochSecond();
        long remainingTime = (issuedAt + expiresIn) - currentTime;
        return Math.max(0, remainingTime);
    }

    /**
     * Checks if this is a Bearer token.
     *
     * @return true if the token type is "Bearer"
     */
    public boolean isBearerToken() {
        return "Bearer".equalsIgnoreCase(tokenType);
    }

    // Getters
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

    // Validation helper methods
    private String validateNotBlank(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private Integer validatePositive(Integer value, String message) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TokenResponse that = (TokenResponse) o;
        return Objects.equals(accessToken, that.accessToken) &&
                Objects.equals(tokenType, that.tokenType) &&
                Objects.equals(expiresIn, that.expiresIn) &&
                Objects.equals(scope, that.scope) &&
                Objects.equals(issuedAt, that.issuedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(accessToken, tokenType, expiresIn, scope, issuedAt);
    }

    @Override
    public String toString() {
        return "TokenResponse{" +
                "accessToken='[PROTECTED]'" +
                ", tokenType='" + tokenType + '\'' +
                ", expiresIn=" + expiresIn +
                ", scope='" + scope + '\'' +
                ", issuedAt=" + issuedAt +
                '}';
    }
} 