package com.example.auth.domain.port;

import com.example.auth.domain.model.User;

import java.util.Map;
import java.util.Optional;

/**
 * Port interface for user data access following hexagonal architecture principles.
 * This interface defines the contract for user repository implementations
 * without coupling the domain layer to any specific persistence technology.
 */
public interface UserRepository {

    /**
     * Finds a user by username.
     *
     * @param username the username to search for (case-sensitive)
     * @return an Optional containing the user if found, empty otherwise
     * @throws IllegalArgumentException if username is null or empty
     */
    Optional<User> findByUsername(String username);

    /**
     * Retrieves all users from the repository.
     * This method is primarily used for administrative purposes and caching.
     *
     * @return a map of username to User objects
     * @throws RuntimeException if there's an error accessing the data store
     */
    Map<String, User> getAllUsers();

    /**
     * Checks if a user exists with the given username.
     *
     * @param username the username to check
     * @return true if a user exists with the given username
     */
    default boolean userExists(String username) {
        return findByUsername(username).isPresent();
    }

    /**
     * Gets the total number of users in the repository.
     *
     * @return the total user count
     */
    default long getUserCount() {
        return getAllUsers().size();
    }

    /**
     * Checks if the repository is healthy and accessible.
     * This can be used for health checks and monitoring.
     *
     * @return true if the repository is accessible and functional
     */
    default boolean isHealthy() {
        try {
            getUserCount();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
} 