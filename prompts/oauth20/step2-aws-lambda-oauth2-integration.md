# Step 2: OAuth 2.0 Client Credentials Flow - AWS Lambda Integration

## Objective
Integrate the OAuth 2.0 Client Credentials Flow with AWS Lambda and extend the existing infrastructure to support OAuth 2.0 endpoints alongside the existing Basic Authentication. This step focuses on infrastructure adapters, AWS services integration, and API Gateway configuration while maintaining clean architecture separation.

## Prerequisites
- Step 1 (OAuth 2.0 Core Domain Implementation) completed successfully
- Existing AWS infrastructure from Basic Auth implementation
- AWS Development environment configured

## Implementation Tasks

### 1. Infrastructure Adapters

**OAuth2LambdaHandler.java**:
```java
@Component
public class OAuth2LambdaHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    
    @Autowired
    private ClientCredentialsService clientCredentialsService;
    
    @Autowired
    private OAuth2TokenService tokenService;
    
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        // Handle /oauth/token endpoint
        // Parse OAuth 2.0 token request
        // Return proper OAuth 2.0 response format
        // Include proper error handling and logging
    }
}
```

**SecretsManagerOAuthClientRepository.java**:
```java
@Repository
public class SecretsManagerOAuthClientRepository implements OAuthClientRepository {
    
    @Autowired
    private AWSSecretsManager secretsManager;
    
    @Value("${oauth.client.secret.arn}")
    private String clientSecretArn;
    
    // Implement secure client credential retrieval from AWS Secrets Manager
    // Include caching mechanism with configurable TTL
    // Handle AWS API failures gracefully
}
```

**ParameterStoreConfigRepository.java**:
```java
@Repository
public class ParameterStoreConfigRepository {
    
    @Autowired
    private AWSSimpleSystemsManagement ssmClient;
    
    // Retrieve OAuth 2.0 configuration from AWS Systems Manager Parameter Store
    // Cache configuration with appropriate TTL
    // Support encrypted parameters for sensitive configuration
}
```

### 2. AWS Secrets Manager Integration

**OAuth Client Storage Structure** (JSON format in Secrets Manager):
```json
{
  "clients": {
    "client-app-1": {
      "clientId": "client-app-1",
      "clientSecretHash": "$argon2id$v=19$m=65536,t=3,p=4$...",
      "status": "ACTIVE",
      "allowedScopes": ["read", "write", "admin"],
      "allowedGrantTypes": ["client_credentials"],
      "tokenExpirationSeconds": 3600,
      "description": "Application 1 Client"
    },
    "client-app-2": {
      "clientId": "client-app-2",
      "clientSecretHash": "$argon2id$v=19$m=65536,t=3,p=4$...",
      "status": "ACTIVE",
      "allowedScopes": ["read"],
      "allowedGrantTypes": ["client_credentials"],
      "tokenExpirationSeconds": 1800,
      "description": "Application 2 Client"
    }
  }
}
```

### 3. AWS Systems Manager Parameter Store Configuration

**OAuth 2.0 Configuration Parameters**:
```
/oauth2/dev/jwt/signing-algorithm = RS256
/oauth2/dev/jwt/issuer = https://auth.example.com
/oauth2/dev/jwt/audience = https://api.example.com
/oauth2/dev/jwt/key-id = oauth2-signing-key-2024
/oauth2/dev/token/default-expiration-seconds = 3600
/oauth2/dev/token/max-expiration-seconds = 7200
/oauth2/dev/scopes/default = read
/oauth2/dev/scopes/available = read,write,admin,delete
```

### 4. JWT Key Management

**AWS KMS Integration** for JWT signing keys:
```java
@Service
public class KMSJWTKeyService {
    
    @Autowired
    private AWSKMS kmsClient;
    
    @Value("${oauth.jwt.kms.key.arn}")
    private String jwtSigningKeyArn;
    
    // Generate and retrieve RSA key pairs from AWS KMS
    // Support key rotation
    // Cache public keys with appropriate TTL
    // Handle KMS API failures gracefully
}
```

### 5. Extended CloudFormation Template

**template.yaml Updates**:

Add OAuth 2.0 specific resources:

```yaml
  # OAuth 2.0 Token Endpoint Lambda Function
  OAuth2TokenFunction:
    Type: AWS::Serverless::Function
    Properties:
      FunctionName: !Sub "${AWS::StackName}-oauth2-token-function"
      CodeUri: target/auth-server-lambda.jar
      Handler: com.example.auth.infrastructure.oauth.OAuth2LambdaHandler::handleRequest
      Description: "OAuth 2.0 Client Credentials Token Endpoint"
      Environment:
        Variables:
          OAUTH_CLIENT_SECRET_ARN: !Ref OAuth2ClientSecretArn
          JWT_SIGNING_KEY_ARN: !Ref JWTSigningKeyArn
          OAUTH_CONFIG_PREFIX: !Sub "/oauth2/${StageName}"
          STAGE: !Ref StageName
      Events:
        OAuth2Token:
          Type: Api
          Properties:
            RestApiId: !Ref AuthApi
            Path: /oauth/token
            Method: POST
        OAuth2Introspect:
          Type: Api
          Properties:
            RestApiId: !Ref AuthApi
            Path: /oauth/introspect
            Method: POST
      Policies:
        - Version: '2012-10-17'
          Statement:
            - Effect: Allow
              Action:
                - secretsmanager:GetSecretValue
              Resource: !Ref OAuth2ClientSecretArn
            - Effect: Allow
              Action:
                - ssm:GetParameter
                - ssm:GetParameters
                - ssm:GetParametersByPath
              Resource: !Sub "arn:${AWS::Partition}:ssm:${AWS::Region}:${AWS::AccountId}:parameter/oauth2/${StageName}/*"
            - Effect: Allow
              Action:
                - kms:Decrypt
                - kms:GetPublicKey
                - kms:Sign
              Resource: !Ref JWTSigningKeyArn
            - Effect: Allow
              Action:
                - logs:CreateLogGroup
                - logs:CreateLogStream
                - logs:PutLogEvents
              Resource: !Sub "arn:${AWS::Partition}:logs:${AWS::Region}:${AWS::AccountId}:log-group:/aws/lambda/${AWS::StackName}-oauth2-token-function*"

  # OAuth 2.0 Client Secrets in Secrets Manager
  OAuth2ClientSecret:
    Type: AWS::SecretsManager::Secret
    Properties:
      Name: !Sub "${AWS::StackName}-oauth2-clients"
      Description: "OAuth 2.0 Client Credentials for Client Credentials Flow"
      SecretString: |
        {
          "clients": {
            "test-client-1": {
              "clientId": "test-client-1",
              "clientSecretHash": "$argon2id$v=19$m=65536,t=3,p=4$example-hash",
              "status": "ACTIVE",
              "allowedScopes": ["read", "write"],
              "allowedGrantTypes": ["client_credentials"],
              "tokenExpirationSeconds": 3600,
              "description": "Test Client 1"
            }
          }
        }

  # JWT Signing Key in KMS
  JWTSigningKey:
    Type: AWS::KMS::Key
    Properties:
      Description: "OAuth 2.0 JWT Signing Key"
      KeyUsage: SIGN_VERIFY
      KeySpec: RSA_2048
      KeyPolicy:
        Version: '2012-10-17'
        Statement:
          - Sid: Enable IAM User Permissions
            Effect: Allow
            Principal:
              AWS: !Sub "arn:${AWS::Partition}:iam::${AWS::AccountId}:root"
            Action: "kms:*"
            Resource: "*"
          - Sid: Allow Lambda Function Access
            Effect: Allow
            Principal:
              AWS: !GetAtt OAuth2TokenFunctionRole.Arn
            Action:
              - kms:Decrypt
              - kms:GetPublicKey
              - kms:Sign
            Resource: "*"

  JWTSigningKeyAlias:
    Type: AWS::KMS::Alias
    Properties:
      AliasName: !Sub "alias/${AWS::StackName}-jwt-signing-key"
      TargetKeyId: !Ref JWTSigningKey

  # OAuth 2.0 Configuration Parameters
  OAuth2JWTIssuerParameter:
    Type: AWS::SSM::Parameter
    Properties:
      Name: !Sub "/oauth2/${StageName}/jwt/issuer"
      Type: String
      Value: !Sub "https://${AuthApi}.execute-api.${AWS::Region}.amazonaws.com/${StageName}"
      Description: "OAuth 2.0 JWT Issuer"

  OAuth2JWTAudienceParameter:
    Type: AWS::SSM::Parameter
    Properties:
      Name: !Sub "/oauth2/${StageName}/jwt/audience"
      Type: String
      Value: !Sub "https://${AuthApi}.execute-api.${AWS::Region}.amazonaws.com/${StageName}"
      Description: "OAuth 2.0 JWT Audience"

  OAuth2DefaultScopesParameter:
    Type: AWS::SSM::Parameter
    Properties:
      Name: !Sub "/oauth2/${StageName}/scopes/default"
      Type: String
      Value: "read"
      Description: "OAuth 2.0 Default Scopes"

  OAuth2AvailableScopesParameter:
    Type: AWS::SSM::Parameter
    Properties:
      Name: !Sub "/oauth2/${StageName}/scopes/available"
      Type: String
      Value: "read,write,admin"
      Description: "OAuth 2.0 Available Scopes"
```

### 6. API Gateway Extensions

**Enhanced CORS Configuration**:
```yaml
      Cors:
        AllowMethods: "'POST, OPTIONS'"
        AllowHeaders: "'Content-Type,Authorization,X-Amz-Date,X-Api-Key,X-Amz-Security-Token'"
        AllowOrigin: "'*'"
```

**Request Validation**:
```yaml
      RequestValidators:
        OAuth2TokenValidator:
          ValidateRequestBody: true
          ValidateRequestParameters: true
      Models:
        OAuth2TokenRequest:
          Type: object
          Required: [grant_type]
          Properties:
            grant_type:
              Type: string
              Pattern: "^client_credentials$"
            client_id:
              Type: string
            client_secret:
              Type: string  
            scope:
              Type: string
```

### 7. Environment Configuration

**application-dev.yml** (for AWS Development environment):
```yaml
oauth2:
  client:
    secret:
      arn: ${OAUTH_CLIENT_SECRET_ARN}
      cache-ttl-minutes: 5
  jwt:
    signing-key:
      arn: ${JWT_SIGNING_KEY_ARN}
    issuer: ${OAUTH_JWT_ISSUER:https://auth.example.com}
    audience: ${OAUTH_JWT_AUDIENCE:https://api.example.com}
  token:
    default-expiration-seconds: 3600
    max-expiration-seconds: 7200
  config:
    parameter-prefix: ${OAUTH_CONFIG_PREFIX:/oauth2/dev}
    cache-ttl-minutes: 10

aws:
  region: ${AWS_REGION:us-east-1}
  kms:
    enabled: true
  ssm:
    enabled: true
  secretsmanager:
    enabled: true
```

## Validation Criteria

### Integration Tests

1. **OAuth 2.0 Token Endpoint Tests**:
   - Valid client credentials → successful token response
   - Invalid client credentials → proper error response
   - Malformed requests → appropriate error handling
   - Token format validation → proper JWT structure
   - Performance testing → meets latency requirements

2. **AWS Services Integration Tests**:
   - Secrets Manager retrieval → successful client data
   - Parameter Store configuration → correct values loaded
   - KMS JWT signing → valid signatures generated
   - Error handling → graceful degradation

3. **API Gateway Integration Tests**:
   - CORS headers → properly configured
   - Request validation → malformed requests rejected
   - Response format → compliant with OAuth 2.0 spec
   - Rate limiting → proper throttling applied

### Security Tests

1. **Credential Security**:
   - Secrets Manager access → proper IAM permissions
   - Client secrets → never logged in plaintext
   - JWT signing keys → securely managed in KMS
   - Network traffic → HTTPS only

2. **OAuth 2.0 Security**:
   - Token validation → signature verification works
   - Scope enforcement → proper scope validation
   - Client authentication → secure secret verification
   - Error responses → no information leakage

### Performance Tests

1. **Lambda Cold Start**:
   - First invocation < 3 seconds
   - Warm invocations < 150ms
   - Memory usage < 512MB

2. **AWS Service Latency**:
   - Secrets Manager calls < 100ms
   - Parameter Store calls < 50ms
   - KMS operations < 100ms

## Deliverables

1. **Infrastructure Code**:
   - Extended CloudFormation template (template.yaml)
   - OAuth 2.0 Lambda handlers and adapters
   - AWS service integration classes
   - Configuration files for Development environment

2. **Deployment Artifacts**:
   - Updated build configuration (pom.xml)
   - Environment-specific configuration
   - JWT key provisioning scripts
   - OAuth client initialization scripts

3. **Testing Suite**:
   - AWS service integration tests
   - End-to-end OAuth 2.0 flow tests
   - Performance validation tests
   - Security validation tests

4. **Documentation**:
   - Updated deployment guide
   - OAuth 2.0 API documentation
   - Configuration reference
   - Troubleshooting guide

## Success Criteria

- [ ] OAuth 2.0 token endpoint deployed and accessible
- [ ] Token introspection endpoint functional
- [ ] JWT tokens properly signed with KMS keys
- [ ] Client credentials securely stored in Secrets Manager
- [ ] Configuration parameters loaded from Parameter Store
- [ ] All integration tests pass
- [ ] Performance targets met (< 150ms warm latency)
- [ ] Security validations pass
- [ ] Existing Basic Auth functionality unaffected
- [ ] CloudFormation stack deploys successfully
- [ ] API Gateway properly configured with OAuth 2.0 endpoints

## AWS Service Dependencies

- **AWS Lambda**: OAuth 2.0 token endpoint handler
- **API Gateway**: OAuth 2.0 endpoint exposure and validation
- **Secrets Manager**: OAuth client credential storage
- **Systems Manager Parameter Store**: OAuth configuration
- **KMS**: JWT signing key management
- **CloudWatch**: Monitoring and logging
- **IAM**: Service permissions and roles

## Next Step Preview

Step 3 will focus on comprehensive unit and integration testing, including test automation, security testing, and performance validation for the complete OAuth 2.0 Client Credentials Flow implementation. 