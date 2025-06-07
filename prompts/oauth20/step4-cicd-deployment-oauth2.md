# Step 4: OAuth 2.0 Client Credentials Flow - CI/CD Pipeline Integration and Deployment

## Objective
Integrate the OAuth 2.0 Client Credentials Flow implementation with the existing CI/CD pipeline, implement deployment automation, and establish monitoring and operational procedures for the AWS Development environment. This step ensures reliable, automated deployment and proper observability of the OAuth 2.0 authentication system.

## Prerequisites
- Step 1 (OAuth 2.0 Core Domain Implementation) completed successfully
- Step 2 (AWS Lambda Integration) completed successfully
- Step 3 (Comprehensive Testing) completed successfully
- AWS Development environment configured
- GitHub Actions CI/CD pipeline operational for Basic Auth

## Implementation Tasks

### 1. GitHub Actions Workflow Enhancement

**Enhanced CI/CD Pipeline** (.github/workflows/oauth2-ci-cd.yml):

```yaml
name: OAuth 2.0 Auth Server CI/CD

on:
  push:
    branches: [main, develop]
    paths:
      - 'src/**'
      - 'pom.xml'
      - 'template.yaml'
      - 'infrastructure/**'
  pull_request:
    branches: [main]
    paths:
      - 'src/**'
      - 'pom.xml'
      - 'template.yaml'
      - 'infrastructure/**'

env:
  AWS_REGION: us-east-1
  STAGE_NAME: dev
  JAVA_VERSION: '21'

jobs:
  # Unit Tests and Code Quality
  unit-tests:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Set up Java
        uses: actions/setup-java@v4
        with:
          java-version: ${{ env.JAVA_VERSION }}
          distribution: 'corretto'

      - name: Cache Maven dependencies
        uses: actions/cache@v3
        with:
          path: ~/.m2
          key: ${{ runner.os }}-m2-${{ hashFiles('**/pom.xml') }}
          restore-keys: ${{ runner.os }}-m2

      - name: Run unit tests
        run: mvn clean test -P unit-tests

      - name: Generate test coverage report
        run: mvn jacoco:report

      - name: Upload coverage to Codecov
        uses: codecov/codecov-action@v3
        with:
          file: ./target/site/jacoco/jacoco.xml
          flags: unittests
          name: codecov-umbrella

      - name: Check coverage threshold
        run: |
          COVERAGE=$(mvn jacoco:check -Dthreshold=90 | grep "coverage ratio" | tail -1)
          echo "Coverage: $COVERAGE"
          mvn jacoco:check -Dthreshold=90

  # Security Scanning
  security-scan:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Set up Java
        uses: actions/setup-java@v4
        with:
          java-version: ${{ env.JAVA_VERSION }}
          distribution: 'corretto'

      - name: Cache Maven dependencies
        uses: actions/cache@v3
        with:
          path: ~/.m2
          key: ${{ runner.os }}-m2-${{ hashFiles('**/pom.xml') }}
          restore-keys: ${{ runner.os }}-m2

      - name: Build project
        run: mvn clean compile

      - name: Run OWASP Dependency Check
        run: mvn org.owasp:dependency-check-maven:aggregate

      - name: Upload OWASP report
        uses: actions/upload-artifact@v3
        with:
          name: owasp-dependency-check-report
          path: target/dependency-check-report.html

      - name: Run SpotBugs analysis
        run: mvn com.github.spotbugs:spotbugs-maven-plugin:check

  # Build and Package
  build:
    runs-on: ubuntu-latest
    needs: [unit-tests, security-scan]
    outputs:
      artifact-name: ${{ steps.package.outputs.artifact-name }}
    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Set up Java
        uses: actions/setup-java@v4
        with:
          java-version: ${{ env.JAVA_VERSION }}
          distribution: 'corretto'

      - name: Cache Maven dependencies
        uses: actions/cache@v3
        with:
          path: ~/.m2
          key: ${{ runner.os }}-m2-${{ hashFiles('**/pom.xml') }}
          restore-keys: ${{ runner.os }}-m2

      - name: Build and package
        id: package
        run: |
          mvn clean package -DskipTests
          ARTIFACT_NAME="auth-server-oauth2-$(date +%Y%m%d%H%M%S).jar"
          cp target/auth-server-lambda.jar target/$ARTIFACT_NAME
          echo "artifact-name=$ARTIFACT_NAME" >> $GITHUB_OUTPUT

      - name: Upload build artifact
        uses: actions/upload-artifact@v3
        with:
          name: lambda-package
          path: target/auth-server-lambda.jar

  # Integration Tests (AWS Services)
  integration-tests:
    runs-on: ubuntu-latest
    needs: build
    if: github.event_name == 'push' || github.base_ref == 'main'
    environment: development
    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Set up Java
        uses: actions/setup-java@v4
        with:
          java-version: ${{ env.JAVA_VERSION }}
          distribution: 'corretto'

      - name: Configure AWS credentials
        uses: aws-actions/configure-aws-credentials@v4
        with:
          aws-access-key-id: ${{ secrets.AWS_ACCESS_KEY_ID }}
          aws-secret-access-key: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
          aws-region: ${{ env.AWS_REGION }}

      - name: Download build artifact
        uses: actions/download-artifact@v3
        with:
          name: lambda-package
          path: target/

      - name: Run integration tests
        run: mvn test -P integration-tests
        env:
          AWS_REGION: ${{ env.AWS_REGION }}
          OAUTH_CLIENT_SECRET_ARN: ${{ secrets.OAUTH_CLIENT_SECRET_ARN }}
          JWT_SIGNING_KEY_ARN: ${{ secrets.JWT_SIGNING_KEY_ARN }}

  # Deploy to Development Environment
  deploy-dev:
    runs-on: ubuntu-latest
    needs: [build, integration-tests]
    if: github.ref == 'refs/heads/develop' || github.ref == 'refs/heads/main'
    environment: development
    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Configure AWS credentials
        uses: aws-actions/configure-aws-credentials@v4
        with:
          aws-access-key-id: ${{ secrets.AWS_ACCESS_KEY_ID }}
          aws-secret-access-key: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
          aws-region: ${{ env.AWS_REGION }}

      - name: Download build artifact
        uses: actions/download-artifact@v3
        with:
          name: lambda-package
          path: target/

      - name: Install SAM CLI
        uses: aws-actions/setup-sam@v2

      - name: Deploy OAuth 2.0 Infrastructure
        run: |
          sam deploy \
            --template-file template.yaml \
            --stack-name auth-server-oauth2-dev \
            --s3-bucket ${{ secrets.SAM_DEPLOYMENT_BUCKET }} \
            --s3-prefix oauth2-deployments \
            --region ${{ env.AWS_REGION }} \
            --capabilities CAPABILITY_IAM CAPABILITY_NAMED_IAM \
            --parameter-overrides \
              StageName=dev \
              CredentialSecretArn=${{ secrets.BASIC_AUTH_SECRET_ARN }} \
              OAuth2ClientSecretArn=${{ secrets.OAUTH_CLIENT_SECRET_ARN }} \
              JWTSigningKeyArn=${{ secrets.JWT_SIGNING_KEY_ARN }} \
            --no-confirm-changeset \
            --no-fail-on-empty-changeset

      - name: Get API Gateway URL
        id: get-api-url
        run: |
          API_URL=$(aws cloudformation describe-stacks \
            --stack-name auth-server-oauth2-dev \
            --query 'Stacks[0].Outputs[?OutputKey==`AuthApiUrl`].OutputValue' \
            --output text \
            --region ${{ env.AWS_REGION }})
          echo "api-url=$API_URL" >> $GITHUB_OUTPUT
          echo "Deployed API URL: $API_URL"

      - name: Update Parameter Store with API URL
        run: |
          aws ssm put-parameter \
            --name "/oauth2/dev/api/url" \
            --value "${{ steps.get-api-url.outputs.api-url }}" \
            --type String \
            --overwrite \
            --region ${{ env.AWS_REGION }}

  # End-to-End Tests (Post-Deployment)
  e2e-tests:
    runs-on: ubuntu-latest
    needs: deploy-dev
    if: github.ref == 'refs/heads/develop' || github.ref == 'refs/heads/main'
    environment: development
    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Set up Java
        uses: actions/setup-java@v4
        with:
          java-version: ${{ env.JAVA_VERSION }}
          distribution: 'corretto'

      - name: Configure AWS credentials
        uses: aws-actions/configure-aws-credentials@v4
        with:
          aws-access-key-id: ${{ secrets.AWS_ACCESS_KEY_ID }}
          aws-secret-access-key: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
          aws-region: ${{ env.AWS_REGION }}

      - name: Get API Gateway URL
        id: get-api-url
        run: |
          API_URL=$(aws ssm get-parameter \
            --name "/oauth2/dev/api/url" \
            --query 'Parameter.Value' \
            --output text \
            --region ${{ env.AWS_REGION }})
          echo "api-url=$API_URL" >> $GITHUB_OUTPUT

      - name: Run end-to-end tests
        run: mvn test -P e2e-tests
        env:
          OAUTH_TEST_API_URL: ${{ steps.get-api-url.outputs.api-url }}
          AWS_REGION: ${{ env.AWS_REGION }}

      - name: Validate API health
        run: |
          curl -f "${{ steps.get-api-url.outputs.api-url }}/health" || exit 1

  # Performance Tests
  performance-tests:
    runs-on: ubuntu-latest
    needs: deploy-dev
    if: github.ref == 'refs/heads/main'
    environment: development
    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Configure AWS credentials
        uses: aws-actions/configure-aws-credentials@v4
        with:
          aws-access-key-id: ${{ secrets.AWS_ACCESS_KEY_ID }}
          aws-secret-access-key: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
          aws-region: ${{ env.AWS_REGION }}

      - name: Get API Gateway URL
        id: get-api-url
        run: |
          API_URL=$(aws ssm get-parameter \
            --name "/oauth2/dev/api/url" \
            --query 'Parameter.Value' \
            --output text \
            --region ${{ env.AWS_REGION }})
          echo "api-url=$API_URL" >> $GITHUB_OUTPUT

      - name: Install Artillery
        run: npm install -g artillery@latest

      - name: Run performance tests
        run: |
          cat > oauth2-perf-test.yml << EOF
          config:
            target: '${{ steps.get-api-url.outputs.api-url }}'
            phases:
              - duration: 60
                arrivalRate: 10
          scenarios:
            - name: "OAuth 2.0 Token Request"
              requests:
                - post:
                    url: "/oauth/token"
                    headers:
                      Content-Type: "application/x-www-form-urlencoded"
                    form:
                      grant_type: "client_credentials"
                      client_id: "test-client-1"
                      client_secret: "test-secret"
          EOF
          
          artillery run oauth2-perf-test.yml --output performance-report.json

      - name: Upload performance report
        uses: actions/upload-artifact@v3
        with:
          name: performance-report
          path: performance-report.json
```

### 2. Enhanced Monitoring and Observability

**CloudWatch Dashboard** (infrastructure/monitoring/oauth2-dashboard.json):

```json
{
  "widgets": [
    {
      "type": "metric",
      "properties": {
        "metrics": [
          ["AWS/Lambda", "Duration", "FunctionName", "${AWS::StackName}-oauth2-token-function"],
          ["AWS/Lambda", "Errors", "FunctionName", "${AWS::StackName}-oauth2-token-function"],
          ["AWS/Lambda", "Invocations", "FunctionName", "${AWS::StackName}-oauth2-token-function"]
        ],
        "period": 300,
        "stat": "Average",
        "region": "us-east-1",
        "title": "OAuth 2.0 Lambda Metrics"
      }
    },
    {
      "type": "metric",
      "properties": {
        "metrics": [
          ["AWS/ApiGateway", "4XXError", "ApiName", "${AWS::StackName}-auth-api"],
          ["AWS/ApiGateway", "5XXError", "ApiName", "${AWS::StackName}-auth-api"],
          ["AWS/ApiGateway", "Latency", "ApiName", "${AWS::StackName}-auth-api"]
        ],
        "period": 300,
        "stat": "Sum",
        "region": "us-east-1",
        "title": "API Gateway OAuth 2.0 Errors"
      }
    },
    {
      "type": "log",
      "properties": {
        "query": "SOURCE '/aws/lambda/${AWS::StackName}-oauth2-token-function'\n| fields @timestamp, @message\n| filter @message like /OAuth.*token.*generated/\n| stats count() by bin(5m)",
        "region": "us-east-1",
        "title": "OAuth 2.0 Token Generation Rate",
        "view": "table"
      }
    }
  ]
}
```

**CloudWatch Alarms** (template.yaml additions):

```yaml
  # OAuth 2.0 Specific Alarms
  OAuth2TokenErrorAlarm:
    Type: AWS::CloudWatch::Alarm
    Properties:
      AlarmName: !Sub "${AWS::StackName}-oauth2-token-errors"
      AlarmDescription: "OAuth 2.0 token endpoint error rate alarm"
      MetricName: Errors
      Namespace: AWS/Lambda
      Statistic: Sum
      Period: 300
      EvaluationPeriods: 2
      Threshold: 10
      ComparisonOperator: GreaterThanOrEqualToThreshold
      Dimensions:
        - Name: FunctionName
          Value: !Ref OAuth2TokenFunction
      TreatMissingData: notBreaching
      AlarmActions:
        - !Ref OAuth2NotificationTopic

  OAuth2TokenLatencyAlarm:
    Type: AWS::CloudWatch::Alarm
    Properties:
      AlarmName: !Sub "${AWS::StackName}-oauth2-token-latency"
      AlarmDescription: "OAuth 2.0 token endpoint high latency alarm"
      MetricName: Duration
      Namespace: AWS/Lambda
      Statistic: Average
      Period: 300
      EvaluationPeriods: 3
      Threshold: 5000  # 5 seconds
      ComparisonOperator: GreaterThanThreshold
      Dimensions:
        - Name: FunctionName
          Value: !Ref OAuth2TokenFunction
      TreatMissingData: notBreaching
      AlarmActions:
        - !Ref OAuth2NotificationTopic

  OAuth2NotificationTopic:
    Type: AWS::SNS::Topic
    Properties:
      TopicName: !Sub "${AWS::StackName}-oauth2-alerts"
      DisplayName: "OAuth 2.0 Auth Server Alerts"
```

### 3. Deployment Scripts and Automation

**Deployment Script** (scripts/deploy-oauth2.sh):

```bash
#!/bin/bash

set -e

# Configuration
STACK_NAME="auth-server-oauth2-dev"
REGION="us-east-1"
STAGE="dev"
SAM_CONFIG_FILE="samconfig.toml"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

log() {
    echo -e "${GREEN}[$(date +'%Y-%m-%d %H:%M:%S')] $1${NC}"
}

warn() {
    echo -e "${YELLOW}[$(date +'%Y-%m-%d %H:%M:%S')] WARNING: $1${NC}"
}

error() {
    echo -e "${RED}[$(date +'%Y-%m-%d %H:%M:%S')] ERROR: $1${NC}"
}

# Pre-deployment checks
check_prerequisites() {
    log "Checking prerequisites..."
    
    # Check AWS CLI
    if ! command -v aws &> /dev/null; then
        error "AWS CLI is not installed"
        exit 1
    fi
    
    # Check SAM CLI
    if ! command -v sam &> /dev/null; then
        error "SAM CLI is not installed"
        exit 1
    fi
    
    # Check AWS credentials
    if ! aws sts get-caller-identity &> /dev/null; then
        error "AWS credentials not configured"
        exit 1
    fi
    
    # Check if required secrets exist
    check_secret "oauth-clients-dev" "OAuth 2.0 client credentials"
    check_secret "jwt-signing-key-dev" "JWT signing key"
    
    log "Prerequisites check completed successfully"
}

check_secret() {
    local secret_name=$1
    local description=$2
    
    if aws secretsmanager describe-secret --secret-id "$secret_name" --region "$REGION" &> /dev/null; then
        log "Found required secret: $secret_name"
    else
        error "Required secret not found: $secret_name ($description)"
        exit 1
    fi
}

# Build the application
build_application() {
    log "Building OAuth 2.0 Auth Server..."
    
    # Clean and build
    mvn clean package -DskipTests
    
    if [ $? -eq 0 ]; then
        log "Build completed successfully"
    else
        error "Build failed"
        exit 1
    fi
}

# Run tests
run_tests() {
    log "Running unit tests..."
    
    mvn test
    
    if [ $? -eq 0 ]; then
        log "All tests passed"
    else
        error "Tests failed"
        exit 1
    fi
}

# Deploy infrastructure
deploy_infrastructure() {
    log "Deploying OAuth 2.0 infrastructure to AWS..."
    
    # Get secret ARNs
    local oauth_secret_arn=$(aws secretsmanager describe-secret \
        --secret-id "oauth-clients-dev" \
        --query 'ARN' \
        --output text \
        --region "$REGION")
    
    local jwt_key_arn=$(aws kms describe-key \
        --key-id "alias/jwt-signing-key-dev" \
        --query 'KeyMetadata.Arn' \
        --output text \
        --region "$REGION")
    
    # Deploy with SAM
    sam deploy \
        --template-file template.yaml \
        --stack-name "$STACK_NAME" \
        --s3-bucket "$SAM_DEPLOYMENT_BUCKET" \
        --s3-prefix "oauth2-deployments" \
        --region "$REGION" \
        --capabilities CAPABILITY_IAM CAPABILITY_NAMED_IAM \
        --parameter-overrides \
            "StageName=$STAGE" \
            "OAuth2ClientSecretArn=$oauth_secret_arn" \
            "JWTSigningKeyArn=$jwt_key_arn" \
        --no-confirm-changeset \
        --no-fail-on-empty-changeset
    
    if [ $? -eq 0 ]; then
        log "Deployment completed successfully"
    else
        error "Deployment failed"
        exit 1
    fi
}

# Post-deployment validation
validate_deployment() {
    log "Validating OAuth 2.0 deployment..."
    
    # Get API Gateway URL
    local api_url=$(aws cloudformation describe-stacks \
        --stack-name "$STACK_NAME" \
        --query 'Stacks[0].Outputs[?OutputKey==`AuthApiUrl`].OutputValue' \
        --output text \
        --region "$REGION")
    
    if [ -z "$api_url" ]; then
        error "Could not retrieve API Gateway URL"
        exit 1
    fi
    
    log "API Gateway URL: $api_url"
    
    # Test health endpoint
    local health_status=$(curl -s -o /dev/null -w "%{http_code}" "$api_url/health")
    
    if [ "$health_status" = "200" ]; then
        log "Health check passed"
    else
        warn "Health check failed with status: $health_status"
    fi
    
    # Test OAuth 2.0 token endpoint (with test credentials)
    log "Testing OAuth 2.0 token endpoint..."
    
    local token_response=$(curl -s -X POST "$api_url/oauth/token" \
        -H "Content-Type: application/x-www-form-urlencoded" \
        -d "grant_type=client_credentials&client_id=test-client-1&client_secret=test-secret")
    
    if echo "$token_response" | grep -q "access_token"; then
        log "OAuth 2.0 token endpoint is working correctly"
    else
        warn "OAuth 2.0 token endpoint test failed"
        echo "Response: $token_response"
    fi
}

# Main execution
main() {
    log "Starting OAuth 2.0 Auth Server deployment..."
    
    check_prerequisites
    build_application
    run_tests
    deploy_infrastructure
    validate_deployment
    
    log "OAuth 2.0 Auth Server deployment completed successfully!"
}

# Handle script arguments
case "${1:-}" in
    "build")
        build_application
        ;;
    "test")
        run_tests
        ;;
    "deploy")
        deploy_infrastructure
        ;;
    "validate")
        validate_deployment
        ;;
    "")
        main
        ;;
    *)
        echo "Usage: $0 [build|test|deploy|validate]"
        exit 1
        ;;
esac
```

### 4. Environment Configuration Management

**Environment-Specific Configuration** (config/environments/dev.yml):

```yaml
# OAuth 2.0 Development Environment Configuration
oauth2:
  environment: development
  
  # JWT Configuration
  jwt:
    issuer: "https://auth-dev.example.com"
    audience: "https://api-dev.example.com"
    expiration:
      default: 3600  # 1 hour
      maximum: 7200  # 2 hours
    
  # Client Management
  clients:
    test-client-1:
      allowed-scopes: ["read", "write"]
      token-expiration: 3600
    test-client-2:
      allowed-scopes: ["read"]
      token-expiration: 1800
  
  # Security Settings
  security:
    client-secret-min-length: 32
    enable-client-certificate-auth: false
    require-pkce: false  # Not applicable for client credentials flow
    
  # Rate Limiting
  rate-limiting:
    enabled: true
    requests-per-minute: 100
    burst-capacity: 200
    
  # Monitoring
  monitoring:
    metrics-enabled: true
    detailed-logging: true
    performance-tracking: true

# AWS Services Configuration
aws:
  region: us-east-1
  
  # Secrets Manager
  secrets:
    oauth-clients: "arn:aws:secretsmanager:us-east-1:ACCOUNT:secret:oauth-clients-dev"
    
  # KMS
  kms:
    jwt-signing-key: "arn:aws:kms:us-east-1:ACCOUNT:key/jwt-signing-key-dev"
    
  # CloudWatch
  cloudwatch:
    log-retention-days: 14
    metrics-namespace: "OAuth2/AuthServer/Dev"
    detailed-monitoring: true
```

### 5. Rollback and Recovery Procedures

**Rollback Script** (scripts/rollback-oauth2.sh):

```bash
#!/bin/bash

set -e

STACK_NAME="auth-server-oauth2-dev"
REGION="us-east-1"

log() {
    echo -e "\033[0;32m[$(date +'%Y-%m-%d %H:%M:%S')] $1\033[0m"
}

error() {
    echo -e "\033[0;31m[$(date +'%Y-%m-%d %H:%M:%S')] ERROR: $1\033[0m"
}

# Get previous stack version
get_previous_version() {
    aws cloudformation describe-stack-events \
        --stack-name "$STACK_NAME" \
        --query 'StackEvents[?ResourceStatus==`UPDATE_COMPLETE`].[Timestamp,LogicalResourceId]' \
        --output table \
        --region "$REGION" \
        --max-items 10
}

# Rollback deployment
rollback_deployment() {
    log "Initiating rollback for stack: $STACK_NAME"
    
    # Check if stack exists and is in a rollback-able state
    local stack_status=$(aws cloudformation describe-stacks \
        --stack-name "$STACK_NAME" \
        --query 'Stacks[0].StackStatus' \
        --output text \
        --region "$REGION" 2>/dev/null || echo "NOT_FOUND")
    
    if [ "$stack_status" = "NOT_FOUND" ]; then
        error "Stack $STACK_NAME not found"
        exit 1
    fi
    
    if [[ "$stack_status" == *"IN_PROGRESS"* ]]; then
        error "Stack is currently being updated. Please wait for completion."
        exit 1
    fi
    
    # Perform rollback
    aws cloudformation cancel-update-stack \
        --stack-name "$STACK_NAME" \
        --region "$REGION" 2>/dev/null || true
    
    log "Rollback initiated. Monitoring progress..."
    
    # Monitor rollback progress
    aws cloudformation wait stack-update-rollback-complete \
        --stack-name "$STACK_NAME" \
        --region "$REGION"
    
    log "Rollback completed successfully"
}

# Validate rollback
validate_rollback() {
    log "Validating rollback..."
    
    # Get API Gateway URL after rollback
    local api_url=$(aws cloudformation describe-stacks \
        --stack-name "$STACK_NAME" \
        --query 'Stacks[0].Outputs[?OutputKey==`AuthApiUrl`].OutputValue' \
        --output text \
        --region "$REGION")
    
    # Test health endpoint
    local health_status=$(curl -s -o /dev/null -w "%{http_code}" "$api_url/health")
    
    if [ "$health_status" = "200" ]; then
        log "Health check passed after rollback"
    else
        error "Health check failed after rollback with status: $health_status"
        exit 1
    fi
    
    log "Rollback validation completed successfully"
}

# Main execution
main() {
    log "Starting OAuth 2.0 Auth Server rollback process..."
    
    get_previous_version
    read -p "Do you want to proceed with rollback? (y/N): " -n 1 -r
    echo
    
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        rollback_deployment
        validate_rollback
        log "Rollback process completed successfully!"
    else
        log "Rollback cancelled by user"
    fi
}

main "$@"
```

### 6. Documentation and Operational Procedures

**Deployment Guide** (docs/oauth2-deployment-guide.md):

```markdown
# OAuth 2.0 Client Credentials Flow - Deployment Guide

## Overview
This guide covers the deployment process for the OAuth 2.0 Client Credentials Flow extension to the existing authentication server.

## Prerequisites
- AWS CLI configured with appropriate permissions
- SAM CLI installed
- Java 21 JDK installed
- Maven 3.8+ installed
- Git access to the repository

## Deployment Process

### 1. Initial Setup
```bash
# Clone the repository
git clone <repository-url>
cd auth-server-oauth2

# Install dependencies
mvn clean install
```

### 2. Configure Secrets
```bash
# Create OAuth client credentials in Secrets Manager
aws secretsmanager create-secret \
    --name "oauth-clients-dev" \
    --description "OAuth 2.0 client credentials for development" \
    --secret-string file://config/oauth-clients-dev.json

# Create JWT signing key in KMS
aws kms create-key \
    --description "OAuth 2.0 JWT signing key for development" \
    --key-usage SIGN_VERIFY \
    --key-spec RSA_2048
```

### 3. Deploy Infrastructure
```bash
# Run the deployment script
./scripts/deploy-oauth2.sh

# Or deploy manually
sam deploy --guided
```

### 4. Validate Deployment
```bash
# Test OAuth 2.0 endpoint
curl -X POST https://api-url/oauth/token \
    -H "Content-Type: application/x-www-form-urlencoded" \
    -d "grant_type=client_credentials&client_id=test-client&client_secret=test-secret"
```

## Monitoring
- CloudWatch Dashboard: OAuth2-AuthServer-Dev
- Alarms: Check AWS SNS topic for alerts
- Logs: /aws/lambda/auth-server-oauth2-dev-*

## Troubleshooting
See [troubleshooting guide](oauth2-troubleshooting.md) for common issues and solutions.
```

## Validation Criteria

### CI/CD Pipeline Requirements
- **Automated Testing**: All test suites run automatically on code changes
- **Security Scanning**: OWASP dependency check and static analysis
- **Code Coverage**: Maintains ≥90% coverage with automatic reporting
- **Deployment Automation**: Zero-touch deployment to development environment
- **Rollback Capability**: Automated rollback procedures in place

### Monitoring and Observability
- **CloudWatch Dashboards**: Real-time monitoring of OAuth 2.0 metrics
- **Alerting**: Proactive alerts for errors, latency, and security issues
- **Log Aggregation**: Centralized logging with proper security filtering
- **Performance Tracking**: Continuous monitoring of response times and throughput

### Operational Procedures
- **Deployment Scripts**: Automated deployment with validation
- **Rollback Procedures**: Tested rollback mechanisms
- **Configuration Management**: Environment-specific configuration handling
- **Documentation**: Complete operational documentation

## Deliverables

1. **CI/CD Pipeline**:
   - Enhanced GitHub Actions workflow
   - Automated testing and security scanning
   - Deployment automation for development environment
   - Performance testing integration

2. **Monitoring Infrastructure**:
   - CloudWatch dashboards and alarms
   - SNS notification setup
   - Log aggregation and filtering
   - Performance monitoring

3. **Operational Tools**:
   - Deployment and rollback scripts
   - Environment configuration management
   - Health check and validation tools
   - Troubleshooting utilities

4. **Documentation**:
   - Deployment guide and procedures
   - Monitoring and alerting guide
   - Troubleshooting documentation
   - Operational runbooks

## Success Criteria

- [ ] CI/CD pipeline successfully builds, tests, and deploys OAuth 2.0 implementation
- [ ] All automated tests pass in pipeline (unit, integration, e2e)
- [ ] Security scanning passes with no high-severity vulnerabilities
- [ ] Code coverage maintains ≥90% threshold
- [ ] Deployment automation works reliably without manual intervention
- [ ] Monitoring dashboards display OAuth 2.0 metrics correctly
- [ ] Alerting triggers appropriately for error conditions
- [ ] Rollback procedures tested and functional
- [ ] Performance benchmarks meet latency requirements (≤150ms)
- [ ] Documentation is complete and accurate
- [ ] Existing Basic Auth functionality remains unaffected

## Security Considerations

- **Secrets Management**: All secrets stored securely in AWS Secrets Manager
- **Access Control**: Proper IAM permissions for CI/CD pipeline
- **Network Security**: HTTPS-only communication
- **Vulnerability Management**: Regular security scanning and dependency updates
- **Audit Logging**: Complete audit trail of deployments and changes

## Performance Benchmarks

- **Cold Start**: < 3 seconds for first Lambda invocation
- **Warm Invocation**: < 150ms for OAuth 2.0 token generation
- **Throughput**: Support for 100+ requests/minute per client
- **Scalability**: Auto-scaling based on demand

## Next Steps

With the completion of Step 4, the OAuth 2.0 Client Credentials Flow implementation is production-ready for the AWS Development environment. Consider the following for future enhancements:

1. **Additional OAuth 2.0 Flows**: Authorization Code, Implicit, or Device flows
2. **Token Refresh**: Implement refresh token capabilities
3. **Advanced Scopes**: Hierarchical scope management
4. **Multi-Environment**: Extend to staging and production environments
5. **Client Management**: Administrative interface for OAuth client management
</rewritten_file> 