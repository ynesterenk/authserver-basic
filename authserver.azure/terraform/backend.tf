terraform {
  backend "azurerm" {
    # Backend configuration will be provided via backend-config files
    # or via terraform init -backend-config parameters

    # Example configuration (will be overridden):
    # resource_group_name  = "rg-authserver-terraform-dev"
    # storage_account_name = "stateauthserverdev"
    # container_name       = "terraform-state"
    # key                  = "authserver.tfstate"
  }
} 