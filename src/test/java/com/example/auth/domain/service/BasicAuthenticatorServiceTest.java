package com.example.auth.domain.service;

import com.example.auth.domain.model.AuthenticationRequest;
import com.example.auth.domain.model.AuthenticationResult;
import com.example.auth.domain.model.User;
import com.example.auth.domain.model.UserStatus;
import com.example.auth.domain.port.UserRepository;
import com.example.auth.domain.util.PasswordHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BasicAuthenticatorServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordHasher passwordHasher;

    private BasicAuthenticatorService authenticatorService;

    // Valid hash format for testing
    private static final String VALID_HASH = "$argon2id$v=19$m=65536,t=3,p=1$c29tZXNhbHQ$aGFzaHZhbHVl";

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        authenticatorService = new BasicAuthenticatorService(userRepository, passwordHasher);
    }

    @Test
    void shouldAuthenticateValidUser() {
        User user = new User("alice", VALID_HASH, UserStatus.ACTIVE, List.of("user"));
        AuthenticationRequest request = new AuthenticationRequest("alice", "password");

        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(passwordHasher.verifyPassword("password", VALID_HASH)).thenReturn(true);

        AuthenticationResult result = authenticatorService.authenticate(request);

        assertTrue(result.isAllowed());
        assertEquals("alice", result.getUsername());
    }

    @Test
    void shouldRejectInvalidPassword() {
        User user = new User("alice", VALID_HASH, UserStatus.ACTIVE, List.of("user"));
        AuthenticationRequest request = new AuthenticationRequest("alice", "wrong");

        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(passwordHasher.verifyPassword("wrong", VALID_HASH)).thenReturn(false);

        AuthenticationResult result = authenticatorService.authenticate(request);

        assertFalse(result.isAllowed());
        assertEquals("Invalid credentials", result.getReason());
    }

    @Test
    void shouldRejectNonExistentUser() {
        AuthenticationRequest request = new AuthenticationRequest("unknown", "password");

        when(userRepository.findByUsername("unknown")).thenReturn(Optional.empty());

        AuthenticationResult result = authenticatorService.authenticate(request);

        assertFalse(result.isAllowed());
        assertEquals("Invalid credentials", result.getReason());
    }

    @Test
    void shouldRejectDisabledUser() {
        User user = new User("bob", VALID_HASH, UserStatus.DISABLED, List.of("user"));
        AuthenticationRequest request = new AuthenticationRequest("bob", "password");

        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(user));

        AuthenticationResult result = authenticatorService.authenticate(request);

        assertFalse(result.isAllowed());
        assertEquals("Account disabled", result.getReason());
    }
} 