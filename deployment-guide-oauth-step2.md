# OAuth2 Authentication Server Deployment Guide - Step 2

## Overview
This guide provides step-by-step instructions for manually deploying the OAuth2-enabled authentication server to AWS Dev environment. The deployment includes both the existing Basic Authentication functionality and the new OAuth2 Client Credentials Grant flow.

## Prerequisites

### Local Environment
- Java 21 or higher
- Maven 3.8+
- AWS CLI configured with appropriate credentials
- Docker (for local testing with LocalStack)

### AWS Environment
- AWS Account with appropriate permissions
- IAM permissions for:
  - Lambda function creation/updates
  - API Gateway management
  - IAM role creation
  - Secrets Manager (if using)
  - CloudWatch Logs

## Architecture Overview

The deployment includes:
1. **Lambda Function**: `auth-server-dev` (updated with OAuth2 support)
2. **API Gateway**: REST API with endpoints:
   - `POST /auth` - Basic Authentication
   - `POST /oauth/token` - OAuth2 Token Endpoint
   - `POST /oauth/introspect` - OAuth2 Token Introspection
3. **IAM Role**: Lambda execution role with necessary permissions

## Step 1: Build the Application

### 1.1 Clean and Build
```bash
# Navigate to project directory
cd "C:\Projects\LSEG\AI Initiative\AWS to Azure Migration\AWS server - basic"

# Clean previous builds
mvn clean

# Run tests to ensure everything is working
mvn test

# Build the deployment package
mvn package -DskipTests

# Verify the JAR file was created
dir target\*.jar
```

**Expected Output**: `target/java-auth-server-1.0-SNAPSHOT.jar` (approximately 15-20MB)

### 1.2 Verify Build Contents
```bash
# List contents of the JAR to verify OAuth2 classes are included
jar -tf target/java-auth-server-1.0-SNAPSHOT.jar | findstr OAuth2
```

**Expected Output**: Should show OAuth2-related classes including:
- `com/example/auth/infrastructure/oauth/OAuth2LambdaHandler.class`
- `com/example/auth/infrastructure/oauth/InMemoryOAuthClientRepository.class`
- `com/example/auth/infrastructure/oauth/model/`

## Step 2: AWS Infrastructure Setup

### 2.1 Create/Update IAM Role for Lambda

```bash
# Create IAM role (if not exists)
aws iam create-role --role-name lambda-auth-server-role --assume-role-policy-document '{
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
}'

# Attach basic execution policy
aws iam attach-role-policy --role-name lambda-auth-server-role --policy-arn arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole

# Attach VPC execution policy (if Lambda needs VPC access)
aws iam attach-role-policy --role-name lambda-auth-server-role --policy-arn arn:aws:iam::aws:policy/service-role/AWSLambdaVPCAccessExecutionRole
```

### 2.2 Get IAM Role ARN
```bash
aws iam get-role --role-name lambda-auth-server-role --query 'Role.Arn' --output text
```
**Save this ARN** - you'll need it for Lambda function creation.

## Step 3: Deploy Lambda Functions

### 3.1 Create/Update Basic Auth Lambda Function

```bash
# Set variables (replace with your actual values)
set ACCOUNT_ID=YOUR_AWS_ACCOUNT_ID
set REGION=us-east-1
set ROLE_ARN=arn:aws:iam::%ACCOUNT_ID%:role/lambda-auth-server-role

# Create or update the Lambda function
aws lambda create-function ^
  --function-name auth-server-dev ^
  --runtime java21 ^
  --role %ROLE_ARN% ^
  --handler com.example.auth.infrastructure.LambdaHandler::handleRequest ^
  --zip-file fileb://target/java-auth-server-1.0-SNAPSHOT.jar ^
  --timeout 30 ^
  --memory-size 512 ^
  --environment Variables="{SPRING_PROFILES_ACTIVE=local}" ^
  --description "Authentication server with OAuth2 support"
```

**If function already exists, update it:**
```bash
aws lambda update-function-code ^
  --function-name auth-server-dev ^
  --zip-file fileb://target/java-auth-server-1.0-SNAPSHOT.jar

aws lambda update-function-configuration ^
  --function-name auth-server-dev ^
  --timeout 30 ^
  --memory-size 512 ^
  --environment Variables="{SPRING_PROFILES_ACTIVE=local}"
```

### 3.2 Create OAuth2 Lambda Function

```bash
# Create OAuth2-specific Lambda function
aws lambda create-function ^
  --function-name oauth2-server-dev ^
  --runtime java21 ^
  --role %ROLE_ARN% ^
  --handler com.example.auth.infrastructure.oauth.OAuth2LambdaHandler::handleRequest ^
  --zip-file fileb://target/java-auth-server-1.0-SNAPSHOT.jar ^
  --timeout 30 ^
  --memory-size 512 ^
  --environment Variables="{SPRING_PROFILES_ACTIVE=local}" ^
  --description "OAuth2 token and introspection endpoints"
```

**If function already exists, update it:**
```bash
aws lambda update-function-code ^
  --function-name oauth2-server-dev ^
  --zip-file fileb://target/java-auth-server-1.0-SNAPSHOT.jar

aws lambda update-function-configuration ^
  --function-name oauth2-server-dev ^
  --timeout 30 ^
  --memory-size 512 ^
  --environment Variables="{SPRING_PROFILES_ACTIVE=local}"
```

### 3.3 Verify Lambda Deployment

```bash
# Test basic auth function
aws lambda invoke ^
  --function-name auth-server-dev ^
  --payload "{\"requestContext\":{\"http\":{\"method\":\"POST\"}},\"headers\":{\"authorization\":\"Basic dGVzdDp0ZXN0\"},\"body\":\"\"}" ^
  response.json

# Check response
type response.json

# Test OAuth2 function
aws lambda invoke ^
  --function-name oauth2-server-dev ^
  --payload "{\"requestContext\":{\"http\":{\"method\":\"POST\",\"path\":\"/oauth/token\"}},\"headers\":{\"content-type\":\"application/x-www-form-urlencoded\"},\"body\":\"grant_type=client_credentials&client_id=test-client-1&client_secret=test-client-1-secret&scope=read\"}" ^
  oauth-response.json

# Check OAuth2 response
type oauth-response.json
```

## Step 4: API Gateway Setup

### 4.1 Create API Gateway (if not exists)

```bash
# Create REST API
aws apigateway create-rest-api ^
  --name "auth-server-dev-api" ^
  --description "Authentication and OAuth2 API for Dev environment" ^
  --endpoint-configuration types=REGIONAL

# Get API ID (save this)
aws apigateway get-rest-apis --query 'items[?name==`auth-server-dev-api`].id' --output text
```

### 4.2 Set Up API Gateway Resources and Methods

```bash
# Set API ID variable
set API_ID=YOUR_API_GATEWAY_ID

# Get root resource ID
aws apigateway get-resources --rest-api-id %API_ID% --query 'items[?path==`/`].id' --output text
set ROOT_RESOURCE_ID=OBTAINED_ROOT_RESOURCE_ID

# Create /auth resource (if not exists)
aws apigateway create-resource ^
  --rest-api-id %API_ID% ^
  --parent-id %ROOT_RESOURCE_ID% ^
  --path-part auth

# Get auth resource ID
aws apigateway get-resources --rest-api-id %API_ID% --query 'items[?pathPart==`auth`].id' --output text
set AUTH_RESOURCE_ID=OBTAINED_AUTH_RESOURCE_ID

# Create /oauth resource
aws apigateway create-resource ^
  --rest-api-id %API_ID% ^
  --parent-id %ROOT_RESOURCE_ID% ^
  --path-part oauth

# Get oauth resource ID
aws apigateway get-resources --rest-api-id %API_ID% --query 'items[?pathPart==`oauth`].id' --output text
set OAUTH_RESOURCE_ID=OBTAINED_OAUTH_RESOURCE_ID

# Create /oauth/token resource
aws apigateway create-resource ^
  --rest-api-id %API_ID% ^
  --parent-id %OAUTH_RESOURCE_ID% ^
  --path-part token

# Create /oauth/introspect resource
aws apigateway create-resource ^
  --rest-api-id %API_ID% ^
  --parent-id %OAUTH_RESOURCE_ID% ^
  --path-part introspect
```

### 4.3 Create Methods and Integrations

```bash
# Get resource IDs for token and introspect
aws apigateway get-resources --rest-api-id %API_ID% --query 'items[?pathPart==`token`].id' --output text
set TOKEN_RESOURCE_ID=OBTAINED_TOKEN_RESOURCE_ID

aws apigateway get-resources --rest-api-id %API_ID% --query 'items[?pathPart==`introspect`].id' --output text
set INTROSPECT_RESOURCE_ID=OBTAINED_INTROSPECT_RESOURCE_ID

# Create POST method for /auth
aws apigateway put-method ^
  --rest-api-id %API_ID% ^
  --resource-id %AUTH_RESOURCE_ID% ^
  --http-method POST ^
  --authorization-type NONE

# Create POST method for /oauth/token
aws apigateway put-method ^
  --rest-api-id %API_ID% ^
  --resource-id %TOKEN_RESOURCE_ID% ^
  --http-method POST ^
  --authorization-type NONE

# Create POST method for /oauth/introspect
aws apigateway put-method ^
  --rest-api-id %API_ID% ^
  --resource-id %INTROSPECT_RESOURCE_ID% ^
  --http-method POST ^
  --authorization-type NONE

# Set up Lambda integrations
# For /auth endpoint
aws apigateway put-integration ^
  --rest-api-id %API_ID% ^
  --resource-id %AUTH_RESOURCE_ID% ^
  --http-method POST ^
  --type AWS_PROXY ^
  --integration-http-method POST ^
  --uri arn:aws:apigateway:%REGION%:lambda:path/2015-03-31/functions/arn:aws:lambda:%REGION%:%ACCOUNT_ID%:function:auth-server-dev/invocations

# For /oauth/token endpoint
aws apigateway put-integration ^
  --rest-api-id %API_ID% ^
  --resource-id %TOKEN_RESOURCE_ID% ^
  --http-method POST ^
  --type AWS_PROXY ^
  --integration-http-method POST ^
  --uri arn:aws:apigateway:%REGION%:lambda:path/2015-03-31/functions/arn:aws:lambda:%REGION%:%ACCOUNT_ID%:function:oauth2-server-dev/invocations

# For /oauth/introspect endpoint
aws apigateway put-integration ^
  --rest-api-id %API_ID% ^
  --resource-id %INTROSPECT_RESOURCE_ID% ^
  --http-method POST ^
  --type AWS_PROXY ^
  --integration-http-method POST ^
  --uri arn:aws:apigateway:%REGION%:lambda:path/2015-03-31/functions/arn:aws:lambda:%REGION%:%ACCOUNT_ID%:function:oauth2-server-dev/invocations
```

### 4.4 Grant API Gateway Permission to Invoke Lambda

```bash
# Permission for auth-server-dev
aws lambda add-permission ^
  --function-name auth-server-dev ^
  --statement-id apigateway-auth ^
  --action lambda:InvokeFunction ^
  --principal apigateway.amazonaws.com ^
  --source-arn arn:aws:execute-api:%REGION%:%ACCOUNT_ID%:%API_ID%/*/*

# Permission for oauth2-server-dev
aws lambda add-permission ^
  --function-name oauth2-server-dev ^
  --statement-id apigateway-oauth ^
  --action lambda:InvokeFunction ^
  --principal apigateway.amazonaws.com ^
  --source-arn arn:aws:execute-api:%REGION%:%ACCOUNT_ID%:%API_ID%/*/*
```

### 4.5 Deploy API

```bash
# Create deployment
aws apigateway create-deployment ^
  --rest-api-id %API_ID% ^
  --stage-name dev ^
  --description "OAuth2 deployment"

# Get API endpoint URL
echo https://%API_ID%.execute-api.%REGION%.amazonaws.com/dev
```

## Step 5: Verification and Testing

### 5.1 Test Basic Authentication

```bash
# Test valid credentials (use base64 encoded test:test123)
curl -X POST https://%API_ID%.execute-api.%REGION%.amazonaws.com/dev/auth ^
  -H "Authorization: Basic dGVzdDp0ZXN0MTIz" ^
  -H "Content-Type: application/json"

# Expected: {"authenticated": true, "user": "test"}
```

### 5.2 Test OAuth2 Token Endpoint

```bash
# Test client credentials grant
curl -X POST https://%API_ID%.execute-api.%REGION%.amazonaws.com/dev/oauth/token ^
  -H "Content-Type: application/x-www-form-urlencoded" ^
  -d "grant_type=client_credentials&client_id=test-client-1&client_secret=test-client-1-secret&scope=read"

# Expected: JSON response with access_token, token_type, expires_in, scope
```

### 5.3 Test OAuth2 Token Introspection

```bash
# First get a token, then introspect it
# Replace ACCESS_TOKEN with actual token from previous step
curl -X POST https://%API_ID%.execute-api.%REGION%.amazonaws.com/dev/oauth/introspect ^
  -H "Content-Type: application/x-www-form-urlencoded" ^
  -d "token=ACCESS_TOKEN"

# Expected: JSON response with active: true, client_id, scope, etc.
```

### 5.4 Test Error Cases

```bash
# Test invalid grant type
curl -X POST https://%API_ID%.execute-api.%REGION%.amazonaws.com/dev/oauth/token ^
  -H "Content-Type: application/x-www-form-urlencoded" ^
  -d "grant_type=authorization_code&client_id=test-client-1&client_secret=test-client-1-secret"

# Expected: {"error": "unsupported_grant_type", "error_description": "..."}

# Test invalid client
curl -X POST https://%API_ID%.execute-api.%REGION%.amazonaws.com/dev/oauth/token ^
  -H "Content-Type: application/x-www-form-urlencoded" ^
  -d "grant_type=client_credentials&client_id=invalid&client_secret=invalid"

# Expected: {"error": "invalid_client", "error_description": "..."}

# Test invalid scope
curl -X POST https://%API_ID%.execute-api.%REGION%.amazonaws.com/dev/oauth/token ^
  -H "Content-Type: application/x-www-form-urlencoded" ^
  -d "grant_type=client_credentials&client_id=test-client-2&client_secret=test-client-2-secret&scope=admin"

# Expected: {"error": "invalid_scope", "error_description": "..."}
```

## Step 6: Monitor and Verify

### 6.1 Check CloudWatch Logs

```bash
# View logs for auth-server-dev
aws logs describe-log-groups --log-group-name-prefix /aws/lambda/auth-server-dev

# View logs for oauth2-server-dev
aws logs describe-log-groups --log-group-name-prefix /aws/lambda/oauth2-server-dev

# Get recent log events
aws logs get-log-events ^
  --log-group-name /aws/lambda/oauth2-server-dev ^
  --log-stream-name LATEST_LOG_STREAM_NAME ^
  --start-time 1640995200000
```

### 6.2 Verify Lambda Metrics

```bash
# Check Lambda function metrics
aws cloudwatch get-metric-statistics ^
  --namespace AWS/Lambda ^
  --metric-name Invocations ^
  --dimensions Name=FunctionName,Value=oauth2-server-dev ^
  --start-time 2024-01-01T00:00:00Z ^
  --end-time 2024-01-01T23:59:59Z ^
  --period 3600 ^
  --statistics Sum
```

## Test Client Configurations

### Available Test OAuth Clients

The deployment includes these pre-configured test clients:

1. **test-client-1**
   - Secret: `test-client-1-secret`
   - Scopes: `read`, `write`, `admin`
   - Status: Active

2. **test-client-2**
   - Secret: `test-client-2-secret`
   - Scopes: `read`
   - Status: Active

3. **test-client-3**
   - Secret: `test-client-3-secret`
   - Scopes: `read`, `write`
   - Status: Disabled (for testing access_denied)

## Troubleshooting

### Common Issues

1. **Lambda timeout**: Increase timeout to 30 seconds
2. **Memory issues**: Increase memory to 512MB or higher
3. **Cold start delays**: Expected for first invocation after deployment
4. **Permission errors**: Verify IAM roles and API Gateway permissions

### Log Analysis

```bash
# Enable debug logging by updating environment variables
aws lambda update-function-configuration ^
  --function-name oauth2-server-dev ^
  --environment Variables="{SPRING_PROFILES_ACTIVE=local,LOGGING_LEVEL_COM_EXAMPLE_AUTH=DEBUG}"
```

### Performance Tuning

- Monitor cold start times
- Consider provisioned concurrency for production
- Optimize Lambda memory allocation based on usage patterns

## Security Considerations

1. **Client Secrets**: In production, store in AWS Secrets Manager
2. **API Gateway**: Add rate limiting and API keys
3. **Lambda**: Run in VPC if accessing private resources
4. **Monitoring**: Set up CloudWatch alarms for errors

## Next Steps

After successful manual deployment:

1. Document any environment-specific configurations
2. Test with real client applications
3. Set up monitoring and alerting
4. Prepare for CI/CD pipeline integration
5. Plan production deployment strategy

## Rollback Plan

If deployment issues occur:

```bash
# Rollback Lambda function to previous version
aws lambda update-function-code ^
  --function-name oauth2-server-dev ^
  --zip-file fileb://PREVIOUS_VERSION.jar

# Rollback API Gateway deployment
aws apigateway create-deployment ^
  --rest-api-id %API_ID% ^
  --stage-name dev ^
  --description "Rollback deployment"
```

## Support

For issues or questions:
1. Check CloudWatch logs for error details
2. Verify all configuration parameters
3. Test individual components (Lambda functions, API Gateway)
4. Contact DevOps team for AWS-specific issues
