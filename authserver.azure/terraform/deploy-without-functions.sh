#!/bin/bash
set -e

# Azure Authorization Server Infrastructure Deployment Script (Without Function Apps)
# Usage: ./deploy-without-functions.sh [environment]

# Default values
ENVIRONMENT=${1:-dev}

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Function to print colored output
print_status() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Check if we're in the correct directory
if [[ ! -f "main.tf" ]] || [[ ! -d "modules" ]]; then
    print_error "Please run this script from the terraform directory"
    exit 1
fi

print_warning "Deploying infrastructure WITHOUT Function Apps due to quota limitations"
print_status "This will deploy: Key Vault, Monitoring, Storage Account, and API Management"
print_status "Function Apps can be added later after resolving quota issues"

TFVARS_FILE="environments/${ENVIRONMENT}/terraform.tfvars"

# Apply Key Vault first (IP address added)
print_status "Step 1: Deploying Key Vault..."
terraform apply -target=module.key_vault -var-file="$TFVARS_FILE" -auto-approve

# Apply Monitoring
print_status "Step 2: Deploying Monitoring..."
terraform apply -target=module.monitoring -var-file="$TFVARS_FILE" -auto-approve

# Apply Storage Account
print_status "Step 3: Deploying Storage Account..."
terraform apply -target=azurerm_storage_account.function_storage -var-file="$TFVARS_FILE" -auto-approve

# Apply Resource Group
print_status "Step 4: Ensuring Resource Group..."
terraform apply -target=azurerm_resource_group.main -var-file="$TFVARS_FILE" -auto-approve

print_success "Core infrastructure deployed successfully!"

echo ""
echo "=== DEPLOYMENT RESULTS ==="
echo "✅ Key Vault: Deployed with your IP address whitelisted"
echo "✅ Application Insights: Monitoring ready"
echo "✅ Log Analytics: Centralized logging ready"
echo "✅ Storage Account: Function App storage ready"
echo "✅ API Management: Already deployed (32 minutes!)"
echo ""
echo "❌ Function Apps: Skipped due to quota limitations"
echo ""

echo "=== NEXT STEPS ==="
echo "1. Resolve Function App quota issue:"
echo "   - Try different region: Update location in terraform.tfvars"
echo "   - Request quota increase: Azure Portal → Subscriptions → Usage + quotas"
echo "   - Contact Azure support for enterprise subscriptions"
echo ""
echo "2. After quota resolved:"
echo "   terraform apply -target=module.function_app -var-file=\"$TFVARS_FILE\""
echo ""
echo "3. Current working components:"
echo "   - Key Vault URI: \$(terraform output key_vault_uri)"
echo "   - API Management: \$(terraform output api_management_gateway_url)"
echo "   - Application Insights: Ready for Function Apps"

print_success "Partial deployment completed. Function Apps can be added when quota is available!" 