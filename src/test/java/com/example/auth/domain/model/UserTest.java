package com.example.auth.domain.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("User Domain Model Tests")
class UserTest {

    private static final String VALID_USERNAME = "testuser";
    private static final String VALID_PASSWORD_HASH = "$argon2id$v=19$m=65536,t=3,p=1$salt$hash";
    private static final UserStatus ACTIVE_STATUS = UserStatus.ACTIVE;
    private static final List<String> TEST_ROLES = Arrays.asList("user", "admin");

    @Test
    @DisplayName("Should create user with all parameters")
    void shouldCreateUserWithAllParameters() {
        User user = new User(VALID_USERNAME, VALID_PASSWORD_HASH, ACTIVE_STATUS, TEST_ROLES);

        assertEquals(VALID_USERNAME, user.getUsername());
        assertEquals(VALID_PASSWORD_HASH, user.getPasswordHash());
        assertEquals(ACTIVE_STATUS, user.getStatus());
        assertEquals(TEST_ROLES, user.getRoles());
        assertTrue(user.isActive());
    }

    @Test
    @DisplayName("Should create user with empty roles when roles list is null")
    void shouldCreateUserWithEmptyRolesWhenRolesListIsNull() {
        User user = new User(VALID_USERNAME, VALID_PASSWORD_HASH, ACTIVE_STATUS, null);

        assertTrue(user.getRoles().isEmpty());
    }

    @Test
    @DisplayName("Should create user with three-parameter constructor")
    void shouldCreateUserWithThreeParameterConstructor() {
        User user = new User(VALID_USERNAME, VALID_PASSWORD_HASH, ACTIVE_STATUS);

        assertEquals(VALID_USERNAME, user.getUsername());
        assertEquals(VALID_PASSWORD_HASH, user.getPasswordHash());
        assertEquals(ACTIVE_STATUS, user.getStatus());
        assertTrue(user.getRoles().isEmpty());
    }

    @Test
    @DisplayName("Should throw exception when username is null")
    void shouldThrowExceptionWhenUsernameIsNull() {
        assertThrows(IllegalArgumentException.class, 
            () -> new User(null, VALID_PASSWORD_HASH, ACTIVE_STATUS));
    }

    @Test
    @DisplayName("Should throw exception when username is empty")
    void shouldThrowExceptionWhenUsernameIsEmpty() {
        assertThrows(IllegalArgumentException.class, 
            () -> new User("", VALID_PASSWORD_HASH, ACTIVE_STATUS));
    }

    @Test
    @DisplayName("Should throw exception when username is too long")
    void shouldThrowExceptionWhenUsernameIsTooLong() {
        String longUsername = "a".repeat(256);
        assertThrows(IllegalArgumentException.class, 
            () -> new User(longUsername, VALID_PASSWORD_HASH, ACTIVE_STATUS));
    }

    @Test
    @DisplayName("Should throw exception when username contains invalid characters")
    void shouldThrowExceptionWhenUsernameContainsInvalidCharacters() {
        assertThrows(IllegalArgumentException.class, 
            () -> new User("user@domain", VALID_PASSWORD_HASH, ACTIVE_STATUS));
    }

    @Test
    @DisplayName("Should accept valid username characters")
    void shouldAcceptValidUsernameCharacters() {
        assertDoesNotThrow(() -> new User("user.name-123_test", VALID_PASSWORD_HASH, ACTIVE_STATUS));
    }

    @Test
    @DisplayName("Should throw exception when password hash is null")
    void shouldThrowExceptionWhenPasswordHashIsNull() {
        assertThrows(IllegalArgumentException.class, 
            () -> new User(VALID_USERNAME, null, ACTIVE_STATUS));
    }

    @Test
    @DisplayName("Should throw exception when password hash is empty")
    void shouldThrowExceptionWhenPasswordHashIsEmpty() {
        assertThrows(IllegalArgumentException.class, 
            () -> new User(VALID_USERNAME, "", ACTIVE_STATUS));
    }

    @Test
    @DisplayName("Should throw exception when password hash has invalid format")
    void shouldThrowExceptionWhenPasswordHashHasInvalidFormat() {
        assertThrows(IllegalArgumentException.class, 
            () -> new User(VALID_USERNAME, "invalid-hash", ACTIVE_STATUS));
    }

    @Test
    @DisplayName("Should accept BCrypt hash format")
    void shouldAcceptBCryptHashFormat() {
        String bcryptHash = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";
        assertDoesNotThrow(() -> new User(VALID_USERNAME, bcryptHash, ACTIVE_STATUS));
    }

    @Test
    @DisplayName("Should throw exception when status is null")
    void shouldThrowExceptionWhenStatusIsNull() {
        assertThrows(NullPointerException.class, 
            () -> new User(VALID_USERNAME, VALID_PASSWORD_HASH, null));
    }

    @Test
    @DisplayName("Should return false for isActive when status is DISABLED")
    void shouldReturnFalseForIsActiveWhenStatusIsDisabled() {
        User user = new User(VALID_USERNAME, VALID_PASSWORD_HASH, UserStatus.DISABLED);
        
        assertFalse(user.isActive());
    }

    @Test
    @DisplayName("Should return true for hasRole when user has the role")
    void shouldReturnTrueForHasRoleWhenUserHasTheRole() {
        User user = new User(VALID_USERNAME, VALID_PASSWORD_HASH, ACTIVE_STATUS, Arrays.asList("admin", "user"));
        
        assertTrue(user.hasRole("admin"));
        assertTrue(user.hasRole("user"));
    }

    @Test
    @DisplayName("Should return false for hasRole when user does not have the role")
    void shouldReturnFalseForHasRoleWhenUserDoesNotHaveTheRole() {
        User user = new User(VALID_USERNAME, VALID_PASSWORD_HASH, ACTIVE_STATUS, Arrays.asList("user"));
        
        assertFalse(user.hasRole("admin"));
    }

    @Test
    @DisplayName("Should create new user with updated status")
    void shouldCreateNewUserWithUpdatedStatus() {
        User originalUser = new User(VALID_USERNAME, VALID_PASSWORD_HASH, ACTIVE_STATUS, TEST_ROLES);
        User updatedUser = originalUser.withStatus(UserStatus.DISABLED);

        assertEquals(UserStatus.DISABLED, updatedUser.getStatus());
        assertEquals(ACTIVE_STATUS, originalUser.getStatus()); // Original unchanged
        assertEquals(originalUser.getUsername(), updatedUser.getUsername());
        assertEquals(originalUser.getPasswordHash(), updatedUser.getPasswordHash());
        assertEquals(originalUser.getRoles(), updatedUser.getRoles());
    }

    @Test
    @DisplayName("Should create new user with additional role")
    void shouldCreateNewUserWithAdditionalRole() {
        User originalUser = new User(VALID_USERNAME, VALID_PASSWORD_HASH, ACTIVE_STATUS, Arrays.asList("user"));
        User updatedUser = originalUser.withRole("admin");

        assertTrue(updatedUser.hasRole("admin"));
        assertTrue(updatedUser.hasRole("user"));
        assertFalse(originalUser.hasRole("admin")); // Original unchanged
    }

    @Test
    @DisplayName("Should not duplicate roles when adding existing role")
    void shouldNotDuplicateRolesWhenAddingExistingRole() {
        User originalUser = new User(VALID_USERNAME, VALID_PASSWORD_HASH, ACTIVE_STATUS, Arrays.asList("user"));
        User updatedUser = originalUser.withRole("user");

        assertEquals(1, updatedUser.getRoles().size());
        assertTrue(updatedUser.hasRole("user"));
    }

    @Test
    @DisplayName("Should return immutable roles list")
    void shouldReturnImmutableRolesList() {
        User user = new User(VALID_USERNAME, VALID_PASSWORD_HASH, ACTIVE_STATUS, TEST_ROLES);
        List<String> roles = user.getRoles();

        assertThrows(UnsupportedOperationException.class, () -> roles.add("newrole"));
    }

    @Test
    @DisplayName("Should implement equals correctly")
    void shouldImplementEqualsCorrectly() {
        User user1 = new User(VALID_USERNAME, VALID_PASSWORD_HASH, ACTIVE_STATUS, TEST_ROLES);
        User user2 = new User(VALID_USERNAME, VALID_PASSWORD_HASH, ACTIVE_STATUS, TEST_ROLES);
        User user3 = new User("different", VALID_PASSWORD_HASH, ACTIVE_STATUS, TEST_ROLES);

        assertEquals(user1, user2);
        assertNotEquals(user1, user3);
        assertNotEquals(user1, null);
        assertNotEquals(user1, "not a user");
    }

    @Test
    @DisplayName("Should implement hashCode correctly")
    void shouldImplementHashCodeCorrectly() {
        User user1 = new User(VALID_USERNAME, VALID_PASSWORD_HASH, ACTIVE_STATUS, TEST_ROLES);
        User user2 = new User(VALID_USERNAME, VALID_PASSWORD_HASH, ACTIVE_STATUS, TEST_ROLES);

        assertEquals(user1.hashCode(), user2.hashCode());
    }

    @Test
    @DisplayName("Should not include password hash in toString")
    void shouldNotIncludePasswordHashInToString() {
        User user = new User(VALID_USERNAME, VALID_PASSWORD_HASH, ACTIVE_STATUS, TEST_ROLES);
        String toString = user.toString();

        assertFalse(toString.contains(VALID_PASSWORD_HASH));
        assertTrue(toString.contains(VALID_USERNAME));
        assertTrue(toString.contains(ACTIVE_STATUS.toString()));
    }
} 