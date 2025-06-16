package com.example.auth.infrastructure.azure.functions;

import com.example.auth.domain.model.User;
import com.example.auth.domain.model.UserStatus;
import com.example.auth.domain.port.UserRepository;
import com.example.auth.domain.service.AuthenticatorService;
import com.example.auth.domain.model.AuthenticationResult;
import com.example.auth.domain.model.AuthenticationRequest;
import com.example.auth.infrastructure.azure.config.AzureFunctionConfiguration;
import com.example.auth.infrastructure.oauth.model.OAuth2ErrorResponse;
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

import java.util.*;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Test class for BasicAuthFunction - tests the actual Azure Function implementation
 * that handles POST /api/auth/validate with Basic Auth header parsing.
 * 
 * Tests the real implementation against actual domain service method signatures.
 */
@ExtendWith(MockitoExtension.class)
class BasicAuthFunctionTest {
    
    @Mock
    private AuthenticatorService authenticatorService;
    
    @Mock
    private UserRepository userRepository;
    
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
    private AzureFunctionConfiguration.AzureFunctionMetrics metrics;
    
    @InjectMocks
    private BasicAuthFunction function;
    
    @BeforeEach
    void setUp() {
        lenient().when(context.getLogger()).thenReturn(logger);
        lenient().when(request.createResponseBuilder(any(HttpStatus.class))).thenReturn(responseBuilder);
        lenient().when(responseBuilder.header(anyString(), anyString())).thenReturn(responseBuilder);
        lenient().when(responseBuilder.body(any())).thenReturn(responseBuilder);
        lenient().when(responseBuilder.build()).thenReturn(response);
    }
    
    @Test
    void testValidBasicAuthentication() {
        // Arrange
        String basicAuthHeader = "Basic " + Base64.getEncoder().encodeToString("testuser:password123".getBytes());
        Map<String, String> headers = new HashMap<>();
        headers.put("authorization", basicAuthHeader);
        
        when(request.getHeaders()).thenReturn(headers);
          // Mock real AuthenticationResult methods: isAllowed() and getReason()
        AuthenticationResult authResult = mock(AuthenticationResult.class);
        when(authResult.isAllowed()).thenReturn(true);
        lenient().when(authResult.getReason()).thenReturn("Authentication successful");
        when(authenticatorService.authenticate(new AuthenticationRequest("testuser", "password123"))).thenReturn(authResult);
        
        // Mock real User constructor: new User(username, passwordHash, status, roles)
        User user = new User("testuser", "$2a$10$hashedPassword", UserStatus.ACTIVE, Arrays.asList("user", "read"));
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
        
        // Act
        HttpResponseMessage result = function.run(request, context);
          // Assert
        verify(authenticatorService).authenticate(new AuthenticationRequest("testuser", "password123"));
        verify(userRepository).findByUsername("testuser");
        verify(responseBuilder).body(argThat(body -> {
            String bodyString = (String) body;
            return bodyString.contains("\"authenticated\":true") &&
                   bodyString.contains("\"message\":\"Authentication successful\"") &&
                   bodyString.contains("\"roles\":[\"user\",\"read\"]");
        }));
        verify(request).createResponseBuilder(HttpStatus.OK);
    }
    
    @Test
    void testInvalidCredentials() {
        // Arrange
        String basicAuthHeader = "Basic " + Base64.getEncoder().encodeToString("testuser:wrongpassword".getBytes());
        Map<String, String> headers = new HashMap<>();
        headers.put("authorization", basicAuthHeader);
        
        when(request.getHeaders()).thenReturn(headers);
        
        // Mock authentication failure with real AuthenticationResult methods
        AuthenticationResult authResult = mock(AuthenticationResult.class);
        when(authResult.isAllowed()).thenReturn(false);
        when(authResult.getReason()).thenReturn("Invalid credentials");
        when(authenticatorService.authenticate(new AuthenticationRequest("testuser", "wrongpassword"))).thenReturn(authResult);
        
        // Act
        HttpResponseMessage result = function.run(request, context);
        
        // Assert
        verify(authenticatorService).authenticate(new AuthenticationRequest("testuser", "wrongpassword"));
        verify(responseBuilder).body(argThat(body -> {
            String bodyString = (String) body;
            return bodyString.contains("\"authenticated\":false") &&
                   bodyString.contains("\"message\":\"Invalid credentials\"");
        }));
        verify(request).createResponseBuilder(HttpStatus.UNAUTHORIZED);
    }
    
    @Test
    void testMissingAuthorizationHeader() {
        // Arrange
        Map<String, String> headers = new HashMap<>();
        when(request.getHeaders()).thenReturn(headers);
        
        // Act
        HttpResponseMessage result = function.run(request, context);
        
        // Assert
        verify(responseBuilder).body(argThat(body -> {
            String bodyString = (String) body;
            return bodyString.contains("\"authenticated\":false") &&
                   bodyString.contains("\"message\":\"Missing or invalid Authorization header\"");
        }));
        verify(request).createResponseBuilder(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(authenticatorService);
    }
    
    @Test
    void testInvalidBasicAuthFormat() {
        // Arrange
        Map<String, String> headers = new HashMap<>();
        headers.put("authorization", "Bearer invalidtoken");
        when(request.getHeaders()).thenReturn(headers);
        
        // Act
        HttpResponseMessage result = function.run(request, context);
        
        // Assert
        verify(responseBuilder).body(argThat(body -> {
            String bodyString = (String) body;
            return bodyString.contains("\"authenticated\":false") &&
                   bodyString.contains("\"message\":\"Missing or invalid Authorization header\"");
        }));
        verify(request).createResponseBuilder(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(authenticatorService);
    }
    
    @Test
    void testUserNotFound() {
        // Arrange
        String basicAuthHeader = "Basic " + Base64.getEncoder().encodeToString("nonexistent:password".getBytes());
        Map<String, String> headers = new HashMap<>();
        headers.put("authorization", basicAuthHeader);
        
        when(request.getHeaders()).thenReturn(headers);
        
        // Mock authentication success but user not found in repository
        AuthenticationResult authResult = mock(AuthenticationResult.class);
        when(authResult.isAllowed()).thenReturn(true);
        lenient().when(authResult.getReason()).thenReturn("Authentication successful");
        when(authenticatorService.authenticate(new AuthenticationRequest("nonexistent", "password"))).thenReturn(authResult);
        when(userRepository.findByUsername("nonexistent")).thenReturn(Optional.empty());
        
        // Act
        HttpResponseMessage result = function.run(request, context);
        
        // Assert
        verify(authenticatorService).authenticate(new AuthenticationRequest("nonexistent", "password"));
        verify(userRepository).findByUsername("nonexistent");
        verify(responseBuilder).body(argThat(body -> {
            String bodyString = (String) body;
            return bodyString.contains("\"authenticated\":true") &&
                   bodyString.contains("\"message\":\"Authentication successful\"") &&
                   bodyString.contains("\"roles\":[]");
        }));
        verify(request).createResponseBuilder(HttpStatus.OK);
    }
    
    @Test
    void testMalformedBase64Credentials() {
        // Arrange
        Map<String, String> headers = new HashMap<>();
        headers.put("authorization", "Basic invalidbase64!!!");
        when(request.getHeaders()).thenReturn(headers);
        
        // Act
        HttpResponseMessage result = function.run(request, context);
        
        // Assert
        verify(responseBuilder).body(argThat(body -> {
            String bodyString = (String) body;
            return bodyString.contains("\"authenticated\":false") &&
                   bodyString.contains("\"message\":\"Invalid Authorization header format\"");
        }));
        verify(request).createResponseBuilder(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(authenticatorService);
    }
}




