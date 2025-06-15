# Development Environment Configuration
environment    = "dev"
project_name   = "authserver"
location       = "westus"
location_short = "wus"

# Network Security - Add your IP addresses for Key Vault access
allowed_ips = [
  "193.194.106.45/32", # Your current IP address
  # "203.0.113.1/32",  # Example: Your office IP
  # "198.51.100.0/24", # Example: VPN range
]

# Function App Configuration
function_app_sku = "Y1" # Consumption plan - most cost-effective
java_version     = "17" # Latest supported version for Azure Functions

# Key Vault Configuration
key_vault_sku = "standard"

# API Management Configuration
apim_sku      = "Developer" # Free tier with developer features
apim_capacity = 1

# Monitoring Configuration
log_retention_days = 30 # 30 days for dev environment

# Common Tags
common_tags = {
  Project     = "Java Authorization Server"
  Environment = "Development"
  ManagedBy   = "Terraform"
  Owner       = "Development Team"
  CostCenter  = "Engineering"
  Application = "AuthServer"
  Repository  = "authserver-azure-migration"
} 