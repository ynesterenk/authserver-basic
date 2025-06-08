# CI/CD Setup Guide

This guide explains how to configure GitHub Actions CI/CD pipeline for the Java Authorization Server with both Basic Authentication and OAuth2 support.

## Prerequisites

1. **AWS Account**: You need an AWS account with appropriate permissions
2. **GitHub Repository**: Your code should be in a GitHub repository
3. **AWS CLI**: For local testing (optional)

## GitHub Repository Secrets Configuration

You need to configure the following secrets in your GitHub repository settings:

### Go to: Repository Settings > Secrets and variables > Actions

#### Required Secrets:

1. **AWS_ACCESS_KEY_ID** - AWS Access Key for dev environment
2. **AWS_SECRET_ACCESS_KEY** - AWS Secret Key for dev environment

### Creating AWS IAM User for CI/CD

1. Create an IAM user for GitHub Actions with programmatic access
2. Attach the following policies:
   - `CloudFormationFullAccess`
   - `IAMFullAccess` 
   - `S3FullAccess`
   - `LambdaFullAccess`
   - `ApiGatewayFullAccess`
   - `SecretsManagerFullAccess`
   - `CloudWatchFullAccess`

3. Generate Access Keys and add them to GitHub secrets

## Workflow Triggers

The CI/CD pipeline is triggered by:

- **Push to `authserver.migration` branch**: Deploys to development environment
- **Push to `develop` branch**: Deploys to development environment  
- **Pull Requests to `authserver.migration`**: Runs tests and validation only
- **Manual dispatch**: Allows deployment to dev environment manually

## Environment

The pipeline supports one environment:

1. **Development (`dev`)**
   - Auto-deployed on push to `authserver.migration` or `develop` branch
   - Lower resource limits
   - No manual approval required
   - Supports both Basic Auth and OAuth2 endpoints

## Pipeline Stages

### 1. Test & Validate
- Runs all unit tests (including 12 OAuth2 integration tests)
- Security scanning (OWASP dependency check)
- CloudFormation template validation
- Builds Lambda package for both Basic Auth and OAuth2 handlers

### 2. Deploy to Development
- Creates AWS infrastructure using CloudFormation
- Deploys both Lambda functions (Basic Auth + OAuth2)
- Configures API Gateway with all endpoints
- Sets up monitoring and secrets

### 3. Smoke Tests
- Tests Basic Authentication endpoints
- Tests OAuth2 token generation
- Tests OAuth2 token introspection  
- Validates error handling for both auth methods
- Checks response times

### 4. Notifications & Cleanup
- Creates deployment notifications
- Cleans up failed deployments
- Reports test results

## API Endpoints Tested

### Basic Authentication:
- `POST /auth/validate` - Basic auth validation

### OAuth2 Endpoints:
- `POST /oauth/token` - OAuth2 token generation (Client Credentials Grant)
- `POST /oauth/introspect` - OAuth2 token introspection

## Configuration Files

- **`.github/workflows/deploy.yml`**: Main CI/CD workflow (updated for authserver.migration branch)
- **`scripts/deploy.sh`**: Deployment script
- **`scripts/smoke-test.sh`**: Enhanced smoke testing script (tests both auth methods)
- **`infrastructure/`**: CloudFormation templates
- **`infrastructure/parameters/`**: Environment-specific parameters

## Testing the Pipeline

1. **Local Testing**: Use SAM CLI for local development
2. **Development**: Push to `authserver.migration` branch to test full pipeline
3. **Pull Requests**: Create PR to `authserver.migration` to run tests only

## Troubleshooting

### Common Issues:

1. **AWS Credentials**: Ensure secrets are correctly configured
2. **Permissions**: IAM user needs sufficient permissions for all AWS services
3. **Resource Conflicts**: Stack names must be unique per environment
4. **Timeouts**: Lambda and API Gateway have timeout limits
5. **OAuth2 Tests**: Ensure OAuth2 clients are properly configured

### Debugging:

1. Check GitHub Actions logs for detailed error messages
2. Review CloudFormation stack events in AWS Console
3. Check Lambda function logs in CloudWatch for both auth handlers
4. Use `scripts/debug-nested-stacks.ps1` for local debugging

## Security Considerations

1. **Least Privilege**: Use minimal required AWS permissions
2. **Secret Rotation**: Regularly rotate AWS access keys
3. **OAuth2 Security**: JWT tokens are properly signed and validated
4. **Monitoring**: Enable CloudTrail and monitoring for all environments

## Manual Deployment

If you need to deploy manually:

```bash
# Make sure you're in the project root
cd "c:\Projects\LSEG\AI Initiative\AWS to Azure Migration\AWS server - basic"

# Set AWS credentials
$env:AWS_ACCESS_KEY_ID = "your-access-key"
$env:AWS_SECRET_ACCESS_KEY = "your-secret-key"
$env:AWS_DEFAULT_REGION = "us-east-1"

# Deploy to development
.\scripts\deploy.ps1 -Environment dev -Region us-east-1

# Or use bash script (if you have Git Bash/WSL)
chmod +x scripts/deploy.sh
./scripts/deploy.sh dev us-east-1
```

## Testing Both Authentication Methods

After deployment, you can test both auth methods:

### Basic Authentication:
```bash
curl -X POST https://your-api-endpoint/auth/validate \
  -H "Authorization: Basic $(echo -n 'testuser:testpass123' | base64)" \
  -H "Content-Type: application/json"
```

### OAuth2:
```bash
# Get access token
curl -X POST https://your-api-endpoint/oauth/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials&client_id=test-client-1&client_secret=test-client-1-secret&scope=read"

# Introspect token
curl -X POST https://your-api-endpoint/oauth/introspect \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "token=YOUR_ACCESS_TOKEN"
```

## Next Steps

After setting up CI/CD:

1. **Monitoring**: Set up CloudWatch dashboards and alarms for both auth methods
2. **Security**: Enable AWS Config and Security Hub
3. **Backup**: Configure automated backups for critical resources
4. **OAuth2 Clients**: Add production OAuth2 clients via Secrets Manager
5. **Rate Limiting**: Consider implementing rate limiting for both auth endpoints
