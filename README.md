# Java Authorization Server - Step 1: Core Domain Implementation

## Overview

This is the core domain implementation of a lightweight, cloud-native authorization service that authenticates HTTP requests with Basic access authentication. This implementation follows clean hexagonal architecture principles and focuses on pure Java business logic without external dependencies.

## Features

- **Secure Password Hashing**: Uses Argon2id algorithm for maximum security
- **Basic Authentication**: Supports HTTP Basic Authentication (username:password)
- **User Management**: Supports user status (ACTIVE/DISABLED) and role-based access
- **Timing Attack Protection**: Consistent response times to prevent timing attacks
- **Clean Architecture**: Hexagonal architecture with clear separation of concerns
- **Comprehensive Testing**: 90%+ code coverage with unit tests

## Architecture

The implementation follows hexagonal (ports and adapters) architecture:

```
src/main/java/com/example/auth/
├── domain/
│   ├── model/          # Domain entities (User, AuthenticationRequest, AuthenticationResult)
│   ├── service/        # Business logic (AuthenticatorService, BasicAuthenticatorService)
│   ├── port/           # Interfaces for external dependencies (UserRepository)
│   └── util/           # Domain utilities (PasswordHasher, BasicAuthDecoder)
├── infrastructure/     # Infrastructure implementations (InMemoryUserRepository)
└── config/             # Spring configuration (AuthConfig)
```

## Key Components

### Domain Models

- **User**: Immutable user entity with username, password hash, status, and roles
- **AuthenticationRequest**: Input credentials for authentication
- **AuthenticationResult**: Authentication outcome with metadata
- **UserStatus**: Enum for user account status (ACTIVE, DISABLED)

### Services

- **AuthenticatorService**: Interface defining authentication contract
- **BasicAuthenticatorService**: Core authentication logic implementation

### Utilities

- **PasswordHasher**: Argon2id password hashing and verification
- **BasicAuthDecoder**: HTTP Basic Authentication header parsing

### Infrastructure

- **InMemoryUserRepository**: Test implementation of UserRepository with sample users

## Getting Started

### Prerequisites

- Java 21 or higher
- Maven 3.6 or higher

### Building the Project

```bash
# Clone the repository
git clone <repository-url>
cd java-auth-server

# Build and run tests
mvn clean test

# Generate code coverage report
mvn clean test jacoco:report

# Check for security vulnerabilities
mvn dependency-check:check
```

### Running Tests

```bash
# Run all tests
mvn test

# Run tests with coverage
mvn test jacoco:report

# View coverage report
open target/site/jacoco/index.html
```

## Test Users

The in-memory repository includes these test users:

| Username | Password     | Status   | Roles           |
|----------|-------------|----------|-----------------|
| alice    | password123 | ACTIVE   | user            |
| admin    | admin123    | ACTIVE   | admin, user     |
| bob      | password456 | DISABLED | user            |
| charlie  | charlie789  | ACTIVE   | (none)          |

## Usage Example

```java
// Create dependencies
PasswordHasher passwordHasher = new PasswordHasher();
UserRepository userRepository = new InMemoryUserRepository(passwordHasher);
AuthenticatorService authenticator = new BasicAuthenticatorService(userRepository, passwordHasher);

// Authenticate user
AuthenticationRequest request = new AuthenticationRequest("alice", "password123");
AuthenticationResult result = authenticator.authenticate(request);

if (result.isAllowed()) {
    System.out.println("Authentication successful for: " + result.getUsername());
} else {
    System.out.println("Authentication failed: " + result.getReason());
}
```

## Security Features

1. **Argon2id Password Hashing**: Industry-standard secure password hashing
2. **Timing Attack Prevention**: Consistent response times regardless of user existence
3. **No Credential Logging**: Passwords and hashes are never logged
4. **Input Validation**: Comprehensive validation of all inputs
5. **Secure Defaults**: Security-first configuration out of the box

## Performance Targets

- **Authentication Latency**: < 50ms (excluding I/O)
- **Password Verification**: < 10ms
- **Memory Usage**: < 50MB per operation

## Testing

The project includes comprehensive unit tests with:

- **User Model Tests**: Validation, immutability, security
- **Password Hasher Tests**: Hashing, verification, security
- **Authenticator Service Tests**: All authentication scenarios
- **Performance Tests**: Latency and throughput validation

### Running Specific Test Categories

```bash
# Run all tests
mvn test

# Run tests with specific pattern
mvn test -Dtest="*Test"

# Run tests with coverage
mvn test jacoco:report
```

## Code Coverage

Current code coverage targets:
- **Line Coverage**: ≥ 90%
- **Branch Coverage**: ≥ 90%

View coverage report:
```bash
mvn test jacoco:report
open target/site/jacoco/index.html
```

## Configuration

The application uses Spring Boot for dependency injection. Key configuration:

```java
@Configuration
@ComponentScan(basePackages = "com.example.auth.domain")
public class AuthConfig {
    // Bean configurations for PasswordHasher, BasicAuthDecoder, etc.
}
```

## Next Steps

This implementation satisfies Step 1 requirements:
- ✅ Pure Java domain layer without AWS dependencies
- ✅ Clean hexagonal architecture
- ✅ Comprehensive unit tests (≥90% coverage)
- ✅ Security-first design
- ✅ Performance optimizations

**Step 2** will add:
- AWS Lambda integration
- Secrets Manager integration
- Infrastructure adapters
- Integration testing

## Validation Checklist

- [x] All unit tests pass
- [x] Code coverage ≥ 90%
- [x] No security vulnerabilities in dependency scan
- [x] Performance targets met in local testing
- [x] Clean architecture principles followed
- [x] No AWS dependencies in domain layer

## License

This project is licensed under the MIT License - see the LICENSE file for details. 