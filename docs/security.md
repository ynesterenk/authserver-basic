# Security Model and Authentication Flow

## Overview

The Java Authorization Server implements a defense-in-depth security model with multiple layers of protection. This document details the security architecture, authentication flows, and security considerations for both HTTP Basic Authentication and OAuth 2.0 Client Credentials Grant.

## Security Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                    Security Layers                                  │
│                                                                     │
│  ┌─────────────────────────────────────────────────────────────────┐ │
│  │                    Network Layer                                │ │
│  │  • TLS 1.2+ Encryption                                        │ │
│  │  • WAF Protection                                              │ │
│  │  • API Gateway Rate Limiting                                   │ │
│  │  • VPC Network Isolation                                       │ │
│  └─────────────────────────────────────────────────────────────────┘ │
│                                  │                                   │
│  ┌─────────────────────────────────────────────────────────────────┐ │
│  │                 Application Layer                               │ │
│  │  • Input Validation & Sanitization                            │ │
│  │  • Authentication & Authorization                              │ │
│  │  • Request/Response Filtering                                  │ │
│  │  • Security Headers                                            │ │
│  └─────────────────────────────────────────────────────────────────┘ │
│                                  │                                   │
│  ┌─────────────────────────────────────────────────────────────────┐ │
│  │                   Domain Layer                                  │ │
│  │  • Cryptographic Security                                      │ │
│  │  • Timing Attack Protection                                    │ │
│  │  • Business Logic Validation                                   │ │
│  │  • Secure Token Generation                                     │ │
│  └─────────────────────────────────────────────────────────────────┘ │
│                                  │                                   │
│  ┌─────────────────────────────────────────────────────────────────┐ │
│  │                    Data Layer                                   │ │
│  │  • Encrypted Secrets Storage                                   │ │
│  │  • Access Control (IAM)                                        │ │
│  │  • Audit Logging                                               │ │
│  │  • Data Encryption at Rest                                     │ │
│  └─────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────┘
```

## Authentication Flows

### HTTP Basic Authentication Flow

#### Security Considerations - Basic Auth

1. **Header Processing**:
   - Validates `Authorization` header format
   - Securely decodes Base64 encoded credentials
   - Sanitizes input to prevent injection attacks

2. **Credential Validation**:
   - Constant-time username lookup to prevent timing attacks
   - Argon2id password verification with memory-hard function
   - User status validation (ACTIVE/DISABLED)

3. **Response Security**:
   - No credential information in response
   - Consistent response timing regardless of user existence
   - Secure error messages without information disclosure

### OAuth 2.0 Client Credentials Flow

#### Security Considerations - OAuth 2.0

1. **Client Authentication**:
   - Supports both form-encoded and Basic Auth client credentials
   - Argon2id hashing for client secrets
   - Client status validation (ACTIVE/INACTIVE)

2. **Scope Validation**:
   - Validates requested scopes against client's allowed scopes
   - Implements principle of least privilege
   - Supports fine-grained access control

3. **Token Security**:
   - JWT tokens with cryptographic signatures (HMAC-SHA256)
   - Configurable token expiration
   - Secure random token generation

## Cryptographic Security

### Password Hashing - Argon2id

#### Configuration
```java
public class PasswordHasher {
    // Argon2id parameters optimized for security vs performance
    private static final int MEMORY_COST = 65536;  // 64 MB
    private static final int TIME_COST = 3;        // 3 iterations
    private static final int PARALLELISM = 1;      // Single thread
    private static final int HASH_LENGTH = 32;     // 256-bit hash
}
```

#### Security Properties
- **Memory-hard function**: Resistant to GPU-based attacks
- **Time-memory trade-off resistant**: Argon2id variant
- **Cryptographically secure salts**: Prevents rainbow table attacks
- **Configurable parameters**: Tunable security vs performance

### JWT Token Security

#### Token Structure
```json
{
  "header": {
    "alg": "HS256",
    "typ": "JWT"
  },
  "payload": {
    "iss": "auth-server",
    "sub": "client-id",
    "aud": "api-resource",
    "exp": 1672534800,
    "iat": 1672531200,
    "scope": "read write",
    "client_id": "client-123"
  }
}
```

## Timing Attack Protection

### Constant Time Operations

The system implements constant-time operations to prevent timing attacks:

- **Username lookups**: Fixed execution time regardless of user existence
- **Password verification**: Consistent timing for valid and invalid passwords
- **Client credential validation**: Equal processing time for all client authentication attempts
- **Dummy operations**: Performed for non-existent users to maintain timing consistency

## Input Validation and Sanitization

### Request Validation

- **Username validation**: Length limits, character restrictions
- **Password validation**: Length limits, secure handling
- **Client ID validation**: Format validation, length restrictions
- **Scope validation**: Allowed characters, length limits
- **Grant type validation**: Whitelist of supported grant types

### Output Sanitization

- **Username masking**: Partial masking in logs (e.g., `a***e`)
- **Error message sanitization**: Removal of sensitive information
- **Log data sanitization**: Scrubbing of credentials and secrets

## Security Headers and CORS

### Security Headers

- **X-Frame-Options**: Prevents clickjacking attacks
- **X-Content-Type-Options**: Prevents MIME type sniffing
- **X-XSS-Protection**: Cross-site scripting protection
- **Strict-Transport-Security**: Enforces HTTPS connections
- **Content-Security-Policy**: Restricts resource loading
- **Referrer-Policy**: Controls referrer information

### CORS Configuration

- **Allowed origins**: Specific domain whitelist
- **Allowed methods**: POST and OPTIONS only
- **Allowed headers**: Content-Type and Authorization
- **Credentials**: Disabled for security
- **Max age**: Appropriate caching duration

## Error Handling Security

### Secure Error Responses

- **Generic error messages**: Prevents information disclosure
- **Consistent error format**: RFC-compliant OAuth error responses
- **Detailed internal logging**: Full context for debugging
- **Rate limiting**: Protection against brute force attacks

### Information Disclosure Prevention

- **Error message mapping**: Internal errors mapped to safe external messages
- **Stack trace filtering**: Sensitive information removed from responses
- **Log sanitization**: Credentials and secrets never logged

## Secrets Management Security

### AWS Secrets Manager Integration

- **Encrypted storage**: KMS-encrypted secrets at rest
- **Access control**: IAM-based permissions
- **Automatic rotation**: Configurable secret rotation
- **Versioning**: Support for secret versions and rollback
- **Audit logging**: All secret access logged

### Secret Caching Security

- **TTL-based expiration**: Automatic cache invalidation
- **Memory clearing**: Best-effort clearing of cached secrets
- **Secure removal**: Proper cleanup on cache eviction
- **Access logging**: All cache operations logged

## Compliance and Standards

### OAuth 2.0 Security Best Practices (RFC 6749, RFC 6819)

- **Client Authentication**: Strong client secret requirements
- **Scope Limitation**: Principle of least privilege for scopes
- **Token Expiration**: Short-lived access tokens
- **Secure Transport**: TLS 1.2+ required for all communications
- **Error Handling**: RFC-compliant error responses without information disclosure

### OWASP Security Guidelines

- **Input Validation**: Comprehensive input validation and sanitization
- **Output Encoding**: Proper encoding of all outputs
- **Authentication**: Strong authentication mechanisms
- **Session Management**: Stateless token-based authentication
- **Access Control**: Proper authorization checks
- **Cryptographic Storage**: Secure storage of secrets and credentials
- **Error Handling**: Secure error handling without information leakage
- **Data Protection**: Encryption in transit and at rest
- **Logging**: Comprehensive security event logging
- **Communication Security**: TLS encryption for all communications

### Security Monitoring and Alerting

#### Security Metrics
- Failed authentication attempts per minute
- Unusual client IP addresses
- Token generation rate spikes
- Error rate thresholds
- Response time anomalies

#### Alert Conditions
- Failed auth attempts > 10 per minute from single IP
- New client IP addresses accessing system
- Error rate > 5% for 5 consecutive minutes
- Response time > 2x normal for 10 minutes
- Multiple invalid client credentials attempts

## Audit and Monitoring

### Security Event Logging

All security-relevant events are logged with structured data:

- **Authentication attempts**: Success/failure with masked usernames
- **Token generation**: Client ID and scopes
- **Suspicious activity**: Rate limiting violations, invalid requests
- **System events**: Startup, configuration changes, errors

### Rate Limiting Detection

- **IP-based rate limiting**: Prevents brute force attacks
- **Client-based rate limiting**: Prevents client abuse
- **Automatic blocking**: Temporary blocking of suspicious IPs
- **Alert generation**: Notifications for security events

This comprehensive security model ensures that the Java Authorization Server maintains high security standards while providing reliable authentication and authorization services for both basic authentication and OAuth 2.0 flows.
