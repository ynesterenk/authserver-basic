# Azure Migration Phase Prompts Overview

## Summary
All four phase prompts have been successfully generated based on the Azure Migration PRD. Each prompt follows the standard structure: Context, Task, Constraints, and Output.

## Phase 1: Infrastructure Setup
**File**: `phase1-infrastructure-setup.prompt.md`
- **Focus**: Terraform modules for Azure infrastructure
- **Components**: Function App, Key Vault, API Management, Monitoring
- **Key Output**: Complete Terraform structure under `/authserver.azure/terraform/`

## Phase 2: Code Migration
**File**: `phase2-code-migration.prompt.md`
- **Focus**: Convert AWS Lambda handlers to Azure Functions
- **Components**: BasicAuthFunction, OAuth2TokenFunction, OAuth2IntrospectFunction
- **Key Output**: Azure Functions with HTTP triggers maintaining exact API contracts

## Phase 3: Testing Strategy
**File**: `phase3-testing-strategy.prompt.md`
- **Focus**: Comprehensive test suite for Azure implementation
- **Components**: Unit tests, Integration tests, Performance tests, Security tests
- **Key Output**: 90%+ test coverage with Azure-specific testing patterns

## Phase 4: Deployment Automation
**File**: `phase4-deployment-automation.prompt.md`
- **Focus**: CI/CD pipeline for automated Azure deployment
- **Components**: GitHub Actions workflow, deployment scripts, smoke tests
- **Key Output**: Complete deployment automation for dev environment

## Usage Instructions

1. **Sequential Execution**: Execute prompts in order (Phase 1 → 2 → 3 → 4)
2. **AI Tool Selection**: 
   - Use Claude Sonnet 4 for complex code generation
   - Use Claude Opus 4 for optimization and review
3. **Validation**: After each phase, validate outputs against PRD requirements
4. **Iteration**: May need multiple AI interactions per phase for complete implementation

## Next Steps

After all phases are complete:
1. Conduct end-to-end testing
2. Performance benchmarking vs AWS
3. Security audit
4. Documentation review
5. Prepare for production readiness

## File Locations
All prompts are located in: `/prompts/azure-migration/phase-prompts/`
- Phase 1: Infrastructure Setup
- Phase 2: Code Migration  
- Phase 3: Testing Strategy
- Phase 4: Deployment Automation

Each phase also has a summary file (*-summary.md) for quick reference. 