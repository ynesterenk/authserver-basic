# Azure Infrastructure Troubleshooting Guide

## Common Deployment Issues

### 1. 🚨 Function App Quota Issue (Critical)

**Error Message:**
```
Operation cannot be completed without additional quota.
Current Limit (Dynamic VMs): 0
```

**Root Causes:**
1. **Free Tier Limitations**: Azure Free subscriptions have limited quotas
2. **Corporate Policy**: Company subscriptions may have restrictive policies
3. **Regional Quotas**: Some regions have quota limitations
4. **Account Verification**: Unverified accounts have reduced quotas

**Solutions:**

#### Option A: Request Quota Increase
```bash
# Check current quotas
az vm list-usage --location "East US" -o table

# Request quota increase via Azure Portal:
# 1. Go to Azure Portal → Subscriptions → Your Subscription
# 2. Click "Usage + quotas" in left menu
# 3. Search for "App Service Plan"
# 4. Click "Request increase"
# 5. Request at least 1 Dynamic VM quota
```

#### Option B: Change Region
Try a different Azure region that might have available quota:
```bash
# Check available locations
az account list-locations -o table

# Common regions with good availability:
# - West Europe
# - Central US
# - West US 2
```

Update your `terraform.tfvars`:
```hcl
location = "West Europe"
location_short = "weu"
```

#### Option C: Use Different Subscription
If you have access to another Azure subscription:
```bash
# List available subscriptions
az account list -o table

# Switch to different subscription
az account set --subscription "your-other-subscription-id"
```

#### Option D: Contact Azure Support
For enterprise/corporate subscriptions, contact your Azure administrator or Azure Support.

### 2. 🔤 Key Vault Naming Issue

**Error Message:**
```
"name" may only contain alphanumeric characters and dashes and must be between 3-24 chars
```

**Solution:** ✅ **FIXED** - Updated Key Vault module to use shorter names

### 3. 📊 Monitor Alert Configuration Issue

**Error Message:**
```
Detect invalid value: Microsoft.Web/sites for query parameter: 'metricnamespace'
```

**Solution:** ✅ **FIXED** - Updated monitoring module to use Log Analytics queries instead

## Deployment Recovery Steps

### If Function App Deployment Fails:

1. **Clean up failed resources:**
```bash
cd authserver.azure/terraform

# Destroy failed deployment
terraform destroy -var-file="environments/dev/terraform.tfvars" -auto-approve

# Or target specific failed resources
terraform destroy -target=module.function_app -var-file="environments/dev/terraform.tfvars"
```

2. **Resolve quota issue** (see solutions above)

3. **Redeploy:**
```bash
./deploy.sh dev
```

### If Partial Deployment Succeeds:

You can retry deployment - Terraform will only create missing resources:
```bash
terraform plan -var-file="environments/dev/terraform.tfvars"
terraform apply -var-file="environments/dev/terraform.tfvars"
```

## Alternative Deployment Strategy

If quota issues persist, you can deploy components individually:

### 1. Deploy Core Infrastructure First:
```bash
# Deploy only monitoring and storage
terraform apply -target=module.monitoring -var-file="environments/dev/terraform.tfvars"
terraform apply -target=azurerm_resource_group.main -var-file="environments/dev/terraform.tfvars"
terraform apply -target=azurerm_storage_account.function_storage -var-file="environments/dev/terraform.tfvars"
```

### 2. Deploy Key Vault:
```bash
terraform apply -target=module.key_vault -var-file="environments/dev/terraform.tfvars"
```

### 3. Deploy Function Apps (after quota resolved):
```bash
terraform apply -target=module.function_app -var-file="environments/dev/terraform.tfvars"
```

### 4. Deploy API Management:
```bash
terraform apply -target=module.api_management -var-file="environments/dev/terraform.tfvars"
```

## Verification Commands

After successful deployment:

```bash
# Check all resources
az resource list --resource-group "your-rg-name" -o table

# Check Function Apps
az functionapp list --resource-group "your-rg-name" -o table

# Check Key Vault
az keyvault list --resource-group "your-rg-name" -o table

# Check API Management
az apim list --resource-group "your-rg-name" -o table
```

## Getting Help

1. **Azure CLI Debug Mode:**
```bash
az --debug [command]
```

2. **Terraform Debug Mode:**
```bash
export TF_LOG=DEBUG
terraform apply
```

3. **Check Azure Status:**
   - Visit: https://status.azure.com/
   - Check for regional outages

4. **Azure Support:**
   - Create support ticket in Azure Portal
   - Include subscription ID and error messages

## Next Steps After Resolution

Once deployment succeeds:
1. Verify all resources in Azure Portal
2. Test Key Vault access
3. Proceed to Phase 2 (Code Deployment)
4. Update secrets with production values 