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

variable "function_app_sku" {
  description = "SKU for Function App service plan"
  type        = string
  default     = "Y1"
  validation {
    condition     = contains(["Y1", "EP1", "EP2", "EP3", "B1", "B2", "B3", "S1", "S2", "S3"], var.function_app_sku)
    error_message = "Function App SKU must be Y1 (Consumption), EP1-EP3 (Premium), B1-B3 (Basic), or S1-S3 (Standard)."
  }
}

variable "java_version" {
  description = "Java version for Function Apps (Azure Functions supports 8, 11, 17)"
  type        = string
  default     = "17"
  validation {
    condition     = contains(["8", "11", "17"], var.java_version)
    error_message = "Java version must be 8, 11, or 17 (Azure Functions supported versions)."
  }
}

variable "storage_account_name" {
  description = "Storage account name for Function Apps"
  type        = string
}

variable "storage_account_access_key" {
  description = "Storage account access key"
  type        = string
  sensitive   = true
}

variable "storage_account_connection_string" {
  description = "Storage account connection string"
  type        = string
  sensitive   = true
}

variable "key_vault_uri" {
  description = "Key Vault URI"
  type        = string
}

variable "application_insights_instrumentation_key" {
  description = "Application Insights instrumentation key"
  type        = string
  sensitive   = true
}

variable "application_insights_connection_string" {
  description = "Application Insights connection string"
  type        = string
  sensitive   = true
}

variable "common_tags" {
  description = "Common tags for resources"
  type        = map(string)
  default     = {}
} 