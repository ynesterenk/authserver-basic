# Deployment Fixes Applied

## Overview
This document records all the manual fixes that were applied during initial deployment and debugging, which have now been incorporated into the IaaS configuration.

## Issues Encountered & Fixed

### 1. HTTP API v2 Compatibility
**Problem**: Lambda handler was using REST API v1 event format but CloudFormation deployed HTTP API v2.
**Fix**: Updated `LambdaHandler.java` to use `APIGatewayV2HTTPEvent` and `APIGatewayV2HTTPResponse`.
**Status**: ✅ Fixed in code

### 2. Jackson Version Compatibility with X-Ray
**Problem**: X-Ray tracing failed due to Jackson 2.16.x removing deprecated fields.
**Fix**: Downgraded Jackson to 2.15.4 for X-Ray compatibility.
**Status**: ✅ Fixed in `pom.xml`

### 3. IAM Permissions for Secrets Manager + KMS
**Problem**: Lambda couldn't decrypt KMS-encrypted secrets.
**Fix**: Added KMS decrypt permissions to IAM policy.
**Status**: ✅ Fixed in `iam-stack.yaml`

### 4. Secret JSON Format
**Problem**: Application expected structured JSON but secret contained plain text.
**Fix**: Updated secret format to include `passwordHash`, `status`, and `roles` fields.
**Status**: ✅ Fixed in `secrets-stack-simple.yaml`

### 5. Spring Profile Configuration
**Problem**: Lambda was using `local` profile instead of `aws` profile.
**Fix**: Added `SPRING_PROFILES_ACTIVE=aws` environment variable.
**Status**: ✅ Fixed in `lambda-stack-simple.yaml`

### 6. Missing Environment Variables
**Problem**: Lambda missing critical configuration variables.
**Fix**: Added all required environment variables to Lambda function.
**Status**: ✅ Fixed in `lambda-stack-simple.yaml`

## Manual Commands Applied (Now Automated)

### IAM Policy Update
```bash
aws iam put-role-policy --role-name "java-auth-server-dev-lambda-role-dev" \
  --policy-name "SecretsManagerAccess" \
  --policy-document file://kms-policy.json
```

### Secret Format Update
```bash
aws secretsmanager update-secret \
  --secret-id "java-auth-server-dev-credentials-dev" \
  --secret-string '{"testuser": {"passwordHash": "...", "status": "ACTIVE", "roles": ["user"]}}'
```

### Lambda Environment Variables
```bash
aws lambda update-function-configuration \
  --function-name "java-auth-server-dev-auth-function-dev" \
  --environment "Variables={SPRING_PROFILES_ACTIVE=aws,CREDENTIAL_SECRET_ARN=...,CACHE_TTL_MINUTES=5,ENVIRONMENT=dev,LOG_LEVEL=DEBUG}"
```

## Pre-Deployment Checklist

### Before Next Deployment
- [ ] Verify IAM stack includes KMS permissions
- [ ] Confirm Lambda stack has all environment variables
- [ ] Check secret format in secrets stack
- [ ] Validate Spring profile is set to 'aws'
- [ ] Ensure Jackson version is 2.15.4
- [ ] Test Argon2id hash generation works

### Post-Deployment Verification
- [ ] Test valid credentials: `testuser:testpass123`
- [ ] Test invalid credentials rejection
- [ ] Verify CloudWatch logs show SecretsManagerUserRepository
- [ ] Check X-Ray traces are working
- [ ] Confirm API responds in < 3 seconds (warm)

## Testing Commands

### Quick API Test
```bash
curl -X POST https://ya8l2od9y5.execute-api.us-east-1.amazonaws.com/dev/auth/validate \
  -H "Authorization: Basic dGVzdHVzZXI6dGVzdHBhc3MxMjM=" \
  -H "Content-Type: application/json"
```

### Expected Success Response
```json
{
  "allowed": true,
  "message": "Authentication successful",
  "timestamp": 1748641558512
}
```

## Files Modified
- `src/main/java/com/example/auth/infrastructure/LambdaHandler.java`
- `src/test/java/com/example/auth/infrastructure/LambdaIntegrationTest.java` 
- `pom.xml` (Jackson version)
- `infrastructure/nested-stacks/iam-stack.yaml`
- `infrastructure/nested-stacks/lambda-stack-simple.yaml`
- `infrastructure/nested-stacks/secrets-stack-simple.yaml`

## Lessons Learned
1. Always test HTTP API v2 vs REST API v1 compatibility
2. X-Ray requires specific Jackson versions
3. KMS permissions needed for encrypted secrets
4. Spring profiles must be explicitly set in Lambda
5. Secret format must match application expectations
6. Environment variables are critical for AWS integration 