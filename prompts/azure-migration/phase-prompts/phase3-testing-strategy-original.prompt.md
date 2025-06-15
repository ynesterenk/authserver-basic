# Phase 3: Testing Strategy Prompt

## Context: Current State and Requirements

We are developing a comprehensive testing strategy for the Azure-migrated Java Authorization Server. The current AWS implementation has:

**Existing Test Coverage:**
- Unit tests for domain layer (90%+ coverage)
- Integration tests using Testcontainers and LocalStack
- Performance validation tests
- Security test scenarios

**Testing Technologies:**
- JUnit 5 for test framework
- Mockito for mocking
- Testcontainers for integration testing
- LocalStack for AWS service emulation
- Spring Test for Spring context testing

**Azure Target Requirements:**
- Maintain 90%+ test coverage
- Test Azure Functions with HTTP triggers
- Validate Key Vault integration
- Test Application Insights telemetry
- Performance benchmarking against AWS baseline
- Local development testing with Azure Functions Core Tools
- CI/CD integration with GitHub Actions

**Test Scenarios to Cover:**
- All authentication scenarios (valid/invalid/disabled users)
- OAuth 2.0 flows (token generation, introspection)
- Error cases (malformed requests, missing headers)
- Performance requirements (<100ms warm, <1s cold start)
- Security validations (no credential leakage)

## Task: Specific Generation Request

Generate a complete testing strategy and implementation for the Azure migration, including:

1. **Unit Test Suite** (`authserver.azure/src/test/java/com/example/auth/infrastructure/azure/functions/`):
   - `BasicAuthFunctionTest.java`: Unit tests for basic auth function
   - `OAuth2TokenFunctionTest.java`: Unit tests for token endpoint
   - `OAuth2IntrospectFunctionTest.java`: Unit tests for introspection
   - Mock Azure ExecutionContext and HttpRequestMessage
   - Test all success and error scenarios

2. **Repository Tests** (`authserver.azure/src/test/java/com/example/auth/infrastructure/azure/keyvault/`):
   - `KeyVaultUserRepositoryTest.java`: Test Key Vault user operations
   - `KeyVaultOAuthClientRepositoryTest.java`: Test OAuth client operations
   - Mock SecretClient for Key Vault operations
   - Test caching behavior and TTL

3. **Integration Test Suite** (`authserver.azure/src/test/java/com/example/auth/infrastructure/azure/integration/`):
   - `AzureFunctionIntegrationTest.java`: End-to-end function tests
   - `KeyVaultIntegrationTest.java`: Real Key Vault integration (optional)
   - Use Azure Functions Core Tools for local testing
   - TestContainers setup for Azure services (if available)

4. **Performance Test Suite** (`authserver.azure/src/test/java/com/example/auth/infrastructure/azure/performance/`):
   - `ColdStartPerformanceTest.java`: Measure cold start times
   - `WarmRequestPerformanceTest.java`: Measure warm request latency
   - `ThroughputTest.java`: Test concurrent request handling
   - Compare against AWS baseline metrics

5. **Security Test Suite** (`authserver.azure/src/test/java/com/example/auth/infrastructure/azure/security/`):
   - `CredentialLeakageTest.java`: Ensure no secrets in logs/responses
   - `InputValidationTest.java`: Test injection attack prevention
   - `TimingAttackTest.java`: Verify constant-time operations

6. **Test Configuration** (`authserver.azure/src/test/resources/`):
   - `application-test.properties`: Test-specific Spring configuration
   - `local.settings.test.json`: Azure Functions test settings
   - Test data fixtures

7. **Test Utilities** (`authserver.azure/src/test/java/com/example/auth/test/`):
   - `AzureFunctionTestHelper.java`: Helper methods for creating test requests
   - `TestDataBuilder.java`: Builder pattern for test data
   - `AzureMockFactory.java`: Factory for Azure SDK mocks

## Constraints: Architecture, Security, Performance

### Architecture Constraints
- **Maintain Test Pyramid**: More unit tests than integration tests
- **Test Isolation**: Each test must be independent and repeatable
- **Hexagonal Boundaries**: Test infrastructure adapters separately from domain
- **Spring Context**: Minimize Spring context loading in unit tests

### Security Constraints
- **No Real Secrets**: Never use production secrets in tests
- **Test Security Features**: Verify Argon2id hashing, timing protection
- **Credential Safety**: Test that credentials never appear in logs
- **Secure Test Data**: Use generated test credentials, not real ones

### Performance Constraints
- **Fast Unit Tests**: Each unit test < 100ms
- **Reasonable Integration Tests**: Each integration test < 5s
- **CI/CD Friendly**: Total test suite < 5 minutes
- **Parallel Execution**: Tests must support parallel execution

### Azure-Specific Requirements
- **Azure SDK Mocking**: Use appropriate Azure SDK test utilities
- **Local Function Testing**: Support Azure Functions Core Tools
- **Emulator Support**: Use Azurite for storage emulation if needed
- **Application Insights**: Test telemetry without sending real data

## Output: Expected Format and Structure

Generate the following complete test structure and implementations:

### 1. Test Project Structure
```
/authserver.azure/src/test/
├── java/
│   └── com/example/auth/
│       ├── infrastructure/
│       │   └── azure/
│       │       ├── functions/
│       │       │   ├── BasicAuthFunctionTest.java
│       │       │   ├── OAuth2TokenFunctionTest.java
│       │       │   └── OAuth2IntrospectFunctionTest.java
│       │       ├── keyvault/
│       │       │   ├── KeyVaultUserRepositoryTest.java
│       │       │   └── KeyVaultOAuthClientRepositoryTest.java
│       │       ├── integration/
│       │       │   ├── AzureFunctionIntegrationTest.java
│       │       │   └── KeyVaultIntegrationTest.java
│       │       ├── performance/
│       │       │   ├── ColdStartPerformanceTest.java
│       │       │   ├── WarmRequestPerformanceTest.java
│       │       │   └── ThroughputTest.java
│       │       └── security/
│       │           ├── CredentialLeakageTest.java
│       │           ├── InputValidationTest.java
│       │           └── TimingAttackTest.java
│       └── test/
│           ├── AzureFunctionTestHelper.java
│           ├── TestDataBuilder.java
│           └── AzureMockFactory.java
└── resources/
    ├── application-test.properties
    ├── local.settings.test.json
    └── test-data/
        ├── users.json
        └── oauth-clients.json
```

### 2. Unit Test Example Structure

**BasicAuthFunctionTest.java example:**
```java
@ExtendWith(MockitoExtension.class)
class BasicAuthFunctionTest {
    
    @Mock
    private AuthenticatorService authenticatorService;
    
    @Mock
    private ExecutionContext context;
    
    @InjectMocks
    private BasicAuthFunction function;
    
    @Test
    void testValidAuthentication() {
        // Given
        HttpRequestMessage<Optional<String>> request = createAuthRequest("alice", "password123");
        when(authenticatorService.authenticate(any())).thenReturn(AuthenticationResult.success("alice"));
        
        // When
        HttpResponseMessage response = function.run(request, context);
        
        // Then
        assertEquals(HttpStatus.OK, response.getStatus());
        // Verify response body, headers, etc.
    }
    
    // More test cases...
}
```

### 3. Integration Test Configuration

**Azure Functions Core Tools Testing:**
```java
@TestConfiguration
public class AzureFunctionsTestConfig {
    
    @Bean
    public TestHarness azureFunctionsTestHarness() {
        // Configuration for local Azure Functions runtime
    }
}
```

### 4. Performance Test Requirements
- Measure cold start time (first request after initialization)
- Measure warm request time (subsequent requests)
- Use JMH (Java Microbenchmark Harness) for accurate measurements
- Generate performance comparison report vs AWS

### 5. CI/CD Test Integration

**GitHub Actions test job:**
```yaml
test:
  runs-on: ubuntu-latest
  steps:
    - name: Run Unit Tests
      run: mvn test -Dtest="*Test" -DexcludedGroups="integration,performance"
      
    - name: Run Integration Tests
      run: mvn test -Dgroups="integration"
      
    - name: Generate Coverage Report
      run: mvn jacoco:report
      
    - name: Check Coverage Threshold
      run: mvn jacoco:check
```

### 6. Test Data Management
- Use builder pattern for test data creation
- Separate test data files for different scenarios
- Mock Key Vault responses with realistic data structures

### 7. Local Development Testing

**Running tests locally:**
```bash
# Unit tests only
mvn test -Dtest="*Test" -DexcludedGroups="integration"

# Integration tests with Azure Functions Core Tools
func start --java
mvn test -Dgroups="integration"

# All tests including performance
mvn test

# With coverage report
mvn clean test jacoco:report
```

### Additional Requirements:

1. **Test Coverage Goals**:
   - Line coverage: ≥ 90%
   - Branch coverage: ≥ 90%
   - Mutation coverage: ≥ 80% (optional)

2. **Test Categories**:
   - `@Tag("unit")`: Fast, isolated unit tests
   - `@Tag("integration")`: Tests requiring external services
   - `@Tag("performance")`: Performance benchmarks
   - `@Tag("security")`: Security-focused tests

3. **Assertion Libraries**:
   - AssertJ for fluent assertions
   - JSONAssert for JSON comparisons
   - Hamcrest for complex matchers

4. **Mock Strategies**:
   - Mock Azure SDK clients (SecretClient, etc.)
   - Mock HTTP requests/responses
   - Use test doubles for external dependencies

5. **Test Reporting**:
   - JaCoCo for code coverage
   - Surefire reports for test results
   - Custom performance report generation

Remember to:
- Write tests that are readable and maintainable
- Use descriptive test method names
- Follow AAA pattern (Arrange, Act, Assert)
- Include both positive and negative test cases
- Test edge cases and error conditions
- Make tests deterministic (no random failures)
- Document complex test scenarios 