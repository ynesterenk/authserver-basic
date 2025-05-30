# Step 2: AWS Lambda Integration & Infrastructure Layer

## Objective
Implement the AWS Lambda handler and infrastructure adapters for the Java Authorization Server, integrating with AWS Secrets Manager while maintaining the clean architecture from Step 1.

## Prerequisites
- Step 1 completed: Core domain implementation with ≥90% test coverage
- All Step 1 validation criteria met
- Domain layer remains unchanged in this step

## Requirements from PRD
- **Runtime**: AWS Lambda with Java 21
- **Cold-start latency**: ≤ 600ms (P95)
- **Warm latency**: ≤ 120ms (P95)
- **Credential Store**: AWS Secrets Manager
- **HTTP Interface**: POST /auth/validate → JSON response
- **Observability**: Structured JSON logs, CloudWatch metrics

## Implementation Tasks

### 1. AWS Dependencies
Add to `pom.xml`:
```xml
<dependencies>
    <!-- AWS Lambda -->
    <dependency>
        <groupId>com.amazonaws</groupId>
        <artifactId>aws-lambda-java-core</artifactId>
        <version>1.2.3</version>
    </dependency>
    <dependency>
        <groupId>com.amazonaws</groupId>
        <artifactId>aws-lambda-java-events</artifactId>
        <version>3.11.4</version>
    </dependency>
    
    <!-- AWS SDK v2 -->
    <dependency>
        <groupId>software.amazon.awssdk</groupId>
        <artifactId>secretsmanager</artifactId>
        <version>2.21.29</version>
    </dependency>
    
    <!-- Lambda Powertools for observability -->
    <dependency>
        <groupId>software.amazon.lambda</groupId>
        <artifactId>powertools-logging</artifactId>
        <version>1.18.0</version>
    </dependency>
    <dependency>
        <groupId>software.amazon.lambda</groupId>
        <artifactId>powertools-metrics</artifactId>
        <version>1.18.0</version>
    </dependency>
</dependencies>
```

### 2. Infrastructure Layer Implementation

**SecretsManagerUserRepository.java**:
```java
@Component
public class SecretsManagerUserRepository implements UserRepository {
    private final SecretsManagerClient secretsClient;
    private final String secretArn;
    private final ObjectMapper objectMapper;
    private final Cache<String, Map<String, User>> userCache;
    
    // Implement caching with 5-minute TTL
    // Parse JSON from Secrets Manager
    // Handle AWS SDK exceptions gracefully
    // Add retry logic with exponential backoff
}
```

**LambdaHandler.java**:
```java
public class LambdaHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    private final AuthenticatorService authenticatorService;
    private final ObjectMapper objectMapper;
    
    @Override
    public APIGatewayProxyResponseEvent handleRequest(
        APIGatewayProxyRequestEvent input, 
        Context context
    ) {
        // Extract Authorization header
        // Create AuthenticationRequest
        // Call domain service
        // Return JSON response with proper HTTP status codes
    }
}
```

### 3. Request/Response Models

**AuthValidationRequest.java**:
```java
public class AuthValidationRequest {
    // Optional: for future POST body support
    // Currently using Authorization header only
}
```

**AuthValidationResponse.java**:
```java
public class AuthValidationResponse {
    private boolean allowed;
    private String message;
    private long timestamp;
    // JSON serialization support
}
```

### 4. Configuration & Dependency Injection

**LambdaConfiguration.java**:
```java
@Configuration
@ComponentScan(basePackages = "com.example.auth")
public class LambdaConfiguration {
    
    @Bean
    public SecretsManagerClient secretsManagerClient() {
        return SecretsManagerClient.builder()
            .region(Region.of(System.getenv("AWS_REGION")))
            .build();
    }
    
    @Bean
    public UserRepository userRepository(SecretsManagerClient client) {
        String secretArn = System.getenv("CREDENTIAL_SECRET_ARN");
        return new SecretsManagerUserRepository(client, secretArn);
    }
}
```

### 5. Observability Implementation

**Structured Logging**:
- Use Lambda Powertools for JSON logging
- Log authentication attempts (no credentials)
- Include correlation IDs
- Add performance metrics

**CloudWatch Metrics**:
- AuthSuccess counter
- AuthFailure counter  
- Latency histogram
- CacheHit/CacheMiss counters

**Example logging**:
```java
@Logging(logEvent = true)
@Metrics(namespace = "AuthService")
public class LambdaHandler {
    
    @Tracing
    public APIGatewayProxyResponseEvent handleRequest(...) {
        // Structured logging with no PII
        log.info("Authentication attempt", 
            Map.of("requestId", context.getAwsRequestId(),
                   "userAgent", getUserAgent(input)));
    }
}
```

### 6. Error Handling & HTTP Status Codes

Implement proper HTTP responses:
- **200**: Successful authentication (allowed: true/false)
- **400**: Malformed request (missing/invalid Authorization header)
- **401**: Authentication failed
- **429**: Rate limiting (future enhancement)
- **500**: Internal server error
- **503**: Service unavailable (AWS service issues)

### 7. Local Testing Infrastructure

**LocalSecretsManagerRepository.java**:
```java
@Profile("local")
@Component
public class LocalSecretsManagerRepository implements UserRepository {
    // File-based or in-memory implementation for local testing
    // Load test users from application.yml or JSON file
}
```

**Integration Tests with Testcontainers**:
```java
@SpringBootTest
@Testcontainers
class LambdaIntegrationTest {
    
    @Container
    static LocalStackContainer localstack = new LocalStackContainer(DockerImageName.parse("localstack/localstack:latest"))
        .withServices(LocalStackContainer.Service.SECRETSMANAGER);
    
    // Test full Lambda handler with mocked AWS services
}
```

### 8. Performance Optimizations

**Cold Start Reduction**:
- Minimize dependency injection overhead
- Lazy initialization where possible
- Pre-compile regex patterns
- Connection pooling for AWS SDK

**Memory Management**:
- Configure appropriate Lambda memory (512MB from PRD)
- Monitor memory usage in tests
- Implement proper cache eviction

## Validation Criteria

### Integration Tests
1. **AWS Integration**:
   - Secrets Manager connectivity
   - Proper secret parsing and caching
   - AWS SDK error handling
   - Retry logic validation

2. **Lambda Handler Tests**:
   - Valid Authorization header → 200 with correct JSON
   - Missing header → 400 with error message
   - Invalid credentials → 200 with allowed=false
   - AWS service errors → 503 with retry headers

3. **Performance Tests**:
   - Cold start < 600ms (measure with actual Lambda)
   - Warm invocation < 120ms
   - Cache effectiveness (hit ratio > 80%)

### Local Testing
1. **SAM Local Testing**:
   ```bash
   sam local start-api
   curl -X POST http://localhost:3000/auth/validate \
     -H "Authorization: Basic $(echo -n 'alice:password123' | base64)"
   ```

2. **Unit Tests for Infrastructure**:
   - SecretsManager repository with mocked AWS SDK
   - Lambda handler with mocked dependencies
   - Error scenarios and edge cases

### Security Validation
1. **No Credential Exposure**:
   - CloudWatch logs contain no sensitive data
   - Exception stack traces are sanitized
   - AWS SDK debug logging disabled in production

2. **IAM Least Privilege**:
   - Lambda execution role has minimal permissions
   - Only required Secrets Manager actions allowed
   - No overly broad resource permissions

## Deliverables
1. Complete AWS Lambda implementation
2. Infrastructure adapters with caching
3. Comprehensive integration test suite
4. SAM template for local testing
5. Performance benchmark results
6. Security validation report
7. Updated README with AWS setup instructions

## Success Criteria
- [ ] All integration tests pass
- [ ] Performance targets met in SAM local testing
- [ ] No AWS credentials hardcoded
- [ ] Proper error handling for all AWS service failures
- [ ] Structured logging implemented
- [ ] Cache functionality working correctly
- [ ] Domain layer unchanged from Step 1

## Environment Variables Required
- `CREDENTIAL_SECRET_ARN`: ARN of the Secrets Manager secret
- `AWS_REGION`: AWS region for services
- `LOG_LEVEL`: Logging level (INFO, DEBUG, etc.)
- `CACHE_TTL_MINUTES`: Cache time-to-live (default: 5)

## Next Step Preview
Step 3 will focus on CloudFormation infrastructure deployment and API Gateway configuration. 