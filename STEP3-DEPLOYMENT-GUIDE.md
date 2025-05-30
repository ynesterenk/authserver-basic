# Step 3: CloudFormation Infrastructure & Deployment Guide
## Java Authorization Server - Complete AWS Deployment

---

## 🎯 Overview

This guide walks you through deploying the complete Java Authorization Server infrastructure to AWS using CloudFormation, including:

- **Modular CloudFormation Templates**: Nested stacks for maintainability
- **Multi-Environment Support**: Dev/Staging/Prod configurations  
- **CI/CD Pipeline**: Automated deployment with GitHub Actions
- **Security Hardening**: IAM least privilege, encryption, monitoring
- **Operational Excellence**: CloudWatch dashboards, alarms, logging

---

## 📋 Prerequisites

### 1. AWS CLI Installation

#### Windows (PowerShell)
```powershell
# Option 1: Using winget
winget install Amazon.AWSCLI

# Option 2: Direct download
# Download from: https://aws.amazon.com/cli/
```

#### macOS
```bash
# Using Homebrew
brew install awscli

# Or download from: https://aws.amazon.com/cli/
```

#### Linux
```bash
# Using pip
pip install awscli

# Or download from: https://aws.amazon.com/cli/
```

### 2. Java & Maven
- Java 21 (Amazon Corretto recommended)
- Maven 3.8+
- Git

### 3. AWS Account Setup
- AWS Account ID: `545009823602` (your account)
- IAM User with appropriate permissions (see below)

---

## 🔐 AWS Credentials Configuration

### 1. Create IAM User
Create an IAM user with the following permissions for deployment:

**Required IAM Policies:**
- `AWSCloudFormationFullAccess`
- `AWSLambdaFullAccess` 
- `AmazonAPIGatewayAdministrator`
- `SecretsManagerReadWrite`
- `AmazonS3FullAccess`
- `CloudWatchFullAccess`
- `IAMFullAccess` (for role creation)

### 2. Configure AWS CLI
```bash
aws configure
```

**Required Information:**
- **AWS Access Key ID**: [Your IAM user access key]
- **AWS Secret Access Key**: [Your IAM user secret key]
- **Default region name**: `us-east-1`
- **Default output format**: `json`

### 3. Verify Configuration
```bash
aws sts get-caller-identity
```

Should return:
```json
{
    "UserId": "...",
    "Account": "545009823602",
    "Arn": "arn:aws:iam::545009823602:user/your-username"
}
```

---

## 🏗️ Infrastructure Components

The deployment creates:

### Core Infrastructure
- **Lambda Function**: Java 21 runtime with 512MB-1024MB memory
- **API Gateway**: HTTP API with throttling and CORS
- **Secrets Manager**: Encrypted user credentials storage
- **CloudWatch**: Logs, metrics, alarms, and dashboards
- **IAM Roles**: Least privilege security policies

### Monitoring & Observability
- **CloudWatch Dashboard**: Real-time metrics visualization
- **Custom Alarms**: Error rates, latency, throttling
- **Log Analytics**: Structured JSON logging
- **X-Ray Tracing**: Distributed request tracing
- **SNS Notifications**: Email alerts for critical issues

### Security Features
- **KMS Encryption**: Secrets Manager and SQS encryption
- **IAM Least Privilege**: Minimal required permissions
- **VPC Integration**: Optional network isolation
- **WAF Protection**: Production rate limiting and security rules

---

## 🚀 Deployment Instructions

### Method 1: PowerShell Script (Windows)

1. **Navigate to project root:**
```powershell
cd "C:\Projects\LSEG\AI Initiative\AWS to Azure Migration\AWS server - basic"
```

2. **Run deployment script:**
```powershell
# Deploy to development
.\scripts\deploy.ps1 -Environment dev

# Deploy to staging  
.\scripts\deploy.ps1 -Environment staging

# Deploy to production
.\scripts\deploy.ps1 -Environment prod -Region us-east-1
```

### Method 2: Bash Script (Linux/macOS/Git Bash)

1. **Make scripts executable:**
```bash
chmod +x scripts/deploy.sh scripts/smoke-test.sh
```

2. **Run deployment:**
```bash
# Deploy to development
./scripts/deploy.sh dev us-east-1

# Deploy to staging
./scripts/deploy.sh staging us-east-1

# Deploy to production  
./scripts/deploy.sh prod us-east-1
```

### Method 3: Manual CloudFormation

1. **Build Lambda package:**
```bash
mvn clean package -DskipTests
```

2. **Create S3 bucket:**
```bash
aws s3 mb s3://auth-server-artifacts-545009823602-us-east-1
```

3. **Upload artifacts:**
```bash
aws s3 cp target/auth-server-lambda.jar s3://auth-server-artifacts-545009823602-us-east-1/
aws s3 sync infrastructure/nested-stacks/ s3://auth-server-artifacts-545009823602-us-east-1/nested-stacks/
```

4. **Deploy CloudFormation:**
```bash
aws cloudformation deploy \
  --template-file infrastructure/main-template.yaml \
  --stack-name java-auth-server-dev \
  --parameter-overrides file://infrastructure/parameters/dev-params.json \
  --capabilities CAPABILITY_NAMED_IAM \
  --region us-east-1
```

---

## 🧪 Testing & Validation

### Automated Smoke Tests

After deployment, run comprehensive smoke tests:

```powershell
# Windows
.\scripts\smoke-test.ps1 -Environment dev

# Linux/macOS  
./scripts/smoke-test.sh dev us-east-1
```

### Manual Testing

1. **Get API Endpoint:**
```bash
aws cloudformation describe-stacks \
  --stack-name java-auth-server-dev \
  --query 'Stacks[0].Outputs[?OutputKey==`ApiEndpoint`].OutputValue' \
  --output text
```

2. **Test Authentication:**
```bash
# Valid authentication
curl -X POST https://your-api-endpoint/auth/validate \
  -H "Authorization: Basic $(echo -n 'alice:password123' | base64)" \
  -H "Content-Type: application/json"

# Expected: {"allowed":true,"message":"Authentication successful","timestamp":...}

# Invalid authentication  
curl -X POST https://your-api-endpoint/auth/validate \
  -H "Authorization: Basic $(echo -n 'alice:wrongpassword' | base64)" \
  -H "Content-Type: application/json"

# Expected: {"allowed":false,"message":"Authentication failed","timestamp":...}
```

3. **Health Check:**
```bash
curl -X GET https://your-api-endpoint/health
```

---

## 📊 Monitoring & Operations

### CloudWatch Dashboard

Access your monitoring dashboard:
```
https://us-east-1.console.aws.amazon.com/cloudwatch/home?region=us-east-1#dashboards:name=java-auth-server-dev-dashboard
```

**Key Metrics:**
- Lambda invocations, errors, duration
- API Gateway request count, latency, errors  
- Authentication success/failure rates
- Custom business metrics

### CloudWatch Alarms

**Configured Alarms:**
- Lambda error rate > 5 (prod) / 10 (dev)
- Lambda duration > 5s (prod) / 10s (dev)
- API Gateway 5XX errors > 5
- Authentication failure rate > 5%

### Log Analysis

**CloudWatch Logs Insights Queries:**

Performance Analysis:
```sql
fields @timestamp, @duration
| filter @type = "REPORT"  
| stats avg(@duration), max(@duration), min(@duration) by bin(5m)
```

Error Analysis:
```sql
fields @timestamp, @message
| filter @message like /ERROR/
| stats count() by bin(1h)
```

---

## 🌍 Multi-Environment Management

### Environment Configurations

| Environment | Memory | Concurrency | Cache TTL | Monitoring |
|-------------|--------|-------------|-----------|------------|
| **dev** | 512MB | 10 | 5min | Basic |
| **staging** | 768MB | 50 | 5min | Enhanced |
| **prod** | 1024MB | 100 | 10min | Full |

### Parameter Files

- `infrastructure/parameters/dev-params.json`
- `infrastructure/parameters/staging-params.json`  
- `infrastructure/parameters/prod-params.json`

### Environment Promotion

1. **Dev → Staging:**
```bash
# Deploy to staging after dev validation
./scripts/deploy.sh staging us-east-1
./scripts/smoke-test.sh staging us-east-1
```

2. **Staging → Prod:**
```bash
# Deploy to production after staging validation
./scripts/deploy.sh prod us-east-1
./scripts/smoke-test.sh prod us-east-1
```

---

## 🔄 CI/CD Pipeline

### GitHub Actions Setup

1. **Configure Repository Secrets:**
```
AWS_ACCESS_KEY_ID=your-access-key
AWS_SECRET_ACCESS_KEY=your-secret-key  
AWS_ACCESS_KEY_ID_PROD=your-prod-access-key
AWS_SECRET_ACCESS_KEY_PROD=your-prod-secret-key
```

2. **Trigger Deployment:**
```bash
# Push to develop branch → Deploy to dev
git push origin develop

# Push to main branch → Deploy to staging → prod (with approval)
git push origin main

# Manual deployment
gh workflow run deploy.yml -f environment=prod -f region=us-east-1
```

### Pipeline Stages

1. **Test & Validate**: Unit tests, security scan, template validation
2. **Build**: Maven package, artifact creation
3. **Deploy Dev**: Automatic deployment to development
4. **Deploy Staging**: Automatic deployment to staging  
5. **Deploy Prod**: Manual approval required for production
6. **Smoke Tests**: Automated validation post-deployment
7. **Notifications**: Success/failure notifications

---

## 🛠️ Troubleshooting

### Common Issues

1. **CloudFormation Deployment Fails**
```bash
# Check stack events
aws cloudformation describe-stack-events --stack-name java-auth-server-dev

# Check stack status
aws cloudformation describe-stacks --stack-name java-auth-server-dev --query 'Stacks[0].StackStatus'
```

2. **Lambda Function Errors**
```bash
# Check Lambda logs
aws logs tail /aws/lambda/java-auth-server-auth-function-dev --follow

# Check function configuration
aws lambda get-function --function-name java-auth-server-auth-function-dev
```

3. **API Gateway Issues**
```bash
# Check API Gateway logs
aws logs tail /aws/apigateway/java-auth-server-dev --follow

# Test API Gateway directly
aws apigatewayv2 get-apis --query 'Items[?Name==`java-auth-server-api-dev`]'
```

### Rollback Procedures

1. **CloudFormation Rollback**
```bash
# Cancel in-progress update
aws cloudformation cancel-update-stack --stack-name java-auth-server-dev

# Rollback to previous version
aws cloudformation continue-update-rollback --stack-name java-auth-server-dev
```

2. **Lambda Version Rollback**
```bash
# Update alias to previous version
aws lambda update-alias \
  --function-name java-auth-server-auth-function-dev \
  --name dev \
  --function-version $PREVIOUS_VERSION
```

---

## 📈 Performance Optimization

### Cold Start Optimization
- **Provisioned Concurrency**: 5 instances (prod only)
- **Memory Allocation**: 1024MB (prod), 512MB (dev)
- **Java Optimization**: Tiered compilation, optimized memory settings

### Cost Optimization
- **Reserved Concurrency**: Prevents runaway costs
- **Log Retention**: 30 days (prod), 7 days (dev)
- **S3 Lifecycle**: Automatic artifact cleanup

### Scaling Configuration
- **API Gateway Throttling**: 500 RPS burst (prod), 100 RPS (dev)
- **Lambda Concurrency**: 100 (prod), 10 (dev)
- **Auto Scaling**: Automatic based on demand

---

## 🔒 Security Best Practices

### IAM Security
- Least privilege access policies
- Cross-account role assumptions for CI/CD
- Regular access key rotation

### Data Protection
- KMS encryption for all secrets
- TLS 1.2+ for all communications
- No sensitive data in logs

### Network Security
- VPC integration (optional)
- WAF protection (production)
- Private S3 bucket access

### Compliance
- CloudTrail logging enabled
- AWS Config compliance rules
- Regular security assessments

---

## 📞 Support & Maintenance

### Monitoring Checklist
- [ ] CloudWatch alarms configured
- [ ] SNS notifications working
- [ ] Dashboard accessible
- [ ] Log retention appropriate
- [ ] X-Ray tracing enabled

### Regular Maintenance
- **Weekly**: Review CloudWatch metrics
- **Monthly**: Update dependencies, security patches
- **Quarterly**: Performance optimization review
- **Annually**: Architecture review, cost optimization

### Contact Information
- **DevOps Team**: [Your team contact]
- **Security Team**: [Security contact]
- **AWS Support**: [Support plan details]

---

## 🎉 Conclusion

Your Java Authorization Server is now deployed with:

✅ **Production-Ready Infrastructure**  
✅ **Comprehensive Monitoring**  
✅ **Security Hardening**  
✅ **CI/CD Automation**  
✅ **Multi-Environment Support**

**Next Steps:**
1. Configure custom domain names
2. Set up SSL certificates
3. Implement additional auth strategies
4. Performance load testing
5. Disaster recovery planning

---

*Documentation updated: May 30, 2025*  
*Version: Step 3 CloudFormation Infrastructure* 