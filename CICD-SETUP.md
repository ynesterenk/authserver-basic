# CI/CD Setup Guide

This guide explains how to configure GitHub Actions CI/CD pipeline for the Java Authorization Server.

## Prerequisites

1. **AWS Account**: You need an AWS account with appropriate permissions
2. **GitHub Repository**: Your code should be in a GitHub repository
3. **AWS CLI**: For local testing (optional)

## GitHub Repository Secrets Configuration

You need to configure the following secrets in your GitHub repository settings:

### Go to: Repository Settings > Secrets and variables > Actions

#### Required Secrets:

1. **AWS_ACCESS_KEY_ID** - AWS Access Key for dev/staging environments
2. **AWS_SECRET_ACCESS_KEY** - AWS Secret Key for dev/staging environments
3. **AWS_ACCESS_KEY_ID_PROD** - AWS Access Key for production environment  
4. **AWS_SECRET_ACCESS_KEY_PROD** - AWS Secret Key for production environment

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

- **Push to `develop` branch**: Deploys to development environment
- **Push to `main` branch**: Deploys to staging, then production (with approval)
- **Pull Requests**: Runs tests and validation only
- **Manual dispatch**: Allows deployment to any environment manually

## Environments

The pipeline supports three environments:

1. **Development (`dev`)**
   - Auto-deployed on push to `develop` branch
   - Lower resource limits
   - No manual approval required

2. **Staging (`staging`)**
   - Auto-deployed on push to `main` branch
   - Production-like configuration
   - No manual approval required

3. **Production (`prod`)**
   - Deployed after staging success
   - Requires manual approval in GitHub
   - Full production configuration

## Pipeline Stages

### 1. Test & Validate
- Runs unit tests
- Security scanning (OWASP dependency check)
- CloudFormation template validation
- Builds Lambda package

### 2. Deploy to Environment
- Creates AWS infrastructure using CloudFormation
- Deploys Lambda function
- Configures API Gateway
- Sets up monitoring and secrets

### 3. Smoke Tests
- Tests API endpoints
- Validates authentication
- Checks response times
- Verifies error handling

### 4. Notifications & Cleanup
- Creates GitHub releases for production
- Cleans up failed deployments
- Sends notifications

## Configuration Files

- **`.github/workflows/deploy.yml`**: Main CI/CD workflow
- **`scripts/deploy.sh`**: Deployment script
- **`scripts/smoke-test.sh`**: Smoke testing script
- **`infrastructure/`**: CloudFormation templates
- **`infrastructure/parameters/`**: Environment-specific parameters

## Testing the Pipeline

1. **Local Testing**: Use SAM CLI for local development
2. **Development**: Push to `develop` branch to test full pipeline
3. **Staging**: Create PR and merge to `main` to test staging deployment
4. **Production**: Approve production deployment manually in GitHub Actions

## Troubleshooting

### Common Issues:

1. **AWS Credentials**: Ensure secrets are correctly configured
2. **Permissions**: IAM user needs sufficient permissions for all AWS services
3. **Resource Conflicts**: Stack names must be unique per environment
4. **Timeouts**: Lambda and API Gateway have timeout limits

### Debugging:

1. Check GitHub Actions logs for detailed error messages
2. Review CloudFormation stack events in AWS Console
3. Check Lambda function logs in CloudWatch
4. Use `scripts/debug-nested-stacks.ps1` for local debugging

## Security Considerations

1. **Least Privilege**: Use minimal required AWS permissions
2. **Secret Rotation**: Regularly rotate AWS access keys
3. **Environment Isolation**: Use separate AWS accounts for prod if possible
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

## Next Steps

After setting up CI/CD:

1. **Monitoring**: Set up CloudWatch dashboards and alarms
2. **Security**: Enable AWS Config and Security Hub
3. **Backup**: Configure automated backups for critical resources
4. **Scaling**: Consider auto-scaling policies for production
5. **Disaster Recovery**: Plan for multi-region deployment
