# Phase 3: Testing Strategy Prompt (Updated)

## ⚠️ Critical Prerequisites Based on Phase 2 Lessons Learned

### **MANDATORY FIRST STEP: Verify Actual Implementation**

Before generating any tests, you MUST:

1. **Analyze Real Domain API**: The domain layer was copied unchanged from AWS. Use ONLY existing domain classes:
   ```bash
   ✅ REAL Domain Classes (verified in Phase 2):
   - ClientCredentialsService.authenticate(TokenRequest) → TokenResponse
   - ClientCredentialsService.OAuth2AuthenticationException (inner class)
   - OAuth2TokenService.validateToken(), extractClaims(), extractClientId()
   - OAuthClient with Set<String> allowedGrantTypes (NOT enum)
   - ClientStatus enum: ACTIVE, DISABLED, SUSPENDED
   - User with constructor: new User(username, passwordHash, status, roles)
   
   ❌ DO NOT CREATE tests for fictional classes:
   - OAuthGrantType enum (doesn't exist)
   - IntrospectionRequest/IntrospectionResponse (don't exist)
   - Standalone OAuth2AuthenticationException (doesn't exist)
   ```

2. **Verify Actual Azure Functions Built**: Test the real implementations, not assumptions:
   ```java
   ✅ ACTUAL Azure Functions (from authserver.azure):
   - BasicAuthFunction: @FunctionName("BasicAuth"), route="auth/validate"
   - OAuth2TokenFunction: @FunctionName("OAuth2Token"), route="oauth/token"  
   - OAuth2IntrospectFunction: @FunctionName("OAuth2Introspect"), route="oauth/introspect"
   ```

3. **Use Real Infrastructure Model Classes**:
   ```java
   ✅ ACTUAL Infrastructure Models:
   - OAuth2TokenResponse(accessToken, tokenType, expiresIn, scope, issuedAt)
   - OAuth2ErrorResponse(error, errorDescription, errorUri)
   - OAuth2IntrospectionResponse(active, clientId, scope, tokenType, exp, iat)
   ```

## Context: Current State and Requirements

We have successfully completed Phase 2 migration with a **clean compilation** of the Azure Authorization Server. The implementation includes:

**Actual Azure Functions Implemented:**
- `BasicAuthFunction.java`: POST /api/auth/validate with Basic Auth header parsing
- `OAuth2TokenFunction.java`: POST /api/oauth/token with form-encoded and Basic Auth support
- `OAuth2IntrospectFunction.java`: POST /api/oauth/introspect using OAuth2TokenService methods

**Actual Repository Implementations:**
- `LocalAzureUserRepository`: File-based repository with PasswordHasher constructor
- `LocalAzureOAuthClientRepository`: File-based OAuth client repository
- `KeyVaultUserRepository`: Azure Key Vault with Caffeine caching (5min TTL)
- `KeyVaultOAuthClientRepository`: OAuth client storage in Key Vault with caching

**Real Configuration Structure:**
- `AzureFunctionConfiguration`: Spring beans with profile-based configuration (local/dev/prod)
- `application.properties`: Environment-specific settings
- `host.json`: Azure Functions runtime 4.x configuration
- `local.settings.json`: Local development settings

**Dependencies and Technologies:**
- Azure Functions Java Library 3.1.0
- Azure Key Vault SDK v12 (azure-security-keyvault-secrets 4.8.5)
- Azure Identity 1.13.3 with Managed Identity
- Caffeine caching
- Spring Framework 6.1.13 (no Spring Boot in functions)
- Jackson for JSON processing

## Task: Generate Testing Strategy for Actual Implementation

Create comprehensive tests for the real Azure implementation, focusing on infrastructure adapters since domain tests already exist in AWS.

### **Critical Testing Guidelines Based on Phase 2 Lessons:**

#### **1. Domain Layer Testing - DO NOT RECREATE**
```java
// ❌ WRONG - Do not create domain tests that already exist:
// UserTest.java, OAuthClientTest.java, BasicAuthenticatorServiceTest.java

// ✅ CORRECT - Domain tests already exist in AWS and are 100% portable
// Focus ONLY on Azure infrastructure adapter tests
```

#### **2. Use Real Service Method Names**
```java
// ❌ WRONG - Don't test fictional methods:
// service.generateToken() (doesn't exist)

// ✅ CORRECT - Test actual domain methods:
ClientCredentialsService.authenticate(TokenRequest request)
OAuth2TokenService.validateToken(String token)
OAuth2TokenService.extractClaims(String token)
```

#### **3. Test Real Constructor Signatures**
```java
// ✅ CORRECT - Test actual constructors:
new OAuth2TokenResponse(accessToken, tokenType, expiresIn, scope, issuedAt)
new OAuth2ErrorResponse(error, errorDescription, errorUri)
new OAuth2IntrospectionResponse(active, clientId, scope, tokenType, exp, iat)
new User(username, passwordHash, status, roles) // No email parameter
```

#### **4. Mock Real Azure Dependencies**
```java
// ✅ CORRECT - Mock actual Azure SDK classes:
@Mock SecretClient secretClient;
@Mock ExecutionContext context;
@Mock HttpRequestMessage<Optional<String>> request;

// ✅ CORRECT - Mock real domain services:
@Mock ClientCredentialsService clientCredentialsService;
@Mock OAuth2TokenService tokenService;
@Mock UserRepository userRepository;
```

## Required Test Structure and Implementation

Generate the following test structure for the **actual Azure implementation**:

### 1. Azure Functions Tests (`src/test/java/com/example/auth/infrastructure/azure/functions/`)

#### **BasicAuthFunctionTest.java**
```java
@ExtendWith(MockitoExtension.class)
class BasicAuthFunctionTest {
    
    @Mock private AuthenticatorService authenticatorService;
    @Mock private UserRepository userRepository;
    @Mock private ExecutionContext context;
    @InjectMocks private BasicAuthFunction function;
    
    @Test
    void testValidBasicAuthentication() {
        // Test with real Basic Auth header format
        // Mock result.isAllowed() and result.getReason() (actual methods)
        // Verify getUserRoles lookup via userRepository.findByUsername()
    }
    
    @Test
    void testInvalidCredentials() {
        // Test authentication failure scenarios
        // Verify proper OAuth2ErrorResponse creation
    }
    
    @Test
    void testMissingAuthorizationHeader() {
        // Test HTTP 400 responses for malformed requests
    }
}
```

#### **OAuth2TokenFunctionTest.java**
```java
@ExtendWith(MockitoExtension.class)
class OAuth2TokenFunctionTest {
    
    @Mock private ClientCredentialsService clientCredentialsService;
    @Mock private ExecutionContext context;
    @InjectMocks private OAuth2TokenFunction function;
    
    @Test
    void testClientCredentialsFlow() {
        // Test form-encoded client_credentials grant
        // Mock clientCredentialsService.authenticate() (real method)
        // Verify OAuth2TokenResponse with 5 parameters
    }
    
    @Test
    void testBasicAuthClientCredentials() {
        // Test Basic Auth header for client credentials
        // Verify credential extraction from Authorization header
    }
    
    @Test
    void testOAuth2AuthenticationException() {
        // Test ClientCredentialsService.OAuth2AuthenticationException (inner class)
        // Verify e.getOAuthError().getError() and getErrorDescription()
    }
}
```

#### **OAuth2IntrospectFunctionTest.java**
```java
@ExtendWith(MockitoExtension.class)
class OAuth2IntrospectFunctionTest {
    
    @Mock private OAuth2TokenService tokenService;
    @Mock private ExecutionContext context;
    @InjectMocks private OAuth2IntrospectFunction function;
    
    @Test
    void testActiveTokenIntrospection() {
        // Mock tokenService.validateToken() returning true
        // Mock tokenService.extractClaims(), extractClientId(), extractScope()
        // Verify OAuth2IntrospectionResponse(active=true, clientId, scope, "Bearer", exp, iat)
    }
    
    @Test
    void testInactiveTokenIntrospection() {
        // Mock tokenService.validateToken() returning false
        // Verify OAuth2IntrospectionResponse(active=false, null, null, null, null, null)
    }
}
```

### 2. Repository Tests (`src/test/java/com/example/auth/infrastructure/azure/`)

#### **LocalAzureUserRepositoryTest.java**
```java
@ExtendWith(MockitoExtension.class)
class LocalAzureUserRepositoryTest {
    
    @Mock private PasswordHasher passwordHasher;
    private LocalAzureUserRepository repository;
    
    @BeforeEach
    void setUp() {
        // Test constructor with PasswordHasher parameter
        repository = new LocalAzureUserRepository(passwordHasher);
    }
    
    @Test
    void testFindByUsername() {
        // Test user lookup from JSON file
        // Verify correct User constructor usage
    }
    
    @Test
    void testGetAllUsers() {
        // Test UserRepository.getAllUsers() implementation
        // Verify Map<String, User> return type
    }
    
    @Test
    void testUserStatusMapping() {
        // Test UserStatus.ACTIVE vs UserStatus.DISABLED (not INACTIVE)
        // Verify enum mapping from JSON
    }
}
```

#### **LocalAzureOAuthClientRepositoryTest.java**
```java
@ExtendWith(MockitoExtension.class) 
class LocalAzureOAuthClientRepositoryTest {
    
    private LocalAzureOAuthClientRepository repository;
    
    @BeforeEach
    void setUp() {
        repository = new LocalAzureOAuthClientRepository();
    }
    
    @Test
    void testFindByClientId() {
        // Test OAuth client lookup from JSON
        // Verify OAuthClient constructor with 7 parameters
    }
    
    @Test
    void testGrantTypeValidation() {
        // Test isValidGrantType() method with String values
        // Verify "client_credentials", "authorization_code", "refresh_token"
    }
    
    @Test
    void testClientStatusMapping() {
        // Test ClientStatus.ACTIVE, DISABLED, SUSPENDED mapping
    }
    
    @Test
    void testRepositoryMetadata() {
        // Test getRepositoryMetadata() implementation
        // Verify required interface methods
    }
}
```

### 3. Key Vault Repository Tests (`src/test/java/com/example/auth/infrastructure/azure/keyvault/`)

#### **KeyVaultUserRepositoryTest.java**
```java
@ExtendWith(MockitoExtension.class)
class KeyVaultUserRepositoryTest {
    
    @Mock private SecretClient secretClient;
    @Mock private PasswordHasher passwordHasher;
    private KeyVaultUserRepository repository;
    
    @BeforeEach
    void setUp() {
        repository = new KeyVaultUserRepository(secretClient, passwordHasher, 5, 100);
    }
    
    @Test
    void testFindByUsernameWithCaching() {
        // Mock secretClient.getSecret() with JSON user data
        // Test Caffeine cache behavior (5 minute TTL)
        // Verify correct User constructor usage
    }
    
    @Test
    void testGetAllUsers() {
        // Test UserRepository.getAllUsers() implementation
        // Mock multiple Key Vault calls
    }
    
    @Test
    void testKeyVaultExceptionHandling() {
        // Test ResourceNotFoundException handling
        // Verify graceful degradation
    }
}
```

#### **KeyVaultOAuthClientRepositoryTest.java**
```java
@ExtendWith(MockitoExtension.class)
class KeyVaultOAuthClientRepositoryTest {
    
    @Mock private SecretClient secretClient;
    private KeyVaultOAuthClientRepository repository;
    
    @Test
    void testFindByClientIdWithCaching() {
        // Mock Key Vault secret retrieval
        // Test caching with Caffeine
        // Verify OAuthClient constructor with real parameters
    }
    
    @Test
    void testGrantTypeDeserialization() {
        // Test Set<String> allowedGrantTypes (not enum)
        // Verify isValidGrantType() validation
    }
    
    @Test
    void testRepositoryHealthy() {
        // Test isRepositoryHealthy() implementation
        // Mock Key Vault connectivity check
    }
}
```

### 4. Configuration Tests (`src/test/java/com/example/auth/infrastructure/azure/config/`)

#### **AzureFunctionConfigurationTest.java**
```java
@ExtendWith(SpringExtension.class)
@TestPropertySource(properties = {
    "spring.profiles.active=test",
    "cache.ttl.minutes=1",
    "cache.max.size=10"
})
class AzureFunctionConfigurationTest {
    
    @Test
    void testBeanConfiguration() {
        // Test Spring bean creation with real dependencies
        // Verify dependency injection patterns match implementation
    }
    
    @Test
    void testProfileBasedConfiguration() {
        // Test local vs dev vs prod profile behavior
        // Verify correct repository implementations
    }
    
    @Test
    void testMetricsBean() {
        // Test AzureFunctionMetrics bean creation
        // Verify Application Insights integration
    }
}
```

### 5. Integration Tests (`src/test/java/com/example/auth/infrastructure/azure/integration/`)

#### **AzureFunctionIntegrationTest.java**
```java
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AzureFunctionIntegrationTest {
    
    @Test
    void testBasicAuthEndToEnd() {
        // Test complete Basic Auth flow
        // Use Azure Functions Core Tools if available
        // Verify HTTP request/response cycle
    }
    
    @Test
    void testOAuth2TokenFlowIntegration() {
        // Test token generation and introspection together
        // Verify JWT token creation and validation
    }
    
    @Test
    void testLocalRepositoriesIntegration() {
        // Test with local JSON files
        // Verify data loading and caching
    }
}
```

### 6. Test Configuration (`src/test/resources/`)

#### **application-test.properties**
```properties
# Real configuration matching implementation
spring.profiles.active=test
cache.ttl.minutes=1
cache.max.size=10
logging.level.com.example.auth=DEBUG

# Test-specific settings
azure.keyvault.enabled=false
local.repositories.enabled=true
```

#### **local.settings.test.json**
```json
{
  "IsEncrypted": false,
  "Values": {
    "AzureWebJobsStorage": "",
    "FUNCTIONS_WORKER_RUNTIME": "java",
    "SPRING_PROFILES_ACTIVE": "test",
    "CACHE_TTL_MINUTES": "1"
  }
}
```

### 7. Test Utilities (`src/test/java/com/example/auth/test/`)

#### **AzureFunctionTestHelper.java**
```java
public class AzureFunctionTestHelper {
    
    public static HttpRequestMessage<Optional<String>> createBasicAuthRequest(String username, String password) {
        // Create real Basic Auth header
        // Mock HttpRequestMessage with correct structure
    }
    
    public static HttpRequestMessage<Optional<String>> createTokenRequest(String clientId, String clientSecret) {
        // Create form-encoded OAuth request
        // Support both form data and Basic Auth patterns
    }
    
    public static void verifyOAuth2TokenResponse(HttpResponseMessage response) {
        // Verify OAuth2TokenResponse structure with 5 parameters
        // Check required headers (Content-Type, Cache-Control, Pragma)
    }
}
```

## Testing Strategy Aligned with Actual Implementation

### **1. Focus Areas Based on Real Code**

**Infrastructure Adapter Testing (95% of effort):**
- Azure Functions HTTP trigger handling
- Key Vault integration with caching
- Local repository file parsing
- Spring configuration and dependency injection
- JSON request/response serialization

**Domain Integration Points (5% of effort):**
- Verify correct domain method calls
- Test real constructor signatures
- Validate actual exception handling

### **2. Mock Strategies for Real Dependencies**

```java
// Mock real Azure SDK classes
@Mock SecretClient secretClient;
@Mock KeyVaultSecret keyVaultSecret;

// Mock real Azure Functions runtime
@Mock ExecutionContext context;
@Mock HttpRequestMessage<Optional<String>> request;
@Mock HttpResponseMessage.Builder responseBuilder;

// Mock real domain services (don't recreate domain tests)
@Mock ClientCredentialsService clientCredentialsService;
@Mock OAuth2TokenService tokenService;
@Mock AuthenticatorService authenticatorService;
```

### **3. Test Data Aligned with Real Structure**

```json
// Test user data matching LocalAzureUserRepository format
{
  "username": "testuser",
  "password": "plaintext123",
  "status": "ACTIVE",
  "roles": ["user", "read"]
}

// Test OAuth client data matching LocalAzureOAuthClientRepository format  
{
  "clientId": "test-client",
  "clientSecret": "secret123",
  "status": "ACTIVE",
  "allowedGrantTypes": ["client_credentials"],
  "allowedScopes": ["read", "write"],
  "tokenExpirationSeconds": 3600,
  "description": "Test Client"
}
```

## Performance and Security Testing

### **4. Performance Tests Matching Real Implementation**

```java
@Test
void testCaffeineCache Performance() {
    // Test 5-minute TTL behavior
    // Measure cache hit/miss ratios
    // Verify memory usage with 100-item limit
}

@Test
void testAzureFunctionColdStart() {
    // Measure Spring context initialization time
    // Test first request after deployment
    // Compare against <1s cold start requirement
}
```

### **5. Security Tests for Real Features**

```java
@Test
void testPasswordHasherIntegration() {
    // Test real PasswordHasher usage in LocalAzureUserRepository
    // Verify Argon2id hashing (if implemented)
}

@Test  
void testCredentialMasking() {
    // Test maskClientId() and maskUsername() methods
    // Verify no credentials in logs
}
```

## Success Criteria

### **Compilation and Coverage Goals**
- ✅ **Clean test compilation**: All tests compile without errors
- ✅ **Real API usage**: No tests for fictional domain classes
- ✅ **Infrastructure coverage**: ≥90% line coverage for Azure adapters
- ✅ **Integration validation**: End-to-end function testing

### **Test Categories and Execution**
```bash
# Unit tests (fast, no external dependencies)
mvn test -Dgroups="unit" -DexcludeGroups="integration"

# Integration tests (with Azure Functions Core Tools)
mvn test -Dgroups="integration"

# All tests with coverage
mvn clean test jacoco:report
```

### **Documentation and Maintenance**
- Each test class documents which real implementation it tests
- Test method names clearly describe actual scenarios
- No references to fictional domain classes in comments
- Clear distinction between domain tests (AWS) vs infrastructure tests (Azure)

Remember: **The domain layer is 100% portable and already tested in AWS. Focus Phase 3 testing on Azure infrastructure adapters only.** 