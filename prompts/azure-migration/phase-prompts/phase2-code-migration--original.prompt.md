# Phase 2: Core Code Migration Prompt

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

**Target Azure Architecture:**
- Azure Functions with HTTP triggers
- Azure Key Vault for secrets management
- Application Insights for monitoring
- Same hexagonal architecture preserved
- Located in `/authserver.azure/` subfolder

## Task: Specific Generation Request

Generate the complete Azure Functions implementation code that maintains the same API contracts and functionality:

1. **Basic Authentication Function** (`authserver.azure/src/main/java/com/example/auth/infrastructure/azure/functions/BasicAuthFunction.java`):
   - Convert from AWS Lambda RequestHandler to Azure Function with @FunctionName
   - Use @HttpTrigger annotation for POST /api/auth/validate
   - Maintain exact request/response format
   - Parse Basic Auth header
   - Return same JSON response structure

2. **OAuth2 Functions** (`authserver.azure/src/main/java/com/example/auth/infrastructure/azure/functions/`):
   - `OAuth2TokenFunction.java`: Handle POST /api/oauth/token
   - `OAuth2IntrospectFunction.java`: Handle POST /api/oauth/introspect
   - Parse form-encoded requests
   - Support both Basic Auth and form parameters for client credentials
   - Maintain OAuth 2.0 RFC compliance

3. **Key Vault Repository** (`authserver.azure/src/main/java/com/example/auth/infrastructure/azure/keyvault/KeyVaultUserRepository.java`):
   - Implement UserRepository interface
   - Use Azure Key Vault SDK (com.azure.security.keyvault.secrets)
   - Support same user data structure as Secrets Manager
   - Implement caching with same TTL (5 minutes default)
   - Use Managed Identity for authentication

4. **Key Vault OAuth Repository** (`authserver.azure/src/main/java/com/example/auth/infrastructure/azure/keyvault/KeyVaultOAuthClientRepository.java`):
   - Implement OAuthClientRepository interface
   - Store OAuth client configurations in Key Vault
   - Support same client data structure

5. **Azure Configuration** (`authserver.azure/src/main/java/com/example/auth/infrastructure/azure/config/AzureFunctionConfiguration.java`):
   - Spring configuration for Azure environment
   - Bean definitions for repositories and services
   - Application Insights integration
   - Environment variable configuration

6. **Local Development Repository** (`authserver.azure/src/main/java/com/example/auth/infrastructure/azure/LocalAzureUserRepository.java`):
   - Port LocalUserRepository for Azure local development
   - Support Azure Functions Core Tools testing

7. **Application Entry Point** (`authserver.azure/src/main/java/com/example/auth/AzureApplication.java`):
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
        │       ├── AzureApplication.java
        │       └── infrastructure/
        │           └── azure/
        │               ├── functions/
        │               │   ├── BasicAuthFunction.java
        │               │   ├── OAuth2TokenFunction.java
        │               │   └── OAuth2IntrospectFunction.java
        │               ├── keyvault/
        │               │   ├── KeyVaultUserRepository.java
        │               │   └── KeyVaultOAuthClientRepository.java
        │               ├── config/
        │               │   └── AzureFunctionConfiguration.java
        │               └── LocalAzureUserRepository.java
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
    @FunctionName("BasicAuth")
    public HttpResponseMessage run(
        @HttpTrigger(
            name = "req",
            methods = {HttpMethod.POST},
            authLevel = AuthorizationLevel.ANONYMOUS,
            route = "auth/validate")
        HttpRequestMessage<Optional<String>> request,
        final ExecutionContext context) {
        
        // Implementation matching LambdaHandler.java logic
    }
}
```

### 4. Key Vault Repository Requirements
- Async/sync options for Key Vault operations
- Retry logic with exponential backoff
- Circuit breaker pattern for resilience
- Proper error handling and logging
- Cache implementation using Caffeine or similar

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
    "CACHE_TTL_MINUTES": "5"
  }
}
```

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

Remember to:
- Preserve all business logic in domain layer (no changes)
- Maintain same security standards (Argon2id hashing, timing attack protection)
- Keep consistent code style with AWS implementation
- Make code testable with proper dependency injection
- Handle Azure-specific exceptions appropriately 