package com.example.auth.infrastructure.azure.functions;

import com.example.auth.domain.model.oauth.TokenRequest;
import com.example.auth.domain.model.oauth.TokenResponse;
import com.example.auth.domain.model.oauth.OAuthError;
import com.example.auth.domain.service.oauth.ClientCredentialsService;
import com.example.auth.infrastructure.oauth.model.OAuth2ErrorResponse;
import com.example.auth.infrastructure.oauth.model.OAuth2TokenResponse;
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
 * Test class for OAuth2TokenFunction - tests the actual Azure Function implementation
 * that handles POST /api/oauth/token with form-encoded and Basic Auth support.
 * 
 * Tests against real ClientCredentialsService.authenticate(TokenRequest) method signature
 * and actual OAuth2TokenResponse constructor with 5 parameters.
 */
@ExtendWith(MockitoExtension.class)
class OAuth2TokenFunctionTest {
    
    @Mock
    private ClientCredentialsService clientCredentialsService;
    
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
    private OAuth2TokenFunction function;
    
    @BeforeEach
    void setUp() {
        lenient().when(context.getLogger()).thenReturn(logger);
        lenient().when(request.createResponseBuilder(any(HttpStatus.class))).thenReturn(responseBuilder);
        lenient().when(responseBuilder.header(anyString(), anyString())).thenReturn(responseBuilder);
        lenient().when(responseBuilder.body(any())).thenReturn(responseBuilder);
        lenient().when(responseBuilder.build()).thenReturn(response);
    }
    
    @Test
    void testClientCredentialsFlowWithFormData() {
        // Arrange
        String formData = "grant_type=client_credentials&client_id=test-client&client_secret=secret123&scope=read write";
        when(request.getBody()).thenReturn(Optional.of(formData));
        Map<String, String> headers = new HashMap<>();
        headers.put("content-type", "application/x-www-form-urlencoded");
        when(request.getHeaders()).thenReturn(headers);
        
        // Mock real ClientCredentialsService.authenticate(TokenRequest) method
        TokenResponse tokenResponse = new TokenResponse("access_token_123", "Bearer", 3600, "read write", System.currentTimeMillis() / 1000);
        when(clientCredentialsService.authenticate(any(TokenRequest.class))).thenReturn(tokenResponse);
        
        // Act
        HttpResponseMessage result = function.run(request, context);
          // Assert
        verify(clientCredentialsService).authenticate(argThat(tokenRequest -> 
            "client_credentials".equals(tokenRequest.getGrantType()) &&
            "test-client".equals(tokenRequest.getClientId()) &&
            "secret123".equals(tokenRequest.getClientSecret()) &&
            "read write".equals(tokenRequest.getScope())
        ));
        
        verify(responseBuilder).body(argThat(body -> {
            String jsonBody = (String) body;
            return jsonBody.contains("\"access_token\":\"access_token_123\"") &&
                   jsonBody.contains("\"token_type\":\"Bearer\"") &&
                   jsonBody.contains("\"expires_in\":3600") &&
                   jsonBody.contains("\"scope\":\"read write\"") &&
                   jsonBody.contains("\"issued_at\":");
        }));
        verify(request).createResponseBuilder(HttpStatus.OK);
    }
    
    @Test
    void testClientCredentialsFlowWithBasicAuth() {
        // Arrange
        String basicAuthHeader = "Basic " + Base64.getEncoder().encodeToString("test-client:secret123".getBytes());
        Map<String, String> headers = new HashMap<>();
        headers.put("authorization", basicAuthHeader);
        headers.put("content-type", "application/x-www-form-urlencoded");
        when(request.getHeaders()).thenReturn(headers);
        
        String formData = "grant_type=client_credentials&scope=read";
        when(request.getBody()).thenReturn(Optional.of(formData));
        
        // Mock successful authentication
        TokenResponse tokenResponse = new TokenResponse("access_token_456", "Bearer", 1800, "read", System.currentTimeMillis() / 1000);
        when(clientCredentialsService.authenticate(any(TokenRequest.class))).thenReturn(tokenResponse);
        
        // Act
        HttpResponseMessage result = function.run(request, context);
          // Assert
        verify(clientCredentialsService).authenticate(argThat(tokenRequest -> 
            "client_credentials".equals(tokenRequest.getGrantType()) &&
            "test-client".equals(tokenRequest.getClientId()) &&
            "secret123".equals(tokenRequest.getClientSecret()) &&
            "read".equals(tokenRequest.getScope())
        ));
        
        verify(responseBuilder).body(argThat(body -> {
            String jsonBody = (String) body;
            return jsonBody.contains("\"access_token\":\"access_token_456\"") &&
                   jsonBody.contains("\"token_type\":\"Bearer\"") &&
                   jsonBody.contains("\"expires_in\":1800") &&
                   jsonBody.contains("\"scope\":\"read\"");
        }));
    }
    
    @Test
    void testOAuth2AuthenticationException() {
        // Arrange
        String formData = "grant_type=client_credentials&client_id=invalid-client&client_secret=wrong-secret";
        when(request.getBody()).thenReturn(Optional.of(formData));
        Map<String, String> headers = new HashMap<>();
        headers.put("content-type", "application/x-www-form-urlencoded");
        when(request.getHeaders()).thenReturn(headers);
          // Mock real ClientCredentialsService.OAuth2AuthenticationException (inner class)
        ClientCredentialsService.OAuth2AuthenticationException exception = 
            mock(ClientCredentialsService.OAuth2AuthenticationException.class);
        
        // Mock the real exception methods: getOAuthError().getError() and getErrorDescription()
        OAuthError oAuthError = mock(OAuthError.class);
        when(oAuthError.getError()).thenReturn("invalid_client");
        when(oAuthError.getErrorDescription()).thenReturn("Client authentication failed");
        when(exception.getOAuthError()).thenReturn(oAuthError);
        
        when(clientCredentialsService.authenticate(any(TokenRequest.class))).thenThrow(exception);
        
        // Act
        HttpResponseMessage result = function.run(request, context);
        
        // Assert
        verify(clientCredentialsService).authenticate(any(TokenRequest.class));
        verify(responseBuilder).body(argThat(body -> {
            String jsonBody = (String) body;
            return jsonBody.contains("\"error\":\"invalid_client\"") &&
                   jsonBody.contains("\"error_description\":\"Client authentication failed\"");
        }));
        verify(request).createResponseBuilder(HttpStatus.UNAUTHORIZED);
    }
    
    @Test
    void testUnsupportedGrantType() {
        // Arrange
        String formData = "grant_type=authorization_code&client_id=test-client&client_secret=secret123";
        when(request.getBody()).thenReturn(Optional.of(formData));
        Map<String, String> headers = new HashMap<>();
        headers.put("content-type", "application/x-www-form-urlencoded");
        when(request.getHeaders()).thenReturn(headers);
        
        // Act
        HttpResponseMessage result = function.run(request, context);
        
        // Assert
        verify(responseBuilder).body(argThat(body -> {
            String jsonBody = (String) body;
            return jsonBody.contains("\"error\":\"unsupported_grant_type\"") &&
                   jsonBody.contains("\"error_description\":\"Grant type 'authorization_code' is not supported\"");
        }));
        verify(request).createResponseBuilder(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(clientCredentialsService);
    }
    
    @Test
    void testMissingGrantType() {
        // Arrange
        String formData = "client_id=test-client&client_secret=secret123";
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
                   jsonBody.contains("\"error_description\":\"Failed to parse request parameters\"");
        }));
        verify(request).createResponseBuilder(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(clientCredentialsService);
    }
    
    @Test
    void testMissingClientCredentials() {
        // Arrange
        String formData = "grant_type=client_credentials&scope=read";
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
                   jsonBody.contains("\"error_description\":\"Failed to parse request parameters\"");
        }));
        verify(request).createResponseBuilder(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(clientCredentialsService);
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
                   jsonBody.contains("\"error_description\":\"Failed to parse request parameters\"");
        }));
        verify(request).createResponseBuilder(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(clientCredentialsService);
    }
    
    @Test
    void testInvalidBasicAuthFormat() {
        // Arrange
        Map<String, String> headers = new HashMap<>();
        headers.put("authorization", "Basic invalid-base64!!!");
        headers.put("content-type", "application/x-www-form-urlencoded");
        when(request.getHeaders()).thenReturn(headers);
        
        String formData = "grant_type=client_credentials";
        when(request.getBody()).thenReturn(Optional.of(formData));
        
        // Act
        HttpResponseMessage result = function.run(request, context);
        
        // Assert
        verify(responseBuilder).body(argThat(body -> {
            String jsonBody = (String) body;
            return jsonBody.contains("\"error\":\"invalid_request\"") &&
                   jsonBody.contains("\"error_description\":\"Failed to parse request parameters\"");
        }));
        verify(request).createResponseBuilder(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(clientCredentialsService);
    }
    
    @Test
    void testResponseHeaders() {
        // Arrange
        String formData = "grant_type=client_credentials&client_id=test-client&client_secret=secret123";
        when(request.getBody()).thenReturn(Optional.of(formData));
        Map<String, String> headers = new HashMap<>();
        headers.put("content-type", "application/x-www-form-urlencoded");
        when(request.getHeaders()).thenReturn(headers);
        
        TokenResponse tokenResponse = new TokenResponse("token", "Bearer", 3600, "read", System.currentTimeMillis() / 1000);
        when(clientCredentialsService.authenticate(any(TokenRequest.class))).thenReturn(tokenResponse);
        
        // Act
        HttpResponseMessage result = function.run(request, context);
        
        // Assert - verify required OAuth2 response headers
        verify(responseBuilder).header("Content-Type", "application/json");
        verify(responseBuilder).header("Cache-Control", "no-store");
        verify(responseBuilder).header("Pragma", "no-cache");
    }
}




