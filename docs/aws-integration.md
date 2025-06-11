# AWS Integration Guide

## Overview

The Java Authorization Server is designed as a cloud-native application optimized for AWS services. This document details the AWS integration architecture, configuration, and deployment patterns for both Basic Authentication and OAuth 2.0 functionality.

## AWS Services Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                         AWS Cloud                                   │
│                                                                     │
│  ┌─────────────────────────────────────────────────────────────────┐ │
│  │                    API Gateway                                  │ │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────────┐ │ │
│  │  │ /auth/*     │  │ /oauth/*    │  │ Custom Domain Name      │ │ │
│  │  │ (Basic)     │  │ (OAuth2)    │  │ & SSL Certificate       │ │ │
│  │  └─────────────┘  └─────────────┘  └─────────────────────────┘ │ │
│  └─────────────────────────────────────────────────────────────────┘ │
│             │                    │                                   │
│             ▼                    ▼                                   │
│  ┌─────────────────┐    ┌─────────────────┐                         │
│  │ Lambda Function │    │ Lambda Function │                         │
│  │ auth-server-dev │    │ oauth2-server-  │                         │
│  │                 │    │ dev             │                         │
│  │ Runtime: Java21 │    │ Runtime: Java21 │                         │
│  │ Memory: 512MB   │    │ Memory: 512MB   │                         │
│  │ Timeout: 30s    │    │ Timeout: 30s    │                         │
│  └─────────────────┘    └─────────────────┘                         │
│             │                    │                                   │
│             ▼                    ▼                                   │
│  ┌─────────────────────────────────────────────────────────────────┐ │
│  │                    AWS Secrets Manager                         │ │
│  │  ┌─────────────────┐              ┌─────────────────────────┐   │ │
│  │  │ User Credentials│              │ OAuth Client Secrets    │   │ │
│  │  │ Secret          │              │ Secret                  │   │ │
│  │  │                 │              │                         │   │ │
│  │  │ • Encrypted     │              │ • Client Configurations │   │ │
│  │  │ • Auto-rotation │              │ • Hashed Secrets        │   │ │
│  │  └─────────────────┘              └─────────────────────────┘   │ │
│  └─────────────────────────────────────────────────────────────────┘ │
│                                   │                                   │
│  ┌─────────────────────────────────────────────────────────────────┐ │
│  │                    CloudWatch Services                         │ │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────────┐ │ │
│  │  │ CloudWatch  │  │ CloudWatch  │  │ X-Ray Distributed       │ │ │
│  │  │ Logs        │  │ Metrics     │  │ Tracing                 │ │ │
│  │  └─────────────┘  └─────────────┘  └─────────────────────────┘ │ │
│  └─────────────────────────────────────────────────────────────────┘ │
│                                                                     │
│  ┌─────────────────────────────────────────────────────────────────┐ │
│  │                      IAM Roles & Policies                      │ │
│  │  ┌─────────────────────────────────────────────────────────────┐ │ │
│  │  │ lambda-auth-server-role                                     │ │ │
│  │  │ • AWSLambdaBasicExecutionRole                              │ │ │
│  │  │ • SecretsManager:GetSecretValue                            │ │ │
│  │  │ • CloudWatch:PutMetricData                                 │ │ │
│  │  │ • X-Ray:PutTraceSegments                                   │ │ │
│  │  └─────────────────────────────────────────────────────────────┘ │ │
│  └─────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────┘
```

## Lambda Function Configuration

### 1. Basic Authentication Lambda

#### Function Configuration
```yaml
Function Name: auth-server-dev
Runtime: java21
Handler: com.example.auth.infrastructure.LambdaHandler::handleRequest
Memory: 512 MB
Timeout: 30 seconds
Architecture: x86_64
Package Type: Zip
```

#### Environment Variables
```bash
SPRING_PROFILES_ACTIVE=aws
CREDENTIAL_SECRET_ARN=arn:aws:secretsmanager:us-east-1:ACCOUNT:secret:auth-users-XXXXX
CACHE_TTL_MINUTES=5
LOG_LEVEL=INFO
AWS_REGION=us-east-1
```

#### Handler Implementation Details
```java
// Entry point for Basic Authentication requests
public APIGatewayV2HTTPResponse handleRequest(
    APIGatewayV2HTTPEvent request, 
    Context context
) {
    // 1. Parse Authorization header
    // 2. Extract username/password
    // 3. Call AuthenticatorService
    // 4. Return formatted response
}
```

### 2. OAuth2 Lambda Function

#### Function Configuration
```yaml
Function Name: oauth2-server-dev
Runtime: java21
Handler: com.example.auth.infrastructure.oauth.OAuth2LambdaHandler::handleRequest
Memory: 512 MB
Timeout: 30 seconds
Architecture: x86_64
Package Type: Zip
```

#### Environment Variables
```bash
SPRING_PROFILES_ACTIVE=aws
OAUTH_CLIENT_SECRET_ARN=arn:aws:secretsmanager:us-east-1:ACCOUNT:secret:oauth-clients-XXXXX
JWT_SECRET_KEY=your-jwt-signing-key
TOKEN_EXPIRATION_SECONDS=3600
CACHE_TTL_MINUTES=5
LOG_LEVEL=INFO
AWS_REGION=us-east-1
```

#### Handler Implementation Details
```java
// Entry point for OAuth 2.0 requests
public APIGatewayV2HTTPResponse handleRequest(
    APIGatewayV2HTTPEvent request, 
    Context context
) {
    String path = request.getRequestContext().getHttp().getPath();
    
    if (path.equals("/oauth/token")) {
        return handleTokenRequest(request, context);
    } else if (path.equals("/oauth/introspect")) {
        return handleIntrospectionRequest(request, context);
    }
    
    return createErrorResponse(405, "Method not allowed");
}
```

## API Gateway Configuration

### 1. REST API Setup

#### API Configuration
```yaml
API Name: auth-server-dev-api
Description: Authentication and OAuth2 API
Endpoint Type: Regional
Binary Media Types: []
Minimum Compression Size: 1024
```

#### Resource Structure
```
/
├── auth
│   └── POST (→ auth-server-dev Lambda)
└── oauth
    ├── token
    │   └── POST (→ oauth2-server-dev Lambda)
    └── introspect
        └── POST (→ oauth2-server-dev Lambda)
```

### 2. Method Configuration

#### POST /auth Method
```yaml
Authorization: None
Request Validation: None
Integration Type: AWS_PROXY
Integration Method: POST
Lambda Function: arn:aws:lambda:REGION:ACCOUNT:function:auth-server-dev
```

#### POST /oauth/token Method
```yaml
Authorization: None
Request Validation: None
Integration Type: AWS_PROXY
Integration Method: POST
Lambda Function: arn:aws:lambda:REGION:ACCOUNT:function:oauth2-server-dev
```

#### POST /oauth/introspect Method
```yaml
Authorization: None
Request Validation: None
Integration Type: AWS_PROXY
Integration Method: POST  
Lambda Function: arn:aws:lambda:REGION:ACCOUNT:function:oauth2-server-dev
```

### 3. CORS Configuration

```yaml
Access-Control-Allow-Origin: '*'
Access-Control-Allow-Headers: 'Content-Type,Authorization'
Access-Control-Allow-Methods: 'POST,OPTIONS'
Access-Control-Max-Age: 86400
```

### 4. Throttling and Quotas

```yaml
Throttle Settings:
  Rate: 1000 requests per second
  Burst: 2000 requests
  
Usage Plan:
  Quota: 1000000 requests per month
  Throttle: 500 requests per second per API key
```

## AWS Secrets Manager Integration

### 1. User Credentials Secret

#### Secret Structure
```json
{
  "SecretName": "auth-users",
  "SecretValue": {
    "alice": {
      "passwordHash": "$argon2id$v=19$m=65536,t=3,p=1$...",
      "status": "ACTIVE",
      "roles": ["admin", "user"]
    },
    "bob": {
      "passwordHash": "$argon2id$v=19$m=65536,t=3,p=1$...",
      "status": "DISABLED",
      "roles": ["user"]
    }
  }
}
```

#### Access Pattern
```java
@Service
public class SecretsManagerUserRepository implements UserRepository {
    
    @Cacheable(value = "users", key = "#username")
    public Optional<User> findByUsername(String username) {
        // 1. Get secret from AWS Secrets Manager
        // 2. Parse JSON content
        // 3. Convert to User domain object
        // 4. Cache result for performance
    }
}
```

### 2. OAuth Client Secret

#### Secret Structure
```json
{
  "SecretName": "oauth-clients",
  "SecretValue": {
    "client-1": {
      "clientSecretHash": "$argon2id$v=19$m=65536,t=3,p=1$...",
      "status": "ACTIVE",
      "allowedScopes": ["read", "write"],
      "allowedGrantTypes": ["client_credentials"],
      "tokenExpirationSeconds": 3600,
      "description": "Production API Client"
    },
    "client-2": {
      "clientSecretHash": "$argon2id$v=19$m=65536,t=3,p=1$...",
      "status": "ACTIVE", 
      "allowedScopes": ["read"],
      "allowedGrantTypes": ["client_credentials"],
      "tokenExpirationSeconds": 1800,
      "description": "Limited Read-Only Client"
    }
  }
}
```

### 3. Secret Rotation Strategy

#### Automated Rotation
```yaml
Rotation Configuration:
  Enabled: true
  Rotation Interval: 30 days
  Lambda Function: auth-secret-rotation-function
  
Rotation Process:
  1. Generate new credentials
  2. Update AWSPENDING version
  3. Test new credentials
  4. Promote to AWSCURRENT
  5. Delete AWSPENDING
```

## IAM Policies and Roles

### 1. Lambda Execution Role

#### Role Trust Policy
```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": {
        "Service": "lambda.amazonaws.com"
      },
      "Action": "sts:AssumeRole"
    }
  ]
}
```

#### Permissions Policy
```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "logs:CreateLogGroup",
        "logs:CreateLogStream",
        "logs:PutLogEvents"
      ],
      "Resource": "arn:aws:logs:*:*:*"
    },
    {
      "Effect": "Allow",
      "Action": [
        "secretsmanager:GetSecretValue"
      ],
      "Resource": [
        "arn:aws:secretsmanager:*:*:secret:auth-users-*",
        "arn:aws:secretsmanager:*:*:secret:oauth-clients-*"
      ]
    },
    {
      "Effect": "Allow",
      "Action": [
        "cloudwatch:PutMetricData"
      ],
      "Resource": "*"
    },
    {
      "Effect": "Allow",
      "Action": [
        "xray:PutTraceSegments",
        "xray:PutTelemetryRecords"
      ],
      "Resource": "*"
    }
  ]
}
```

### 2. API Gateway Service Role

#### Lambda Invoke Permission
```bash
aws lambda add-permission \
  --function-name auth-server-dev \
  --statement-id apigateway-invoke \
  --action lambda:InvokeFunction \
  --principal apigateway.amazonaws.com \
  --source-arn "arn:aws:execute-api:REGION:ACCOUNT:API_ID/*/*"
```

## CloudWatch Integration

### 1. Logging Configuration

#### Log Group Structure
```
/aws/lambda/auth-server-dev
├── 2024/01/15/[$LATEST]abcdef123456...
└── 2024/01/15/[$LATEST]ghijkl789012...

/aws/lambda/oauth2-server-dev  
├── 2024/01/15/[$LATEST]mnopqr345678...
└── 2024/01/15/[$LATEST]stuvwx901234...
```

#### Structured Logging Format
```json
{
  "timestamp": "2024-01-15T10:30:00.000Z",
  "level": "INFO",
  "logger": "com.example.auth.infrastructure.LambdaHandler",
  "message": "Authentication successful for user: a***e",
  "requestId": "12345678-1234-1234-1234-123456789012",
  "duration": 15,
  "success": true,
  "clientIp": "203.0.113.0"
}
```

### 2. Custom Metrics

#### Authentication Metrics
```java
// Custom CloudWatch metrics emitted by the application
cloudWatch.putMetricData(PutMetricDataRequest.builder()
    .namespace("AuthServer/BasicAuth")
    .metricData(MetricDatum.builder()
        .metricName("AuthenticationSuccess")
        .value(1.0)
        .unit(StandardUnit.COUNT)
        .timestamp(Instant.now())
        .build())
    .build());
```

#### OAuth Metrics
```java
// OAuth-specific metrics
cloudWatch.putMetricData(PutMetricDataRequest.builder()
    .namespace("AuthServer/OAuth2")
    .metricData(
        MetricDatum.builder()
            .metricName("TokenGenerated")
            .value(1.0)
            .unit(StandardUnit.COUNT)
            .dimensions(Dimension.builder()
                .name("ClientId")
                .value(maskedClientId)
                .build())
            .build(),
        MetricDatum.builder()
            .metricName("TokenIntrospection")
            .value(1.0)
            .unit(StandardUnit.COUNT)
            .build()
    )
    .build());
```

### 3. CloudWatch Alarms

#### High Error Rate Alarm
```yaml
AlarmName: auth-server-high-error-rate
MetricName: Errors
Namespace: AWS/Lambda
Dimensions:
  FunctionName: auth-server-dev
Statistic: Sum
Period: 300
EvaluationPeriods: 2
Threshold: 10
ComparisonOperator: GreaterThanThreshold
TreatMissingData: notBreaching
```

#### High Latency Alarm  
```yaml
AlarmName: oauth2-server-high-latency
MetricName: Duration
Namespace: AWS/Lambda
Dimensions:
  FunctionName: oauth2-server-dev
Statistic: Average
Period: 300
EvaluationPeriods: 3
Threshold: 5000
ComparisonOperator: GreaterThanThreshold
```

## Performance Optimization

### 1. Cold Start Optimization

#### Lambda Configuration
```yaml
Reserved Concurrency: 10
Provisioned Concurrency: 2
Runtime: java21 (GraalVM native image future consideration)
Memory Allocation: 512MB (optimal for Java workloads)
```

#### Code Optimization
```java
// Static initialization for better cold start performance
private static final PasswordHasher PASSWORD_HASHER = new PasswordHasher();
private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

// Connection reuse
private static final SecretsManagerClient SECRETS_CLIENT = 
    SecretsManagerClient.builder()
        .region(Region.of(System.getenv("AWS_REGION")))
        .build();
```

### 2. Caching Strategy

#### Multi-Level Caching
```java
// 1. Lambda container memory cache
private static final Map<String, User> USER_CACHE = new ConcurrentHashMap<>();

// 2. External cache (ElastiCache) for production
@Cacheable(value = "users", key = "#username", unless = "#result == null")
public Optional<User> findByUsername(String username) {
    // Implementation with cache-aside pattern
}

// 3. TTL-based cache invalidation
@CacheEvict(value = "users", key = "#username")
@Scheduled(fixedRate = 300000) // 5 minutes
public void evictExpiredCache() {
    // Cache cleanup logic
}
```

### 3. Connection Pooling

#### AWS SDK Configuration
```java
@Configuration
public class AwsConfig {
    
    @Bean
    public SecretsManagerClient secretsManagerClient() {
        return SecretsManagerClient.builder()
            .region(Region.of(System.getenv("AWS_REGION")))
            .overrideConfiguration(ClientOverrideConfiguration.builder()
                .retryPolicy(RetryPolicy.builder()
                    .numRetries(3)
                    .build())
                .build())
            .build();
    }
}
```

## Error Handling and Resilience

### 1. Circuit Breaker Pattern

```java
@Component
public class ResilientSecretsManagerClient {
    
    private final CircuitBreaker circuitBreaker;
    
    @Retryable(value = {SecretsManagerException.class}, maxAttempts = 3)
    public String getSecretValue(String secretArn) {
        return circuitBreaker.executeSupplier(() -> {
            GetSecretValueResponse response = secretsClient
                .getSecretValue(GetSecretValueRequest.builder()
                    .secretId(secretArn)
                    .build());
            return response.secretString();
        });
    }
}
```

### 2. Graceful Degradation

```java
public AuthenticationResult authenticate(AuthenticationRequest request) {
    try {
        // Primary authentication flow
        return primaryAuthentication(request);
    } catch (AwsServiceException e) {
        // Log error and attempt fallback
        logger.warn("AWS service error, attempting fallback", e);
        return fallbackAuthentication(request);
    }
}
```

## Monitoring and Alerting

### 1. X-Ray Distributed Tracing

#### Tracing Configuration
```java
@Configuration
@EnableAspectJAutoProxy
public class TracingConfig {
    
    @Bean
    public XRayInterceptor xrayInterceptor() {
        return new XRayInterceptor();
    }
    
    @Bean
    public Filter TracingFilter() {
        return new AWSXRayServletFilter("auth-server");
    }
}
```

#### Custom Segments
```java
@XRayEnabled
public AuthenticationResult authenticate(AuthenticationRequest request) {
    Subsegment subsegment = AWSXRay.beginSubsegment("user-authentication");
    try {
        subsegment.putAnnotation("username", maskUsername(request.getUsername()));
        subsegment.putMetadata("request", sanitizeRequest(request));
        
        AuthenticationResult result = performAuthentication(request);
        
        subsegment.putAnnotation("success", result.isAllowed());
        return result;
    } finally {
        AWSXRay.endSubsegment();
    }
}
```

### 2. Custom Dashboards

#### CloudWatch Dashboard Configuration
```json
{
  "widgets": [
    {
      "type": "metric",
      "properties": {
        "metrics": [
          ["AWS/Lambda", "Duration", "FunctionName", "auth-server-dev"],
          ["AWS/Lambda", "Duration", "FunctionName", "oauth2-server-dev"]
        ],
        "period": 300,
        "stat": "Average",
        "region": "us-east-1",
        "title": "Lambda Duration"
      }
    },
    {
      "type": "metric", 
      "properties": {
        "metrics": [
          ["AuthServer/BasicAuth", "AuthenticationSuccess"],
          ["AuthServer/BasicAuth", "AuthenticationFailure"],
          ["AuthServer/OAuth2", "TokenGenerated"],
          ["AuthServer/OAuth2", "TokenIntrospection"]
        ],
        "period": 300,
        "stat": "Sum",
        "region": "us-east-1",
        "title": "Authentication Metrics"
      }
    }
  ]
}
```

## Deployment Automation

### 1. CloudFormation Template

#### Template Structure
```yaml
AWSTemplateFormatVersion: '2010-09-09'
Description: 'Java Authorization Server with OAuth 2.0 support'

Parameters:
  Environment:
    Type: String
    Default: dev
    AllowedValues: [dev, staging, prod]
  
Resources:
  # Lambda Functions
  BasicAuthFunction:
    Type: AWS::Lambda::Function
    Properties:
      FunctionName: !Sub 'auth-server-${Environment}'
      Runtime: java21
      Handler: com.example.auth.infrastructure.LambdaHandler::handleRequest
      
  OAuth2Function:
    Type: AWS::Lambda::Function
    Properties:
      FunctionName: !Sub 'oauth2-server-${Environment}'
      Runtime: java21
      Handler: com.example.auth.infrastructure.oauth.OAuth2LambdaHandler::handleRequest
      
  # API Gateway
  AuthAPI:
    Type: AWS::ApiGateway::RestApi
    Properties:
      Name: !Sub 'auth-server-${Environment}-api'
      
  # IAM Roles and Policies
  LambdaExecutionRole:
    Type: AWS::IAM::Role
    Properties:
      AssumeRolePolicyDocument: # Trust policy
      ManagedPolicyArns:
        - arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole
      Policies: # Custom policies for Secrets Manager, CloudWatch
```

### 2. CI/CD Pipeline Integration

#### GitHub Actions Workflow
```yaml
name: Deploy Auth Server
on:
  push:
    branches: [main]
    
jobs:
  deploy:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      
      - name: Setup Java
        uses: actions/setup-java@v3
        with:
          java-version: '21'
          
      - name: Build Application
        run: mvn clean package -DskipTests
        
      - name: Deploy to AWS
        env:
          AWS_ACCESS_KEY_ID: ${{ secrets.AWS_ACCESS_KEY_ID }}
          AWS_SECRET_ACCESS_KEY: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
        run: |
          aws cloudformation deploy \
            --template-file infrastructure/auth-server.yaml \
            --stack-name auth-server-dev \
            --parameter-overrides Environment=dev \
            --capabilities CAPABILITY_IAM
```

## Security Best Practices

### 1. Network Security
- **VPC Integration**: Run Lambda functions in private subnets
- **Security Groups**: Restrict outbound traffic to necessary services
- **WAF Integration**: Web Application Firewall for API Gateway

### 2. Data Encryption
- **Secrets Encryption**: KMS-encrypted secrets in Secrets Manager
- **Transit Encryption**: TLS 1.2+ for all API communications
- **At-Rest Encryption**: Encrypted CloudWatch logs

### 3. Access Control
- **Least Privilege**: Minimal IAM permissions for each component
- **Resource-Based Policies**: Fine-grained access to Secrets Manager
- **API Keys**: Optional API key authentication for additional security 