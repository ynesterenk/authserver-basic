# Phase 3 Lessons Learned: Azure Functions Deployment and Configuration

## Overview
This document captures the key lessons learned during Phase 3 of the Azure Functions migration, focusing on deployment issues, Spring dependency injection problems, and configuration fixes that were required to make the authorization server work correctly in Azure Functions.

## Critical Fixes Applied

### 1. Spring Cloud Functions Architecture
**Issue**: Initial implementation used direct Azure Functions annotations, which caused Spring dependency injection failures.

**Solution**: Migrated to Spring Cloud Functions approach:
- Created `FunctionConfiguration.java` with Spring function beans using `Function<String, String>`
- Created `AzureFunctionEntryPoints.java` with Azure Function entry points using `FunctionInvoker`
- Added Spring Cloud Function dependencies to `pom.xml`

**Key Files**:
- `src/main/java/com/example/auth/infrastructure/azure/functions/FunctionConfiguration.java`
- `src/main/java/com/example/auth/infrastructure/azure/functions/AzureFunctionEntryPoints.java`

### 2. Spring Dependency Injection with Profiles
**Issue**: Spring couldn't resolve `UserRepository` and `OAuthClientRepository` beans due to profile mismatches and missing `@Component` annotations.

**Solution**: Applied AWS pattern with proper annotations:
- Added `@Component` and `@Profile` annotations to all repository implementations
- Aligned profile strategy: "azure" for local development, "dev"/"prod" for KeyVault
- Removed conflicting bean definitions from configuration classes

**Profile Strategy**:
```java
// Local repositories for Azure Functions development
@Component
@Profile("azure")
public class LocalAzureUserRepository implements UserRepository

// KeyVault repositories for deployed environments  
@Component
@Profile({"dev", "prod"})
public class KeyVaultUserRepository implements UserRepository
```

### 3. Spring Profiles Configuration Fix
**Issue**: Exception occurred due to incorrect profile configuration:
```
org.springframework.boot.context.config.InvalidConfigDataPropertyException:
Property 'spring.profiles.active' imported from location 'class path resource [application-azure.properties]'
```

**Solution**: Removed `spring.profiles.active=azure` from `application-azure.properties`. Azure Functions sets the profile via environment variables (`SPRING_PROFILES_ACTIVE=azure`), not property files.

### 4. Azure Functions Host Configuration  
**Issue**: `host.json` contained `customHandler` section meant for non-Java functions, preventing Java worker from starting.

**Solution**: Removed the `customHandler` section from `host.json`:
```json
{
  "version": "2.0",
  "extensionBundle": {
    "id": "Microsoft.Azure.Functions.ExtensionBundle",
    "version": "[4.*, 5.0.0)"
  },
  "functionTimeout": "00:05:00"
}
```

### 5. Java Version Compatibility
**Issue**: Initially used Java 21, but Azure Functions has limitations.

**Solution**: Downgraded to Java 17 in `pom.xml`:
```xml
<java.version>17</java.version>
<maven.compiler.source>17</maven.compiler.source>
<maven.compiler.target>17</maven.compiler.target>
```

### 6. Standard Maven Deployment
**Issue**: Custom PowerShell deployment scripts (`deploy-simple.ps1`, `deploy-to-azure.ps1`) were complex and error-prone.

**Solution**: Use standard Maven Azure Functions plugin:
```bash
mvn clean package azure-functions:deploy
```

**Benefits**:
- Automatic function app creation
- Storage account setup
- Application Insights configuration
- Proper ZIP deployment
- Function binding registration

### 7. SecretClient Profile Alignment
**Issue**: Profile mismatch between `SecretClient` bean and repositories using it caused dependency injection failures.

**Solution**: Aligned profiles consistently:
- `SecretClient`: `@Profile({"dev", "prod"})`
- `KeyVaultUserRepository`: `@Profile({"dev", "prod"})`
- `KeyVaultOAuthClientRepository`: `@Profile({"dev", "prod"})`

### 8. Function HTTP Routing
**Issue**: Custom routes in `@HttpTrigger` weren't being recognized initially.

**Solution**: Proper Spring Cloud Functions integration enabled custom routing:
- `/api/oauth/token` (not `/api/oauthtoken`)
- `/api/oauth/introspect` (not `/api/oauthintrospect`)
- `/api/auth/validate` (not `/api/basicauth`)

## Azure-Specific Constraints

### Infrastructure
- **Region**: West US preferred over East US for quota availability
- **Storage Account**: Names limited to 24 characters, lowercase only
- **Consumption Plan**: Y1 most cost-effective but quota-dependent

### Configuration
- **Environment Variables**: Use Azure Function app settings, not property files for profile activation
- **Managed Identity**: Required for KeyVault access in deployed environments
- **Application Insights**: Automatically configured by Maven plugin

## Testing Approach

### Local Testing
```bash
mvn azure-functions:run
```

### Endpoint Testing
```bash
# OAuth Token
curl -X POST https://func-app.azurewebsites.net/api/oauth/token \
  -H "Authorization: Basic dGVzdC1jbGllbnQ6dGVzdC1zZWNyZXQ=" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials&scope=read"

# OAuth Introspection  
curl -X POST https://func-app.azurewebsites.net/api/oauth/introspect \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "token=$TOKEN"

# Basic Auth
curl -X POST https://func-app.azurewebsites.net/api/auth/validate \
  -H "Authorization: Basic dGVzdDp0ZXN0MTIz"
```

## Best Practices Established

### Code Organization
1. **Separate entry points**: Use `AzureFunctionEntryPoints.java` for Azure bindings
2. **Spring function beans**: Define business logic in `FunctionConfiguration.java`
3. **Profile-based repositories**: Clear separation between local and cloud implementations

### Deployment
1. **Use Maven plugin**: Avoid custom deployment scripts
2. **Environment-specific profiles**: "azure" for development, "dev"/"prod" for production
3. **Automatic resource creation**: Let Maven plugin handle infrastructure

### Configuration Management
1. **Environment variables**: Use Azure app settings for runtime configuration
2. **Property files**: Only for static, environment-agnostic settings
3. **Profile activation**: Via environment variables, not property files

## Dependencies Added

### Maven Dependencies
```xml
<!-- Spring Cloud Functions -->
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-function-adapter-azure</artifactId>
</dependency>

<!-- Azure Functions Java Library -->
<dependency>
    <groupId>com.microsoft.azure.functions</groupId>
    <artifactId>azure-functions-java-library</artifactId>
</dependency>
```

### Maven Plugins
```xml
<plugin>
    <groupId>com.microsoft.azure</groupId>
    <artifactId>azure-functions-maven-plugin</artifactId>
    <version>1.36.0</version>
</plugin>
```

## Performance Notes

### Cold Start Optimization
- Spring Cloud Functions adds ~2-3 seconds to cold start
- Consider using Azure Functions Premium plan for production
- Implement warming strategies if needed

### Memory Usage
- Spring Boot applications require more memory
- Monitor function execution times and memory consumption
- Adjust function timeout settings as needed

## Security Considerations

### Managed Identity
- Use Azure Managed Identity for KeyVault access
- Avoid storing secrets in application settings
- Configure proper RBAC permissions

### Network Security
- Consider VNet integration for production
- Use Azure Application Gateway for external access
- Implement proper CORS policies

## Migration Success Metrics

### Functional Validation
✅ OAuth Token Generation: Working
✅ OAuth Token Introspection: Working  
✅ Basic Authentication: Working
✅ Spring Dependency Injection: Resolved
✅ Custom HTTP Routes: Working
✅ Local Development: Working
✅ Azure Deployment: Working

### Architecture Benefits
- **Maintainability**: Consistent with AWS implementation
- **Testability**: Local development support
- **Scalability**: Azure Functions auto-scaling
- **Cost**: Pay-per-execution model
- **Integration**: Native Azure ecosystem support

## Future Enhancements

### Production Readiness
1. **KeyVault Integration**: Implement for "dev"/"prod" profiles
2. **Monitoring**: Enhanced Application Insights integration
3. **CI/CD**: Automated deployment pipelines
4. **Performance**: Optimize cold start times

### Additional Features
1. **API Management**: Azure APIM integration
2. **Authentication**: Azure AD integration
3. **Networking**: VNet integration
4. **Backup**: Configuration and data backup strategies

## Conclusion

The Azure Functions migration successfully replicated the AWS Lambda functionality while maintaining code compatibility and Spring framework benefits. The key to success was understanding Azure Functions' Spring Cloud Functions integration pattern and properly configuring Spring profiles for different environments.

The final implementation provides a robust, scalable authorization server that works consistently across local development and Azure cloud environments. 