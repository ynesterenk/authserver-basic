# Service Plan for Function Apps
resource "azurerm_service_plan" "function_apps" {
  name                = "asp-${var.project_name}-${var.resource_suffix}"
  location            = var.location
  resource_group_name = var.resource_group_name
  os_type             = "Linux"
  sku_name            = var.function_app_sku

  tags = merge(var.common_tags, {
    Purpose = "Function App Service Plan"
  })
}

# Basic Auth Function App
resource "azurerm_linux_function_app" "basic_auth" {
  name                = "func-${var.project_name}-basic-${var.resource_suffix}"
  location            = var.location
  resource_group_name = var.resource_group_name
  service_plan_id     = azurerm_service_plan.function_apps.id

  storage_account_name       = var.storage_account_name
  storage_account_access_key = var.storage_account_access_key

  # Enable system-assigned managed identity
  identity {
    type = "SystemAssigned"
  }

  site_config {
    application_stack {
      java_version = var.java_version
    }

    # Performance and reliability settings
    always_on                              = var.function_app_sku != "Y1" # Always on for non-consumption plans
    application_insights_connection_string = var.application_insights_connection_string
    application_insights_key               = var.application_insights_instrumentation_key

    cors {
      allowed_origins = ["*"] # Configure as needed
    }
  }

  app_settings = {
    # Spring Boot configuration
    "SPRING_PROFILES_ACTIVE" = var.environment
    "JAVA_OPTS"              = "-Dspring.profiles.active=${var.environment}"

    # Azure-specific settings
    "WEBSITE_RUN_FROM_PACKAGE"        = "1"
    "FUNCTIONS_WORKER_RUNTIME"        = "java"
    "FUNCTIONS_EXTENSION_VERSION"     = "~4"
    "WEBSITE_ENABLE_SYNC_UPDATE_SITE" = "true"

    # Application Insights
    "APPINSIGHTS_INSTRUMENTATIONKEY"        = var.application_insights_instrumentation_key
    "APPLICATIONINSIGHTS_CONNECTION_STRING" = var.application_insights_connection_string

    # Key Vault configuration
    "KEY_VAULT_URI" = var.key_vault_uri

    # Content share settings (only for consumption plans)
    "WEBSITE_CONTENTAZUREFILECONNECTIONSTRING" = var.function_app_sku == "Y1" ? var.storage_account_connection_string : null
    "WEBSITE_CONTENTSHARE"                     = var.function_app_sku == "Y1" ? "basic-auth-function-content" : null
  }

  tags = merge(var.common_tags, {
    Purpose      = "Basic Authentication Function"
    Component    = "Authentication"
    FunctionType = "BasicAuth"
  })
}

# OAuth Function App
resource "azurerm_linux_function_app" "oauth" {
  name                = "func-${var.project_name}-oauth-${var.resource_suffix}"
  location            = var.location
  resource_group_name = var.resource_group_name
  service_plan_id     = azurerm_service_plan.function_apps.id

  storage_account_name       = var.storage_account_name
  storage_account_access_key = var.storage_account_access_key

  # Enable system-assigned managed identity
  identity {
    type = "SystemAssigned"
  }

  site_config {
    application_stack {
      java_version = var.java_version
    }

    # Performance and reliability settings
    always_on                              = var.function_app_sku != "Y1" # Always on for non-consumption plans
    application_insights_connection_string = var.application_insights_connection_string
    application_insights_key               = var.application_insights_instrumentation_key

    cors {
      allowed_origins = ["*"] # Configure as needed
    }
  }

  app_settings = {
    # Spring Boot configuration
    "SPRING_PROFILES_ACTIVE" = var.environment
    "JAVA_OPTS"              = "-Dspring.profiles.active=${var.environment}"

    # Azure-specific settings
    "WEBSITE_RUN_FROM_PACKAGE"        = "1"
    "FUNCTIONS_WORKER_RUNTIME"        = "java"
    "FUNCTIONS_EXTENSION_VERSION"     = "~4"
    "WEBSITE_ENABLE_SYNC_UPDATE_SITE" = "true"

    # Application Insights
    "APPINSIGHTS_INSTRUMENTATIONKEY"        = var.application_insights_instrumentation_key
    "APPLICATIONINSIGHTS_CONNECTION_STRING" = var.application_insights_connection_string

    # Key Vault configuration
    "KEY_VAULT_URI" = var.key_vault_uri

    # Content share settings (only for consumption plans)
    "WEBSITE_CONTENTAZUREFILECONNECTIONSTRING" = var.function_app_sku == "Y1" ? var.storage_account_connection_string : null
    "WEBSITE_CONTENTSHARE"                     = var.function_app_sku == "Y1" ? "oauth-function-content" : null
  }

  tags = merge(var.common_tags, {
    Purpose      = "OAuth 2.0 Functions"
    Component    = "Authentication"
    FunctionType = "OAuth2"
  })
} 