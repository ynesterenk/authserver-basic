# Phase 4: Deployment Automation Prompt

## Context: Current State and Requirements

We are creating a deployment automation pipeline for the Azure-migrated Java Authorization Server. The current AWS implementation has:

**Existing AWS CI/CD Pipeline:**
- GitHub Actions workflow triggered on push to main/develop branches
- Build and test stages with Maven
- Security scanning with OWASP Dependency Check
- CloudFormation deployment using deploy.sh script
- Smoke tests to validate deployment
- Artifact storage in S3
- Deployment info JSON output

**Azure Target Requirements:**
- Deploy only to dev environment (single environment focus)
- Use Terraform instead of CloudFormation
- Azure Functions deployment with Java 21
- Store artifacts in Azure Storage
- Key Vault for secrets management
- Application Insights for monitoring
- Maintain similar deployment flow as AWS

**Pipeline Features to Maintain:**
- Automated testing before deployment
- Security scanning
- Infrastructure validation
- Automated deployment on push to main
- Smoke tests after deployment
- Deployment info output
- Rollback capabilities

## Task: Specific Generation Request

Generate a complete CI/CD pipeline for Azure deployment, including:

1. **GitHub Actions Workflow** (`authserver.azure/.github/workflows/azure-deploy.yml`):
   - Trigger on push to main branch with paths filter for authserver.azure/
   - Test stage: unit tests, integration tests, coverage
   - Security scanning stage
   - Build stage: Maven package for Azure Functions
   - Infrastructure stage: Terraform plan and apply
   - Deploy stage: Function app deployment
   - Verify stage: smoke tests
   - Notification and cleanup jobs

2. **Deployment Script** (`authserver.azure/scripts/deploy-azure.sh`):
   - Terraform initialization and workspace selection
   - Azure Storage backend configuration
   - Terraform plan with validation
   - Terraform apply with auto-approve for dev
   - Function app deployment using Azure CLI
   - Output deployment information

3. **Smoke Test Script** (`authserver.azure/scripts/smoke-test-azure.sh`):
   - Test Basic Auth endpoint (/api/auth/validate)
   - Test OAuth2 token generation (/api/oauth/token)
   - Test OAuth2 token introspection (/api/oauth/introspect)
   - Validate response formats and status codes
   - Performance validation (response times)

4. **Terraform Backend Configuration** (`authserver.azure/terraform/backend-config/dev.tfvars`):
   - Azure Storage Account for Terraform state
   - State file locking with blob lease
   - Encryption at rest

5. **Environment Configuration** (`authserver.azure/.github/env/dev.env`):
   - Azure subscription settings
   - Resource naming conventions
   - Region configuration
   - Function app settings

6. **Deployment Documentation** (`authserver.azure/docs/deployment-guide.md`):
   - Prerequisites and setup
   - Local deployment instructions
   - CI/CD pipeline overview
   - Troubleshooting guide
   - Rollback procedures

## Constraints: Architecture, Security, Performance

### Security Constraints
- **No Secrets in Code**: All secrets via GitHub Secrets or Key Vault
- **Service Principal**: Use least-privilege Azure AD service principal
- **Managed Identity**: Function App uses managed identity for Key Vault
- **Secure State**: Terraform state encrypted and access-controlled
- **Network Security**: Private endpoints where applicable

### Architecture Constraints
- **Single Environment**: Only dev environment deployment
- **Terraform Modules**: Use modular Terraform design from Phase 1
- **Artifact Storage**: Use Azure Storage for build artifacts
- **Naming Convention**: Follow Azure naming standards (e.g., func-authserver-dev-001)

### Performance Constraints
- **Pipeline Speed**: Total deployment < 15 minutes
- **Parallel Jobs**: Run tests in parallel where possible
- **Caching**: Cache Maven dependencies and Terraform providers
- **Incremental Deploy**: Only deploy changed resources

### Azure-Specific Requirements
- **Azure CLI**: Use latest Azure CLI for deployments
- **Terraform Version**: Pin to specific version (>= 1.5)
- **Function Runtime**: JAVA|21 on Linux consumption plan
- **API Management**: Deploy API definitions if using APIM

## Output: Expected Format and Structure

Generate the following deployment automation components:

### 1. GitHub Actions Workflow Structure

**azure-deploy.yml:**
```yaml
name: Deploy Azure Authorization Server

on:
  push:
    branches: [main]
    paths:
      - 'authserver.azure/**'
      - '.github/workflows/azure-deploy.yml'
  pull_request:
    branches: [main]
    paths:
      - 'authserver.azure/**'
  workflow_dispatch:
    inputs:
      deploy:
        description: 'Deploy to Azure'
        required: true
        type: boolean
        default: true

env:
  AZURE_FUNCTIONAPP_NAME: func-authserver-dev-001
  AZURE_RESOURCE_GROUP: rg-authserver-dev
  TERRAFORM_VERSION: '1.6.0'
  JAVA_VERSION: '21'

jobs:
  test:
    # Test job implementation
  
  security-scan:
    # Security scanning implementation
  
  build:
    # Build and package implementation
  
  deploy-infrastructure:
    # Terraform deployment
  
  deploy-application:
    # Function app deployment
  
  smoke-test:
    # Post-deployment validation
  
  notify:
    # Status notifications
```

### 2. Deployment Scripts

**deploy-azure.sh structure:**
- Parameter validation
- Azure CLI authentication check
- Terraform initialization
- Terraform workspace management
- Infrastructure deployment
- Function app deployment
- Deployment info generation

**smoke-test-azure.sh structure:**
- Endpoint discovery from Terraform outputs
- Basic auth validation tests
- OAuth2 flow tests
- Performance checks
- Summary report generation

### 3. Terraform Backend Configuration

**backend-config/dev.tfvars:**
```hcl
resource_group_name  = "rg-authserver-terraform-dev"
storage_account_name = "stateauthserverdev"
container_name       = "terraform-state"
key                  = "authserver.tfstate"
```

### 4. Required GitHub Secrets

Configure these secrets in GitHub repository:
- `AZURE_CLIENT_ID`: Service principal application ID
- `AZURE_CLIENT_SECRET`: Service principal secret
- `AZURE_SUBSCRIPTION_ID`: Azure subscription ID
- `AZURE_TENANT_ID`: Azure AD tenant ID
- `TERRAFORM_BACKEND_KEY`: Storage account key for Terraform state

### 5. Pipeline Stages Detail

**Test Stage:**
- Run unit tests with coverage
- Run integration tests (if not PR)
- Generate test reports
- Check coverage thresholds (90%)

**Security Scan Stage:**
- OWASP dependency check
- Code vulnerability scanning
- License compliance check
- Security report upload

**Build Stage:**
- Maven clean package
- Create deployment package
- Version tagging
- Artifact upload

**Deploy Infrastructure Stage:**
- Terraform fmt check
- Terraform init with backend
- Terraform validate
- Terraform plan
- Terraform apply (auto-approve for main branch)
- Output infrastructure details

**Deploy Application Stage:**
- Download build artifacts
- Deploy to Function App
- Configure app settings
- Restart Function App
- Health check validation

**Smoke Test Stage:**
- Wait for app to be ready
- Run all API endpoint tests
- Validate functionality
- Performance benchmarks
- Generate test report

### 6. Monitoring and Alerts

**Application Insights Integration:**
- Deploy custom alerts via Terraform
- Response time alerts
- Error rate alerts
- Availability alerts

**Deployment Metrics:**
- Track deployment duration
- Success/failure rates
- Resource costs
- Performance baselines

### 7. Rollback Strategy

**Automatic Rollback Triggers:**
- Smoke test failures
- Health check failures
- Critical alerts

**Manual Rollback Process:**
```bash
# Rollback to previous Terraform state
terraform workspace select dev
terraform plan -var-file="environments/dev/terraform.tfvars" -refresh=true
terraform apply -auto-approve

# Rollback Function App to previous version
az functionapp deployment list-publishing-profiles \
  --name $AZURE_FUNCTIONAPP_NAME \
  --resource-group $AZURE_RESOURCE_GROUP
```

### 8. Local Development Deployment

**Local deployment commands:**
```bash
# Set up Azure CLI
az login
az account set --subscription $AZURE_SUBSCRIPTION_ID

# Deploy infrastructure
cd authserver.azure/terraform
terraform init -backend-config=backend-config/dev.tfvars
terraform plan -var-file=environments/dev/terraform.tfvars
terraform apply -var-file=environments/dev/terraform.tfvars

# Deploy function app
cd ../
mvn clean package
func azure functionapp publish $AZURE_FUNCTIONAPP_NAME --java

# Run smoke tests
./scripts/smoke-test-azure.sh
```

### Additional Requirements:

1. **Error Handling**:
   - Graceful failure with clear error messages
   - Automatic rollback on critical failures
   - Detailed logs for troubleshooting

2. **Notifications**:
   - Success/failure notifications
   - Deployment summary with links
   - Performance comparison vs baseline

3. **Cost Management**:
   - Tag all resources with deployment info
   - Cost estimation in Terraform plan
   - Budget alerts configuration

4. **Compliance**:
   - Ensure all resources follow naming standards
   - Apply required tags (Environment, Project, Owner)
   - Security baseline compliance

5. **Documentation**:
   - Update deployment guide with each change
   - Document any manual steps required
   - Maintain troubleshooting FAQ

Remember to:
- Keep deployment idempotent
- Use --what-if/plan before any changes
- Implement proper state locking
- Version all deployment artifacts
- Test rollback procedures regularly
- Monitor deployment metrics
- Keep secrets secure and rotated 