# Phase 2: Core Code Migration Prompt (Improved)

## ⚠️ Critical Prerequisites

### **MANDATORY FIRST STEP: Domain Layer Analysis**

Before generating any Azure infrastructure code, you MUST:

1. **Copy Complete Domain Folder**: Copy the entire `src/main/java/com/example/auth/domain/` folder from AWS implementation to `authserver.azure/src/main/java/com/example/auth/domain/` 
   - **Why**: Domain layer is 100% portable in hexagonal architecture
   - **Action**: Complete folder copy with NO modifications
   - **Verification**: Compile domain layer independently first

2. **Document Actual Domain Classes**: Analyze and document the actual class names, constructors, and method signatures:
   ```bash
   # Required documentation before code generation:
   ✅ User class constructor signature
   ✅ AuthenticationResult method names (isAllowed vs isAuthenticated)
   ✅ Service implementation class names (BasicAuthenticatorService vs AuthenticatorServiceImpl)
   ✅ ClientCredentialsService method names (authenticate vs generateToken)
   ✅ Dependency injection patterns (constructor vs @Autowired)
   ```

3. **Verify Infrastructure Model Requirements**: Check if OAuth response models need copying:
   - `OAuth2TokenResponse.java`
   - `OAuth2ErrorResponse.java` 
   - `OAuth2IntrospectionResponse.java`

## Context: Current State and Requirements

We are migrating Java Authorization Server code from AWS Lambda to Azure Functions. The current implementation includes:

**AWS Lambda Handlers:**
- `LambdaHandler.java`: Processes Basic Authentication requests from API Gateway
- `OAuth2LambdaHandler.java`: Handles OAuth 2.0 token and introspection endpoints
- Both use Spring context, AWS Lambda RequestHandler interface, and API Gateway events

**Repository Implementations:**
- `SecretsManagerUserRepository`: Fetches user credentials from AWS Secrets Manager
- `InMemoryOAuthClientRepository`: In-memory OAuth client storage
- `LocalUserRepository`: Local file-based repository for development

**Key Dependencies:**
- AWS Lambda Runtime (com.amazonaws)
- AWS SDK for Secrets Manager
- Spring Framework for dependency injection
- Lambda Powertools for logging/metrics/tracing
- Spring Security for password encoding
- Apache Commons Codec for secure token generation

**Target Azure Architecture:**
- Azure Functions with HTTP triggers
- Azure Key Vault for secrets management
- Application Insights for monitoring
- Same hexagonal architecture preserved
- Located in `/authserver.azure/` subfolder

## Task: Specific Generation Request

Generate the complete Azure Functions implementation code that maintains the same API contracts and functionality:

### **Critical Implementation Guidelines Based on Lessons Learned:**

#### **1. Service Class Names - VERIFY ACTUAL NAMES**
```java
// ❌ WRONG - Do not assume these class names:
// AuthenticatorServiceImpl, OAuth2TokenServiceImpl

// ✅ CORRECT - Use actual domain class names:
import com.example.auth.domain.service.BasicAuthenticatorService;
import com.example.auth.domain.service.oauth.JwtTokenService;
```

#### **2. Method Names - VERIFY ACTUAL SIGNATURES**
```java
// ❌ WRONG - Do not assume these method names:
// result.isAuthenticated(), result.getFailureReason(), service.generateToken()

// ✅ CORRECT - Use actual domain method names:
result.isAllowed()
result.getReason()
service.authenticate(request)
```

#### **3. Constructor Signatures - VERIFY ACTUAL PARAMETERS**
```java
// ❌ WRONG - Do not assume User constructor includes email:
// new User(username, passwordHash, status, roles, email)

// ✅ CORRECT - Use actual User constructor:
new User(username, passwordHash, status, new ArrayList<>(roles))
```

#### **4. Dependency Injection Patterns - CHECK ACTUAL IMPLEMENTATION**
```java
// ✅ CORRECT - ClientCredentialsServiceImpl uses @Autowired fields, not constructor:
@Bean
public ClientCredentialsService clientCredentialsService() {
    return new ClientCredentialsServiceImpl(); // Spring handles @Autowired injection
}

// ✅ CORRECT - BasicAuthenticatorService uses constructor injection:
@Bean
public AuthenticatorService authenticatorService(UserRepository userRepository, 
                                               PasswordHasher passwordHasher) {
    return new BasicAuthenticatorService(userRepository, passwordHasher);
}
```

#### **5. Lambda Expression Best Practices**
```java
// ❌ AVOID - Lambda expressions with loop variables cause compilation errors:
for (String clientId : clientIds) {
    client.ifPresent(c -> allClients.put(clientId, c)); // ← Variable scope issue
}

// ✅ CORRECT - Use explicit if-statements:
for (String clientId : clientIds) {
    if (client.isPresent()) {
        allClients.put(clientId, client.get());
    }
}
```

### **Required Files to Generate:**

1. **Basic Authentication Function** (`authserver.azure/src/main/java/com/example/auth/infrastructure/azure/functions/BasicAuthFunction.java`):
   - Convert from AWS Lambda RequestHandler to Azure Function with @FunctionName
   - Use @HttpTrigger annotation for POST /api/auth/validate
   - **CRITICAL**: Use `@Autowired UserRepository userRepository` field
   - **CRITICAL**: Use `result.isAllowed()` not `result.isAuthenticated()`
   - **CRITICAL**: Use `result.getReason()` not `result.getFailureReason()`
   - **CRITICAL**: Get user roles by looking up user: `userRepository.findByUsername(username).map(User::getRoles)`
   - Parse Basic Auth header
   - Return same JSON response structure

2. **OAuth2 Functions** (`authserver.azure/src/main/java/com/example/auth/infrastructure/azure/functions/`):
   - `OAuth2TokenFunction.java`: Handle POST /api/oauth/token
     - **CRITICAL**: Use `clientService.authenticate(request)` not `generateToken()`
   - `OAuth2IntrospectFunction.java`: Handle POST /api/oauth/introspect
   - Parse form-encoded requests
   - Support both Basic Auth and form parameters for client credentials
   - **REQUIRED**: Copy OAuth response model classes from AWS infrastructure
   - Maintain OAuth 2.0 RFC compliance

3. **Infrastructure OAuth Model Classes** (Copy from AWS):
   - `authserver.azure/src/main/java/com/example/auth/infrastructure/oauth/model/OAuth2TokenResponse.java`
   - `authserver.azure/src/main/java/com/example/auth/infrastructure/oauth/model/OAuth2ErrorResponse.java`
   - `authserver.azure/src/main/java/com/example/auth/infrastructure/oauth/model/OAuth2IntrospectionResponse.java`
   - `authserver.azure/src/main/java/com/example/auth/infrastructure/oauth/InMemoryOAuthClientRepository.java`

4. **Key Vault Repository** (`authserver.azure/src/main/java/com/example/auth/infrastructure/azure/keyvault/KeyVaultUserRepository.java`):
   - Implement UserRepository interface
   - Use Azure Key Vault SDK (com.azure.security.keyvault.secrets)
   - **CRITICAL**: Use correct User constructor: `new User(username, passwordHash, status, new ArrayList<>(roles))`
   - **CRITICAL**: Avoid lambda expressions with loop variables
   - Implement caching with same TTL (5 minutes default)
   - Use Managed Identity for authentication

5. **Key Vault OAuth Repository** (`authserver.azure/src/main/java/com/example/auth/infrastructure/azure/keyvault/KeyVaultOAuthClientRepository.java`):
   - Implement OAuthClientRepository interface
   - Store OAuth client configurations in Key Vault
   - **CRITICAL**: Avoid lambda expressions with loop variables
   - Support same client data structure

6. **Azure Configuration** (`authserver.azure/src/main/java/com/example/auth/infrastructure/azure/config/AzureFunctionConfiguration.java`):
   - Spring configuration for Azure environment
   - **CRITICAL**: Use actual service class names: `BasicAuthenticatorService`, `JwtTokenService`
   - **CRITICAL**: Use correct dependency injection patterns
   - Bean definitions for repositories and services
   - Application Insights integration
   - Environment variable configuration

7. **Local Development Repositories**:
   - `LocalAzureUserRepository.java`: 
     - **CRITICAL**: Provide constructor: `public LocalAzureUserRepository(PasswordHasher passwordHasher)`
     - **CRITICAL**: Use correct User constructor (no email parameter)
   - `LocalAzureOAuthClientRepository.java`: Create for local development

8. **Application Entry Point** (`authserver.azure/src/main/java/com/example/auth/AzureApplication.java`):
   - Main class for local Azure Functions runtime
   - Spring Boot application configuration

## Constraints: Architecture, Security, Performance

### Architecture Constraints
- **Preserve Hexagonal Architecture**: Domain layer remains untouched, only infrastructure adapters change
- **Maintain API Contracts**: Exact same request/response formats
- **Use Spring DI**: Keep Spring dependency injection pattern
- **Stateless Design**: No session state, all state in requests/tokens

### Security Constraints
- **Managed Identity**: Use system-assigned managed identity for Key Vault access
- **No Hardcoded Secrets**: All sensitive data from Key Vault or environment variables
- **Secure Logging**: No credentials or sensitive data in logs
- **Input Validation**: Same validation rules as AWS implementation

### Performance Constraints
- **Cold Start Optimization**: 
  - Minimize dependencies
  - Lazy initialization where possible
  - Efficient Spring context loading
- **Response Times**: Match AWS performance (<100ms warm)
- **Memory Usage**: Stay under 512MB
- **Concurrent Requests**: Support same throughput

### Azure-Specific Requirements
- **Azure Functions Runtime 4.x**: Latest runtime version
- **Java 21**: Match AWS Lambda Java version
- **Azure SDK v12**: Use latest Azure SDK for Java
- **Application Insights**: Integrate for monitoring and tracing
- **Local Development**: Support Azure Functions Core Tools

## Output: Expected Format and Structure

Generate the following complete Java files with all imports, annotations, and implementations:

### 1. Project Structure
```
/authserver.azure/
├── pom.xml                              # Maven configuration with Azure dependencies
├── host.json                            # Azure Functions host configuration
├── local.settings.json                  # Local development settings
└── src/
    └── main/
        ├── java/
        │   └── com/example/auth/
        │       ├── domain/               # ← COPIED FROM AWS (complete folder)
        │       ├── infrastructure/
        │       │   ├── oauth/            # ← Model classes copied from AWS
        │       │   │   └── model/
        │       │   │       ├── OAuth2TokenResponse.java
        │       │   │       ├── OAuth2ErrorResponse.java
        │       │   │       └── OAuth2IntrospectionResponse.java
        │       │   │   └── InMemoryOAuthClientRepository.java
        │       │   └── azure/
        │       │       ├── functions/
        │       │       │   ├── BasicAuthFunction.java
        │       │       │   ├── OAuth2TokenFunction.java
        │       │       │   └── OAuth2IntrospectFunction.java
        │       │       ├── keyvault/
        │       │       │   ├── KeyVaultUserRepository.java
        │       │       │   └── KeyVaultOAuthClientRepository.java
        │       │       ├── config/
        │       │       │   └── AzureFunctionConfiguration.java
        │       │       ├── LocalAzureUserRepository.java
        │       │       └── LocalAzureOAuthClientRepository.java
        │       └── AzureApplication.java
        └── resources/
            └── application.properties   # Spring configuration
```

### 2. Maven POM Configuration Requirements
```xml
<!-- Key dependencies to include -->
- com.microsoft.azure.functions:azure-functions-java-library
- com.azure:azure-security-keyvault-secrets
- com.azure:azure-identity
- com.azure:azure-monitor-opentelemetry-exporter
- org.springframework:spring-context
- Same domain dependencies as AWS project
```

### 3. Function Implementation Requirements

**BasicAuthFunction Example Structure:**
```java
@Component
public class BasicAuthFunction {
    
    @Autowired
    private AuthenticatorService authenticatorService;
    
    @Autowired
    private UserRepository userRepository;
    
    @FunctionName("BasicAuth")
    public HttpResponseMessage run(
        @HttpTrigger(
            name = "req",
            methods = {HttpMethod.POST},
            authLevel = AuthorizationLevel.ANONYMOUS,
            route = "auth/validate")
        HttpRequestMessage<Optional<String>> request,
        final ExecutionContext context) {
        
        // ✅ CRITICAL: Use result.isAllowed() not isAuthenticated()
        if (result.isAllowed()) {
            return createSuccessResponse(request, username, getUserRoles(username));
        } else {
            return createUnauthorizedResponse(request, result.getReason());
        }
    }
    
    // ✅ CRITICAL: Get roles by looking up user
    private List<String> getUserRoles(String username) {
        return userRepository.findByUsername(username)
                .map(User::getRoles)
                .orElse(Collections.emptyList());
    }
}
```

### 4. Key Vault Repository Requirements
- Async/sync options for Key Vault operations
- Retry logic with exponential backoff
- Circuit breaker pattern for resilience
- Proper error handling and logging
- Cache implementation using Caffeine or similar
- **CRITICAL**: Avoid lambda expressions with loop variables

### 5. Configuration Files

**host.json:**
```json
{
  "version": "2.0",
  "logging": {
    "applicationInsights": {
      "samplingSettings": {
        "isEnabled": true
      }
    }
  },
  "extensionBundle": {
    "id": "Microsoft.Azure.Functions.ExtensionBundle",
    "version": "[4.*, 5.0.0)"
  }
}
```

**local.settings.json:**
```json
{
  "IsEncrypted": false,
  "Values": {
    "AzureWebJobsStorage": "",
    "FUNCTIONS_WORKER_RUNTIME": "java",
    "KEY_VAULT_URL": "https://kv-auth-dev.vault.azure.net/",
    "APPLICATIONINSIGHTS_CONNECTION_STRING": "",
    "CACHE_TTL_MINUTES": "5",
    "SPRING_PROFILES_ACTIVE": "local"
  }
}
```

## 🚨 Compilation Error Prevention Checklist

Before generating code, verify:

- [ ] **Domain folder copied completely**: All domain classes available
- [ ] **Actual class names documented**: No assumed service implementation names
- [ ] **Method signatures verified**: Actual method names from domain classes
- [ ] **Constructor signatures verified**: Actual constructor parameters
- [ ] **Dependency injection patterns identified**: Constructor vs @Autowired
- [ ] **Infrastructure models identified**: Which models need copying
- [ ] **Lambda expressions avoided**: Use explicit if-statements in loops

### 6. Testing Considerations
- Include unit test examples for each function
- Show how to test with Azure Functions Core Tools
- Demonstrate integration with TestContainers if applicable

### Additional Requirements:
1. **Comprehensive Comments**: Document all classes and methods
2. **Error Handling**: Match AWS Lambda error responses
3. **Logging**: Use SLF4J with same log levels and patterns
4. **Metrics**: Emit custom metrics to Application Insights
5. **Health Check**: Include health endpoint like AWS version

### Success Criteria:
- **Clean Compilation**: `mvn clean compile` succeeds with zero errors
- **Successful Packaging**: `mvn clean package` builds Azure Functions app
- **All Functions Detected**: 3 Azure Functions properly configured
- **API Compatibility**: Identical request/response formats maintained

Remember to:
- **FIRST**: Copy entire domain folder and verify all actual class signatures
- Preserve all business logic in domain layer (no changes)
- Maintain same security standards (Argon2id hashing, timing attack protection)
- Keep consistent code style with AWS implementation
- Make code testable with proper dependency injection
- Handle Azure-specific exceptions appropriately
- Test compilation after each major component addition
