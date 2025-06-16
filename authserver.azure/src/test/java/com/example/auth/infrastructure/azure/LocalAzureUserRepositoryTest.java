package com.example.auth.infrastructure.azure;

import com.example.auth.domain.model.User;
import com.example.auth.domain.model.UserStatus;
import com.example.auth.domain.util.PasswordHasher;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Test class for LocalAzureUserRepository - tests the actual Azure implementation
 * with PasswordHasher constructor and file-based user storage.
 * 
 * Tests the real User constructor: new User(username, passwordHash, status, roles)
 * and UserStatus enum values (ACTIVE, DISABLED, not INACTIVE).
 */
@ExtendWith(MockitoExtension.class)
class LocalAzureUserRepositoryTest {
    
    @Mock
    private PasswordHasher passwordHasher;
    
    private LocalAzureUserRepository repository;
    
    // ObjectMapper can be static or instance, depending on needs.
    // For this test, a static one is fine.
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws IOException {
        // JSON structure should be a list of users as expected by LocalAzureUserRepository
        // Simplified to match reverted User domain model (username, password, status, roles only)
        String usersJsonList = """
            [
              {
                "username": "testuser",
                "password": "plaintext123",
                "status": "ACTIVE",
                "roles": ["user", "read"]
              },
              {
                "username": "adminuser", 
                "password": "adminpass456",
                "status": "ACTIVE",
                "roles": ["admin", "read", "write"]
              },
              {
                "username": "disableduser",
                "password": "disabled789",
                "status": "DISABLED",
                "roles": ["user"]
              }
            ]
            """;
        // Convert the JSON string to an InputStream
        InputStream usersInputStream = new ByteArrayInputStream(usersJsonList.getBytes(StandardCharsets.UTF_8));
        
        // Mock PasswordHasher to return different hashes for different passwords
        when(passwordHasher.hashPassword("plaintext123")).thenReturn("$2a$10$hashed_plaintext123");
        when(passwordHasher.hashPassword("adminpass456")).thenReturn("$2a$10$hashed_adminpass456");
        when(passwordHasher.hashPassword("disabled789")).thenReturn("$2a$10$hashed_disabled789");

        repository = new LocalAzureUserRepository(passwordHasher, usersInputStream);
    }
    
    @Test
    void testConstructorWithPasswordHasherAndInputStream() { // Renamed for clarity
        // Verify that constructor accepts PasswordHasher and InputStream
        assertNotNull(repository);
        // Verify PasswordHasher was used during initialization for each user
        verify(passwordHasher, times(1)).hashPassword("plaintext123");
        verify(passwordHasher, times(1)).hashPassword("adminpass456");
        verify(passwordHasher, times(1)).hashPassword("disabled789");
    }
    
    @Test
    void testFindByUsernameActiveUser() {
        // Act
        Optional<User> result = repository.findByUsername("testuser");
        
        // Assert
        assertTrue(result.isPresent());
        User user = result.get();
        
        // Verify correct User constructor usage
        assertEquals("testuser", user.getUsername());
        assertEquals("$2a$10$hashed_plaintext123", user.getPasswordHash());
        assertEquals(UserStatus.ACTIVE, user.getStatus());
        assertEquals(Arrays.asList("user", "read"), user.getRoles());
          // Verify PasswordHasher was called with plain text password
        verify(passwordHasher).hashPassword("plaintext123");
    }
    
    @Test
    void testFindByUsernameDisabledUser() {
        // Act
        Optional<User> result = repository.findByUsername("disableduser");
        
        // Assert
        assertTrue(result.isPresent());
        User user = result.get();
        
        // Verify UserStatus.DISABLED mapping (not INACTIVE)
        assertEquals("disableduser", user.getUsername());
        assertEquals("$2a$10$hashed_disabled789", user.getPasswordHash());
        assertEquals(UserStatus.DISABLED, user.getStatus());
        assertEquals(Arrays.asList("user"), user.getRoles());
        
        verify(passwordHasher).hashPassword("disabled789");
    }
    
    @Test
    void testFindByUsernameNotFound() {
        // Act
        Optional<User> result = repository.findByUsername("nonexistent");
        
        // Assert
        assertFalse(result.isPresent());
    }
    
    @Test
    void testGetAllUsers() {
        // Act
        Map<String, User> allUsersMap = repository.getAllUsers();
        
        // Assert
        assertEquals(3, allUsersMap.size());
        
        assertTrue(allUsersMap.containsKey("testuser"));
        assertTrue(allUsersMap.containsKey("adminuser"));
        assertTrue(allUsersMap.containsKey("disableduser"));
        
        // Verify Map<String, User> return type and content
        User testUser = allUsersMap.get("testuser");
        assertNotNull(testUser);
        assertEquals("testuser", testUser.getUsername());
        assertEquals(UserStatus.ACTIVE, testUser.getStatus());
        
        User adminUser = allUsersMap.get("adminuser");
        assertNotNull(adminUser);
        assertEquals("adminuser", adminUser.getUsername());
        assertEquals(Arrays.asList("admin", "read", "write"), adminUser.getRoles());
        
        User disabledUser = allUsersMap.get("disableduser");
        assertNotNull(disabledUser);
        assertEquals(UserStatus.DISABLED, disabledUser.getStatus());
    }
    
    @Test
    void testUserStatusMapping() {
        // Test all UserStatus enum values mapping from JSON
        Map<String, User> allUsersMap = repository.getAllUsers();
        
        // Verify ACTIVE status mapping
        User activeUser = allUsersMap.get("testuser");
        assertNotNull(activeUser);
        assertEquals(UserStatus.ACTIVE, activeUser.getStatus());
        
        // Verify DISABLED status mapping
        User disabledUser = allUsersMap.get("disableduser");
        assertNotNull(disabledUser);
        assertEquals(UserStatus.DISABLED, disabledUser.getStatus());
    }
    
    @Test
    void testPasswordHashingDuringLoad() {
        // Verify that all passwords were hashed using PasswordHasher
        verify(passwordHasher).hashPassword("plaintext123");
        verify(passwordHasher).hashPassword("adminpass456");
        verify(passwordHasher).hashPassword("disabled789");
        
        // Verify each user has the correctly hashed password
        Optional<User> testUser = repository.findByUsername("testuser");
        assertTrue(testUser.isPresent());
        assertEquals("$2a$10$hashed_plaintext123", testUser.get().getPasswordHash());
        
        Optional<User> adminUser = repository.findByUsername("adminuser");
        assertTrue(adminUser.isPresent());
        assertEquals("$2a$10$hashed_adminpass456", adminUser.get().getPasswordHash());
    }
    
    @Test
    void testRolesAsListOfStrings() {
        // Verify roles are loaded as List<String>, not Set or other collection
        Optional<User> adminUser = repository.findByUsername("adminuser");
        assertTrue(adminUser.isPresent());
        
        // Verify roles type and content
        assertEquals(Arrays.asList("admin", "read", "write"), adminUser.get().getRoles());
        assertTrue(adminUser.get().getRoles() instanceof java.util.List);
    }
    
    @Test
    void testFileNotFound() throws IOException {
        // FIXED: After interface compliance fix, null stream should throw IllegalArgumentException
        // instead of creating empty repository (fail-fast behavior)
        assertThrows(IllegalArgumentException.class, () -> {
            new LocalAzureUserRepository(passwordHasher, (InputStream) null);
        }, "Should throw IllegalArgumentException for null input stream");
    }
    
    @Test
    void testMalformedJsonFile() throws IOException {
        // Arrange - create malformed JSON input stream
        String malformedJson = "{ invalid json";
        InputStream malformedInputStream = new ByteArrayInputStream(malformedJson.getBytes(StandardCharsets.UTF_8));
        
        // Act & Assert - should handle malformed JSON gracefully
        assertThrows(IllegalStateException.class, () -> { // Changed from RuntimeException for more specificity if that's what's thrown
            new LocalAzureUserRepository(passwordHasher, malformedInputStream);
        });
    }
    
    @Test
    void testEmptyJsonFile() throws IOException {
        // Arrange - create empty JSON array input stream
        String emptyJsonArray = "[]"; // Empty list is valid JSON for a list of users
        InputStream emptyInputStream = new ByteArrayInputStream(emptyJsonArray.getBytes(StandardCharsets.UTF_8));
        
        LocalAzureUserRepository emptyRepository = new LocalAzureUserRepository(passwordHasher, emptyInputStream);
        
        // Act
        Map<String, User> allUsers = emptyRepository.getAllUsers();
        Optional<User> result = emptyRepository.findByUsername("anyuser");
        
        // Assert
        assertTrue(allUsers.isEmpty());
        assertFalse(result.isPresent());
    }
    
    @Test
    void testCaseSensitiveUsernames() {
        // Test that usernames are case-insensitive for lookup (as per current implementation of putting to map with toLowerCase())
        // but original case is preserved in User object.
        Optional<User> lowerCase = repository.findByUsername("testuser");
        Optional<User> upperCase = repository.findByUsername("TESTUSER"); // Should find due to toLowerCase() in findByUsername
        Optional<User> mixedCase = repository.findByUsername("TestUser"); // Should find
        
        assertTrue(lowerCase.isPresent(), "Should find 'testuser'");
        assertEquals("testuser", lowerCase.get().getUsername()); // Verify original casing

        assertTrue(upperCase.isPresent(), "Should find 'TESTUSER' (case-insensitive lookup)");
        assertEquals("testuser", upperCase.get().getUsername()); // Verify original casing from loaded data

        assertTrue(mixedCase.isPresent(), "Should find 'TestUser' (case-insensitive lookup)");
        assertEquals("testuser", mixedCase.get().getUsername()); // Verify original casing from loaded data

        // Test a user that is genuinely not present
        Optional<User> nonExistent = repository.findByUsername("nonexistentuser");
        assertFalse(nonExistent.isPresent(), "Should not find 'nonexistentuser'");
    }
}




