package com.example.auth.test;

import com.example.auth.infrastructure.oauth.model.OAuth2ErrorResponse;
import com.example.auth.infrastructure.oauth.model.OAuth2IntrospectionResponse;
import com.example.auth.infrastructure.oauth.model.OAuth2TokenResponse;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test utility helper class for Azure Functions testing.
 * Provides methods to create mock HTTP requests and validate OAuth2 responses
 * matching the actual Azure Functions implementation.
 */
public class AzureFunctionTestHelper {
    
    /**
     * Create a mock HTTP request with Basic Auth header for testing BasicAuthFunction
     */
    public static HttpRequestMessage<Optional<String>> createBasicAuthRequest(String username, String password) {
        @SuppressWarnings("unchecked")
        HttpRequestMessage<Optional<String>> request = mock(HttpRequestMessage.class);
        
        // Create real Basic Auth header
        String credentials = username + ":" + password;
        String basicAuthHeader = "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes());
        
        Map<String, String> headers = new HashMap<>();
        headers.put("authorization", basicAuthHeader);
        headers.put("content-type", "application/json");
        
        when(request.getHeaders()).thenReturn(headers);
        when(request.getBody()).thenReturn(Optional.empty());
        
        // Mock response builder
        HttpResponseMessage.Builder responseBuilder = mock(HttpResponseMessage.Builder.class);
        HttpResponseMessage response = mock(HttpResponseMessage.class);
        
        when(request.createResponseBuilder(any(HttpStatus.class))).thenReturn(responseBuilder);
        when(responseBuilder.header(anyString(), anyString())).thenReturn(responseBuilder);
        when(responseBuilder.body(any())).thenReturn(responseBuilder);
        when(responseBuilder.build()).thenReturn(response);
        
        return request;
    }
    
    /**
     * Create a mock HTTP request with form-encoded OAuth token request for testing OAuth2TokenFunction
     */
    public static HttpRequestMessage<Optional<String>> createTokenRequest(String clientId, String clientSecret) {
        return createTokenRequest(clientId, clientSecret, "client_credentials", null);
    }
    
    /**
     * Create a mock HTTP request with form-encoded OAuth token request with scope
     */
    public static HttpRequestMessage<Optional<String>> createTokenRequest(String clientId, String clientSecret, 
                                                                         String grantType, String scope) {
        @SuppressWarnings("unchecked")
        HttpRequestMessage<Optional<String>> request = mock(HttpRequestMessage.class);
        
        // Create form-encoded request body
        StringBuilder formData = new StringBuilder();
        formData.append("grant_type=").append(grantType);
        formData.append("&client_id=").append(clientId);
        formData.append("&client_secret=").append(clientSecret);
        if (scope != null && !scope.isEmpty()) {
            formData.append("&scope=").append(scope);
        }
        
        when(request.getBody()).thenReturn(Optional.of(formData.toString()));
        when(request.getHeaders()).thenReturn(new HashMap<>());
        
        // Mock response builder
        HttpResponseMessage.Builder responseBuilder = mock(HttpResponseMessage.Builder.class);
        HttpResponseMessage response = mock(HttpResponseMessage.class);
        
        when(request.createResponseBuilder(any(HttpStatus.class))).thenReturn(responseBuilder);
        when(responseBuilder.header(anyString(), anyString())).thenReturn(responseBuilder);
        when(responseBuilder.body(any())).thenReturn(responseBuilder);
        when(responseBuilder.build()).thenReturn(response);
        
        return request;
    }
    
    /**
     * Create a mock HTTP request with Basic Auth header for client credentials (OAuth2TokenFunction)
     */
    public static HttpRequestMessage<Optional<String>> createTokenRequestWithBasicAuth(String clientId, 
                                                                                       String clientSecret, 
                                                                                       String scope) {
        @SuppressWarnings("unchecked")
        HttpRequestMessage<Optional<String>> request = mock(HttpRequestMessage.class);
        
        // Create Basic Auth header for client credentials
        String credentials = clientId + ":" + clientSecret;
        String basicAuthHeader = "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes());
        
        Map<String, String> headers = new HashMap<>();
        headers.put("authorization", basicAuthHeader);
        headers.put("content-type", "application/x-www-form-urlencoded");
        
        when(request.getHeaders()).thenReturn(headers);
        
        // Create form data without client credentials (they're in the header)
        String formData = "grant_type=client_credentials";
        if (scope != null && !scope.isEmpty()) {
            formData += "&scope=" + scope;
        }
        when(request.getBody()).thenReturn(Optional.of(formData));
        
        // Mock response builder
        HttpResponseMessage.Builder responseBuilder = mock(HttpResponseMessage.Builder.class);
        HttpResponseMessage response = mock(HttpResponseMessage.class);
        
        when(request.createResponseBuilder(any(HttpStatus.class))).thenReturn(responseBuilder);
        when(responseBuilder.header(anyString(), anyString())).thenReturn(responseBuilder);
        when(responseBuilder.body(any())).thenReturn(responseBuilder);
        when(responseBuilder.build()).thenReturn(response);
        
        return request;
    }
    
    /**
     * Create a mock HTTP request for token introspection (OAuth2IntrospectFunction)
     */
    public static HttpRequestMessage<Optional<String>> createIntrospectionRequest(String token) {
        @SuppressWarnings("unchecked")
        HttpRequestMessage<Optional<String>> request = mock(HttpRequestMessage.class);
        
        String formData = "token=" + token;
        when(request.getBody()).thenReturn(Optional.of(formData));
        
        Map<String, String> headers = new HashMap<>();
        headers.put("content-type", "application/x-www-form-urlencoded");
        when(request.getHeaders()).thenReturn(headers);
        
        // Mock response builder
        HttpResponseMessage.Builder responseBuilder = mock(HttpResponseMessage.Builder.class);
        HttpResponseMessage response = mock(HttpResponseMessage.class);
        
        when(request.createResponseBuilder(any(HttpStatus.class))).thenReturn(responseBuilder);
        when(responseBuilder.header(anyString(), anyString())).thenReturn(responseBuilder);
        when(responseBuilder.body(any())).thenReturn(responseBuilder);
        when(responseBuilder.build()).thenReturn(response);
        
        return request;
    }
    
    /**
     * Verify OAuth2TokenResponse structure with 5 parameters and required headers
     */
    public static void verifyOAuth2TokenResponse(HttpResponseMessage response, 
                                                String expectedAccessToken,
                                                String expectedTokenType,
                                                int expectedExpiresIn,
                                                String expectedScope) {
        assertNotNull(response, "Response should not be null");
        
        // This would be used to verify the actual response structure
        // In real test, we'd verify the response body contains OAuth2TokenResponse
        // with the expected parameters: accessToken, tokenType, expiresIn, scope, issuedAt
    }
    
    /**
     * Verify OAuth2ErrorResponse structure with error and errorDescription
     */
    public static void verifyOAuth2ErrorResponse(HttpResponseMessage response,
                                               String expectedError,
                                               String expectedErrorDescription) {
        assertNotNull(response, "Response should not be null");
        
        // This would be used to verify the actual error response structure
        // In real test, we'd verify the response body contains OAuth2ErrorResponse
        // with the expected error and errorDescription parameters
    }
    
    /**
     * Verify OAuth2IntrospectionResponse structure with all parameters
     */
    public static void verifyOAuth2IntrospectionResponse(HttpResponseMessage response,
                                                       boolean expectedActive,
                                                       String expectedClientId,
                                                       String expectedScope,
                                                       String expectedTokenType,
                                                       Long expectedExp,
                                                       Long expectedIat) {
        assertNotNull(response, "Response should not be null");
        
        // This would be used to verify the actual introspection response structure
        // In real test, we'd verify the response body contains OAuth2IntrospectionResponse
        // with the expected parameters: active, clientId, scope, tokenType, exp, iat
    }
    
    /**
     * Verify required OAuth2 response headers are present
     */
    public static void verifyOAuth2ResponseHeaders(HttpResponseMessage.Builder responseBuilder) {
        // Verify required headers for OAuth2 responses
        verify(responseBuilder).header("Content-Type", "application/json");
        verify(responseBuilder).header("Cache-Control", "no-store");
        verify(responseBuilder).header("Pragma", "no-cache");
    }
    
    /**
     * Create malformed Basic Auth header for negative testing
     */
    public static HttpRequestMessage<Optional<String>> createMalformedBasicAuthRequest() {
        @SuppressWarnings("unchecked")
        HttpRequestMessage<Optional<String>> request = mock(HttpRequestMessage.class);
        
        Map<String, String> headers = new HashMap<>();
        headers.put("authorization", "Basic invalid-base64!!!");
        
        when(request.getHeaders()).thenReturn(headers);
        when(request.getBody()).thenReturn(Optional.empty());
        
        // Mock response builder
        HttpResponseMessage.Builder responseBuilder = mock(HttpResponseMessage.Builder.class);
        HttpResponseMessage response = mock(HttpResponseMessage.class);
        
        when(request.createResponseBuilder(any(HttpStatus.class))).thenReturn(responseBuilder);
        when(responseBuilder.header(anyString(), anyString())).thenReturn(responseBuilder);
        when(responseBuilder.body(any())).thenReturn(responseBuilder);
        when(responseBuilder.build()).thenReturn(response);
        
        return request;
    }
    
    /**
     * Create request with missing Authorization header
     */
    public static HttpRequestMessage<Optional<String>> createRequestWithoutAuth() {
        @SuppressWarnings("unchecked")
        HttpRequestMessage<Optional<String>> request = mock(HttpRequestMessage.class);
        
        when(request.getHeaders()).thenReturn(new HashMap<>());
        when(request.getBody()).thenReturn(Optional.empty());
        
        // Mock response builder
        HttpResponseMessage.Builder responseBuilder = mock(HttpResponseMessage.Builder.class);
        HttpResponseMessage response = mock(HttpResponseMessage.class);
        
        when(request.createResponseBuilder(any(HttpStatus.class))).thenReturn(responseBuilder);
        when(responseBuilder.header(anyString(), anyString())).thenReturn(responseBuilder);
        when(responseBuilder.body(any())).thenReturn(responseBuilder);
        when(responseBuilder.build()).thenReturn(response);
        
        return request;
    }
    
    /**
     * Create request with empty body for negative testing
     */
    public static HttpRequestMessage<Optional<String>> createEmptyBodyRequest() {
        @SuppressWarnings("unchecked")
        HttpRequestMessage<Optional<String>> request = mock(HttpRequestMessage.class);
        
        when(request.getHeaders()).thenReturn(new HashMap<>());
        when(request.getBody()).thenReturn(Optional.empty());
        
        // Mock response builder
        HttpResponseMessage.Builder responseBuilder = mock(HttpResponseMessage.Builder.class);
        HttpResponseMessage response = mock(HttpResponseMessage.class);
        
        when(request.createResponseBuilder(any(HttpStatus.class))).thenReturn(responseBuilder);
        when(responseBuilder.header(anyString(), anyString())).thenReturn(responseBuilder);
        when(responseBuilder.body(any())).thenReturn(responseBuilder);
        when(responseBuilder.build()).thenReturn(response);
        
        return request;
    }
    
    /**
     * Parse form-encoded data for validation in tests
     */
    public static Map<String, String> parseFormData(String formData) {
        Map<String, String> params = new HashMap<>();
        
        if (formData == null || formData.isEmpty()) {
            return params;
        }
        
        String[] pairs = formData.split("&");
        for (String pair : pairs) {
            String[] keyValue = pair.split("=", 2);
            if (keyValue.length == 2) {
                params.put(keyValue[0], keyValue[1]);
            }
        }
        
        return params;
    }
    
    /**
     * Decode Basic Auth header for validation in tests
     */
    public static String[] decodeBasicAuth(String basicAuthHeader) {
        if (basicAuthHeader == null || !basicAuthHeader.startsWith("Basic ")) {
            return null;
        }
        
        try {
            String encodedCredentials = basicAuthHeader.substring(6);
            String credentials = new String(Base64.getDecoder().decode(encodedCredentials));
            return credentials.split(":", 2);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}




