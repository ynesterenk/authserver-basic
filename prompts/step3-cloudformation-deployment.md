# Step 3: CloudFormation Infrastructure & Deployment

## Objective
Create complete AWS infrastructure using CloudFormation, implement CI/CD pipeline, and establish production-ready deployment processes for the Java Authorization Server.

## Prerequisites
- Step 1 completed: Core domain implementation
- Step 2 completed: AWS Lambda integration with local testing
- All previous validation criteria met
- SAM local testing successful

## Requirements from PRD
- **Infrastructure as Code**: CloudFormation templates
- **API Gateway**: HTTP API with proper routing
- **Security**: IAM least privilege, HTTPS only
- **Observability**: CloudWatch alarms, X-Ray tracing
- **Scalability**: 500 RPS burst capacity
- **Reliability**: Automatic retries, DLQ for failed invocations

## Implementation Tasks

### 1. CloudFormation Template Structure

Create modular CloudFormation templates:
```
infrastructure/
├── main-template.yaml           # Root stack
├── nested-stacks/
│   ├── lambda-stack.yaml       # Lambda function and execution role
│   ├── api-gateway-stack.yaml  # HTTP API Gateway
│   ├── secrets-stack.yaml      # Secrets Manager
│   ├── monitoring-stack.yaml   # CloudWatch alarms, dashboards
│   └── iam-stack.yaml         # IAM roles and policies
└── parameters/
    ├── dev-params.json
    ├── staging-params.json
    └── prod-params.json
```

### 2. Main CloudFormation Template

**main-template.yaml**:
```yaml
AWSTemplateFormatVersion: '2010-09-09'
Description: 'Java Authorization Server - Complete Infrastructure'

Parameters:
  Environment:
    Type: String
    AllowedValues: [dev, staging, prod]
    Default: dev
  
  ArtifactBucket:
    Type: String
    Description: S3 bucket containing Lambda deployment package
  
  ArtifactKey:
    Type: String
    Description: S3 key for Lambda ZIP file
  
  LambdaMemorySize:
    Type: Number
    Default: 512
    MinValue: 128
    MaxValue: 3008

Resources:
  # Nested stacks for modular deployment
  IAMStack:
    Type: AWS::CloudFormation::Stack
    Properties:
      TemplateURL: !Sub 'https://${ArtifactBucket}.s3.amazonaws.com/nested-stacks/iam-stack.yaml'
      Parameters:
        Environment: !Ref Environment

  SecretsStack:
    Type: AWS::CloudFormation::Stack
    Properties:
      TemplateURL: !Sub 'https://${ArtifactBucket}.s3.amazonaws.com/nested-stacks/secrets-stack.yaml'
      Parameters:
        Environment: !Ref Environment

  LambdaStack:
    Type: AWS::CloudFormation::Stack
    DependsOn: [IAMStack, SecretsStack]
    Properties:
      TemplateURL: !Sub 'https://${ArtifactBucket}.s3.amazonaws.com/nested-stacks/lambda-stack.yaml'
      Parameters:
        Environment: !Ref Environment
        ExecutionRoleArn: !GetAtt IAMStack.Outputs.LambdaExecutionRoleArn
        SecretArn: !GetAtt SecretsStack.Outputs.CredentialSecretArn
        ArtifactBucket: !Ref ArtifactBucket
        ArtifactKey: !Ref ArtifactKey
        MemorySize: !Ref LambdaMemorySize

  APIGatewayStack:
    Type: AWS::CloudFormation::Stack
    DependsOn: LambdaStack
    Properties:
      TemplateURL: !Sub 'https://${ArtifactBucket}.s3.amazonaws.com/nested-stacks/api-gateway-stack.yaml'
      Parameters:
        Environment: !Ref Environment
        LambdaFunctionArn: !GetAtt LambdaStack.Outputs.LambdaFunctionArn

  MonitoringStack:
    Type: AWS::CloudFormation::Stack
    DependsOn: [LambdaStack, APIGatewayStack]
    Properties:
      TemplateURL: !Sub 'https://${ArtifactBucket}.s3.amazonaws.com/nested-stacks/monitoring-stack.yaml'
      Parameters:
        Environment: !Ref Environment
        LambdaFunctionName: !GetAtt LambdaStack.Outputs.LambdaFunctionName
        ApiGatewayId: !GetAtt APIGatewayStack.Outputs.ApiGatewayId

Outputs:
  ApiEndpoint:
    Description: 'API Gateway endpoint URL'
    Value: !GetAtt APIGatewayStack.Outputs.ApiEndpoint
    Export:
      Name: !Sub '${AWS::StackName}-ApiEndpoint'
  
  LambdaFunctionArn:
    Description: 'Lambda function ARN'
    Value: !GetAtt LambdaStack.Outputs.LambdaFunctionArn
    Export:
      Name: !Sub '${AWS::StackName}-LambdaArn'
```

### 3. Lambda Stack Template

**nested-stacks/lambda-stack.yaml**:
```yaml
AWSTemplateFormatVersion: '2010-09-09'
Description: 'Lambda function for Java Authorization Server'

Parameters:
  Environment:
    Type: String
  ExecutionRoleArn:
    Type: String
  SecretArn:
    Type: String
  ArtifactBucket:
    Type: String
  ArtifactKey:
    Type: String
  MemorySize:
    Type: Number

Resources:
  AuthFunction:
    Type: AWS::Lambda::Function
    Properties:
      FunctionName: !Sub 'auth-server-${Environment}'
      Runtime: java21
      Handler: com.example.auth.LambdaHandler::handleRequest
      Code:
        S3Bucket: !Ref ArtifactBucket
        S3Key: !Ref ArtifactKey
      MemorySize: !Ref MemorySize
      Timeout: 30
      Role: !Ref ExecutionRoleArn
      Environment:
        Variables:
          CREDENTIAL_SECRET_ARN: !Ref SecretArn
          LOG_LEVEL: !If [IsProd, 'INFO', 'DEBUG']
          CACHE_TTL_MINUTES: '5'
          ENVIRONMENT: !Ref Environment
      TracingConfig:
        Mode: Active
      DeadLetterQueue:
        TargetArn: !GetAtt DeadLetterQueue.Arn
      ReservedConcurrencyLimit: !If [IsProd, 100, 10]

  # Provisioned concurrency for production
  ProvisionedConcurrency:
    Type: AWS::Lambda::ProvisionedConcurrencyConfig
    Condition: IsProd
    Properties:
      FunctionName: !Ref AuthFunction
      ProvisionedConcurrencyLevel: 5
      Qualifier: !GetAtt AuthFunction.Version

  DeadLetterQueue:
    Type: AWS::SQS::Queue
    Properties:
      QueueName: !Sub 'auth-server-dlq-${Environment}'
      MessageRetentionPeriod: 1209600  # 14 days
      KmsMasterKeyId: alias/aws/sqs

  # CloudWatch Log Group with retention
  LogGroup:
    Type: AWS::Logs::LogGroup
    Properties:
      LogGroupName: !Sub '/aws/lambda/auth-server-${Environment}'
      RetentionInDays: !If [IsProd, 30, 7]

Conditions:
  IsProd: !Equals [!Ref Environment, 'prod']

Outputs:
  LambdaFunctionArn:
    Value: !GetAtt AuthFunction.Arn
  LambdaFunctionName:
    Value: !Ref AuthFunction
```

### 4. API Gateway Stack Template

**nested-stacks/api-gateway-stack.yaml**:
```yaml
AWSTemplateFormatVersion: '2010-09-09'
Description: 'HTTP API Gateway for Java Authorization Server'

Parameters:
  Environment:
    Type: String
  LambdaFunctionArn:
    Type: String

Resources:
  HttpApi:
    Type: AWS::ApiGatewayV2::Api
    Properties:
      Name: !Sub 'auth-server-api-${Environment}'
      Description: 'Java Authorization Server HTTP API'
      ProtocolType: HTTP
      CorsConfiguration:
        AllowOrigins:
          - 'https://*.example.com'  # Restrict to your domains
        AllowMethods:
          - POST
        AllowHeaders:
          - Authorization
          - Content-Type
        MaxAge: 300

  # Lambda integration
  LambdaIntegration:
    Type: AWS::ApiGatewayV2::Integration
    Properties:
      ApiId: !Ref HttpApi
      IntegrationType: AWS_PROXY
      IntegrationUri: !Ref LambdaFunctionArn
      PayloadFormatVersion: '2.0'
      TimeoutInMillis: 29000

  # Route for auth validation
  AuthRoute:
    Type: AWS::ApiGatewayV2::Route
    Properties:
      ApiId: !Ref HttpApi
      RouteKey: 'POST /auth/validate'
      Target: !Sub 'integrations/${LambdaIntegration}'
      AuthorizationType: NONE

  # Health check route
  HealthRoute:
    Type: AWS::ApiGatewayV2::Route
    Properties:
      ApiId: !Ref HttpApi
      RouteKey: 'GET /health'
      Target: !Sub 'integrations/${LambdaIntegration}'
      AuthorizationType: NONE

  # Stage with throttling
  ApiStage:
    Type: AWS::ApiGatewayV2::Stage
    Properties:
      ApiId: !Ref HttpApi
      StageName: !Ref Environment
      AutoDeploy: true
      ThrottleSettings:
        BurstLimit: 500
        RateLimit: 100
      AccessLogSettings:
        DestinationArn: !GetAtt AccessLogGroup.Arn
        Format: >
          {
            "requestId": "$context.requestId",
            "requestTime": "$context.requestTime",
            "httpMethod": "$context.httpMethod",
            "path": "$context.path",
            "status": "$context.status",
            "responseLength": "$context.responseLength",
            "responseTime": "$context.responseTime",
            "userAgent": "$context.identity.userAgent",
            "sourceIp": "$context.identity.sourceIp"
          }

  # Lambda permission for API Gateway
  LambdaPermission:
    Type: AWS::Lambda::Permission
    Properties:
      FunctionName: !Ref LambdaFunctionArn
      Action: lambda:InvokeFunction
      Principal: apigateway.amazonaws.com
      SourceArn: !Sub '${HttpApi}/*/*'

  # Access logs
  AccessLogGroup:
    Type: AWS::Logs::LogGroup
    Properties:
      LogGroupName: !Sub '/aws/apigateway/auth-server-${Environment}'
      RetentionInDays: !If [IsProd, 30, 7]

Conditions:
  IsProd: !Equals [!Ref Environment, 'prod']

Outputs:
  ApiEndpoint:
    Value: !Sub 'https://${HttpApi}.execute-api.${AWS::Region}.amazonaws.com/${Environment}'
  ApiGatewayId:
    Value: !Ref HttpApi
```

### 5. IAM Stack Template

**nested-stacks/iam-stack.yaml**:
```yaml
AWSTemplateFormatVersion: '2010-09-09'
Description: 'IAM roles and policies for Java Authorization Server'

Parameters:
  Environment:
    Type: String

Resources:
  LambdaExecutionRole:
    Type: AWS::IAM::Role
    Properties:
      RoleName: !Sub 'AuthServerLambdaRole-${Environment}'
      AssumeRolePolicyDocument:
        Version: '2012-10-17'
        Statement:
          - Effect: Allow
            Principal:
              Service: lambda.amazonaws.com
            Action: sts:AssumeRole
      ManagedPolicyArns:
        - arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole
        - arn:aws:iam::aws:policy/AWSXRayDaemonWriteAccess
      Policies:
        - PolicyName: SecretsManagerAccess
          PolicyDocument:
            Version: '2012-10-17'
            Statement:
              - Effect: Allow
                Action:
                  - secretsmanager:GetSecretValue
                Resource: !Sub 'arn:aws:secretsmanager:${AWS::Region}:${AWS::AccountId}:secret:auth-server-credentials-${Environment}-*'
        - PolicyName: SQSDeadLetterQueue
          PolicyDocument:
            Version: '2012-10-17'
            Statement:
              - Effect: Allow
                Action:
                  - sqs:SendMessage
                Resource: !Sub 'arn:aws:sqs:${AWS::Region}:${AWS::AccountId}:auth-server-dlq-${Environment}'

Outputs:
  LambdaExecutionRoleArn:
    Value: !GetAtt LambdaExecutionRole.Arn
```

### 6. Deployment Scripts

**scripts/deploy.sh**:
```bash
#!/bin/bash
set -e

ENVIRONMENT=${1:-dev}
REGION=${2:-us-east-1}
ARTIFACT_BUCKET=${3}

if [ -z "$ARTIFACT_BUCKET" ]; then
    echo "Usage: $0 <environment> <region> <artifact-bucket>"
    exit 1
fi

echo "Deploying to environment: $ENVIRONMENT"

# Build and package Lambda
mvn clean package -DskipTests

# Upload artifacts to S3
ARTIFACT_KEY="auth-server-${ENVIRONMENT}-$(date +%Y%m%d-%H%M%S).zip"
aws s3 cp target/auth-server.zip "s3://${ARTIFACT_BUCKET}/${ARTIFACT_KEY}"

# Upload nested stack templates
aws s3 sync infrastructure/nested-stacks/ "s3://${ARTIFACT_BUCKET}/nested-stacks/"

# Deploy CloudFormation stack
aws cloudformation deploy \
    --template-file infrastructure/main-template.yaml \
    --stack-name "auth-server-${ENVIRONMENT}" \
    --parameter-overrides \
        Environment="${ENVIRONMENT}" \
        ArtifactBucket="${ARTIFACT_BUCKET}" \
        ArtifactKey="${ARTIFACT_KEY}" \
    --capabilities CAPABILITY_NAMED_IAM \
    --region "${REGION}"

# Get API endpoint
API_ENDPOINT=$(aws cloudformation describe-stacks \
    --stack-name "auth-server-${ENVIRONMENT}" \
    --query 'Stacks[0].Outputs[?OutputKey==`ApiEndpoint`].OutputValue' \
    --output text \
    --region "${REGION}")

echo "Deployment complete!"
echo "API Endpoint: ${API_ENDPOINT}"
```

### 7. CI/CD Pipeline (GitHub Actions)

**.github/workflows/deploy.yml**:
```yaml
name: Deploy Auth Server

on:
  push:
    branches: [main, develop]
  pull_request:
    branches: [main]

env:
  AWS_REGION: us-east-1
  ARTIFACT_BUCKET: auth-server-artifacts-${{ github.repository_owner }}

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'corretto'
      
      - name: Cache Maven dependencies
        uses: actions/cache@v3
        with:
          path: ~/.m2
          key: ${{ runner.os }}-m2-${{ hashFiles('**/pom.xml') }}
      
      - name: Run tests
        run: mvn clean test
      
      - name: Security scan
        run: mvn org.owasp:dependency-check-maven:check
      
      - name: Upload test results
        uses: actions/upload-artifact@v3
        if: always()
        with:
          name: test-results
          path: target/surefire-reports/

  deploy-dev:
    needs: test
    runs-on: ubuntu-latest
    if: github.ref == 'refs/heads/develop'
    environment: development
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'corretto'
      
      - name: Configure AWS credentials
        uses: aws-actions/configure-aws-credentials@v4
        with:
          aws-access-key-id: ${{ secrets.AWS_ACCESS_KEY_ID }}
          aws-secret-access-key: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
          aws-region: ${{ env.AWS_REGION }}
      
      - name: Deploy to dev
        run: ./scripts/deploy.sh dev ${{ env.AWS_REGION }} ${{ env.ARTIFACT_BUCKET }}
      
      - name: Run smoke tests
        run: ./scripts/smoke-test.sh dev

  deploy-prod:
    needs: test
    runs-on: ubuntu-latest
    if: github.ref == 'refs/heads/main'
    environment: production
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'corretto'
      
      - name: Configure AWS credentials
        uses: aws-actions/configure-aws-credentials@v4
        with:
          aws-access-key-id: ${{ secrets.AWS_ACCESS_KEY_ID }}
          aws-secret-access-key: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
          aws-region: ${{ env.AWS_REGION }}
      
      - name: Deploy to prod
        run: ./scripts/deploy.sh prod ${{ env.AWS_REGION }} ${{ env.ARTIFACT_BUCKET }}
      
      - name: Run smoke tests
        run: ./scripts/smoke-test.sh prod
```

### 8. Smoke Testing Script

**scripts/smoke-test.sh**:
```bash
#!/bin/bash
set -e

ENVIRONMENT=${1:-dev}
REGION=${2:-us-east-1}

# Get API endpoint from CloudFormation
API_ENDPOINT=$(aws cloudformation describe-stacks \
    --stack-name "auth-server-${ENVIRONMENT}" \
    --query 'Stacks[0].Outputs[?OutputKey==`ApiEndpoint`].OutputValue' \
    --output text \
    --region "${REGION}")

echo "Running smoke tests against: ${API_ENDPOINT}"

# Test 1: Health check
echo "Testing health endpoint..."
curl -f "${API_ENDPOINT}/health" || exit 1

# Test 2: Valid authentication (requires test credentials in Secrets Manager)
echo "Testing valid authentication..."
curl -f -X POST "${API_ENDPOINT}/auth/validate" \
    -H "Authorization: Basic $(echo -n 'testuser:testpass' | base64)" \
    -H "Content-Type: application/json" || exit 1

# Test 3: Invalid authentication
echo "Testing invalid authentication..."
RESPONSE=$(curl -s -X POST "${API_ENDPOINT}/auth/validate" \
    -H "Authorization: Basic $(echo -n 'invalid:invalid' | base64)" \
    -H "Content-Type: application/json")

if echo "$RESPONSE" | grep -q '"allowed":false'; then
    echo "✓ Invalid auth test passed"
else
    echo "✗ Invalid auth test failed"
    exit 1
fi

echo "All smoke tests passed!"
```

## Validation Criteria

### Infrastructure Validation
1. **CloudFormation Deployment**:
   - All stacks deploy successfully
   - No drift in deployed resources
   - Proper parameter validation
   - Rollback capability tested

2. **Security Validation**:
   - IAM roles follow least privilege
   - No hardcoded credentials
   - HTTPS-only traffic
   - Proper secret rotation setup

3. **Performance Validation**:
   - API Gateway throttling configured
   - Lambda concurrency limits set
   - CloudWatch alarms functional
   - X-Ray tracing enabled

### CI/CD Validation
1. **Pipeline Tests**:
   - Unit tests pass in CI
   - Security scans complete
   - Deployment to dev environment
   - Smoke tests pass

2. **Environment Promotion**:
   - Dev → Staging → Prod workflow
   - Manual approval gates
   - Rollback procedures tested

## Deliverables
1. Complete CloudFormation templates
2. Deployment automation scripts
3. CI/CD pipeline configuration
4. Smoke testing suite
5. Infrastructure documentation
6. Runbook for operations
7. Security compliance report

## Success Criteria
- [ ] Infrastructure deploys successfully in all environments
- [ ] API Gateway properly routes requests to Lambda
- [ ] CloudWatch monitoring and alarms functional
- [ ] CI/CD pipeline deploys and tests automatically
- [ ] Smoke tests pass in all environments
- [ ] Security scan shows no critical vulnerabilities
- [ ] Performance targets met in production environment

## Next Step Preview
Step 4 will focus on production monitoring, security hardening, and operational procedures. 