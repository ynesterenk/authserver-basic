# Step 1: OAuth 2.0 Client Credentials Flow - Core Domain Implementation

## Objective
Extend the existing Java Authorization Server with OAuth 2.0 Client Credentials Flow support, implementing the core domain layer following clean hexagonal architecture principles. This step focuses on the pure Java business logic without AWS dependencies, complementing the existing Basic Authentication functionality.

## Requirements from OAuth 2.0 Specification (RFC 6749)
- **Primary Goal**: Implement Client Credentials Grant flow for machine-to-machine authentication
- **Flow Type**: OAuth 2.0 Client Credentials Grant (Section 4.4 of RFC 6749)
- **Security**: Client authentication via client_id and client_secret
- **Token Format**: JWT Bearer tokens with configurable expiration
- **Architecture**: Clean hexagonal architecture with dependency injection
- **Performance Target**: ≤ 150ms warm invocation latency (including token generation)
- **Testing**: ≥ 90% unit test coverage for OAuth 2.0 core components
- **Security**: Secure client secret handling, no plaintext secrets in logs

## Implementation Tasks

### 1. Extended Project Structure
Extend the existing structure with OAuth 2.0 specific components:
```
src/
├── main/java/com/example/auth/
│   ├── domain/
│   │   ├── model/
│   │   │   ├── oauth/          # New OAuth 2.0 models
│   │   │   └── ...             # Existing models
│   │   ├── service/
│   │   │   ├── oauth/          # New OAuth 2.0 services
│   │   │   └── ...             # Existing services
│   │   └── port/
│   │       ├── oauth/          # New OAuth 2.0 ports
│   │       └── ...             # Existing ports
│   └── infrastructure/
│       ├── oauth/             # New OAuth 2.0 infrastructure
│       └── ...                # Existing infrastructure
└── test/java/com/example/auth/
    └── domain/oauth/          # New OAuth 2.0 tests
```

**Additional Dependencies** (add to `pom.xml`):
- JWT Library (java-jwt or jjwt-impl)
- Java Time API (built-in)
- Apache Commons Codec (for secure token generation)
- Spring Security OAuth2 Core (for token utilities)

### 2. OAuth 2.0 Domain Models

**OAuthClient.java**:
```java
public class OAuthClient {
    private String clientId;
    private String clientSecretHash;
    private ClientStatus status;
    private List<String> allowedScopes;
    private Set<String> allowedGrantTypes;
    private Integer tokenExpirationSeconds;
    private String description;
    // constructors, getters, validation
}

public enum ClientStatus {
    ACTIVE, DISABLED, SUSPENDED
}
```

**TokenRequest.java**:
```java
public class TokenRequest {
    private String grantType;
    private String clientId;
    private String clientSecret;
    private String scope;
    // constructors, getters, validation
}
```

**TokenResponse.java**:
```java
public class TokenResponse {
    private String accessToken;
    private String tokenType;
    private Integer expiresIn;
    private String scope;
    private Long issuedAt;
    // constructors, getters
}
```

**OAuthError.java**:
```java
public class OAuthError {
    private String error;
    private String errorDescription;
    private String errorUri;
    // constructors, getters (following RFC 6749 Section 5.2)
}
```

### 3. Core OAuth 2.0 Service Interfaces

**OAuth2TokenService.java**:
```java
public interface OAuth2TokenService {
    TokenResponse generateToken(TokenRequest request);
    boolean validateToken(String token);
    Claims extractClaims(String token);
}
```

**ClientCredentialsService.java**:
```java
public interface ClientCredentialsService {
    TokenResponse authenticate(TokenRequest request);
    boolean validateClientCredentials(String clientId, String clientSecret);
}
```

### 4. Repository Ports for OAuth 2.0

**OAuthClientRepository.java**:
```java
public interface OAuthClientRepository {
    Optional<OAuthClient> findByClientId(String clientId);
    Map<String, OAuthClient> getAllClients();
    boolean existsByClientId(String clientId);
}
```

### 5. Core Service Implementations

**JwtTokenService.java**:
- Implement JWT token generation with RS256 signing
- Include standard claims (iss, aud, exp, iat, sub)
- Include custom claims (scope, client_id)
- Token validation and parsing
- Configurable expiration times
- Secure key management hooks

**ClientCredentialsServiceImpl.java**:
- Validate grant_type is "client_credentials"
- Authenticate client using client_id and client_secret
- Validate client status is ACTIVE
- Validate requested scopes against allowed scopes
- Generate access token with appropriate claims
- Return structured TokenResponse or OAuthError
- Comprehensive logging without credential exposure

Key OAuth 2.0 Features:
- Support for "client_credentials" grant type only
- Client authentication via HTTP Basic or form parameters
- Scope validation and limitation
- Proper error responses per RFC 6749
- Token introspection capabilities

### 6. Utility Classes

**OAuth2RequestParser.java**:
- Parse OAuth 2.0 token requests from HTTP
- Support both Basic Auth and form-encoded client credentials
- Validate required parameters per RFC 6749
- Handle malformed requests gracefully

**ScopeValidator.java**:
- Validate requested scopes against client allowed scopes
- Implement scope hierarchy if needed
- Generate appropriate scope strings for tokens

**ClientSecretHasher.java**:
- Secure client secret hashing (Argon2id or BCrypt)
- Secure secret verification
- Timing attack resistance

### 7. Configuration Extensions

**OAuth2Config.java**:
- Spring configuration for OAuth 2.0 components
- JWT signing key configuration
- Token expiration defaults
- Scope configuration
- Error response configuration

**SecurityConfig.java** (extend existing):
- Add OAuth 2.0 security configuration
- Bearer token validation
- Resource server configuration

## Validation Criteria

### Unit Tests (≥90% coverage)

1. **OAuth2TokenService Tests**:
   - Valid token generation → proper JWT structure
   - Token validation → correct true/false results
   - Claims extraction → accurate claim values
   - Expired token handling → proper rejection
   - Invalid signature → proper rejection

2. **ClientCredentialsService Tests**:
   - Valid client credentials → successful token response
   - Invalid client_secret → error response with proper error code
   - Non-existent client → error response
   - Disabled client → access_denied error
   - Invalid grant_type → unsupported_grant_type error
   - Invalid scope → invalid_scope error

3. **OAuth2RequestParser Tests**:
   - Valid form-encoded request → correct TokenRequest object
   - Valid Basic Auth request → correct client credentials extraction
   - Missing required parameters → proper validation errors
   - Malformed requests → graceful error handling

4. **ScopeValidator Tests**:
   - Valid scope combinations → allowed
   - Invalid scopes → rejected with error
   - Empty scope → default scope assignment
   - Scope hierarchy → proper validation

### Integration Tests
1. **Full OAuth 2.0 Flow Tests**:
   - Complete client credentials flow → valid access token
   - Token validation workflow → correct validation results
   - Error scenarios → proper error responses
   - Performance under load → latency requirements met

### Security Tests
1. **Credential Protection**:
   - No plaintext client secrets in logs
   - No client secrets in error messages
   - Secure hash comparison for client authentication

2. **Token Security**:
   - JWT signature validation
   - Token expiration enforcement
   - No sensitive data in token claims

### Performance Tests
1. **Latency Validation**:
   - Token generation < 100ms (excluding I/O)
   - Token validation < 50ms
   - Client authentication < 30ms

## Deliverables
1. Extended Maven project with OAuth 2.0 dependencies
2. All OAuth 2.0 domain models and services implemented
3. Comprehensive unit test suite (≥90% coverage)
4. Integration tests for OAuth 2.0 flows
5. Updated README.md with OAuth 2.0 usage examples
6. Performance benchmark results for OAuth 2.0 operations
7. Security validation report for OAuth 2.0 implementation

## Success Criteria
- [ ] All unit tests pass (both existing and new OAuth 2.0 tests)
- [ ] Code coverage ≥ 90% for OAuth 2.0 components
- [ ] OAuth 2.0 specification compliance (RFC 6749 Section 4.4)
- [ ] No security vulnerabilities in dependency scan
- [ ] Performance targets met in local testing
- [ ] Clean architecture principles maintained
- [ ] No AWS dependencies in domain layer
- [ ] Backward compatibility with existing Basic Auth functionality

## OAuth 2.0 Compliance Checklist
- [ ] Implements Client Credentials Grant (RFC 6749 Section 4.4)
- [ ] Proper error response format (RFC 6749 Section 5.2)
- [ ] Client authentication via HTTP Basic or form parameters
- [ ] Scope parameter handling
- [ ] Bearer token format compliance
- [ ] Security considerations addressed (RFC 6749 Section 10)

## Next Step Preview
Step 2 will focus on AWS Lambda integration, API Gateway configuration for OAuth 2.0 endpoints, and infrastructure updates while maintaining clean separation between domain and infrastructure layers. 