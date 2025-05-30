package com.example.auth.domain.service;

import com.example.auth.domain.model.AuthenticationRequest;
import com.example.auth.domain.model.AuthenticationResult;
import com.example.auth.domain.model.User;
import com.example.auth.domain.port.UserRepository;
import com.example.auth.domain.util.PasswordHasher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Core implementation of the authentication service using Basic Authentication.
 */
@Service
public class BasicAuthenticatorService implements AuthenticatorService {

    private static final Logger log = LoggerFactory.getLogger(BasicAuthenticatorService.class);

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final AtomicLong authenticationAttempts = new AtomicLong(0);
    private final AtomicLong successfulAuthentications = new AtomicLong(0);
    private final AtomicLong failedAuthentications = new AtomicLong(0);

    public BasicAuthenticatorService(UserRepository userRepository, PasswordHasher passwordHasher) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        log.info("BasicAuthenticatorService initialized");
    }

    @Override
    public AuthenticationResult authenticate(AuthenticationRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Authentication request cannot be null");
        }

        long startTime = System.currentTimeMillis();
        authenticationAttempts.incrementAndGet();

        String username = request.getUsername();
        String password = request.getPassword();

        try {
            log.debug("Processing authentication request for user: {}", maskUsername(username));

            // User lookup
            Optional<User> userOptional = findUser(username);
            if (userOptional.isEmpty()) {
                return handleUserNotFound(username, startTime);
            }

            User user = userOptional.get();

            // User status validation
            if (!user.isActive()) {
                return handleInactiveUser(user, startTime);
            }

            // Password verification
            boolean passwordValid = verifyPassword(password, user.getPasswordHash());
            
            if (passwordValid) {
                return handleSuccessfulAuthentication(user, startTime);
            } else {
                return handleInvalidPassword(user, startTime);
            }

        } catch (Exception e) {
            log.error("Unexpected error during authentication for user: {}", maskUsername(username), e);
            failedAuthentications.incrementAndGet();
            
            return AuthenticationResult.failure(username, "Internal authentication error")
                    .withMetadata("duration", System.currentTimeMillis() - startTime)
                    .withMetadata("error", "unexpected_error");
        }
    }

    private Optional<User> findUser(String username) {
        try {
            return userRepository.findByUsername(username);
        } catch (Exception e) {
            log.warn("Error looking up user: {}", maskUsername(username), e);
            return Optional.empty();
        }
    }

    private boolean verifyPassword(String password, String storedHash) {
        try {
            return passwordHasher.verifyPassword(password, storedHash);
        } catch (Exception e) {
            log.warn("Password verification error", e);
            return false;
        }
    }

    private AuthenticationResult handleSuccessfulAuthentication(User user, long startTime) {
        successfulAuthentications.incrementAndGet();
        long duration = System.currentTimeMillis() - startTime;
        
        log.info("Authentication successful for user: {} in {}ms", 
                maskUsername(user.getUsername()), duration);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("duration", duration);
        metadata.put("userStatus", user.getStatus().name());
        metadata.put("authMethod", "basic");

        return AuthenticationResult.success(user.getUsername(), metadata);
    }

    private AuthenticationResult handleUserNotFound(String username, long startTime) {
        failedAuthentications.incrementAndGet();
        performDummyPasswordVerification();
        
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("duration", System.currentTimeMillis() - startTime);
        metadata.put("reason", "user_not_found");

        return AuthenticationResult.failure(username, "Invalid credentials", metadata);
    }

    private AuthenticationResult handleInactiveUser(User user, long startTime) {
        failedAuthentications.incrementAndGet();
        performDummyPasswordVerification();
        
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("duration", System.currentTimeMillis() - startTime);
        metadata.put("reason", "account_disabled");

        return AuthenticationResult.failure(user.getUsername(), "Account disabled", metadata);
    }

    private AuthenticationResult handleInvalidPassword(User user, long startTime) {
        failedAuthentications.incrementAndGet();
        
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("duration", System.currentTimeMillis() - startTime);
        metadata.put("reason", "invalid_password");

        return AuthenticationResult.failure(user.getUsername(), "Invalid credentials", metadata);
    }

    private void performDummyPasswordVerification() {
        try {
            String dummyHash = "$argon2id$v=19$m=65536,t=3,p=1$c29tZXNhbHQ$aGFzaHZhbHVl";
            passwordHasher.verifyPassword("dummy", dummyHash);
        } catch (Exception e) {
            // Ignore exceptions in dummy verification
        }
    }

    private String maskUsername(String username) {
        if (username == null || username.length() <= 2) {
            return "***";
        }
        
        return username.charAt(0) + "*".repeat(username.length() - 2) + username.charAt(username.length() - 1);
    }

    public Map<String, Long> getMetrics() {
        Map<String, Long> metrics = new HashMap<>();
        metrics.put("total_attempts", authenticationAttempts.get());
        metrics.put("successful_attempts", successfulAuthentications.get());
        metrics.put("failed_attempts", failedAuthentications.get());
        return metrics;
    }
} 