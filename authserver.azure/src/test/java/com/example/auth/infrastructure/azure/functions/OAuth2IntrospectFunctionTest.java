package com.example.auth.infrastructure.azure.functions;

import com.example.auth.domain.service.oauth.OAuth2TokenService;
import com.example.auth.infrastructure.oauth.model.OAuth2ErrorResponse;
import com.example.auth.infrastructure.oauth.model.OAuth2IntrospectionResponse;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.*;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Test class for OAuth2IntrospectFunction - tests the actual Azure Function implementation
 * that handles POST /api/oauth/introspect using real OAuth2TokenService methods.
 * 
 * Tests against actual domain service methods: validateToken(), extractClaims(), 
 * extractClientId(), extractScope() and OAuth2IntrospectionResponse constructor.
 */
@ExtendWith(MockitoExtension.class)
class OAuth2IntrospectFunctionTest {
    
    @Mock
    private OAuth2TokenService tokenService;
    
    @Mock
    private ExecutionContext context;
    
    @Mock
    private Logger logger;
    
    @Mock
    private HttpRequestMessage<Optional<String>> request;
    
    @Mock
    private HttpResponseMessage.Builder responseBuilder;
    
    @Mock
    private HttpResponseMessage response;
    
    @Mock
    private com.example.auth.infrastructure.azure.config.AzureFunctionConfiguration.AzureFunctionMetrics metrics;
    
    @InjectMocks
    private OAuth2IntrospectFunction function;
    
    @BeforeEach
    void setUp() {
        lenient().when(context.getLogger()).thenReturn(logger);
        lenient().when(request.createResponseBuilder(any(HttpStatus.class))).thenReturn(responseBuilder);
        lenient().when(responseBuilder.header(anyString(), anyString())).thenReturn(responseBuilder);
        lenient().when(responseBuilder.body(any())).thenReturn(responseBuilder);
        lenient().when(responseBuilder.build()).thenReturn(response);
    }
    
    @Test
    void testActiveTokenIntrospection() {
        // Arrange
        String token = "valid_access_token_123";
        String formData = "token=" + token;
        when(request.getBody()).thenReturn(Optional.of(formData));
        Map<String, String> headers = new HashMap<>();
        headers.put("content-type", "application/x-www-form-urlencoded");
        when(request.getHeaders()).thenReturn(headers);
        
        // Mock real OAuth2TokenService methods
        when(tokenService.validateToken(token)).thenReturn(true);
        
        Map<String, Object> claims = new HashMap<>();
        claims.put("exp", Instant.now().plusSeconds(3600).getEpochSecond());
        claims.put("iat", Instant.now().getEpochSecond());
        when(tokenService.extractClaims(token)).thenReturn(claims);
        
        when(tokenService.extractClientId(token)).thenReturn("test-client");
        when(tokenService.extractScope(token)).thenReturn("read write");
        
        // Act
        HttpResponseMessage result = function.run(request, context);
        
        // Assert
        verify(tokenService).validateToken(token);
        verify(tokenService).extractClaims(token);
        verify(tokenService).extractClientId(token);
        verify(tokenService).extractScope(token);
        
        verify(responseBuilder).body(argThat(body -> {
            String jsonBody = (String) body;
            return jsonBody.contains("\"active\":true") &&
                   jsonBody.contains("\"client_id\":\"test-client\"") &&
                   jsonBody.contains("\"scope\":\"read write\"") &&
                   jsonBody.contains("\"token_type\":\"Bearer\"") &&
                   jsonBody.contains("\"exp\":") &&
                   jsonBody.contains("\"iat\":");
        }));
        verify(request).createResponseBuilder(HttpStatus.OK);
    }
    
    @Test
    void testInactiveTokenIntrospection() {
        // Arrange
        String token = "invalid_or_expired_token";
        String formData = "token=" + token;
        when(request.getBody()).thenReturn(Optional.of(formData));
        Map<String, String> headers = new HashMap<>();
        headers.put("content-type", "application/x-www-form-urlencoded");
        when(request.getHeaders()).thenReturn(headers);
        
        // Mock tokenService.validateToken() returning false for invalid token
        when(tokenService.validateToken(token)).thenReturn(false);
        
        // Act
        HttpResponseMessage result = function.run(request, context);
        
        // Assert
        verify(tokenService).validateToken(token);
        // Should not call other extraction methods for invalid tokens
        verify(tokenService, never()).extractClaims(token);
        verify(tokenService, never()).extractClientId(token);
        verify(tokenService, never()).extractScope(token);
        
        verify(responseBuilder).body(argThat(body -> {
            String jsonBody = (String) body;
            return jsonBody.contains("\"active\":false") &&
                   (jsonBody.contains("\"client_id\":null") || !jsonBody.contains("\"client_id\"")) &&
                   (jsonBody.contains("\"scope\":null") || !jsonBody.contains("\"scope\"")) &&
                   (jsonBody.contains("\"token_type\":null") || !jsonBody.contains("\"token_type\""));
        }));
        verify(request).createResponseBuilder(HttpStatus.OK);
    }
    
    @Test
    void testMissingTokenParameter() {
        // Arrange
        String formData = "client_id=test-client"; // Missing token parameter
        when(request.getBody()).thenReturn(Optional.of(formData));
        Map<String, String> headers = new HashMap<>();
        headers.put("content-type", "application/x-www-form-urlencoded");
        when(request.getHeaders()).thenReturn(headers);
        
        // Act
        HttpResponseMessage result = function.run(request, context);
        
        // Assert
        verify(responseBuilder).body(argThat(body -> {
            String jsonBody = (String) body;
            return jsonBody.contains("\"error\":\"invalid_request\"") &&
                   jsonBody.contains("\"error_description\":\"Missing token parameter\"");
        }));
        verify(request).createResponseBuilder(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(tokenService);
    }
    
    @Test
    void testEmptyTokenParameter() {
        // Arrange
        String formData = "token="; // Empty token value
        when(request.getBody()).thenReturn(Optional.of(formData));
        Map<String, String> headers = new HashMap<>();
        headers.put("content-type", "application/x-www-form-urlencoded");
        when(request.getHeaders()).thenReturn(headers);
        
        // Act
        HttpResponseMessage result = function.run(request, context);
        
        // Assert
        verify(responseBuilder).body(argThat(body -> {
            String jsonBody = (String) body;
            return jsonBody.contains("\"error\":\"invalid_request\"") &&
                   jsonBody.contains("\"error_description\":\"Missing token parameter\"");
        }));
        verify(request).createResponseBuilder(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(tokenService);
    }
    
    @Test
    void testEmptyRequestBody() {
        // Arrange
        when(request.getBody()).thenReturn(Optional.empty());
        Map<String, String> headers = new HashMap<>();
        headers.put("content-type", "application/x-www-form-urlencoded");
        when(request.getHeaders()).thenReturn(headers);
        
        // Act
        HttpResponseMessage result = function.run(request, context);
        
        // Assert
        verify(responseBuilder).body(argThat(body -> {
            String jsonBody = (String) body;
            return jsonBody.contains("\"error\":\"invalid_request\"") &&
                   jsonBody.contains("\"error_description\":\"Missing token parameter\"");
        }));
        verify(request).createResponseBuilder(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(tokenService);
    }
    
    @Test
    void testTokenServiceException() {
        // Arrange
        String token = "problematic_token";
        String formData = "token=" + token;
        when(request.getBody()).thenReturn(Optional.of(formData));
        Map<String, String> headers = new HashMap<>();
        headers.put("content-type", "application/x-www-form-urlencoded");
        when(request.getHeaders()).thenReturn(headers);
        
        // Mock tokenService throwing exception
        when(tokenService.validateToken(token)).thenThrow(new RuntimeException("Token parsing error"));
        
        // Act
        HttpResponseMessage result = function.run(request, context);
        
        // Assert
        verify(tokenService).validateToken(token);
        
        verify(responseBuilder).body(argThat(body -> {
            String jsonBody = (String) body;
            return jsonBody.contains("\"active\":false") &&
                   (jsonBody.contains("\"client_id\":null") || !jsonBody.contains("\"client_id\"")) &&
                   (jsonBody.contains("\"scope\":null") || !jsonBody.contains("\"scope\""));
        }));
        verify(request).createResponseBuilder(HttpStatus.OK);
    }
    
    @Test
    void testClaimsExtractionError() {
        // Arrange
        String token = "valid_token_bad_claims";
        String formData = "token=" + token;
        when(request.getBody()).thenReturn(Optional.of(formData));
        Map<String, String> headers = new HashMap<>();
        headers.put("content-type", "application/x-www-form-urlencoded");
        when(request.getHeaders()).thenReturn(headers);
        
        when(tokenService.validateToken(token)).thenReturn(true);
        when(tokenService.extractClaims(token)).thenThrow(new RuntimeException("Claims extraction failed"));
        
        // Act
        HttpResponseMessage result = function.run(request, context);
        
        // Assert
        verify(tokenService).validateToken(token);
        verify(tokenService).extractClaims(token);
        
        verify(responseBuilder).body(argThat(body -> {
            String jsonBody = (String) body;
            return jsonBody.contains("\"active\":false");
        }));
        verify(request).createResponseBuilder(HttpStatus.OK);
    }
    
    @Test
    void testMalformedFormData() {
        // Arrange
        String formData = "invalid=form&data&structure"; // No token parameter
        when(request.getBody()).thenReturn(Optional.of(formData));
        Map<String, String> headers = new HashMap<>();
        headers.put("content-type", "application/x-www-form-urlencoded");
        when(request.getHeaders()).thenReturn(headers);
        
        // Act
        HttpResponseMessage result = function.run(request, context);
        
        // Assert
        verify(responseBuilder).body(argThat(body -> {
            String jsonBody = (String) body;
            return jsonBody.contains("\"error\":\"invalid_request\"") &&
                   jsonBody.contains("\"error_description\":\"Missing token parameter\"");
        }));
        verify(request).createResponseBuilder(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(tokenService);
    }
    
    @Test
    void testResponseHeaders() {
        // Arrange
        String token = "valid_token";
        String formData = "token=" + token;
        when(request.getBody()).thenReturn(Optional.of(formData));
        Map<String, String> headers = new HashMap<>();
        headers.put("content-type", "application/x-www-form-urlencoded");
        when(request.getHeaders()).thenReturn(headers);
        
        when(tokenService.validateToken(token)).thenReturn(true);
        when(tokenService.extractClaims(token)).thenReturn(new HashMap<>());
        when(tokenService.extractClientId(token)).thenReturn("client");
        when(tokenService.extractScope(token)).thenReturn("read");
        
        // Act
        HttpResponseMessage result = function.run(request, context);
        
        // Assert - verify required OAuth2 introspection response headers
        verify(responseBuilder).header("Content-Type", "application/json");
        verify(responseBuilder).header("Cache-Control", "no-store");
        verify(responseBuilder).header("Pragma", "no-cache");
    }
    
    @Test
    void testTokenWithCompleteClaimsStructure() {
        // Arrange
        String token = "complete_token";
        String formData = "token=" + token;
        when(request.getBody()).thenReturn(Optional.of(formData));
        Map<String, String> headers = new HashMap<>();
        headers.put("content-type", "application/x-www-form-urlencoded");
        when(request.getHeaders()).thenReturn(headers);
        
        when(tokenService.validateToken(token)).thenReturn(true);
        
        // Mock complete claims structure with exp and iat
        long currentTime = Instant.now().getEpochSecond();
        Map<String, Object> claims = new HashMap<>();
        claims.put("exp", currentTime + 3600);
        claims.put("iat", currentTime);
        claims.put("sub", "user123");
        when(tokenService.extractClaims(token)).thenReturn(claims);
        
        when(tokenService.extractClientId(token)).thenReturn("production-client");
        when(tokenService.extractScope(token)).thenReturn("read write admin");
        
        // Act
        HttpResponseMessage result = function.run(request, context);
        
        // Assert
        verify(responseBuilder).body(argThat(body -> {
            String jsonBody = (String) body;
            return jsonBody.contains("\"active\":true") &&
                   jsonBody.contains("\"client_id\":\"production-client\"") &&
                   jsonBody.contains("\"scope\":\"read write admin\"") &&
                   jsonBody.contains("\"token_type\":\"Bearer\"") &&
                   jsonBody.contains("\"exp\":" + (currentTime + 3600)) &&
                   jsonBody.contains("\"iat\":" + currentTime);
        }));
    }
}




