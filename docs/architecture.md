# Architecture Overview

## System Design

The Java Authorization Server is built using **Clean Hexagonal Architecture** principles, providing a secure, scalable, and maintainable authentication and authorization service. The system supports both HTTP Basic Authentication and OAuth 2.0 Client Credentials Grant flow.

## High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        API Gateway                              │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐ │
│  │ POST /auth      │  │ POST /oauth/    │  │ POST /oauth/    │ │
│  │ /validate       │  │ token           │  │ introspect      │ │
│  └─────────────────┘  └─────────────────┘  └─────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
            │                      │                      │
            ▼                      ▼                      ▼
┌─────────────────┐    ┌─────────────────────────────────────────┐
│ Basic Auth      │    │        OAuth2 Lambda Handler           │
│ Lambda Handler  │    │                                         │
│                 │    │  ┌─────────────┐  ┌─────────────────┐   │
│                 │    │  │ Token       │  │ Introspection   │   │
│                 │    │  │ Endpoint    │  │ Endpoint        │   │
│                 │    │  └─────────────┘  └─────────────────┘   │
└─────────────────┘    └─────────────────────────────────────────┘
            │                              │
            ▼                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                    Domain Layer                                 │
│                                                                 │
│  ┌─────────────────────────────────────────────────────────────┐ │
│  │                   Core Services                             │ │
│  │  ┌─────────────────┐    ┌─────────────────────────────────┐ │ │
│  │  │ Basic Auth      │    │ OAuth 2.0 Services              │ │ │
│  │  │ Service         │    │  ┌─────────────────────────────┐ │ │ │
│  │  │                 │    │  │ ClientCredentialsService    │ │ │ │  
│  │  │                 │    │  │ OAuth2TokenService          │ │ │ │
│  │  │                 │    │  │ TokenIntrospectionService   │ │ │ │
│  │  └─────────────────┘    │  └─────────────────────────────┘ │ │ │
│  │                         └─────────────────────────────────┘ │ │
│  └─────────────────────────────────────────────────────────────┘ │
│                                                                 │
│  ┌─────────────────────────────────────────────────────────────┐ │
│  │                  Domain Models                              │ │
│  │  ┌─────────────────┐    ┌─────────────────────────────────┐ │ │
│  │  │ User            │    │ OAuth Models                    │ │ │
│  │  │ AuthRequest     │    │  ┌─────────────────────────────┐ │ │ │
│  │  │ AuthResult      │    │  │ OAuthClient                 │ │ │ │
│  │  │                 │    │  │ TokenRequest                │ │ │ │
│  │  │                 │    │  │ TokenResponse               │ │ │ │
│  │  │                 │    │  │ OAuthError                  │ │ │ │
│  │  └─────────────────┘    │  └─────────────────────────────┘ │ │ │
│  │                         └─────────────────────────────────┘ │ │
│  └─────────────────────────────────────────────────────────────┘ │
│                                                                 │
│  ┌─────────────────────────────────────────────────────────────┐ │
│  │                    Ports (Interfaces)                      │ │
│  │  ┌─────────────────┐    ┌─────────────────────────────────┐ │ │
│  │  │ UserRepository  │    │ OAuthClientRepository           │ │ │
│  │  │                 │    │                                 │ │ │
│  │  └─────────────────┘    └─────────────────────────────────┘ │ │
│  └─────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
            │                              │
            ▼                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                 Infrastructure Layer                            │
│                                                                 │
│  ┌─────────────────┐    ┌─────────────────────────────────────┐ │
│  │ User Storage    │    │ OAuth Client Storage                │ │
│  │                 │    │                                     │ │
│  │ • Local Repo    │    │ • InMemory Repository               │ │
│  │ • Secrets Mgr   │    │ • Secrets Manager (Future)         │ │
│  │                 │    │                                     │ │
│  └─────────────────┘    └─────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
```

## Layer Responsibilities

### 1. API Gateway Layer
- **Request Routing**: Routes requests to appropriate Lambda functions
- **Protocol Translation**: HTTP to Lambda event conversion
- **Rate Limiting**: API-level throttling and quotas
- **CORS Handling**: Cross-origin request support
- **Request Validation**: Basic HTTP request validation

### 2. Infrastructure Layer (AWS Lambda Handlers)

#### Basic Authentication Handler
- **Purpose**: Processes HTTP Basic Authentication requests
- **Handler**: `com.example.auth.infrastructure.LambdaHandler`
- **Responsibilities**:
  - HTTP Basic Auth header parsing
  - Request/response transformation
  - Error handling and status code mapping
  - CloudWatch logging

#### OAuth2 Lambda Handler  
- **Purpose**: Processes OAuth 2.0 token and introspection requests
- **Handler**: `com.example.auth.infrastructure.oauth.OAuth2LambdaHandler`
- **Responsibilities**:
  - OAuth 2.0 request parsing (form-encoded)
  - Token endpoint implementation
  - Token introspection endpoint implementation
  - OAuth-specific error response formatting
  - JWT token validation

### 3. Domain Layer (Core Business Logic)

#### Domain Services

##### BasicAuthenticatorService
- **Purpose**: Core basic authentication logic
- **Key Methods**:
  - `authenticate(AuthenticationRequest)`: Validates user credentials
  - Input validation and sanitization
  - Password verification using Argon2id
  - Timing attack protection

##### ClientCredentialsService
- **Purpose**: OAuth 2.0 Client Credentials Grant implementation
- **Key Methods**:
  - `authenticate(TokenRequest)`: Complete OAuth flow
  - `validateClientCredentials()`: Client authentication
  - `validateClientScopes()`: Scope validation
  - `isClientActive()`: Client status verification

##### OAuth2TokenService
- **Purpose**: JWT token generation and validation
- **Key Methods**:
  - `generateAccessToken()`: Creates JWT access tokens
  - `validateToken()`: Validates token signatures and claims
  - `introspectToken()`: Token introspection logic
  - Token expiration and scope management

#### Domain Models

##### Core Authentication Models
- **User**: Immutable user entity with credentials and status
- **AuthenticationRequest**: Basic auth input data
- **AuthenticationResult**: Authentication outcome with metadata

##### OAuth 2.0 Models
- **OAuthClient**: OAuth client configuration with scopes and secrets
- **TokenRequest**: OAuth token request parameters
- **TokenResponse**: Standard OAuth token response
- **OAuthError**: RFC 6749 compliant error responses

#### Domain Utilities
- **PasswordHasher**: Argon2id password hashing
- **BasicAuthDecoder**: HTTP Basic Auth header parsing
- **OAuth2RequestParser**: OAuth request parameter extraction
- **ScopeValidator**: OAuth scope validation logic
- **ClientSecretHasher**: OAuth client secret hashing

### 4. Ports (Dependency Interfaces)
- **UserRepository**: User data access abstraction
- **OAuthClientRepository**: OAuth client data access abstraction

### 5. Infrastructure Adapters

#### Data Storage Adapters
- **LocalUserRepository**: File-based user storage for development
- **SecretsManagerUserRepository**: AWS Secrets Manager integration
- **InMemoryOAuthClientRepository**: In-memory OAuth client storage

## Component Interaction Flow

### Basic Authentication Flow
```
1. API Gateway → Basic Auth Lambda Handler
2. Lambda Handler → BasicAuthDecoder (parse header)
3. BasicAuthDecoder → BasicAuthenticatorService
4. BasicAuthenticatorService → UserRepository (fetch user)
5. BasicAuthenticatorService → PasswordHasher (verify password)  
6. Response flows back through the chain
```

### OAuth 2.0 Token Flow
```
1. API Gateway → OAuth2 Lambda Handler
2. OAuth2 Lambda Handler → OAuth2RequestParser (parse form data)
3. OAuth2RequestParser → ClientCredentialsService
4. ClientCredentialsService → OAuthClientRepository (fetch client)
5. ClientCredentialsService → ClientSecretHasher (verify secret)
6. ClientCredentialsService → ScopeValidator (validate scopes)
7. ClientCredentialsService → OAuth2TokenService (generate token)
8. OAuth2TokenService → JWT library (create signed token)
9. Response flows back through the chain
```

### Token Introspection Flow
```
1. API Gateway → OAuth2 Lambda Handler
2. OAuth2 Lambda Handler → OAuth2TokenService (parse introspection request)
3. OAuth2TokenService → JWT library (validate token signature)
4. OAuth2TokenService → Token claims validation
5. Response with token metadata or active=false
```

## Design Patterns

### 1. Hexagonal Architecture (Ports and Adapters)
- **Benefits**: Clean separation of concerns, testability, technology agnostic core
- **Implementation**: Domain layer isolated from infrastructure concerns
- **Ports**: Interfaces defining external dependencies
- **Adapters**: Infrastructure implementations of ports

### 2. Strategy Pattern
- **Usage**: Multiple authentication mechanisms (Basic Auth, OAuth)
- **Implementation**: Common `AuthenticatorService` interface with different implementations
- **Benefits**: Easy to add new authentication methods

### 3. Factory Pattern  
- **Usage**: Token generation with different configurations
- **Implementation**: `OAuth2TokenService` creates tokens based on client configuration
- **Benefits**: Flexible token creation logic

### 4. Repository Pattern
- **Usage**: Data access abstraction
- **Implementation**: `UserRepository` and `OAuthClientRepository` interfaces
- **Benefits**: Storage technology independence, easy testing

## Security Architecture

### 1. Defense in Depth
- **API Gateway**: Request validation, rate limiting
- **Lambda**: Input sanitization, business logic validation
- **Domain**: Cryptographic operations, timing attack protection

### 2. Cryptographic Security
- **Password Hashing**: Argon2id with configurable parameters
- **Client Secret Hashing**: Same Argon2id algorithm
- **JWT Tokens**: HMAC-SHA256 or RSA signatures
- **Random Generation**: Cryptographically secure random values

### 3. Input Validation
- **Multi-layer Validation**: API Gateway, Lambda handler, Domain service
- **Type Safety**: Strong typing throughout the domain
- **Length Limits**: Prevent buffer overflow and DoS attacks
- **Character Validation**: Prevent injection attacks

## Scalability Considerations

### 1. Stateless Design
- **No Session State**: All authentication state in tokens or requests
- **Horizontal Scaling**: Easy to scale Lambda functions independently
- **Cache-friendly**: User and client data can be cached effectively

### 2. Performance Optimization
- **Cold Start Minimization**: Optimized Lambda package size and initialization
- **Connection Pooling**: Efficient database/external service connections  
- **Caching Strategy**: Multi-level caching (Lambda memory, external cache)

### 3. Throughput Design
- **Async Processing**: Non-blocking I/O where possible
- **Batching**: Efficient bulk operations for data access
- **Circuit Breakers**: Fail-fast patterns for external dependencies

## Monitoring and Observability

### 1. Structured Logging
- **JSON Format**: Machine-readable log entries
- **Correlation IDs**: Request tracing across components
- **Security-Safe**: No credential logging
- **Performance Metrics**: Request duration, cache hit rates

### 2. CloudWatch Integration
- **Metrics**: Custom metrics for authentication success/failure rates
- **Alarms**: Automated alerting for error conditions
- **Dashboards**: Real-time monitoring views

### 3. Distributed Tracing
- **X-Ray Integration**: End-to-end request tracing
- **Performance Profiling**: Identify bottlenecks
- **Error Analysis**: Root cause analysis for failures

## Configuration Management

### 1. Environment-based Configuration
- **Development**: Local repositories, debug logging
- **Staging**: AWS Secrets Manager, production-like settings
- **Production**: Encrypted secrets, optimized performance settings

### 2. Feature Flags
- **OAuth Enablement**: Toggle OAuth functionality
- **Security Levels**: Adjustable security parameters
- **Rate Limiting**: Configurable throttling parameters

### 3. Secrets Management
- **AWS Secrets Manager**: Encrypted credential storage
- **Rotation Support**: Automated secret rotation
- **Access Control**: IAM-based secret access

## Deployment Architecture

### 1. Infrastructure as Code
- **CloudFormation**: Complete infrastructure definition
- **Parameterized**: Environment-specific configurations
- **Version Controlled**: Infrastructure changes tracked

### 2. CI/CD Pipeline
- **Automated Testing**: Unit, integration, and security tests
- **Blue/Green Deployment**: Zero-downtime deployments
- **Rollback Capability**: Quick reversion to previous versions

### 3. Multi-Environment Support
- **Development**: Local development with mocked dependencies
- **Staging**: Production-like environment for testing
- **Production**: High-availability, monitored environment 