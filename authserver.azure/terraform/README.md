# Azure Authorization Server Infrastructure

This Terraform configuration deploys the complete Azure infrastructure for the Java Authorization Server migration from AWS.

## Overview

The infrastructure includes:
- **Azure Functions**: Two Function Apps for Basic Auth and OAuth 2.0 endpoints
- **Key Vault**: Secure storage for credentials and secrets
- **API Management**: API gateway with rate limiting and documentation
- **Application Insights**: Monitoring and logging
- **Log Analytics**: Centralized log storage and analysis

## Important Notes

### Java Version Support
- **Azure Functions currently supports**: Java 8, 11, and 17
- **Java 21 is not yet supported** by Azure Functions
- This deployment uses **Java 17** (latest supported version)
- When Java 21 support is added, update `java_version = "21"` in your terraform.tfvars

### Function App Pricing Tiers
- **Consumption Plan (Y1)**: Pay-per-execution, cheapest for low usage
- **Basic Plan (B1)**: Always-on instances, ~$13-15/month per Function App
- **Issue**: Y1 (Consumption) may not be available in all regions/subscriptions
- **Default**: This configuration uses B1 for reliability
- **To use Consumption**: Change `function_app_sku = "Y1"` if available

**Cost Impact**: B1 plan costs ~$25-30/month vs Y1 consumption which could be $0-5/month for development usage.

## Prerequisites

### Required Software
- [Terraform](https://www.terraform.io/downloads.html) >= 1.5
- [Azure CLI](https://docs.microsoft.com/en-us/cli/azure/install-azure-cli) >= 2.30

### Azure Requirements
- Azure subscription with Contributor access
- Service Principal with appropriate permissions (see below)

### Required Azure Permissions
The deploying service principal needs the following permissions:
- `Contributor` role on the subscription or resource group
- `Key Vault Contributor` role for Key Vault operations
- `Application Insights Component Contributor` for monitoring setup

## Quick Start

### 1. Azure CLI Login
```bash
az login
az account set --subscription "your-subscription-id"
```

### 2. Set Up Terraform Backend (First Time Only)
```bash
# Create resource group for Terraform state
az group create --name rg-authserver-terraform-dev --location "westus"

# Create storage account for Terraform state
az storage account create \
  --resource-group rg-authserver-terraform-dev \
  --name stateauthserverdev \
  --sku Standard_LRS \
  --encryption-services blob

# Create container for state
az storage container create \
  --name terraform-state \
  --account-name stateauthserverdev
```

### 3. Initialize Terraform
```bash
cd authserver.azure/terraform

# Initialize with backend configuration
terraform init \
  -backend-config="resource_group_name=rg-authserver-terraform-dev" \
  -backend-config="storage_account_name=stateauthserverdev" \
  -backend-config="container_name=terraform-state" \
  -backend-config="key=authserver.tfstate"
```

### 4. Configure Variables
```bash
# Copy example variables
cp terraform.tfvars.example terraform.tfvars

# Edit variables for your environment
nano terraform.tfvars
```

### 5. Deploy Infrastructure
```bash
# Plan deployment
terraform plan -var-file="environments/dev/terraform.tfvars"

# Apply deployment
terraform apply -var-file="environments/dev/terraform.tfvars"
```

## Configuration

### Environment Variables
Key variables in `terraform.tfvars`:

```hcl
# Basic Configuration
environment    = "dev"
project_name   = "authserver"
location       = "East US"

# Java Version (Azure Functions supported versions only)
java_version   = "17"  # Options: "8", "11", "17"

# Function App Pricing
function_app_sku = "B1"   # Basic plan (~$25/month for both apps)
# function_app_sku = "Y1" # Consumption plan (try if available)

# Security
allowed_ips = ["your.ip.address.here/32"]

# Sizing
apim_sku        = "Developer"  # Free tier
```

### Backend Configuration
Store state in Azure Storage for team collaboration:

```hcl
# backend.tf
terraform {
  backend "azurerm" {
    resource_group_name  = "rg-authserver-terraform-dev"
    storage_account_name = "stateauthserverdev"
    container_name       = "terraform-state"
    key                  = "authserver.tfstate"
  }
}
```

## Module Structure

```
terraform/
├── main.tf                 # Root module
├── variables.tf            # Input variables
├── outputs.tf              # Output values
├── modules/
│   ├── function-app/       # Azure Functions module
│   ├── key-vault/          # Key Vault module
│   ├── api-management/     # API Management module
│   └── monitoring/         # Application Insights module
└── environments/
    └── dev/               # Environment-specific configs
```

## Outputs

After successful deployment, Terraform outputs:

- **Function App URLs**: Direct URLs to the Function Apps
- **API Management Gateway**: Public API endpoint
- **Key Vault URI**: For application configuration
- **Application Insights**: For monitoring setup

## Deployment Scripts

### Automated Deployment
Use the provided deployment script:

```bash
./deploy.sh dev
```

### Manual Commands
```bash
# Format code
terraform fmt -recursive

# Validate configuration
terraform validate

# Plan deployment
terraform plan -var-file="environments/dev/terraform.tfvars"

# Apply changes
terraform apply -var-file="environments/dev/terraform.tfvars" -auto-approve

# Show outputs
terraform output
```

## Security Considerations

### Key Vault Access
- Function Apps use Managed Identity for Key Vault access
- Network access restricted by IP whitelist (if configured)
- Soft delete enabled for secret recovery

### Function App Security
- HTTPS-only endpoints
- Managed Identity for Azure service authentication
- Application Insights for security monitoring

### API Management
- Rate limiting: 100 requests/minute
- CORS configured for cross-origin requests
- Built-in DDoS protection

## Monitoring

### Application Insights
- Automatic performance monitoring
- Custom metrics for auth success/failure
- Alert rules for critical issues

### Log Analytics
- Centralized logging from all components
- Custom queries for security analysis
- 30-day retention (configurable)

### Alerts
Pre-configured alerts for:
- Authentication failures (>5 in 5 minutes)
- Performance degradation (>1000ms requests)
- Custom auth metrics

## Troubleshooting

### Common Issues

**Consumption Plan Not Available**
```
Error: 'Y1' is not a valid value for '--sku'
```
**Solution**: Use Basic plan instead. Update `function_app_sku = "B1"` in terraform.tfvars. This adds ~$25/month cost but provides always-on reliability.

**Java Version Error**
```
Error: expected site_config.0.application_stack.0.java_version to be one of ["8" "11" "17"], got 21
```
**Solution**: Update `java_version = "17"` in your terraform.tfvars file. Azure Functions doesn't support Java 21 yet.

**Key Vault Access Denied**
```
Error: Client address is not authorized and caller is not a trusted service
```
**Solution**: Add your IP address to `allowed_ips` in terraform.tfvars:
```hcl
allowed_ips = ["your.public.ip.here/32"]
```

**Terraform Init Fails**
```bash
# Check Azure CLI authentication
az account show

# Verify backend storage account exists
az storage account show --name stateauthserverdev --resource-group rg-authserver-terraform-dev
```

**Function App Deployment Issues**
```bash
# Check Function App logs
az functionapp log tail --name <function-app-name> --resource-group <rg-name>

# Verify managed identity permissions
az role assignment list --assignee <function-app-principal-id>
```

### Getting Help

1. **Check Terraform logs**: Enable detailed logging with `TF_LOG=DEBUG`
2. **Azure CLI debugging**: Use `az --debug` for verbose output
3. **Resource logs**: Check Azure Portal for resource-specific logs

## Cost Optimization

### Development Environment
- Function Apps: Basic plan (~$25-30/month total)
- API Management: Developer tier (free)
- Key Vault: Standard tier (~$2-5/month)
- Storage: LRS replication (~$1-2/month)

### Estimated Monthly Cost
- **With Basic Plan (B1)**: ~$30-40/month
- **With Consumption Plan (Y1)**: ~$5-15/month (if available)
- API Management: $0 (Developer tier)
- Other services: ~$5-10/month
- **Total**: ~$35-50/month (Basic) or ~$10-25/month (Consumption)

### Cost Reduction Options
1. **Try Consumption Plan**: Change to `function_app_sku = "Y1"` if available
2. **Use Free Tier**: Some services have free tiers with usage limits
3. **Scale Down**: Use smaller Basic plans (B1 → smaller if needed)

## Next Steps

1. **Deploy Application Code**: Use Phase 2 to deploy Function App code
2. **Configure Secrets**: Update Key Vault with production secrets
3. **Set Up CI/CD**: Use Phase 4 deployment automation
4. **Monitor Performance**: Review Application Insights dashboards
5. **Scale Up**: Move to production-ready SKUs when needed

## Support

For issues with this infrastructure:
1. Check the troubleshooting section above
2. Review Terraform state: `terraform show`
3. Check Azure resource status in the portal
4. Consult the Azure documentation for specific services 