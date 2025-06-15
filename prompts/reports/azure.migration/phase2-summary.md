# Phase 2 Prompt Generated

## Generated File
✅ `phase2-code-migration.prompt.md` (226 lines)

## Prompt Structure Used
Following the PRD standard structure:
1. **Context**: Current AWS Lambda implementation details
2. **Task**: Generate Azure Functions code maintaining same functionality
3. **Constraints**: Architecture, security, performance, and Azure-specific requirements
4. **Output**: Complete file structure with implementation requirements

## Key Components to Migrate

### Azure Functions
1. **BasicAuthFunction** - Replace LambdaHandler
2. **OAuth2TokenFunction** - Handle token endpoint
3. **OAuth2IntrospectFunction** - Handle introspection

### Repository Implementations
1. **KeyVaultUserRepository** - Replace SecretsManagerUserRepository
2. **KeyVaultOAuthClientRepository** - OAuth client storage
3. **LocalAzureUserRepository** - Local development support

### Configuration & Setup
1. **AzureFunctionConfiguration** - Spring configuration
2. **AzureApplication** - Entry point
3. **pom.xml** - Maven dependencies
4. **host.json** - Azure Functions config
5. **local.settings.json** - Local dev settings

## Migration Mapping

| AWS Component | Azure Component |
|--------------|-----------------|
| Lambda RequestHandler | @FunctionName + @HttpTrigger |
| API Gateway Events | HttpRequestMessage |
| Secrets Manager | Azure Key Vault |
| CloudWatch | Application Insights |
| IAM Roles | Managed Identity |

## Key Migration Points

### API Contract Preservation
- Same endpoints: `/api/auth/validate`, `/api/oauth/token`, `/api/oauth/introspect`
- Identical request/response formats
- Same error handling behavior

### Architecture Preservation
- Domain layer untouched
- Only infrastructure adapters change
- Spring DI maintained
- Hexagonal boundaries respected

### Performance Requirements
- Cold start < 1s
- Warm requests < 100ms
- Memory < 512MB
- Same throughput capabilities

## Usage Instructions

### For AI Tools
1. Copy the entire prompt from `phase2-code-migration.prompt.md`
2. Generate each component sequentially:
   - Start with configuration files
   - Then Azure Functions
   - Finally repository implementations
3. Review generated code for:
   - Correct package structure
   - Proper error handling
   - Security compliance
   - Performance optimizations

### Expected Deliverables
- 7 Java classes
- 3 configuration files
- Maven POM with Azure dependencies
- Unit test examples

## Next Steps
1. Execute prompt with AI tools
2. Create `/authserver.azure/` structure
3. Copy domain layer from AWS project
4. Implement generated Azure adapters
5. Test with Azure Functions Core Tools
6. Proceed to Phase 3 (Testing Strategy) 