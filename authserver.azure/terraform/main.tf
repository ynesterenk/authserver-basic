# Configure the Azure Provider
provider "azurerm" {
  features {
    key_vault {
      purge_soft_delete_on_destroy    = true
      recover_soft_deleted_key_vaults = true
    }
    resource_group {
      prevent_deletion_if_contains_resources = false
    }
  }
}

# Generate random suffix for unique naming
resource "random_string" "suffix" {
  length  = 3
  special = false
  upper   = false
}

# Local values for consistent naming
locals {
  resource_suffix = "${var.environment}-${var.location_short}-${random_string.suffix.result}"
  common_tags = merge(var.common_tags, {
    Environment = title(var.environment)
    Location    = var.location
    CreatedBy   = "Terraform"
    CreatedDate = timestamp()
  })
}

# Resource Group
resource "azurerm_resource_group" "main" {
  name     = "rg-${var.project_name}-${local.resource_suffix}"
  location = var.location
  tags     = local.common_tags
}

# Storage Account for Function Apps runtime
resource "azurerm_storage_account" "function_storage" {
  name                     = "st${var.project_name}${replace(local.resource_suffix, "-", "")}"
  resource_group_name      = azurerm_resource_group.main.name
  location                 = azurerm_resource_group.main.location
  account_tier             = "Standard"
  account_replication_type = "LRS"

  # Security settings
  allow_nested_items_to_be_public = false
  shared_access_key_enabled       = true

  tags = merge(local.common_tags, {
    Purpose = "Function App Runtime Storage"
  })
}

# Monitoring Module
module "monitoring" {
  source = "./modules/monitoring"

  project_name        = var.project_name
  environment         = var.environment
  location            = azurerm_resource_group.main.location
  resource_group_name = azurerm_resource_group.main.name
  resource_suffix     = local.resource_suffix
  log_retention_days  = var.log_retention_days
  common_tags         = local.common_tags
}

# Key Vault Module
module "key_vault" {
  source = "./modules/key-vault"

  project_name        = var.project_name
  environment         = var.environment
  location            = azurerm_resource_group.main.location
  resource_group_name = azurerm_resource_group.main.name
  resource_suffix     = local.resource_suffix
  key_vault_sku       = var.key_vault_sku
  allowed_ips         = var.allowed_ips
  common_tags         = local.common_tags

  # Dependencies
  log_analytics_workspace_id = module.monitoring.log_analytics_workspace_id
}

# Function App Module
module "function_app" {
  source = "./modules/function-app"

  project_name        = var.project_name
  environment         = var.environment
  location            = azurerm_resource_group.main.location
  resource_group_name = azurerm_resource_group.main.name
  resource_suffix     = local.resource_suffix
  function_app_sku    = var.function_app_sku
  java_version        = var.java_version
  common_tags         = local.common_tags

  # Dependencies
  storage_account_name                     = azurerm_storage_account.function_storage.name
  storage_account_access_key               = azurerm_storage_account.function_storage.primary_access_key
  storage_account_connection_string        = azurerm_storage_account.function_storage.primary_connection_string
  key_vault_uri                            = module.key_vault.key_vault_uri
  application_insights_instrumentation_key = module.monitoring.application_insights_instrumentation_key
  application_insights_connection_string   = module.monitoring.application_insights_connection_string
}

# API Management Module
module "api_management" {
  source = "./modules/api-management"

  project_name        = var.project_name
  environment         = var.environment
  location            = azurerm_resource_group.main.location
  resource_group_name = azurerm_resource_group.main.name
  resource_suffix     = local.resource_suffix
  apim_sku            = var.apim_sku
  apim_capacity       = var.apim_capacity
  common_tags         = local.common_tags

  # Dependencies
  function_app_basic_auth_url = module.function_app.basic_auth_function_url
  function_app_oauth_url      = module.function_app.oauth_function_url
  log_analytics_workspace_id  = module.monitoring.log_analytics_workspace_id
}

# Grant Key Vault access to Function App managed identities
resource "azurerm_key_vault_access_policy" "basic_auth_function" {
  key_vault_id = module.key_vault.key_vault_id
  tenant_id    = module.function_app.basic_auth_function_identity_tenant_id
  object_id    = module.function_app.basic_auth_function_identity_principal_id

  secret_permissions = [
    "Get",
    "List"
  ]
}

resource "azurerm_key_vault_access_policy" "oauth_function" {
  key_vault_id = module.key_vault.key_vault_id
  tenant_id    = module.function_app.oauth_function_identity_tenant_id
  object_id    = module.function_app.oauth_function_identity_principal_id

  secret_permissions = [
    "Get",
    "List"
  ]
} 