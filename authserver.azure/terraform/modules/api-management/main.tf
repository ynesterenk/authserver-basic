# API Management Instance
resource "azurerm_api_management" "main" {
  name                = "apim-${var.project_name}-${var.resource_suffix}"
  location            = var.location
  resource_group_name = var.resource_group_name
  publisher_name      = "Auth Server Team"
  publisher_email     = "admin@example.com" # Change this

  sku_name = "${var.apim_sku}_${var.apim_capacity}"

  # Identity for accessing other Azure services
  identity {
    type = "SystemAssigned"
  }

  tags = merge(var.common_tags, {
    Purpose = "API Gateway for Authorization Server"
  })
}

# Product for Auth Server APIs
resource "azurerm_api_management_product" "auth_server" {
  product_id            = "auth-server"
  api_management_name   = azurerm_api_management.main.name
  resource_group_name   = var.resource_group_name
  display_name          = "Authorization Server APIs"
  description           = "APIs for Basic Authentication and OAuth 2.0"
  terms                 = "Use of this API is subject to terms and conditions"
  subscription_required = false # Set to true if you want to require subscriptions
  approval_required     = false
  published             = true
}

# Basic Auth API
resource "azurerm_api_management_api" "basic_auth" {
  name                = "basic-auth-api"
  resource_group_name = var.resource_group_name
  api_management_name = azurerm_api_management.main.name
  revision            = "1"
  display_name        = "Basic Authentication API"
  path                = "auth"
  protocols           = ["https"]
  description         = "API for HTTP Basic Authentication validation"
  service_url         = var.function_app_basic_auth_url

  import {
    content_format = "openapi+json"
    content_value = jsonencode({
      openapi = "3.0.0"
      info = {
        title       = "Basic Authentication API"
        version     = "1.0.0"
        description = "API for validating HTTP Basic Authentication credentials"
      }
      servers = [
        {
          url = var.function_app_basic_auth_url
        }
      ]
      paths = {
        "/api/auth/validate" = {
          post = {
            summary     = "Validate Basic Authentication"
            description = "Validates HTTP Basic Authentication credentials"
            parameters = [
              {
                name        = "Authorization"
                in          = "header"
                required    = true
                description = "Basic authentication header"
                schema = {
                  type = "string"
                }
              }
            ]
            responses = {
              "200" = {
                description = "Authentication result"
                content = {
                  "application/json" = {
                    schema = {
                      type = "object"
                      properties = {
                        allowed = {
                          type = "boolean"
                        }
                        message = {
                          type = "string"
                        }
                        timestamp = {
                          type = "integer"
                        }
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    })
  }
}

# OAuth API
resource "azurerm_api_management_api" "oauth" {
  name                = "oauth-api"
  resource_group_name = var.resource_group_name
  api_management_name = azurerm_api_management.main.name
  revision            = "1"
  display_name        = "OAuth 2.0 API"
  path                = "oauth"
  protocols           = ["https"]
  description         = "API for OAuth 2.0 token operations"
  service_url         = var.function_app_oauth_url

  import {
    content_format = "openapi+json"
    content_value = jsonencode({
      openapi = "3.0.0"
      info = {
        title       = "OAuth 2.0 API"
        version     = "1.0.0"
        description = "API for OAuth 2.0 token generation and introspection"
      }
      servers = [
        {
          url = var.function_app_oauth_url
        }
      ]
      paths = {
        "/api/oauth/token" = {
          post = {
            summary     = "Generate OAuth Token"
            description = "Generates an OAuth 2.0 access token using client credentials grant"
            requestBody = {
              required = true
              content = {
                "application/x-www-form-urlencoded" = {
                  schema = {
                    type = "object"
                    properties = {
                      grant_type = {
                        type = "string"
                        enum = ["client_credentials"]
                      }
                      client_id = {
                        type = "string"
                      }
                      client_secret = {
                        type = "string"
                      }
                      scope = {
                        type = "string"
                      }
                    }
                    required = ["grant_type", "client_id", "client_secret"]
                  }
                }
              }
            }
            responses = {
              "200" = {
                description = "Access token response"
                content = {
                  "application/json" = {
                    schema = {
                      type = "object"
                      properties = {
                        access_token = {
                          type = "string"
                        }
                        token_type = {
                          type = "string"
                        }
                        expires_in = {
                          type = "integer"
                        }
                        scope = {
                          type = "string"
                        }
                      }
                    }
                  }
                }
              }
            }
          }
        }
        "/api/oauth/introspect" = {
          post = {
            summary     = "Introspect OAuth Token"
            description = "Introspects an OAuth 2.0 token to determine its status and metadata"
            requestBody = {
              required = true
              content = {
                "application/x-www-form-urlencoded" = {
                  schema = {
                    type = "object"
                    properties = {
                      token = {
                        type = "string"
                      }
                    }
                    required = ["token"]
                  }
                }
              }
            }
            responses = {
              "200" = {
                description = "Token introspection response"
                content = {
                  "application/json" = {
                    schema = {
                      type = "object"
                      properties = {
                        active = {
                          type = "boolean"
                        }
                        client_id = {
                          type = "string"
                        }
                        scope = {
                          type = "string"
                        }
                        exp = {
                          type = "integer"
                        }
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    })
  }
}

# Associate APIs with the product
resource "azurerm_api_management_product_api" "basic_auth" {
  api_name            = azurerm_api_management_api.basic_auth.name
  product_id          = azurerm_api_management_product.auth_server.product_id
  api_management_name = azurerm_api_management.main.name
  resource_group_name = var.resource_group_name
}

resource "azurerm_api_management_product_api" "oauth" {
  api_name            = azurerm_api_management_api.oauth.name
  product_id          = azurerm_api_management_product.auth_server.product_id
  api_management_name = azurerm_api_management.main.name
  resource_group_name = var.resource_group_name
}

# Rate limiting policy
resource "azurerm_api_management_api_policy" "rate_limit" {
  api_name            = azurerm_api_management_api.basic_auth.name
  api_management_name = azurerm_api_management.main.name
  resource_group_name = var.resource_group_name

  xml_content = <<XML
<policies>
  <inbound>
    <rate-limit calls="100" renewal-period="60" />
    <cors allow-credentials="false">
      <allowed-origins>
        <origin>*</origin>
      </allowed-origins>
      <allowed-methods>
        <method>GET</method>
        <method>POST</method>
      </allowed-methods>
      <allowed-headers>
        <header>*</header>
      </allowed-headers>
    </cors>
    <base />
  </inbound>
  <backend>
    <base />
  </backend>
  <outbound>
    <base />
  </outbound>
  <on-error>
    <base />
  </on-error>
</policies>
XML
}

resource "azurerm_api_management_api_policy" "oauth_rate_limit" {
  api_name            = azurerm_api_management_api.oauth.name
  api_management_name = azurerm_api_management.main.name
  resource_group_name = var.resource_group_name

  xml_content = <<XML
<policies>
  <inbound>
    <rate-limit calls="100" renewal-period="60" />
    <cors allow-credentials="false">
      <allowed-origins>
        <origin>*</origin>
      </allowed-origins>
      <allowed-methods>
        <method>POST</method>
      </allowed-methods>
      <allowed-headers>
        <header>*</header>
      </allowed-headers>
    </cors>
    <base />
  </inbound>
  <backend>
    <base />
  </backend>
  <outbound>
    <base />
  </outbound>
  <on-error>
    <base />
  </on-error>
</policies>
XML
}

# Diagnostic settings
resource "azurerm_monitor_diagnostic_setting" "apim" {
  name                       = "apim-diagnostics"
  target_resource_id         = azurerm_api_management.main.id
  log_analytics_workspace_id = var.log_analytics_workspace_id

  enabled_log {
    category = "GatewayLogs"
  }

  metric {
    category = "AllMetrics"
    enabled  = true
  }
} 