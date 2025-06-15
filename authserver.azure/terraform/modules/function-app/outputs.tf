# Basic Auth Function outputs
output "basic_auth_function_name" {
  description = "Name of the Basic Auth Function App"
  value       = azurerm_linux_function_app.basic_auth.name
}

output "basic_auth_function_url" {
  description = "URL of the Basic Auth Function App"
  value       = "https://${azurerm_linux_function_app.basic_auth.name}.azurewebsites.net"
}

output "basic_auth_function_identity_principal_id" {
  description = "Principal ID of the Basic Auth Function App managed identity"
  value       = azurerm_linux_function_app.basic_auth.identity[0].principal_id
}

output "basic_auth_function_identity_tenant_id" {
  description = "Tenant ID of the Basic Auth Function App managed identity"
  value       = azurerm_linux_function_app.basic_auth.identity[0].tenant_id
}

# OAuth Function outputs
output "oauth_function_name" {
  description = "Name of the OAuth Function App"
  value       = azurerm_linux_function_app.oauth.name
}

output "oauth_function_url" {
  description = "URL of the OAuth Function App"
  value       = "https://${azurerm_linux_function_app.oauth.name}.azurewebsites.net"
}

output "oauth_function_identity_principal_id" {
  description = "Principal ID of the OAuth Function App managed identity"
  value       = azurerm_linux_function_app.oauth.identity[0].principal_id
}

output "oauth_function_identity_tenant_id" {
  description = "Tenant ID of the OAuth Function App managed identity"
  value       = azurerm_linux_function_app.oauth.identity[0].tenant_id
}

# Service Plan outputs
output "service_plan_name" {
  description = "Name of the Function App service plan"
  value       = azurerm_service_plan.function_apps.name
}

output "service_plan_id" {
  description = "ID of the Function App service plan"
  value       = azurerm_service_plan.function_apps.id
} 