package com.example.auth.domain.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PasswordHasherTest {

    private PasswordHasher passwordHasher;

    @BeforeEach
    void setUp() {
        passwordHasher = new PasswordHasher();
    }

    @Test
    void shouldHashPassword() {
        String hash = passwordHasher.hashPassword("test123");
        assertNotNull(hash);
        assertTrue(hash.startsWith("$argon2id$"));
    }

    @Test
    void shouldVerifyCorrectPassword() {
        String password = "test123";
        String hash = passwordHasher.hashPassword(password);
        assertTrue(passwordHasher.verifyPassword(password, hash));
    }

    @Test
    void shouldRejectIncorrectPassword() {
        String hash = passwordHasher.hashPassword("test123");
        assertFalse(passwordHasher.verifyPassword("wrong", hash));
    }

    @Test
    void shouldThrowExceptionForNullPassword() {
        assertThrows(IllegalArgumentException.class, 
            () -> passwordHasher.hashPassword(null));
    }
} 