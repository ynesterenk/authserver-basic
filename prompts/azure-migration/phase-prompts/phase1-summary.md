# Phase 1 Prompt Generated

## Generated File
✅ `phase1-infrastructure-setup.prompt.md` (169 lines)

## Prompt Structure Used
Following the PRD standard structure:
1. **Context**: AWS current state + Azure target requirements
2. **Task**: Generate complete Terraform infrastructure
3. **Constraints**: Architecture, security, performance, and Terraform best practices
4. **Output**: Detailed file structure and module requirements

## Key Components Covered

### Infrastructure Modules
1. **Base Infrastructure** - Resource groups, networking, tags
2. **Function App** - Java 21 runtime, consumption plan, managed identity
3. **Key Vault** - Secrets management with proper access policies
4. **API Management** - Developer tier with rate limiting
5. **Monitoring** - Application Insights and alerts

### Terraform Structure
```
/authserver.azure/terraform/
├── main.tf
├── variables.tf
├── outputs.tf
├── backend.tf
├── modules/
│   ├── function-app/
│   ├── key-vault/
│   ├── api-management/
│   └── monitoring/
└── environments/
    └── dev/
```

## Usage Instructions

### For AI Tools (Claude/Copilot)
1. Copy the entire prompt from `phase1-infrastructure-setup.prompt.md`
2. Paste into your AI tool
3. Request generation of each module sequentially
4. Review and validate generated code
5. Test with `terraform plan`

### Expected Outputs
- Complete Terraform modules
- README with deployment instructions
- Deploy scripts (PowerShell/Bash)
- Example variable files

## Next Steps
1. Execute this prompt with AI tools
2. Create Azure project structure
3. Initialize Terraform
4. Deploy to Azure dev environment
5. Proceed to Phase 2 (Code Migration) 