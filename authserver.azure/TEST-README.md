# Azure Functions Auth Server - Testing Guide

This guide provides comprehensive instructions for testing all endpoints in the Azure Functions Auth Server locally.

## Prerequisites

1. **Start the Azure Functions locally:**
   ```powershell
   cd authserver.azure
   mvn clean package
   mvn azure-functions:run
   ```

2. **Wait for the startup message:**
   Look for: `Http Functions: ... oauthToken: [POST] http://localhost:7071/api/oauth/token`

## Testing Options

### Option 1: PowerShell Test Suite (Windows)

Run the comprehensive test suite:
```powershell
.\test-endpoints.ps1
```

This will run all test scenarios automatically and display results.

### Option 2: Bash Script (Linux/Mac)

View all curl commands:
```bash
./test-endpoints-curl.sh
```

### Option 3: Manual Curl Commands

#### Basic Authentication Tests

```bash
# Valid user authentication
curl -X POST http://localhost:7071/api/auth/validate \
  -H "Authorization: Basic ZGVtbzpkZW1vMTIz" \
  -H "Content-Type: application/json"

# Admin user authentication  
curl -X POST http://localhost:7071/api/auth/validate \
  -H "Authorization: Basic YWRtaW46YWRtaW4xMjM=" \
  -H "Content-Type: application/json"
```

#### OAuth 2.0 Token Tests

```bash
# Get access token (client credentials grant)
curl -X POST http://localhost:7071/api/oauth/token \
  -H "Authorization: Basic dGVzdC1jbGllbnQ6dGVzdC1zZWNyZXQ=" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials&scope=read"

# Using JSON body (after our update)
curl -X POST http://localhost:7071/api/oauth/token \
  -H "Content-Type: application/json" \
  -d '{"grant_type":"client_credentials","client_id":"test-client","client_secret":"test-secret","scope":"read"}'
```

#### OAuth 2.0 Introspection Tests

```bash
# First get a token
TOKEN=$(curl -s -X POST http://localhost:7071/api/oauth/token \
  -H "Authorization: Basic dGVzdC1jbGllbnQ6dGVzdC1zZWNyZXQ=" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials&scope=read" | jq -r '.access_token')

# Introspect the token
curl -X POST http://localhost:7071/api/oauth/introspect \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "token=$TOKEN"
```

## Test Data Reference

### Basic Auth Users

| Username | Password    | Status   | Roles                      | Base64 Auth Header           |
|----------|-------------|----------|----------------------------|------------------------------|
| demo     | demo123     | ACTIVE   | user, read                 | Basic ZGVtbzpkZW1vMTIz      |
| admin    | admin123    | ACTIVE   | admin, user, read, write   | Basic YWRtaW46YWRtaW4xMjM=  |
| test     | test123     | DISABLED | user                       | Basic dGVzdDp0ZXN0MTIz      |
| service  | service123  | ACTIVE   | service, read, write       | Basic c2VydmljZTpzZXJ2aWNlMTIz |

### OAuth Clients

| Client ID        | Client Secret    | Status   | Allowed Scopes         | Base64 Auth Header                            |
|------------------|------------------|----------|------------------------|-----------------------------------------------|
| demo-client      | demo-secret      | ACTIVE   | read, write            | Basic ZGVtby1jbGllbnQ6ZGVtby1zZWNyZXQ=      |
| test-client      | test-secret      | ACTIVE   | read                   | Basic dGVzdC1jbGllbnQ6dGVzdC1zZWNyZXQ=      |
| admin-client     | admin-secret     | ACTIVE   | read, write, admin     | Basic YWRtaW4tY2xpZW50OmFkbWluLXNlY3JldA==  |
| service-client   | service-secret   | ACTIVE   | service, read, write   | Basic c2VydmljZS1jbGllbnQ6c2VydmljZS1zZWNyZXQ= |
| inactive-client  | inactive-secret  | DISABLED | read                   | Basic aW5hY3RpdmUtY2xpZW50OmluYWN0aXZlLXNlY3JldA== |

## Expected Responses

### Basic Auth Success
```json
{
  "authenticated": true,
  "username": "demo",
  "timestamp": 1234567890,
  "roles": ["user", "read"],
  "message": "Authentication successful"
}
```

### Basic Auth Failure
```json
{
  "authenticated": false,
  "username": "demo",
  "timestamp": 1234567890,
  "message": "Invalid password"
}
```

### OAuth Token Success
```json
{
  "access_token": "eyJ0eXAiOiJKV1QiLCJhbGc...",
  "token_type": "Bearer",
  "expires_in": 3600,
  "scope": "read"
}
```

### OAuth Token Error
```json
{
  "error": "invalid_client",
  "error_description": "Client authentication failed"
}
```

### Token Introspection Success
```json
{
  "active": true,
  "scope": "read",
  "client_id": "test-client",
  "token_type": "Bearer",
  "exp": 1234567890,
  "iat": 1234567890
}
```

### Token Introspection Failure
```json
{
  "active": false
}
```

## Common Issues

1. **"Grant type cannot be blank" error**
   - Make sure to use `application/x-www-form-urlencoded` content type for OAuth endpoints
   - Or use JSON format with our updated code

2. **"Invalid client" error**
   - Check that the Base64 encoded credentials are correct
   - Ensure the client exists and is ACTIVE

3. **"Missing or invalid Authorization header"**
   - Include the Authorization header with proper Basic auth format
   - Format: `Authorization: Basic <base64(clientId:clientSecret)>`

## Tips

- Use `jq` for pretty JSON output: `curl ... | jq`
- Add `-v` flag to curl for verbose output
- Add `-w '\n'` to curl to add a newline after response
- Test error scenarios to ensure proper error handling

## Next Steps

After successful local testing:
1. Deploy to Azure Dev environment
2. Update Azure API Management policies if needed
3. Test with real client applications 