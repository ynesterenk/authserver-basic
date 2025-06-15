# Environment and naming variables
variable "environment" {
  description = "Environment name (dev, staging, prod)"
  type        = string
  validation {
    condition     = contains(["dev", "staging", "prod"], var.environment)
    error_message = "Environment must be dev, staging, or prod."
  }
}

variable "project_name" {
  description = "Project name for resource naming"
  type        = string
  default     = "authserver"
  validation {
    condition     = can(regex("^[a-z0-9]{3,12}$", var.project_name))
    error_message = "Project name must be 3-12 characters, lowercase letters and numbers only."
  }
}

variable "location" {
  description = "Azure region for resources"
  type        = string
  default     = "East US"
}

variable "location_short" {
  description = "Short form of Azure region for naming"
  type        = string
  default     = "eus"
  validation {
    condition     = can(regex("^[a-z]{2,4}$", var.location_short))
    error_message = "Location short must be 2-4 lowercase letters."
  }
}

# Networking variables
variable "allowed_ips" {
  description = "List of IP addresses allowed to access Key Vault"
  type        = list(string)
  default     = []
}

# Function App variables
variable "function_app_sku" {
  description = "SKU for the Function App service plan"
  type        = string
  default     = "Y1" # Consumption plan
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

# Key Vault variables
variable "key_vault_sku" {
  description = "SKU for Key Vault"
  type        = string
  default     = "standard"
  validation {
    condition     = contains(["standard", "premium"], var.key_vault_sku)
    error_message = "Key Vault SKU must be standard or premium."
  }
}

# API Management variables
variable "apim_sku" {
  description = "SKU for API Management"
  type        = string
  default     = "Developer"
  validation {
    condition     = contains(["Developer", "Basic", "Standard", "Premium"], var.apim_sku)
    error_message = "API Management SKU must be Developer, Basic, Standard, or Premium."
  }
}

variable "apim_capacity" {
  description = "Capacity for API Management"
  type        = number
  default     = 1
  validation {
    condition     = var.apim_capacity >= 1 && var.apim_capacity <= 10
    error_message = "API Management capacity must be between 1 and 10."
  }
}

# Monitoring variables
variable "log_retention_days" {
  description = "Log retention period in days"
  type        = number
  default     = 30
  validation {
    condition     = var.log_retention_days >= 7 && var.log_retention_days <= 730
    error_message = "Log retention must be between 7 and 730 days."
  }
}

# Tags
variable "common_tags" {
  description = "Common tags to apply to all resources"
  type        = map(string)
  default = {
    Project     = "Java Authorization Server"
    Environment = "Development"
    ManagedBy   = "Terraform"
  }
} 