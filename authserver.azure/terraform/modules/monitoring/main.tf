# Log Analytics Workspace
resource "azurerm_log_analytics_workspace" "main" {
  name                = "law-${var.project_name}-${var.resource_suffix}"
  location            = var.location
  resource_group_name = var.resource_group_name
  sku                 = "PerGB2018"
  retention_in_days   = var.log_retention_days

  tags = merge(var.common_tags, {
    Purpose = "Centralized Logging and Analytics"
  })
}

# Application Insights
resource "azurerm_application_insights" "main" {
  name                = "appi-${var.project_name}-${var.resource_suffix}"
  location            = var.location
  resource_group_name = var.resource_group_name
  workspace_id        = azurerm_log_analytics_workspace.main.id
  application_type    = "web"

  tags = merge(var.common_tags, {
    Purpose = "Application Performance Monitoring"
  })
}

# Action Group for alerts
resource "azurerm_monitor_action_group" "main" {
  name                = "ag-${var.project_name}-${var.resource_suffix}"
  resource_group_name = var.resource_group_name
  short_name          = "authserver"

  # Email notifications (configure as needed)
  email_receiver {
    name          = "admin"
    email_address = "admin@example.com" # Change this
  }

  tags = var.common_tags
}

# Log queries for custom metrics (saved searches only, no alerts yet)
resource "azurerm_log_analytics_saved_search" "auth_success_rate" {
  name                       = "AuthSuccessRate"
  log_analytics_workspace_id = azurerm_log_analytics_workspace.main.id
  category                   = "Auth Server Metrics"
  display_name               = "Authentication Success Rate"
  query                      = <<-EOT
    traces
    | where cloud_RoleName contains "authserver"
    | where message contains "Authentication"
    | summarize 
        Total = count(),
        Success = countif(message contains "successful"),
        Failure = countif(message contains "failed")
    | extend SuccessRate = (Success * 100.0) / Total
    | project TimeGenerated=now(), SuccessRate, Total, Success, Failure
  EOT
}

resource "azurerm_log_analytics_saved_search" "oauth_token_metrics" {
  name                       = "OAuthTokenMetrics"
  log_analytics_workspace_id = azurerm_log_analytics_workspace.main.id
  category                   = "Auth Server Metrics"
  display_name               = "OAuth Token Generation Metrics"
  query                      = <<-EOT
    traces
    | where cloud_RoleName contains "authserver"
    | where message contains "OAuth"
    | summarize 
        TokensGenerated = countif(message contains "token generated"),
        TokensValidated = countif(message contains "token validated"),
        Errors = countif(severityLevel >= 3)
    | project TimeGenerated=now(), TokensGenerated, TokensValidated, Errors
  EOT
}

# Note: Advanced alerts will be added after Function Apps are deployed and generating logs
# For now, we only create the monitoring infrastructure without complex alert rules 