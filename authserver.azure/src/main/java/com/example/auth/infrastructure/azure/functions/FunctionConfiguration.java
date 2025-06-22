package com.example.auth.infrastructure.azure.functions;

import com.example.auth.domain.model.AuthenticationRequest;
import com.example.auth.domain.model.AuthenticationResult;
import com.example.auth.domain.model.User;
import com.example.auth.domain.model.oauth.TokenRequest;
import com.example.auth.domain.model.oauth.TokenResponse;
import com.example.auth.domain.port.UserRepository;
import com.example.auth.domain.service.AuthenticatorService;
import com.example.auth.domain.service.oauth.ClientCredentialsService;
import com.example.auth.domain.service.oauth.OAuth2TokenService;
import com.example.auth.infrastructure.oauth.model.OAuth2IntrospectionResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Configuration
public class FunctionConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(FunctionConfiguration.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Bean
    public Function<String, String> basicAuth(AuthenticatorService authenticatorService, UserRepository userRepository) {
        return input -> {
            try {
                logger.info("Processing basicAuth function with input: {}", input);
                
                // Parse JSON input to AuthenticationRequest
                AuthenticationRequest authRequest = objectMapper.readValue(input, AuthenticationRequest.class);
                
                // Perform authentication
                AuthenticationResult result = authenticatorService.authenticate(authRequest);
                
                // Create response
                Map<String, Object> response = new HashMap<>();
                response.put("authenticated", result.isAllowed());
                response.put("username", authRequest.getUsername());
                response.put("timestamp", System.currentTimeMillis());
                
                if (result.isAllowed()) {
                    // Get user roles
                    List<String> roles = userRepository.findByUsername(authRequest.getUsername())
                            .map(User::getRoles)
                            .orElse(Collections.emptyList());
                    response.put("roles", roles);
                    response.put("message", "Authentication successful");
                } else {
                    response.put("message", result.getReason());
                }
                
                return objectMapper.writeValueAsString(response);
                
            } catch (Exception e) {
                logger.error("Error in basicAuth function", e);
                try {
                    Map<String, Object> errorResponse = new HashMap<>();
                    errorResponse.put("authenticated", false);
                    errorResponse.put("error", "internal_error");
                    errorResponse.put("message", "Authentication processing failed");
                    return objectMapper.writeValueAsString(errorResponse);
                } catch (Exception jsonError) {
                    return "{\"authenticated\":false,\"error\":\"internal_error\"}";
                }
            }
        };
    }

    @Bean
    public Function<String, String> oauthToken(ClientCredentialsService clientCredentialsService) {
        return input -> {
            try {
                logger.info("Processing oauthToken function");
                
                // Parse JSON input to TokenRequest
                TokenRequest tokenRequest = objectMapper.readValue(input, TokenRequest.class);
                
                // Generate token
                TokenResponse tokenResponse = clientCredentialsService.authenticate(tokenRequest);
                
                // Create OAuth2 compliant response
                Map<String, Object> response = new HashMap<>();
                response.put("access_token", tokenResponse.getAccessToken());
                response.put("token_type", tokenResponse.getTokenType());
                response.put("expires_in", tokenResponse.getExpiresIn());
                if (tokenResponse.getScope() != null) {
                    response.put("scope", tokenResponse.getScope());
                }
                
                return objectMapper.writeValueAsString(response);
                
            } catch (ClientCredentialsService.OAuth2AuthenticationException e) {
                logger.warn("OAuth authentication failed: {}", e.getOAuthError().getError());
                try {
                    Map<String, Object> errorResponse = new HashMap<>();
                    errorResponse.put("error", e.getOAuthError().getError());
                    errorResponse.put("error_description", e.getOAuthError().getErrorDescription());
                    return objectMapper.writeValueAsString(errorResponse);
                } catch (Exception jsonError) {
                    return "{\"error\":\"server_error\",\"error_description\":\"Internal server error\"}";
                }
            } catch (Exception e) {
                logger.error("Error in oauthToken function", e);
                try {
                    Map<String, Object> errorResponse = new HashMap<>();
                    errorResponse.put("error", "server_error");
                    errorResponse.put("error_description", "Token generation failed");
                    return objectMapper.writeValueAsString(errorResponse);
                } catch (Exception jsonError) {
                    return "{\"error\":\"server_error\",\"error_description\":\"Internal server error\"}";
                }
            }
        };
    }

    @Bean
    public Function<String, String> oauthIntrospect(OAuth2TokenService tokenService) {
        return input -> {
            try {
                logger.info("Processing oauthIntrospect function");
                
                // Parse JSON input to get token
                Map<String, String> requestData = objectMapper.readValue(input, Map.class);
                String token = requestData.get("token");
                
                if (token == null || token.trim().isEmpty()) {
                    Map<String, Object> errorResponse = new HashMap<>();
                    errorResponse.put("error", "invalid_request");
                    errorResponse.put("error_description", "Missing token parameter");
                    return objectMapper.writeValueAsString(errorResponse);
                }
                
                // Validate token
                boolean isValid = tokenService.validateToken(token);
                
                OAuth2IntrospectionResponse introspectionResponse;
                if (!isValid) {
                    introspectionResponse = new OAuth2IntrospectionResponse(false, null, null, null, null, null);
                } else {
                    // Extract token information
                    String clientId = tokenService.extractClientId(token);
                    String scope = tokenService.extractScope(token);
                    Map<String, Object> claims = tokenService.extractClaims(token);
                    
                    Object expObj = claims.get("exp");
                    Object iatObj = claims.get("iat");
                    Integer exp = expObj != null ? ((Number) expObj).intValue() : null;
                    Integer iat = iatObj != null ? ((Number) iatObj).intValue() : null;
                    
                    introspectionResponse = new OAuth2IntrospectionResponse(
                        true, clientId, scope, "Bearer", exp, iat
                    );
                }
                
                return objectMapper.writeValueAsString(introspectionResponse);
                
            } catch (Exception e) {
                logger.error("Error in oauthIntrospect function", e);
                try {
                    OAuth2IntrospectionResponse errorResponse = new OAuth2IntrospectionResponse(false, null, null, null, null, null);
                    return objectMapper.writeValueAsString(errorResponse);
                } catch (Exception jsonError) {
                    return "{\"active\":false}";
                }
            }
        };
    }
} 