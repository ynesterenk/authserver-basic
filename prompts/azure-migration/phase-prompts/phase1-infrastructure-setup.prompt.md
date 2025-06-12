# Phase 1: Infrastructure Foundation Setup Prompt

## Context: Current State and Requirements

We are migrating a Java Authorization Server from AWS to Azure. The current AWS implementation includes:
- AWS Lambda functions (Java 21) for Basic Auth and OAuth 2.0 endpoints
- API Gateway for REST API management
- AWS Secrets Manager for credential storage
- CloudWatch for monitoring and logging
- CloudFormation for infrastructure as code

The target Azure implementation requires:
- Azure Functions (Java 17, Linux) to replace Lambda **(Note: Java 21 not yet supported by Azure Functions)**
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
   - **IMPORTANT**: Use West US as default region (better quota availability)

2. **Function App Module** (`terraform/modules/function-app/`):
   - Linux Function App with Java 17 runtime **(Java 21 not supported)**
   - Consumption plan (Y1) for dev environment with Basic plan (B1) as fallback
   - Storage Account for function runtime
   - System-assigned Managed Identity
   - Environment variables configuration
   - Application settings for Spring profiles
   - **Handle both Consumption and Basic plans** for quota flexibility

3. **Key Vault Module** (`terraform/modules/key-vault/`):
   - Azure Key Vault with soft delete enabled
   - **CRITICAL**: Ensure Key Vault names stay within 24-character limit
   - Access policies for Function App Managed Identity
   - **IP whitelist configuration** for deployer access
   - Secret rotation support preparation
   - Purge protection enabled
   - Network access rules with IP-based restrictions

4. **Monitoring Module** (`terraform/modules/monitoring/`):
   - Application Insights instance
   - Log Analytics Workspace
   - **Simplified alert rules** (avoid complex metric-based alerts)
   - Custom Log Analytics queries for monitoring
   - Saved searches for common monitoring scenarios

5. **API Management Module** (`terraform/modules/api-management/`):
   - API Management instance (Developer tier)
   - Product and API definitions
   - Rate limiting policies (100 requests/minute)
   - CORS configuration
   - Backend configuration for Function App

6. **Variables and Outputs** (`terraform/variables.tf`, `terraform/outputs.tf`):
   - Environment-specific variables
   - Configurable parameters (region, naming, etc.)
   - **IP address variable** for Key Vault access
   - Output values for CI/CD integration

## Constraints: Architecture, Security, Performance

### Architecture Constraints
- Follow Azure naming conventions: `<resource-type>-<app-name>-<environment>-<region>-<instance>`
- **Keep resource names under limits** (Key Vault: 24 chars, Storage: 24 chars)
- Use Terraform modules for reusability
- Implement proper resource dependencies
- Support for future multi-environment expansion (even though only dev for now)
- **Default to West US region** (proven better quota availability)

### Security Constraints
- Enable Managed Identity for all service-to-service authentication
- No hardcoded secrets or credentials
- Key Vault access restricted to Function App identity + deployer IP
- **Configure IP-based access control** for Key Vault
- Network security rules where applicable
- Enable diagnostic settings for audit logging

### Performance Constraints
- Function App must support auto-scaling
- Cold start optimization settings
- Appropriate SKUs for dev environment (cost-optimized)
- Regional deployment for low latency
- **Handle Consumption plan quota limitations** with Basic plan fallback

### Terraform Best Practices
- Use Terraform 1.5+ features
- Azure Provider version ~> 3.0
- Remote state backend configuration (Azure Storage)
- Proper resource naming and tagging
- Modular design with clear interfaces
- **Include validation for Java versions** (only 8, 11, 17 supported)

## Critical Lessons Learned (Implementation Notes)

### 1. Java Runtime Limitations
- **Azure Functions supports**: Java 8, 11, 17 only
- **Java 21 is NOT supported** (as of 2024)
- Default to Java 17 for latest supported version
- Include validation in variables to prevent Java 21 usage

### 2. Key Vault Naming Restrictions
- **Maximum 24 characters** for Key Vault names
- Use shortened naming pattern: `kv-<project><env><region><suffix>`
- Example: `kv-authserverdevwus7zy` (21 chars)
- **Auto-generate suffix** to ensure uniqueness within limit

### 3. Regional Quota Issues
- **East US**: Often has quota limitations for Function Apps
- **West US**: Better availability for Consumption plans
- **Default to West US** in examples and configuration
- **Include troubleshooting section** about quota issues

### 4. Network Access Configuration
- **Key Vault requires IP whitelist** for deployer access
- Make `allowed_ips` a required variable
- Include current IP detection guidance
- **Firewall blocks deployment** without proper IP configuration

### 5. Function App Plan Flexibility
- **Consumption (Y1)**: Cheapest but may hit quota limits
- **Basic (B1)**: More expensive (~$25/month) but reliable
- **Include both options** in SKU validation
- Provide cost comparison in documentation

### 6. Monitoring Simplification
- **Avoid complex metric-based alerts** (often fail during deployment)
- Use **Log Analytics queries** instead of direct metrics
- **Saved searches** work better than real-time alerts for development
- Focus on simple, functional monitoring

## Output: Expected Format and Structure

Generate the following directory structure with complete Terraform files:

```
/authserver.azure/terraform/
├── main.tf                    # Root module orchestrating all resources
├── variables.tf               # Input variables with validation rules
├── outputs.tf                 # Output values from the deployment
├── terraform.tfvars.example   # Example with West US and Java 17
├── backend.tf                 # Remote state configuration
├── versions.tf                # Provider version constraints
├── modules/
│   ├── function-app/
│   │   ├── main.tf           # Function App with consumption/basic flexibility
│   │   ├── variables.tf      # Java version validation (8,11,17 only)
│   │   └── outputs.tf        # Module outputs
│   ├── key-vault/
│   │   ├── main.tf           # Key Vault with 24-char naming
│   │   ├── variables.tf      # IP whitelist variables
│   │   └── outputs.tf        # Module outputs
│   ├── api-management/
│   │   ├── main.tf           # API Management resources
│   │   ├── variables.tf      # Module input variables
│   │   ├── outputs.tf        # Module outputs
│   │   └── policies/         # API policies
│   │       └── rate-limit.xml
│   └── monitoring/
│       ├── main.tf           # Simplified monitoring setup
│       ├── variables.tf      # Module input variables
│       └── outputs.tf        # Module outputs
├── environments/
│   └── dev/
│       └── terraform.tfvars  # Dev config: West US, Java 17, IP address
└── TROUBLESHOOTING.md         # Common issues and solutions
```

### Specific Requirements for Each Module:

**Function App Module Requirements:**
- Two function apps: one for Basic Auth, one for OAuth2
- **Configure for Java 17 runtime** (not Java 21)
- Include validation to prevent unsupported Java versions
- **Support both Y1 (Consumption) and B1 (Basic) plans**
- Include all necessary app settings for Spring Boot
- **Always On only for non-consumption plans**
- Configure connection strings for Key Vault

**Key Vault Module Requirements:**
- **Ensure names stay under 24 characters**
- Generate short, unique names with random suffix
- Pre-populate with placeholder secrets structure
- **Configure IP-based network access rules**
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
- **Simplified alert approach** using Log Analytics
- **Saved searches instead of real-time alerts**
- Basic monitoring queries:
  - Function execution failures
  - Response time analysis
  - Error rate tracking
- Log retention settings

### Additional Outputs:
1. **Enhanced README.md** including:
   - Quota troubleshooting section
   - Regional deployment guidance
   - Java version limitations
   - IP address configuration steps
   - Cost comparison (Consumption vs Basic plans)

2. **TROUBLESHOOTING.md** covering:
   - "Dynamic VMs quota exceeded" error resolution
   - Key Vault naming length issues
   - Java version compatibility problems
   - Network access denied errors
   - Regional quota limitations

3. Deployment script (`deploy.sh`) with:
   - IP address detection and guidance
   - Quota error handling
   - Region fallback suggestions
   - Validation checks before deployment

4. **terraform.tfvars.example** with realistic defaults:
   - `location = "westus"` (not "East US")
   - `java_version = "17"` (not "21")
   - `allowed_ips = ["YOUR.IP.ADDRESS.HERE/32"]`
   - Cost-optimized settings for development

Remember to:
- **Include extensive validation** for all critical parameters
- **Add detailed comments** explaining limitations and workarounds
- **Provide multiple plan options** for quota flexibility
- **Use proven regional defaults** (West US over East US)
- **Handle common deployment failures** gracefully
- Make the code idempotent and re-runnable with proper error handling

Add summary of implemented changes in this phase to phase1-infrastructure-setup-summary.md. 