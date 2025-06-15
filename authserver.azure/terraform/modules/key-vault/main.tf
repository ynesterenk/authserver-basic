# Get current client configuration
data "azurerm_client_config" "current" {}

# Generate shorter name for Key Vault (max 24 chars, alphanumeric + hyphens only)
locals {
  # Extract just the suffix part (e.g., "dev-eus-vx9" becomes "deveusvx9")
  kv_suffix = replace(replace(var.resource_suffix, "-", ""), "_", "")
  # Ensure total length is under 24 characters
  kv_name = "kv-${var.project_name}-${substr(local.kv_suffix, 0, min(length(local.kv_suffix), 24 - length(var.project_name) - 3))}"
}

# Key Vault
resource "azurerm_key_vault" "main" {
  name                = local.kv_name
  location            = var.location
  resource_group_name = var.resource_group_name
  tenant_id           = data.azurerm_client_config.current.tenant_id
  sku_name            = var.key_vault_sku

  # Security settings
  enabled_for_disk_encryption     = true
  enabled_for_deployment          = false
  enabled_for_template_deployment = false
  enable_rbac_authorization       = false
  soft_delete_retention_days      = 7
  purge_protection_enabled        = false # Set to true for production

  # Network access rules
  network_acls {
    default_action = length(var.allowed_ips) > 0 ? "Deny" : "Allow"
    bypass         = "AzureServices"
    ip_rules       = var.allowed_ips
  }

  tags = merge(var.common_tags, {
    Purpose = "Application Secrets Storage"
  })
}

# Access policy for current user/service principal (for deployment)
resource "azurerm_key_vault_access_policy" "deployer" {
  key_vault_id = azurerm_key_vault.main.id
  tenant_id    = data.azurerm_client_config.current.tenant_id
  object_id    = data.azurerm_client_config.current.object_id

  secret_permissions = [
    "Backup",
    "Delete",
    "Get",
    "List",
    "Purge",
    "Recover",
    "Restore",
    "Set"
  ]

  certificate_permissions = [
    "Create",
    "Delete",
    "Get",
    "Import",
    "List",
    "Update"
  ]

  key_permissions = [
    "Create",
    "Delete",
    "Get",
    "Import",
    "List",
    "Update",
    "Encrypt",
    "Decrypt"
  ]
}

# Placeholder secrets for the application
resource "azurerm_key_vault_secret" "basic_auth_users" {
  name = "basic-auth-users"
  value = jsonencode({
    "testuser" = {
      "passwordHash" = "$argon2id$v=19$m=65536,t=3,p=1$salt$hash", # Placeholder
      "enabled"      = true
    },
    "alice" = {
      "passwordHash" = "$argon2id$v=19$m=65536,t=3,p=1$salt$hash", # Placeholder
      "enabled"      = true
    },
    "bob" = {
      "passwordHash" = "$argon2id$v=19$m=65536,t=3,p=1$salt$hash", # Placeholder
      "enabled"      = false
    }
  })
  key_vault_id = azurerm_key_vault.main.id

  depends_on = [azurerm_key_vault_access_policy.deployer]

  tags = {
    Purpose = "Basic Authentication User Database"
  }
}

resource "azurerm_key_vault_secret" "oauth_clients" {
  name = "oauth-clients"
  value = jsonencode({
    "test-client-1" = {
      "clientSecret" = "test-client-1-secret",
      "scopes"       = ["read", "write"],
      "enabled"      = true
    },
    "test-client-2" = {
      "clientSecret" = "test-client-2-secret",
      "scopes"       = ["read"],
      "enabled"      = true
    }
  })
  key_vault_id = azurerm_key_vault.main.id

  depends_on = [azurerm_key_vault_access_policy.deployer]

  tags = {
    Purpose = "OAuth 2.0 Client Database"
  }
}

resource "azurerm_key_vault_secret" "jwt_signing_key" {
  name         = "jwt-signing-key"
  value        = "your-256-bit-secret-key-here-change-in-production" # Placeholder
  key_vault_id = azurerm_key_vault.main.id

  depends_on = [azurerm_key_vault_access_policy.deployer]

  tags = {
    Purpose = "JWT Token Signing Key"
  }
}

# Diagnostic settings for audit logging
resource "azurerm_monitor_diagnostic_setting" "key_vault" {
  name                       = "key-vault-diagnostics"
  target_resource_id         = azurerm_key_vault.main.id
  log_analytics_workspace_id = var.log_analytics_workspace_id

  enabled_log {
    category = "AuditEvent"
  }

  enabled_log {
    category = "AzurePolicyEvaluationDetails"
  }

  metric {
    category = "AllMetrics"
    enabled  = true
  }
} 