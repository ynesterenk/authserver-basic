variable "project_name" {
  description = "Project name for resource naming"
  type        = string
}

variable "environment" {
  description = "Environment name"
  type        = string
}

variable "location" {
  description = "Azure region"
  type        = string
}

variable "resource_group_name" {
  description = "Resource group name"
  type        = string
}

variable "resource_suffix" {
  description = "Resource suffix for unique naming"
  type        = string
}

variable "apim_sku" {
  description = "SKU for API Management"
  type        = string
  default     = "Developer"
}

variable "apim_capacity" {
  description = "Capacity for API Management"
  type        = number
  default     = 1
}

variable "function_app_basic_auth_url" {
  description = "URL of the Basic Auth Function App"
  type        = string
}

variable "function_app_oauth_url" {
  description = "URL of the OAuth Function App"
  type        = string
}

variable "log_analytics_workspace_id" {
  description = "Log Analytics workspace ID for diagnostics"
  type        = string
}

variable "common_tags" {
  description = "Common tags for resources"
  type        = map(string)
  default     = {}
} 