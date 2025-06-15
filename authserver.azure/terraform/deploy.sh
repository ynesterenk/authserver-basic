#!/bin/bash
set -e

# Azure Authorization Server Infrastructure Deployment Script
# Usage: ./deploy.sh [environment]

# Default values
ENVIRONMENT=${1:-dev}
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

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

# Validate inputs
if [[ ! "$ENVIRONMENT" =~ ^(dev|staging|prod)$ ]]; then
    print_error "Invalid environment: $ENVIRONMENT. Must be dev, staging, or prod."
    exit 1
fi

# Check if we're in the correct directory
if [[ ! -f "main.tf" ]] || [[ ! -d "modules" ]]; then
    print_error "Please run this script from the terraform directory"
    exit 1
fi

print_status "Starting Azure infrastructure deployment for environment: $ENVIRONMENT"

# Step 1: Check prerequisites
print_status "Checking prerequisites..."

# Check if Azure CLI is installed and configured
if ! command -v az &> /dev/null; then
    print_error "Azure CLI is not installed. Please install it from https://docs.microsoft.com/en-us/cli/azure/install-azure-cli"
    exit 1
fi

# Check if Terraform is installed
if ! command -v terraform &> /dev/null; then
    print_error "Terraform is not installed. Please install it from https://www.terraform.io/downloads.html"
    exit 1
fi

# Check Terraform version
TERRAFORM_VERSION=$(terraform version -json | jq -r '.terraform_version')
REQUIRED_VERSION="1.5.0"
if [ "$(printf '%s\n' "$REQUIRED_VERSION" "$TERRAFORM_VERSION" | sort -V | head -n1)" != "$REQUIRED_VERSION" ]; then
    print_error "Terraform version $TERRAFORM_VERSION is too old. Please upgrade to $REQUIRED_VERSION or later."
    exit 1
fi

# Check if Azure CLI is configured
if ! az account show > /dev/null 2>&1; then
    print_error "Azure CLI is not configured. Please run 'az login' first."
    exit 1
fi

print_success "Prerequisites check passed"

# Step 2: Validate environment-specific variables file
TFVARS_FILE="environments/${ENVIRONMENT}/terraform.tfvars"
if [[ ! -f "$TFVARS_FILE" ]]; then
    print_error "Environment variables file not found: $TFVARS_FILE"
    print_status "Creating from template..."
    mkdir -p "environments/${ENVIRONMENT}"
    cp "terraform.tfvars.example" "$TFVARS_FILE"
    print_warning "Please edit $TFVARS_FILE with your specific values before continuing"
    exit 1
fi

print_success "Environment configuration found: $TFVARS_FILE"

# Step 3: Initialize Terraform backend
print_status "Initializing Terraform..."

# Check if backend is configured
if ! terraform init -backend=false > /dev/null 2>&1; then
    print_error "Terraform initialization failed"
    exit 1
fi

# For first-time setup, help user configure backend
if [[ ! -f ".terraform/terraform.tfstate" ]]; then
    print_warning "Backend not initialized. Setting up Azure Storage backend..."
    
    SUBSCRIPTION_ID=$(az account show --query id -o tsv)
    RG_NAME="rg-authserver-terraform-${ENVIRONMENT}"
    STORAGE_NAME="stateauthserver${ENVIRONMENT}$(openssl rand -hex 3)"
    
    print_status "Creating backend infrastructure..."
    
    # Create resource group for Terraform state
    az group create --name "$RG_NAME" --location "East US" --output none
    
    # Create storage account for Terraform state
    az storage account create \
        --resource-group "$RG_NAME" \
        --name "$STORAGE_NAME" \
        --sku Standard_LRS \
        --encryption-services blob \
        --output none
    
    # Create container for state
    az storage container create \
        --name terraform-state \
        --account-name "$STORAGE_NAME" \
        --output none
    
    print_success "Backend infrastructure created"
    print_status "Initializing Terraform with backend..."
    
    # Initialize with backend
    terraform init \
        -backend-config="resource_group_name=$RG_NAME" \
        -backend-config="storage_account_name=$STORAGE_NAME" \
        -backend-config="container_name=terraform-state" \
        -backend-config="key=authserver.tfstate"
else
    # Backend already configured, just init
    terraform init
fi

print_success "Terraform initialized"

# Step 4: Validate configuration
print_status "Validating Terraform configuration..."
terraform validate

if [ $? -ne 0 ]; then
    print_error "Terraform validation failed"
    exit 1
fi

print_success "Configuration validation passed"

# Step 5: Format code
print_status "Formatting Terraform code..."
terraform fmt -recursive

# Step 6: Plan deployment
print_status "Creating deployment plan..."
terraform plan -var-file="$TFVARS_FILE" -out="tfplan-${ENVIRONMENT}"

if [ $? -ne 0 ]; then
    print_error "Terraform planning failed"
    exit 1
fi

print_success "Deployment plan created"

# Step 7: Apply deployment (with confirmation for non-dev environments)
if [[ "$ENVIRONMENT" == "dev" ]]; then
    print_status "Applying deployment for development environment..."
    terraform apply "tfplan-${ENVIRONMENT}"
else
    print_warning "About to deploy to $ENVIRONMENT environment"
    read -p "Are you sure you want to continue? (y/N): " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        print_status "Applying deployment..."
        terraform apply "tfplan-${ENVIRONMENT}"
    else
        print_status "Deployment cancelled"
        exit 0
    fi
fi

if [ $? -ne 0 ]; then
    print_error "Terraform apply failed"
    exit 1
fi

print_success "Deployment completed successfully!"

# Step 8: Display outputs
print_status "Retrieving deployment outputs..."
OUTPUTS=$(terraform output -json)

if [[ "$OUTPUTS" != "{}" ]]; then
    echo ""
    echo "=== DEPLOYMENT RESULTS ==="
    echo "Environment: $ENVIRONMENT"
    echo ""
    
    # Extract key information
    RESOURCE_GROUP=$(echo "$OUTPUTS" | jq -r '.resource_group_name.value')
    BASIC_AUTH_URL=$(echo "$OUTPUTS" | jq -r '.basic_auth_function_url.value')
    OAUTH_URL=$(echo "$OUTPUTS" | jq -r '.oauth_function_url.value')
    API_GATEWAY_URL=$(echo "$OUTPUTS" | jq -r '.api_management_gateway_url.value')
    KEY_VAULT_URI=$(echo "$OUTPUTS" | jq -r '.key_vault_uri.value')
    
    echo "Resource Group: $RESOURCE_GROUP"
    echo "Basic Auth Function: $BASIC_AUTH_URL"
    echo "OAuth Function: $OAUTH_URL"
    echo "API Gateway: $API_GATEWAY_URL"
    echo "Key Vault: $KEY_VAULT_URI"
    echo ""
    
    # Save deployment info
    DEPLOYMENT_INFO=$(cat <<EOF
{
  "environment": "$ENVIRONMENT",
  "timestamp": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
  "resource_group": "$RESOURCE_GROUP",
  "basic_auth_url": "$BASIC_AUTH_URL",
  "oauth_url": "$OAUTH_URL",
  "api_gateway_url": "$API_GATEWAY_URL",
  "key_vault_uri": "$KEY_VAULT_URI"
}
EOF
)
    
    echo "$DEPLOYMENT_INFO" > "deployment-info-${ENVIRONMENT}.json"
    print_success "Deployment info saved to: deployment-info-${ENVIRONMENT}.json"
    
    echo ""
    echo "=== NEXT STEPS ==="
    echo "1. Deploy Function App code (Phase 2)"
    echo "2. Configure secrets in Key Vault"
    echo "3. Test endpoints:"
    echo "   - Basic Auth: POST $API_GATEWAY_URL/auth/api/auth/validate"
    echo "   - OAuth Token: POST $API_GATEWAY_URL/oauth/api/oauth/token"
    echo "   - Token Introspect: POST $API_GATEWAY_URL/oauth/api/oauth/introspect"
    echo ""
else
    print_warning "Could not retrieve deployment outputs"
fi

# Cleanup plan file
rm -f "tfplan-${ENVIRONMENT}"

print_success "Infrastructure deployment completed successfully!"
print_status "Check the Azure Portal to verify all resources are created correctly" 