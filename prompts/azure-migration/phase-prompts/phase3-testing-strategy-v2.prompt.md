# Phase 3: Azure Functions Testing Strategy v2.0
## Comprehensive Test Suite Generation Prompt

### 🎯 **Objective**
Generate a complete, robust test suite for Azure Functions authorization server that achieves 100% pass rate on first attempt by avoiding common pitfalls and following proven patterns.

---

## ⚠️ **CRITICAL: Avoid These Common Failures**

### 1. **Spring Version Compatibility - MANDATORY**
```xml
<spring-boot.version>3.2.0</spring-boot.version>
<!-- Do NOT use Spring Boot 3.4.x - it has version conflicts -->
<!-- Let Spring Boot manage all dependency versions via BOM -->
```

### 2. **Domain Model Validation - MANDATORY**
- **Password Hashes**: MUST start with `$2a$10$` or `$argon2`
- **Client Status**: Use `DISABLED` not `INACTIVE` 
- **User Status**: Use `ACTIVE` or `DISABLED` only
- **Grant Types**: Use lowercase `client_credentials` not `CLIENT_CREDENTIALS`
- **User Constructor**: ONLY 4 parameters - `(username, passwordHash, status, roles)` - NO additional fields

### 3. **JSON Structure - MANDATORY**
- **LocalAzureUserRepository**: Expects JSON array `[{...}, {...}]`
- **LocalAzureOAuthClientRepository**: Expects JSON array `[{...}, {...}]`
- **KeyVault Repositories**: Expect individual JSON objects per secret

### 4. **Domain Model Over-Engineering - CRITICAL WARNING**
⚠️ **DO NOT** expand the User domain model during testing. Common mistake:
```java
// WRONG - Adding extra fields during testing
User user = new User(id, username, password, roles, status, firstName, lastName, email, created, updated);

// CORRECT - Keep domain model minimal (matches AWS)
User user = new User(username, passwordHash, status, roles);
```
**Principle**: Infrastructure testing should never drive domain model changes.

---

## 🏗️ **Test Suite Architecture**

### **Layer 1: Repository Tests** (Unit Level)
Focus: Data validation, JSON parsing, domain object creation

### **Layer 2: Service Tests** (Business Logic)
Focus: Authentication flows, OAuth token generation, validation logic

### **Layer 3: Azure Function Tests** (HTTP Interface)
Focus: Request/response handling, headers, error scenarios

### **Layer 4: Integration Tests** (End-to-End)
Focus: Complete workflows with real repositories and mocked services

### **Layer 5: Configuration Tests** (Spring Context)
Focus: Bean creation, profile management, dependency injection

---

## 📋 **Repository Testing Patterns**

### **LocalAzureUserRepository Tests**
```java
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LocalAzureUserRepositoryTest {
    
    private LocalAzureUserRepository repository;
    private PasswordHasher passwordHasher;
    
    @BeforeAll
    void setup() {
        passwordHasher = mock(PasswordHasher.class);
        when(passwordHasher.hashPassword(anyString())).thenReturn("$2a$10$hashedPassword");
        repository = new LocalAzureUserRepository(passwordHasher);
    }
    
    @Test
    void testValidUserLoading() {
        // Test with properly formatted password hashes
        Optional<User> user = repository.findByUsername("testuser");
        assertTrue(user.isPresent());
        assertEquals(UserStatus.ACTIVE, user.get().getStatus());
        assertTrue(user.get().getPasswordHash().startsWith("$2a$10$"));
    }
    
    @Test
    void testMalformedJsonHandling() {
        // Test error scenarios with invalid JSON
        InputStream malformedStream = new ByteArrayInputStream("{ invalid json".getBytes());
        assertThrows(RuntimeException.class, () -> 
            new LocalAzureUserRepository(passwordHasher, malformedStream));
    }
}
```

### **Required Test Data Formats**
```json
// users.json - MUST be array format
[
  {
    "username": "testuser",
    "password": "password123",
    "status": "ACTIVE",
    "roles": ["user", "read"]
  }
]

// oauth-clients.json - MUST be array format  
[
  {
    "clientId": "test-client",
    "clientSecret": "secret123",
    "status": "ACTIVE",
    "allowedGrantTypes": ["client_credentials"],
    "allowedScopes": ["read", "write"],
    "tokenExpirationSeconds": 3600
  }
]
```

---

## 🔌 **Azure Functions Testing Patterns**

### **HTTP Request Mocking - CRITICAL**
```java
public static HttpRequestMessage<Optional<String>> createBasicAuthRequest(String username, String password) {
    HttpRequestMessage<Optional<String>> request = mock(HttpRequestMessage.class);
    
    // CRITICAL: Proper Basic Auth header format
    String credentials = username + ":" + password;
    String basicAuthHeader = "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes());
    
    Map<String, String> headers = new HashMap<>();
    headers.put("authorization", basicAuthHeader);
    headers.put("content-type", "application/json");
    
    when(request.getHeaders()).thenReturn(headers);
    when(request.getBody()).thenReturn(Optional.empty());
    
    // CRITICAL: Mock response builder chain
    HttpResponseMessage.Builder responseBuilder = mock(HttpResponseMessage.Builder.class);
    HttpResponseMessage response = mock(HttpResponseMessage.class);
    
    when(request.createResponseBuilder(any(HttpStatus.class))).thenReturn(responseBuilder);
    when(responseBuilder.header(anyString(), anyString())).thenReturn(responseBuilder);
    when(responseBuilder.body(any())).thenReturn(responseBuilder);
    when(responseBuilder.build()).thenReturn(response);
    
    return request;
}

public static HttpRequestMessage<Optional<String>> createTokenRequest(String clientId, String clientSecret) {
    HttpRequestMessage<Optional<String>> request = mock(HttpRequestMessage.class);
    
    // CRITICAL: Form-encoded content type for OAuth requests
    Map<String, String> headers = new HashMap<>();
    headers.put("content-type", "application/x-www-form-urlencoded");
    when(request.getHeaders()).thenReturn(headers);
    
    String formData = "grant_type=client_credentials&client_id=" + clientId + "&client_secret=" + clientSecret;
    when(request.getBody()).thenReturn(Optional.of(formData));
    
    return request;
}
```

### **Azure Function Test Structure**
```java
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BasicAuthFunctionTest {
    
    private BasicAuthFunction function;
    private AuthenticatorService authenticatorService;
    private UserRepository userRepository;
    private ExecutionContext mockContext;
    
    @BeforeAll
    void setup() {
        function = new BasicAuthFunction();
        authenticatorService = mock(AuthenticatorService.class);
        userRepository = mock(UserRepository.class);
        
        // CRITICAL: Inject dependencies via reflection
        injectDependency(function, "authenticatorService", authenticatorService);
        injectDependency(function, "userRepository", userRepository);
        injectDependency(function, "metrics", createMockMetrics());
        
        // CRITICAL: Mock ExecutionContext with invocation ID
        mockContext = mock(ExecutionContext.class);
        when(mockContext.getInvocationId()).thenReturn("test-invocation-123");
    }
    
    @BeforeEach
    void resetMocks() {
        // CRITICAL: Reset mocks between tests to avoid interference
        reset(authenticatorService, userRepository);
    }
    
    private void injectDependency(Object target, String fieldName, Object dependency) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, dependency);
        } catch (Exception e) {
            throw new RuntimeException("Failed to inject dependency: " + fieldName, e);
        }
    }
}
```

---

## 🔗 **Integration Testing Patterns**

### **End-to-End Test Setup**
```java
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AzureFunctionIntegrationTest {
    
    @TempDir
    private static Path tempDir;
    
    private UserRepository userRepository;
    private OAuthClientRepository oAuthClientRepository;
    
    @BeforeAll
    void setUpIntegrationTest() throws IOException {
        setupTestData();
        setupRealRepositories();
        setupAzureFunctions();
    }
    
    private void setupTestData() throws IOException {
        // CRITICAL: Create test data files with correct JSON array format
        Path usersFile = tempDir.resolve("users.json");
        String usersJson = """
            [
              {
                "username": "testuser",
                "password": "password123",
                "status": "ACTIVE",
                "roles": ["user", "read"]
              }
            ]
            """;
        Files.write(usersFile, usersJson.getBytes());
        
        // CRITICAL: Use test data files, not classpath resources
        PasswordHasher passwordHasher = mock(PasswordHasher.class);
        when(passwordHasher.hashPassword(anyString())).thenReturn("$2a$10$hashed_password");
        
        userRepository = new LocalAzureUserRepository(passwordHasher, Files.newInputStream(usersFile));
        oAuthClientRepository = new LocalAzureOAuthClientRepository(Files.newInputStream(clientsFile));
    }
}
```

---

## 🏛️ **Spring Configuration Testing**

### **Configuration Test Pattern**
```java
@SpringBootTest(classes = AzureFunctionConfiguration.class)
@ActiveProfiles("local")
@TestPropertySource(properties = {
    "cache.ttl.minutes=15",
    "cache.max.size=500",
    "azure.keyvault.url=https://test-keyvault.vault.azure.net/"
    // CRITICAL: Do NOT set spring.profiles.active here - conflicts with @ActiveProfiles
})
class AzureFunctionConfigurationTest {
    
    @Autowired(required = false)
    private UserRepository userRepository;
    
    @Test
    void testLocalProfileConfiguration() {
        // CRITICAL: Test only what should exist in this profile
        if (userRepository != null) {
            assertTrue(userRepository instanceof LocalAzureUserRepository);
        }
    }
}
```

### **POM.xml Requirements - CRITICAL**
```xml
<properties>
    <maven.compiler.source>21</maven.compiler.source>
    <maven.compiler.target>21</maven.compiler.target>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    
    <!-- CRITICAL: Use Spring Boot 3.2.0, NOT 3.4.x -->
    <spring-boot.version>3.2.0</spring-boot.version>
    
    <!-- CRITICAL: Let Spring Boot manage these versions -->
    <!-- Do NOT specify versions for Jackson, Logback, SLF4J -->
</properties>

<dependencyManagement>
    <dependencies>
        <!-- CRITICAL: Spring Boot BOM manages all compatible versions -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-dependencies</artifactId>
            <version>${spring-boot.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

---

## 🎭 **Mock Management Best Practices**

### **What TO Mock**
```java
// External services and infrastructure
@Mock private AuthenticatorService authenticatorService;
@Mock private OAuth2TokenService tokenService;
@Mock private ExecutionContext executionContext;
@Mock private HttpRequestMessage<Optional<String>> request;
```

### **What NOT TO Mock**
```java
// Domain objects - use real constructors
AuthenticationResult result = AuthenticationResult.success("username");
TokenResponse response = TokenResponse.bearer("token", 3600, "scope");
OAuthError error = OAuthError.of("invalid_client", "Client not found");

// CRITICAL: User constructor - ONLY 4 parameters
User user = new User("testuser", "$2a$10$hashedPassword", UserStatus.ACTIVE, Arrays.asList("user", "read"));
// DO NOT add extra fields like firstName, lastName, email, etc.

// Collections and basic types
List<String> roles = Arrays.asList("user", "admin");
Set<String> grantTypes = Set.of("client_credentials");
```

### **Mock Setup Patterns**
```java
@BeforeEach
void setupMocks() {
    // CRITICAL: Reset all mocks to clean state
    reset(authenticatorService, tokenService);
    
    // CRITICAL: Setup lenient behavior for metrics to avoid strict stubbing errors
    lenient().when(metrics.recordAuthenticationAttempt(anyString())).thenReturn(null);
}
```

---

## 🚨 **Error Scenario Testing**

### **HTTP Error Response Testing**
```java
@Test
void testInvalidAuthorizationHeader() {
    HttpRequestMessage<Optional<String>> request = createRequestWithoutAuth();
    
    HttpResponseMessage response = basicAuthFunction.run(request, mockContext);
    
    assertNotNull(response);
    // Verify proper error response structure
}

@Test
void testMalformedBasicAuth() {
    HttpRequestMessage<Optional<String>> request = createMalformedBasicAuthRequest();
    
    HttpResponseMessage response = basicAuthFunction.run(request, mockContext);
    
    assertNotNull(response);
    // Should handle gracefully without exceptions
}
```

### **Domain Validation Testing**
```java
@Test
void testInvalidPasswordHashFormat() {
    // CRITICAL: Test domain validation rejects invalid formats
    assertThrows(IllegalArgumentException.class, () -> {
        new User("testuser", "invalid_hash", UserStatus.ACTIVE, Arrays.asList("user"));
    });
}

@Test
void testValidUserConstruction() {
    // CRITICAL: User constructor with ONLY 4 parameters
    User user = new User("testuser", "$2a$10$hashedPassword", UserStatus.ACTIVE, Arrays.asList("user", "read"));
    
    assertEquals("testuser", user.getUsername());
    assertEquals("$2a$10$hashedPassword", user.getPasswordHash());
    assertEquals(UserStatus.ACTIVE, user.getStatus());
    assertEquals(Arrays.asList("user", "read"), user.getRoles());
    
    // Verify User has ONLY the core authentication fields
    // NO firstName, lastName, email, dateCreated, lastUpdated
}
```

---

## 🔍 **Verification Patterns**

### **Business Logic Verification**
```java
@Test
void testSuccessfulAuthentication() {
    // Arrange
    when(authenticatorService.authenticate(any())).thenReturn(
        AuthenticationResult.success("testuser"));
    
    when(userRepository.findByUsername("testuser")).thenReturn(
        Optional.of(new User("testuser", "$2a$10$hash", UserStatus.ACTIVE, Arrays.asList("user"))));
    
    // Act
    HttpResponseMessage response = function.run(request, mockContext);
    
    // Assert
    assertNotNull(response);
    verify(authenticatorService).authenticate(any(AuthenticationRequest.class));
    verify(userRepository).findByUsername("testuser");
}
```

### **Data Consistency Verification**
```java
@Test
void testRepositoryDataConsistency() {
    // Multiple calls should return identical objects
    Optional<User> user1 = repository.findByUsername("testuser");
    Optional<User> user2 = repository.findByUsername("testuser");
    
    assertTrue(user1.isPresent() && user2.isPresent());
    assertEquals(user1.get().getUsername(), user2.get().getUsername());
    assertEquals(user1.get().getPasswordHash(), user2.get().getPasswordHash());
}
```

---

## 📊 **Test Coverage Requirements**

### **Repository Layer** (Target: 15+ tests per repository)
- ✅ Valid data loading and parsing
- ✅ Invalid/malformed data handling
- ✅ Cache behavior and performance
- ✅ CRUD operations (where applicable)
- ✅ Domain validation enforcement

### **Azure Functions Layer** (Target: 8+ tests per function)
- ✅ Successful request processing
- ✅ Invalid headers and authentication
- ✅ Malformed request bodies
- ✅ Error response formatting
- ✅ Metrics and logging integration

### **Integration Layer** (Target: 6+ comprehensive scenarios)
- ✅ End-to-end authentication flows
- ✅ Token generation and validation
- ✅ Repository integration
- ✅ Error propagation
- ✅ Performance characteristics

### **Configuration Layer** (Target: 10+ profile tests)
- ✅ Bean creation for each profile
- ✅ Dependency injection validation
- ✅ Profile-specific configurations
- ✅ Cache and metrics setup

---

## ✅ **Success Criteria Checklist**

Before considering testing complete, verify:

- [ ] **100% test pass rate** on first full test run
- [ ] **No Spring Boot version conflicts** in dependency tree
- [ ] **All password hashes** use valid formats (`$2a$10$` or `$argon2`)
- [ ] **All JSON test data** matches repository expected formats (arrays vs objects)
- [ ] **All HTTP requests** include proper content-type headers
- [ ] **All mocks** are properly reset between tests
- [ ] **Real domain objects** used instead of mocking value types
- [ ] **Dependencies injected** correctly in Azure Functions tests
- [ ] **Error scenarios** tested comprehensively
- [ ] **Integration tests** cover end-to-end workflows
- [ ] **Domain model consistency** maintained (User constructor has only 4 parameters)
- [ ] **AWS-Azure alignment** verified (identical User.java across platforms)

---

## 🎯 **Final Validation**

Run the complete test suite and verify:
1. **Maven**: `mvn clean test` - 100% success rate
2. **Performance**: All tests complete under 60 seconds
3. **Reliability**: Tests pass consistently across multiple runs
4. **Coverage**: All critical paths and error scenarios tested

**Expected Output**: `BUILD SUCCESS` with 0 failures, 0 errors, 95+ tests passing.

This prompt ensures robust, comprehensive test coverage while avoiding all common pitfalls that cause test failures in Azure Functions Spring Boot applications. 