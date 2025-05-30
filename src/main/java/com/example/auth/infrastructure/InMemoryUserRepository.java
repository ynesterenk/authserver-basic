package com.example.auth.infrastructure;

import com.example.auth.domain.model.User;
import com.example.auth.domain.model.UserStatus;
import com.example.auth.domain.port.UserRepository;
import com.example.auth.domain.util.PasswordHasher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
@ConditionalOnMissingBean(name = "userRepository")
public class InMemoryUserRepository implements UserRepository {

    private static final Logger log = LoggerFactory.getLogger(InMemoryUserRepository.class);
    private final Map<String, User> users = new ConcurrentHashMap<>();
    private final PasswordHasher passwordHasher;

    public InMemoryUserRepository(PasswordHasher passwordHasher) {
        this.passwordHasher = passwordHasher;
        initializeTestUsers();
        log.info("InMemoryUserRepository initialized with {} test users", users.size());
    }

    @Override
    public Optional<User> findByUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username cannot be null or empty");
        }
        return Optional.ofNullable(users.get(username.trim()));
    }

    @Override
    public Map<String, User> getAllUsers() {
        return new HashMap<>(users);
    }

    public User addUser(String username, String password, UserStatus status, List<String> roles) {
        String hashedPassword = passwordHasher.hashPassword(password);
        User user = new User(username, hashedPassword, status, roles);
        users.put(username, user);
        return user;
    }

    private void initializeTestUsers() {
        try {
            addUser("alice", "password123", UserStatus.ACTIVE, List.of("user"));
            addUser("admin", "admin123", UserStatus.ACTIVE, List.of("admin", "user"));
            addUser("bob", "password456", UserStatus.DISABLED, List.of("user"));
            addUser("charlie", "charlie789", UserStatus.ACTIVE, List.of());
            log.info("Initialized test users");
        } catch (Exception e) {
            log.error("Failed to initialize test users", e);
        }
    }
} 