output "api_management_name" {
  description = "Name of the API Management instance"
  value       = azurerm_api_management.main.name
}

output "api_management_id" {
  description = "ID of the API Management instance"
  value       = azurerm_api_management.main.id
}

output "api_management_gateway_url" {
  description = "Gateway URL of the API Management instance"
  value       = azurerm_api_management.main.gateway_url
}

output "api_management_developer_portal_url" {
  description = "Developer portal URL of the API Management instance"
  value       = azurerm_api_management.main.developer_portal_url
}

output "api_management_public_ip_addresses" {
  description = "Public IP addresses of the API Management instance"
  value       = azurerm_api_management.main.public_ip_addresses
}

output "basic_auth_api_url" {
  description = "URL for the Basic Auth API"
  value       = "${azurerm_api_management.main.gateway_url}/auth"
}

output "oauth_api_url" {
  description = "URL for the OAuth API"
  value       = "${azurerm_api_management.main.gateway_url}/oauth"
} 