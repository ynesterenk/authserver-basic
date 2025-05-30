# Step 1: Core Domain Implementation - Java Authorization Server

## Objective
Implement the core domain layer for the Java Authorization Server with Basic Authentication support, following clean hexagonal architecture principles. This step focuses on the pure Java business logic without AWS dependencies.

## Requirements from PRD
- **Primary Goal**: Securely validate username:password and return allow/deny outcome
- **Architecture**: Clean hexagonal architecture with dependency injection
- **Performance Target**: ≤ 120ms warm invocation latency
- **Testing**: ≥ 90% unit test coverage for auth core
- **Security**: Argon2id password hashing, no plaintext credentials in logs

## Implementation Tasks

### 1. Project Structure & Dependencies
Create a Maven project with the following structure:
```
src/
├── main/java/com/example/auth/
│   ├── domain/
│   │   ├── model/
│   │   ├── service/
│   │   └── port/
│   └── infrastructure/
└── test/java/com/example/auth/
    └── domain/
```

**Required Dependencies** (add to `pom.xml`):
- Java 21 runtime
- Spring Boot 3.x (for dependency injection)
- Spring Security (for password encoding)
- JUnit 5
- Mockito
- Jackson (JSON processing)
- SLF4J with Logback

### 2. Domain Models
Implement the following domain models:

**User.java**:
```java
public class User {
    private String username;
    private String passwordHash;
    private UserStatus status;
    private List<String> roles;
    // constructors, getters, validation
}

public enum UserStatus {
    ACTIVE, DISABLED
}
```

**AuthenticationRequest.java**:
```java
public class AuthenticationRequest {
    private String username;
    private String password;
    // constructors, getters, validation
}
```

**AuthenticationResult.java**:
```java
public class AuthenticationResult {
    private boolean allowed;
    private String reason;
    private String username;
    // constructors, getters
}
```

### 3. Core Service Interface
**AuthenticatorService.java**:
```java
public interface AuthenticatorService {
    AuthenticationResult authenticate(AuthenticationRequest request);
}
```

### 4. Repository Port (Hexagonal Architecture)
**UserRepository.java**:
```java
public interface UserRepository {
    Optional<User> findByUsername(String username);
    Map<String, User> getAllUsers();
}
```

### 5. Core Service Implementation
**BasicAuthenticatorService.java**:
- Implement password verification using Argon2id
- Handle user status validation (ACTIVE/DISABLED)
- Implement proper error handling and logging
- Ensure no sensitive data in logs
- Add performance monitoring hooks

Key features:
- Decode Basic Auth header
- Validate user exists and is ACTIVE
- Verify password hash using secure comparison
- Return structured result with reason codes
- Log authentication attempts (without credentials)

### 6. Utility Classes
**BasicAuthDecoder.java**:
- Decode `Authorization: Basic <base64>` header
- Extract username:password safely
- Handle malformed headers gracefully

**PasswordHasher.java**:
- Argon2id implementation for password hashing
- Secure password verification
- Configurable parameters (memory, iterations, parallelism)

### 7. Configuration
**AuthConfig.java**:
- Spring configuration class
- Bean definitions for services
- Password encoder configuration
- Logging configuration

## Validation Criteria

### Unit Tests (≥90% coverage)
1. **AuthenticatorService Tests**:
   - Valid credentials → allowed=true
   - Invalid password → allowed=false
   - Non-existent user → allowed=false
   - Disabled user → allowed=false
   - Malformed Basic Auth header → proper error handling
   - Empty/null inputs → proper validation

2. **BasicAuthDecoder Tests**:
   - Valid Base64 encoding → correct username:password
   - Invalid Base64 → exception handling
   - Missing colon separator → exception handling
   - Empty username/password → validation

3. **PasswordHasher Tests**:
   - Hash generation → verify format
   - Password verification → correct true/false results
   - Timing attack resistance → consistent execution time

### Performance Tests
1. **Latency Validation**:
   - Authentication operation < 50ms (excluding I/O)
   - Password verification < 10ms
   - Memory usage < 50MB per operation

### Security Tests
1. **No Credential Leakage**:
   - Log output contains no plaintext passwords
   - Log output contains no password hashes
   - Exception messages don't expose sensitive data

2. **Timing Attack Resistance**:
   - Consistent response time for valid/invalid users
   - Consistent response time for correct/incorrect passwords

## Deliverables
1. Complete Maven project with all dependencies
2. All domain models and services implemented
3. Comprehensive unit test suite (≥90% coverage)
4. README.md with build and test instructions
5. Performance benchmark results
6. Security validation report

## Success Criteria
- [ ] All unit tests pass
- [ ] Code coverage ≥ 90%
- [ ] No security vulnerabilities in dependency scan
- [ ] Performance targets met in local testing
- [ ] Clean architecture principles followed
- [ ] No AWS dependencies in domain layer

## Next Step Preview
Step 2 will focus on AWS Lambda integration and infrastructure adapters while keeping the domain layer unchanged. 