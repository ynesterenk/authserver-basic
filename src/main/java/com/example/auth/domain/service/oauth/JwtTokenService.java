package com.example.auth.domain.service.oauth;

import com.example.auth.domain.model.oauth.TokenRequest;
import com.example.auth.domain.model.oauth.TokenResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT Token Service implementation for OAuth 2.0 token operations.
 * This service handles JWT token generation, validation, and parsing using manual JWT construction.
 * Uses HS256 signing algorithm for simplicity and compatibility.
 *
 * @author Auth Server Team
 * @version 1.0
 * @since 1.0
 */
@Service
public class JwtTokenService implements OAuth2TokenService {

    private static final Logger logger = LoggerFactory.getLogger(JwtTokenService.class);

    // JWT Claims
    private static final String CLAIM_CLIENT_ID = "client_id";
    private static final String CLAIM_SCOPE = "scope";
    private static final String CLAIM_TOKEN_TYPE = "token_type";
    private static final String CLAIM_ISS = "iss";
    private static final String CLAIM_AUD = "aud";
    private static final String CLAIM_SUB = "sub";
    private static final String CLAIM_IAT = "iat";
    private static final String CLAIM_EXP = "exp";
    private static final String CLAIM_JTI = "jti";

    @Value("${oauth2.jwt.issuer:https://auth.example.com}")
    private String issuer;

    @Value("${oauth2.jwt.audience:https://api.example.com}")
    private String audience;

    @Value("${oauth2.token.default-expiration-seconds:3600}")
    private int defaultExpirationSeconds;

    @Value("${oauth2.token.max-expiration-seconds:7200}")
    private int maxExpirationSeconds;

    // For development/testing - in production this should be retrieved from KMS
    @Value("${oauth2.jwt.secret:mySecretKeyForJWTTokenGenerationThatIsAtLeast256BitsLong}")
    private String jwtSecret;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public TokenResponse generateToken(TokenRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Token request cannot be null");
        }

        long startTime = System.nanoTime();

        try {
            Instant now = Instant.now();
            long issuedAt = now.getEpochSecond();
            long expiration = now.plusSeconds(defaultExpirationSeconds).getEpochSecond();

            // Create JWT header
            Map<String, Object> header = new HashMap<>();
            header.put("alg", "HS256");
            header.put("typ", "JWT");

            // Create JWT payload
            Map<String, Object> payload = new HashMap<>();
            payload.put(CLAIM_ISS, issuer);
            payload.put(CLAIM_AUD, audience);
            payload.put(CLAIM_SUB, request.getClientId());
            payload.put(CLAIM_IAT, issuedAt);
            payload.put(CLAIM_EXP, expiration);
            payload.put(CLAIM_JTI, generateTokenId());
            payload.put(CLAIM_CLIENT_ID, request.getClientId());
            payload.put(CLAIM_TOKEN_TYPE, "Bearer");
            
            if (request.getScope() != null && !request.getScope().trim().isEmpty()) {
                payload.put(CLAIM_SCOPE, request.getScope().trim());
            }

            // Generate JWT token
            String token = createJwtToken(header, payload);

            long endTime = System.nanoTime();
            long duration = (endTime - startTime) / 1_000_000; // Convert to milliseconds

            logger.debug("JWT token generated for client '{}' in {}ms", request.getClientId(), duration);

            return TokenResponse.bearer(token, defaultExpirationSeconds, request.getScope(), issuedAt);

        } catch (Exception e) {
            logger.error("Failed to generate JWT token for client '{}'", request.getClientId(), e);
            throw new RuntimeException("Token generation failed", e);
        }
    }

    @Override
    public boolean validateToken(String token) {
        if (token == null || token.trim().isEmpty()) {
            throw new IllegalArgumentException("Token cannot be null or empty");
        }

        long startTime = System.nanoTime();

        try {
            Map<String, Object> claims = extractClaims(token.trim());
            
            // Check expiration
            Object expObj = claims.get(CLAIM_EXP);
            if (expObj instanceof Number) {
                long exp = ((Number) expObj).longValue();
                long now = Instant.now().getEpochSecond();
                if (now >= exp) {
                    logger.debug("JWT token expired: exp={}, now={}", exp, now);
                    return false;
                }
            }

            // Check issuer
            Object issObj = claims.get(CLAIM_ISS);
            if (!issuer.equals(issObj)) {
                logger.debug("JWT token issuer mismatch: expected={}, actual={}", issuer, issObj);
                return false;
            }

            // Check audience
            Object audObj = claims.get(CLAIM_AUD);
            if (!audience.equals(audObj)) {
                logger.debug("JWT token audience mismatch: expected={}, actual={}", audience, audObj);
                return false;
            }
            
            long endTime = System.nanoTime();
            long duration = (endTime - startTime) / 1_000_000;
            
            logger.debug("JWT token validated successfully in {}ms", duration);
            return true;

        } catch (Exception e) {
            logger.debug("JWT token validation failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public Map<String, Object> extractClaims(String token) {
        if (token == null || token.trim().isEmpty()) {
            throw new IllegalArgumentException("Token cannot be null or empty");
        }

        try {
            String[] parts = token.trim().split("\\.");
            if (parts.length != 3) {
                throw new IllegalArgumentException("Invalid JWT format");
            }

            // Verify signature
            String expectedSignature = generateSignature(parts[0] + "." + parts[1]);
            if (!expectedSignature.equals(parts[2])) {
                throw new SecurityException("Invalid JWT signature");
            }

            // Decode payload
            String payloadJson = new String(Base64.decodeBase64(parts[1]), StandardCharsets.UTF_8);
            @SuppressWarnings("unchecked")
            Map<String, Object> claims = objectMapper.readValue(payloadJson, Map.class);
            
            logger.debug("JWT claims extracted successfully");
            return claims;

        } catch (Exception e) {
            logger.debug("Failed to extract claims from JWT token", e);
            throw new RuntimeException("Invalid token", e);
        }
    }

    @Override
    public String extractClientId(String token) {
        try {
            Map<String, Object> claims = extractClaims(token);
            return (String) claims.get(CLAIM_CLIENT_ID);
        } catch (Exception e) {
            logger.debug("Failed to extract client ID from token", e);
            return null;
        }
    }

    @Override
    public String extractScope(String token) {
        try {
            Map<String, Object> claims = extractClaims(token);
            return (String) claims.get(CLAIM_SCOPE);
        } catch (Exception e) {
            logger.debug("Failed to extract scope from token", e);
            return null;
        }
    }

    @Override
    public boolean isTokenExpired(String token) {
        try {
            Map<String, Object> claims = extractClaims(token);
            Object expObj = claims.get(CLAIM_EXP);
            if (expObj instanceof Number) {
                long exp = ((Number) expObj).longValue();
                long now = Instant.now().getEpochSecond();
                return now >= exp;
            }
            return true;
        } catch (Exception e) {
            logger.debug("Error checking token expiration", e);
            return true; // Assume expired if we can't determine
        }
    }

    @Override
    public long getTokenRemainingLifetime(String token) {
        try {
            Map<String, Object> claims = extractClaims(token);
            Object expObj = claims.get(CLAIM_EXP);
            if (expObj instanceof Number) {
                long exp = ((Number) expObj).longValue();
                long now = Instant.now().getEpochSecond();
                return Math.max(0, exp - now);
            }
            return 0;
        } catch (Exception e) {
            logger.debug("Error calculating token remaining lifetime", e);
            return 0;
        }
    }

    /**
     * Creates a JWT token from header and payload maps.
     */
    private String createJwtToken(Map<String, Object> header, Map<String, Object> payload) throws JsonProcessingException {
        // Encode header
        String headerJson = objectMapper.writeValueAsString(header);
        String encodedHeader = Base64.encodeBase64URLSafeString(headerJson.getBytes(StandardCharsets.UTF_8));

        // Encode payload
        String payloadJson = objectMapper.writeValueAsString(payload);
        String encodedPayload = Base64.encodeBase64URLSafeString(payloadJson.getBytes(StandardCharsets.UTF_8));

        // Create signature
        String data = encodedHeader + "." + encodedPayload;
        String signature = generateSignature(data);

        return data + "." + signature;
    }

    /**
     * Generates HMAC-SHA256 signature for JWT token.
     */
    private String generateSignature(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(jwtSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] signature = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.encodeBase64URLSafeString(signature);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException("Failed to generate JWT signature", e);
        }
    }

    /**
     * Generates a unique token ID for JWT 'jti' claim.
     */
    private String generateTokenId() {
        return "jwt_" + System.currentTimeMillis() + "_" + 
               Integer.toHexString((int) (Math.random() * Integer.MAX_VALUE));
    }
} 