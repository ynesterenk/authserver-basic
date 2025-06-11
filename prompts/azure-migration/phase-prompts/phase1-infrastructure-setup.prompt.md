# Phase 1: Infrastructure Foundation Setup Prompt

## Context: Current State and Requirements

We are migrating a Java Authorization Server from AWS to Azure. The current AWS implementation includes:
- AWS Lambda functions (Java 21) for Basic Auth and OAuth 2.0 endpoints
- API Gateway for REST API management
- AWS Secrets Manager for credential storage
- CloudWatch for monitoring and logging
- CloudFormation for infrastructure as code

The target Azure implementation requires:
- Azure Functions (Java 21, Linux) to replace Lambda
- Azure API Management (Developer tier) for API gateway functionality
- Azure Key Vault for secrets management
- Application Insights and Azure Monitor for observability
- Terraform for infrastructure as code

Project location: `/authserver.azure/` subfolder
Environment: Development only (single environment)
Architecture: Hexagonal architecture must be preserved

## Task: Specific Generation Request

Generate the complete Terraform infrastructure code for the Azure foundation setup, including:

1. **Base Infrastructure Module** (`terraform/main.tf`):
   - Resource Group configuration
   - Networking setup (if required)
   - Common tags and naming conventions

2. **Function App Module** (`terraform/modules/function-app/`):
   - Linux Function App with Java 21 runtime
   - Consumption plan for dev environment
   - Storage Account for function runtime
   - System-assigned Managed Identity
   - Environment variables configuration
   - Application settings for Spring profiles

3. **Key Vault Module** (`terraform/modules/key-vault/`):
   - Azure Key Vault with soft delete enabled
   - Access policies for Function App Managed Identity
   - Secret rotation support preparation
   - Purge protection enabled
   - Network access rules

4. **Monitoring Module** (`terraform/modules/monitoring/`):
   - Application Insights instance
   - Log Analytics Workspace
   - Basic alert rules for function failures
   - Custom metrics configuration

5. **API Management Module** (`terraform/modules/api-management/`):
   - API Management instance (Developer tier)
   - Product and API definitions
   - Rate limiting policies (100 requests/minute)
   - CORS configuration
   - Backend configuration for Function App

6. **Variables and Outputs** (`terraform/variables.tf`, `terraform/outputs.tf`):
   - Environment-specific variables
   - Configurable parameters (region, naming, etc.)
   - Output values for CI/CD integration

## Constraints: Architecture, Security, Performance

### Architecture Constraints
- Follow Azure naming conventions: `<resource-type>-<app-name>-<environment>-<region>-<instance>`
- Use Terraform modules for reusability
- Implement proper resource dependencies
- Support for future multi-environment expansion (even though only dev for now)

### Security Constraints
- Enable Managed Identity for all service-to-service authentication
- No hardcoded secrets or credentials
- Key Vault access restricted to Function App identity only
- Network security rules where applicable
- Enable diagnostic settings for audit logging

### Performance Constraints
- Function App must support auto-scaling
- Cold start optimization settings
- Appropriate SKUs for dev environment (cost-optimized)
- Regional deployment for low latency

### Terraform Best Practices
- Use Terraform 1.5+ features
- Azure Provider version ~> 3.0
- Remote state backend configuration (Azure Storage)
- Proper resource naming and tagging
- Modular design with clear interfaces

## Output: Expected Format and Structure

Generate the following directory structure with complete Terraform files:

```
/authserver.azure/terraform/
├── main.tf                    # Root module orchestrating all resources
├── variables.tf               # Input variables for the root module
├── outputs.tf                 # Output values from the deployment
├── terraform.tfvars.example   # Example variable values
├── backend.tf                 # Remote state configuration
├── versions.tf                # Provider version constraints
├── modules/
│   ├── function-app/
│   │   ├── main.tf           # Function App resources
│   │   ├── variables.tf      # Module input variables
│   │   └── outputs.tf        # Module outputs
│   ├── key-vault/
│   │   ├── main.tf           # Key Vault resources
│   │   ├── variables.tf      # Module input variables
│   │   └── outputs.tf        # Module outputs
│   ├── api-management/
│   │   ├── main.tf           # API Management resources
│   │   ├── variables.tf      # Module input variables
│   │   ├── outputs.tf        # Module outputs
│   │   └── policies/         # API policies
│   │       └── rate-limit.xml
│   └── monitoring/
│       ├── main.tf           # Application Insights, Log Analytics
│       ├── variables.tf      # Module input variables
│       └── outputs.tf        # Module outputs
└── environments/
    └── dev/
        └── terraform.tfvars  # Development environment values
```

### Specific Requirements for Each Module:

**Function App Module Requirements:**
- Two function apps: one for Basic Auth, one for OAuth2
- Configure for Java 21 runtime
- Include all necessary app settings for Spring Boot
- Enable Always On for production (parameterized)
- Configure connection strings for Key Vault

**Key Vault Module Requirements:**
- Pre-populate with placeholder secrets structure
- Enable RBAC if possible, otherwise use access policies
- Configure backup and recovery settings
- Set up diagnostic logging

**API Management Module Requirements:**
- Define APIs for:
  - POST /api/auth/validate
  - POST /api/oauth/token
  - POST /api/oauth/introspect
- Configure backend URLs to Function Apps
- Set up subscription keys (optional for dev)
- Health check endpoints

**Monitoring Module Requirements:**
- Connect Application Insights to Function Apps
- Basic alert rules:
  - Function execution failures > 5 in 5 minutes
  - Response time > 1000ms
  - Memory usage > 80%
- Log retention settings

### Additional Outputs:
1. README.md for the terraform directory explaining:
   - How to initialize and deploy
   - Required Azure permissions
   - State management setup
   - Variable descriptions

2. Deployment script (`deploy.sh` or `deploy.ps1`) that:
   - Validates Terraform configuration
   - Plans the deployment
   - Applies with appropriate flags
   - Outputs connection information

Remember to:
- Add comprehensive comments in the Terraform code
- Include validation rules for variables
- Use consistent formatting (terraform fmt compliant)
- Provide sensible defaults where appropriate
- Make the code idempotent and re-runnable 