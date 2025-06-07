package com.example.auth.domain.service.oauth;

import com.example.auth.domain.model.oauth.TokenRequest;
import com.example.auth.domain.model.oauth.TokenResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@DisplayName("JWT Token Service Tests")
class JwtTokenServiceTest {

    private JwtTokenService jwtTokenService;

    @BeforeEach
    void setUp() {
        jwtTokenService = new JwtTokenService();
        
        // Set test configuration values
        ReflectionTestUtils.setField(jwtTokenService, "issuer", "https://test.example.com");
        ReflectionTestUtils.setField(jwtTokenService, "audience", "https://api.test.example.com");
        ReflectionTestUtils.setField(jwtTokenService, "defaultExpirationSeconds", 3600);
        ReflectionTestUtils.setField(jwtTokenService, "maxExpirationSeconds", 7200);
        ReflectionTestUtils.setField(jwtTokenService, "jwtSecret", "testSecretKeyForJWTTokenGenerationThatIsAtLeast256BitsLong");
        
        // Service is ready to use
    }

    @Nested
    @DisplayName("Token Generation Tests")
    class TokenGenerationTests {

        @Test
        @DisplayName("Should generate valid JWT token")
        void shouldGenerateValidJwtToken() {
            TokenRequest request = new TokenRequest(
                "client_credentials",
                "test-client",
                "test-secret",
                "read write"
            );

            TokenResponse response = jwtTokenService.generateToken(request);

            assertThat(response).isNotNull();
            assertThat(response.getAccessToken()).isNotBlank();
            assertThat(response.getTokenType()).isEqualTo("Bearer");
            assertThat(response.getExpiresIn()).isEqualTo(3600);
            assertThat(response.getScope()).isEqualTo("read write");
            assertThat(response.getIssuedAt()).isPositive();
        }

        @Test
        @DisplayName("Should generate token without scope")
        void shouldGenerateTokenWithoutScope() {
            TokenRequest request = new TokenRequest(
                "client_credentials",
                "test-client",
                "test-secret",
                null
            );

            TokenResponse response = jwtTokenService.generateToken(request);

            assertThat(response).isNotNull();
            assertThat(response.getAccessToken()).isNotBlank();
            assertThat(response.getScope()).isNull();
        }

        @Test
        @DisplayName("Should handle empty scope")
        void shouldHandleEmptyScope() {
            TokenRequest request = new TokenRequest(
                "client_credentials",
                "test-client",
                "test-secret",
                "   "
            );

            TokenResponse response = jwtTokenService.generateToken(request);

            assertThat(response).isNotNull();
            assertThat(response.getAccessToken()).isNotBlank();
        }

        @Test
        @DisplayName("Should reject null token request")
        void shouldRejectNullTokenRequest() {
            assertThatThrownBy(() -> jwtTokenService.generateToken(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Token request cannot be null");
        }

        @Test
        @DisplayName("Should generate unique tokens")
        void shouldGenerateUniqueTokens() {
            TokenRequest request = new TokenRequest(
                "client_credentials",
                "test-client",
                "test-secret",
                "read"
            );

            TokenResponse response1 = jwtTokenService.generateToken(request);
            TokenResponse response2 = jwtTokenService.generateToken(request);

            assertThat(response1.getAccessToken()).isNotEqualTo(response2.getAccessToken());
        }
    }

    @Nested
    @DisplayName("Token Validation Tests")
    class TokenValidationTests {

        private String validToken;

        @BeforeEach
        void setUp() {
            TokenRequest request = new TokenRequest(
                "client_credentials",
                "test-client",
                "test-secret",
                "read"
            );
            TokenResponse response = jwtTokenService.generateToken(request);
            validToken = response.getAccessToken();
        }

        @Test
        @DisplayName("Should validate valid token")
        void shouldValidateValidToken() {
            assertThat(jwtTokenService.validateToken(validToken)).isTrue();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {" ", "\t", "\n"})
        @DisplayName("Should reject null or blank tokens")
        void shouldRejectNullOrBlankTokens(String token) {
            assertThatThrownBy(() -> jwtTokenService.validateToken(token))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Token cannot be null or empty");
        }

        @Test
        @DisplayName("Should reject malformed token")
        void shouldRejectMalformedToken() {
            assertThat(jwtTokenService.validateToken("invalid.token")).isFalse();
        }

        @Test
        @DisplayName("Should reject token with invalid signature")
        void shouldRejectTokenWithInvalidSignature() {
            String[] parts = validToken.split("\\.");
            String tamperedToken = parts[0] + "." + parts[1] + ".invalid-signature";
            
            assertThat(jwtTokenService.validateToken(tamperedToken)).isFalse();
        }

        @Test
        @DisplayName("Should handle expired token")
        void shouldHandleExpiredToken() {
            // Create a service with very short expiration (1 second)
            JwtTokenService shortExpirationService = new JwtTokenService();
            ReflectionTestUtils.setField(shortExpirationService, "issuer", "https://test.example.com");
            ReflectionTestUtils.setField(shortExpirationService, "audience", "https://api.test.example.com");
            ReflectionTestUtils.setField(shortExpirationService, "defaultExpirationSeconds", 1); // 1 second
            ReflectionTestUtils.setField(shortExpirationService, "jwtSecret", "testSecretKeyForJWTTokenGenerationThatIsAtLeast256BitsLong");

            TokenRequest request = new TokenRequest(
                "client_credentials",
                "test-client",
                "test-secret",
                "read"
            );
            
            TokenResponse response = shortExpirationService.generateToken(request);
            String shortLivedToken = response.getAccessToken();

            // Wait for token to expire
            try {
                Thread.sleep(1500); // 1.5 seconds to ensure expiration
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // The token should now be considered expired
            assertThat(jwtTokenService.isTokenExpired(shortLivedToken)).isTrue();
        }
    }

    @Nested
    @DisplayName("Claims Extraction Tests")
    class ClaimsExtractionTests {

        private String validToken;

        @BeforeEach
        void setUp() {
            TokenRequest request = new TokenRequest(
                "client_credentials",
                "test-client",
                "test-secret",
                "read write"
            );
            TokenResponse response = jwtTokenService.generateToken(request);
            validToken = response.getAccessToken();
        }

        @Test
        @DisplayName("Should extract claims from valid token")
        void shouldExtractClaimsFromValidToken() {
            Map<String, Object> claims = jwtTokenService.extractClaims(validToken);

            assertThat(claims).isNotNull();
            assertThat(claims.get("client_id")).isEqualTo("test-client");
            assertThat(claims.get("scope")).isEqualTo("read write");
            assertThat(claims.get("token_type")).isEqualTo("Bearer");
            assertThat(claims.get("iss")).isEqualTo("https://test.example.com");
            assertThat(claims.get("aud")).isEqualTo("https://api.test.example.com");
            assertThat(claims.get("sub")).isEqualTo("test-client");
            assertThat(claims.get("iat")).isNotNull();
            assertThat(claims.get("exp")).isNotNull();
            assertThat(claims.get("jti")).isNotNull();
        }

        @Test
        @DisplayName("Should extract client ID from token")
        void shouldExtractClientIdFromToken() {
            String clientId = jwtTokenService.extractClientId(validToken);
            assertThat(clientId).isEqualTo("test-client");
        }

        @Test
        @DisplayName("Should extract scope from token")
        void shouldExtractScopeFromToken() {
            String scope = jwtTokenService.extractScope(validToken);
            assertThat(scope).isEqualTo("read write");
        }

        @Test
        @DisplayName("Should handle token without scope")
        void shouldHandleTokenWithoutScope() {
            TokenRequest request = new TokenRequest(
                "client_credentials",
                "test-client",
                "test-secret",
                null
            );
            TokenResponse response = jwtTokenService.generateToken(request);
            String tokenWithoutScope = response.getAccessToken();

            String scope = jwtTokenService.extractScope(tokenWithoutScope);
            assertThat(scope).isNull();
        }

        @Test
        @DisplayName("Should return null for invalid token client ID extraction")
        void shouldReturnNullForInvalidTokenClientIdExtraction() {
            String clientId = jwtTokenService.extractClientId("invalid.token");
            assertThat(clientId).isNull();
        }

        @Test
        @DisplayName("Should return null for invalid token scope extraction")
        void shouldReturnNullForInvalidTokenScopeExtraction() {
            String scope = jwtTokenService.extractScope("invalid.token");
            assertThat(scope).isNull();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {" ", "\t", "\n"})
        @DisplayName("Should reject null or blank tokens for claims extraction")
        void shouldRejectNullOrBlankTokensForClaimsExtraction(String token) {
            assertThatThrownBy(() -> jwtTokenService.extractClaims(token))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Token cannot be null or empty");
        }

        @Test
        @DisplayName("Should throw exception for invalid token claims extraction")
        void shouldThrowExceptionForInvalidTokenClaimsExtraction() {
            assertThatThrownBy(() -> jwtTokenService.extractClaims("invalid.token"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Invalid token");
        }
    }

    @Nested
    @DisplayName("Token Expiration Tests")
    class TokenExpirationTests {

        private String validToken;

        @BeforeEach
        void setUp() {
            TokenRequest request = new TokenRequest(
                "client_credentials",
                "test-client",
                "test-secret",
                "read"
            );
            TokenResponse response = jwtTokenService.generateToken(request);
            validToken = response.getAccessToken();
        }

        @Test
        @DisplayName("Should check if token is not expired")
        void shouldCheckIfTokenIsNotExpired() {
            assertThat(jwtTokenService.isTokenExpired(validToken)).isFalse();
        }

        @Test
        @DisplayName("Should get remaining lifetime of token")
        void shouldGetRemainingLifetimeOfToken() {
            long remainingLifetime = jwtTokenService.getTokenRemainingLifetime(validToken);
            
            assertThat(remainingLifetime).isPositive();
            assertThat(remainingLifetime).isLessThanOrEqualTo(3600);
        }

        @Test
        @DisplayName("Should return zero for invalid token lifetime")
        void shouldReturnZeroForInvalidTokenLifetime() {
            long remainingLifetime = jwtTokenService.getTokenRemainingLifetime("invalid.token");
            assertThat(remainingLifetime).isZero();
        }

        @Test
        @DisplayName("Should return true for invalid token expiration check")
        void shouldReturnTrueForInvalidTokenExpirationCheck() {
            assertThat(jwtTokenService.isTokenExpired("invalid.token")).isTrue();
        }
    }

    @Nested
    @DisplayName("Edge Cases and Error Handling Tests")
    class EdgeCasesTests {

        @Test
        @DisplayName("Should handle token with tampered payload")
        void shouldHandleTokenWithTamperedPayload() {
            TokenRequest request = new TokenRequest(
                "client_credentials",
                "test-client",
                "test-secret",
                "read"
            );
            TokenResponse response = jwtTokenService.generateToken(request);
            String validToken = response.getAccessToken();

            String[] parts = validToken.split("\\.");
            String tamperedToken = parts[0] + ".eyJ0YW1wZXJlZCI6InBheWxvYWQifQ." + parts[2];
            
            assertThat(jwtTokenService.validateToken(tamperedToken)).isFalse();
        }

        @Test
        @DisplayName("Should handle malformed JWT format")
        void shouldHandleMalformedJwtFormat() {
            assertThat(jwtTokenService.validateToken("not.a.jwt.token.format")).isFalse();
            assertThat(jwtTokenService.validateToken("onlyonepart")).isFalse();
            assertThat(jwtTokenService.validateToken("only.twoparts")).isFalse();
        }

        @Test
        @DisplayName("Should handle token with different issuer")
        void shouldHandleTokenWithDifferentIssuer() {
            // Create a service with different issuer
            JwtTokenService differentIssuerService = new JwtTokenService();
            ReflectionTestUtils.setField(differentIssuerService, "issuer", "https://different.issuer.com");
            ReflectionTestUtils.setField(differentIssuerService, "audience", "https://api.test.example.com");
            ReflectionTestUtils.setField(differentIssuerService, "defaultExpirationSeconds", 3600);
            ReflectionTestUtils.setField(differentIssuerService, "jwtSecret", "testSecretKeyForJWTTokenGenerationThatIsAtLeast256BitsLong");

            TokenRequest request = new TokenRequest(
                "client_credentials",
                "test-client",
                "test-secret",
                "read"
            );
            
            TokenResponse response = differentIssuerService.generateToken(request);
            String differentIssuerToken = response.getAccessToken();

            // Our service should reject token with different issuer
            assertThat(jwtTokenService.validateToken(differentIssuerToken)).isFalse();
        }

        @Test
        @DisplayName("Should handle performance requirements")
        void shouldHandlePerformanceRequirements() {
            TokenRequest request = new TokenRequest(
                "client_credentials",
                "test-client",
                "test-secret",
                "read"
            );

            long startTime = System.nanoTime();
            TokenResponse response = jwtTokenService.generateToken(request);
            long endTime = System.nanoTime();
            
            long durationMs = (endTime - startTime) / 1_000_000;
            
            // Token generation should be reasonably fast (< 1000ms for normal cases)
            assertThat(durationMs).isLessThan(1000);
            assertThat(response.getAccessToken()).isNotBlank();
        }
    }
} 