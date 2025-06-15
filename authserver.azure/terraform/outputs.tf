# Resource Group
output "resource_group_name" {
  description = "Name of the resource group"
  value       = azurerm_resource_group.main.name
}

output "resource_group_location" {
  description = "Location of the resource group"
  value       = azurerm_resource_group.main.location
}

# Function Apps
output "basic_auth_function_name" {
  description = "Name of the Basic Auth Function App"
  value       = module.function_app.basic_auth_function_name
}

output "oauth_function_name" {
  description = "Name of the OAuth Function App"
  value       = module.function_app.oauth_function_name
}

output "basic_auth_function_url" {
  description = "URL of the Basic Auth Function App"
  value       = module.function_app.basic_auth_function_url
}

output "oauth_function_url" {
  description = "URL of the OAuth Function App"
  value       = module.function_app.oauth_function_url
}

# Key Vault
output "key_vault_name" {
  description = "Name of the Key Vault"
  value       = module.key_vault.key_vault_name
}

output "key_vault_uri" {
  description = "URI of the Key Vault"
  value       = module.key_vault.key_vault_uri
}

# API Management
output "api_management_name" {
  description = "Name of the API Management instance"
  value       = module.api_management.api_management_name
}

output "api_management_gateway_url" {
  description = "Gateway URL of the API Management instance"
  value       = module.api_management.api_management_gateway_url
}

output "api_management_developer_portal_url" {
  description = "Developer portal URL of the API Management instance"
  value       = module.api_management.api_management_developer_portal_url
}

# Monitoring
output "application_insights_name" {
  description = "Name of the Application Insights instance"
  value       = module.monitoring.application_insights_name
}

output "application_insights_app_id" {
  description = "Application ID of Application Insights"
  value       = module.monitoring.application_insights_app_id
}

output "log_analytics_workspace_name" {
  description = "Name of the Log Analytics workspace"
  value       = module.monitoring.log_analytics_workspace_name
}

# Storage
output "storage_account_name" {
  description = "Name of the storage account for Function Apps"
  value       = azurerm_storage_account.function_storage.name
}

# Deployment Information
output "deployment_info" {
  description = "Key deployment information for CI/CD"
  value = {
    resource_group_name = azurerm_resource_group.main.name
    location            = azurerm_resource_group.main.location
    environment         = var.environment

    function_apps = {
      basic_auth = {
        name = module.function_app.basic_auth_function_name
        url  = module.function_app.basic_auth_function_url
      }
      oauth = {
        name = module.function_app.oauth_function_name
        url  = module.function_app.oauth_function_url
      }
    }

    api_management = {
      name        = module.api_management.api_management_name
      gateway_url = module.api_management.api_management_gateway_url
    }

    key_vault = {
      name = module.key_vault.key_vault_name
      uri  = module.key_vault.key_vault_uri
    }

    monitoring = {
      application_insights_name = module.monitoring.application_insights_name
      app_id                    = module.monitoring.application_insights_app_id
    }
  }
  sensitive = false
} 