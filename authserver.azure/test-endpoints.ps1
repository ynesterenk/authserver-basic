#!/usr/bin/env pwsh
# Azure Functions Local Testing Script
# Tests all endpoints with various scenarios

Write-Host "======================================" -ForegroundColor Cyan
Write-Host "Azure Functions Auth Server Test Suite" -ForegroundColor Cyan
Write-Host "======================================" -ForegroundColor Cyan
Write-Host ""

$BaseUrl = "http://localhost:7071/api"

# Helper function to make requests and display results
function Test-Endpoint {
    param(
        [string]$Name,
        [string]$Method,
        [string]$Url,
        [hashtable]$Headers = @{},
        [string]$Body = ""
    )
    
    Write-Host "Test: $Name" -ForegroundColor Yellow
    Write-Host "-----------------------------------" -ForegroundColor Gray
    
    try {
        $params = @{
            Method = $Method
            Uri = $Url
            Headers = $Headers
            ErrorAction = 'Stop'
        }
        
        if ($Body) {
            $params.Body = $Body
        }
        
        $response = Invoke-RestMethod @params
        Write-Host "✓ Success" -ForegroundColor Green
        Write-Host "Response:" -ForegroundColor Gray
        $response | ConvertTo-Json -Depth 10 | Write-Host
    }
    catch {
        Write-Host "✗ Failed" -ForegroundColor Red
        Write-Host "Error: $_" -ForegroundColor Red
        if ($_.Exception.Response) {
            $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
            $errorBody = $reader.ReadToEnd()
            Write-Host "Response: $errorBody" -ForegroundColor Red
        }
    }
    Write-Host ""
}

# 1. BASIC AUTHENTICATION TESTS
Write-Host "1. BASIC AUTHENTICATION TESTS" -ForegroundColor Magenta
Write-Host "=============================" -ForegroundColor Magenta
Write-Host ""

# Test 1.1: Valid user (demo)
Test-Endpoint -Name "1.1 Valid Basic Auth (demo user)" `
    -Method "POST" `
    -Url "$BaseUrl/auth/validate" `
    -Headers @{
        "Authorization" = "Basic ZGVtbzpkZW1vMTIz"  # demo:demo123
        "Content-Type" = "application/json"
    }

# Test 1.2: Valid admin user
Test-Endpoint -Name "1.2 Valid Basic Auth (admin user)" `
    -Method "POST" `
    -Url "$BaseUrl/auth/validate" `
    -Headers @{
        "Authorization" = "Basic YWRtaW46YWRtaW4xMjM="  # admin:admin123
        "Content-Type" = "application/json"
    }

# Test 1.3: Disabled user (test)
Test-Endpoint -Name "1.3 Basic Auth with disabled user" `
    -Method "POST" `
    -Url "$BaseUrl/auth/validate" `
    -Headers @{
        "Authorization" = "Basic dGVzdDp0ZXN0MTIz"  # test:test123
        "Content-Type" = "application/json"
    }

# Test 1.4: Invalid password
Test-Endpoint -Name "1.4 Basic Auth with wrong password" `
    -Method "POST" `
    -Url "$BaseUrl/auth/validate" `
    -Headers @{
        "Authorization" = "Basic ZGVtbzp3cm9uZ3Bhc3M="  # demo:wrongpass
        "Content-Type" = "application/json"
    }

# Test 1.5: Non-existent user
Test-Endpoint -Name "1.5 Basic Auth with non-existent user" `
    -Method "POST" `
    -Url "$BaseUrl/auth/validate" `
    -Headers @{
        "Authorization" = "Basic bm9uZXhpc3Q6cGFzcw=="  # nonexist:pass
        "Content-Type" = "application/json"
    }

# Test 1.6: Missing Authorization header
Test-Endpoint -Name "1.6 Basic Auth without Authorization header" `
    -Method "POST" `
    -Url "$BaseUrl/auth/validate" `
    -Headers @{
        "Content-Type" = "application/json"
    }

# 2. OAUTH 2.0 TOKEN TESTS
Write-Host "`n2. OAUTH 2.0 TOKEN ENDPOINT TESTS" -ForegroundColor Magenta
Write-Host "=================================" -ForegroundColor Magenta
Write-Host ""

# Test 2.1: Valid client credentials (test-client)
Test-Endpoint -Name "2.1 OAuth Token - Valid client (test-client)" `
    -Method "POST" `
    -Url "$BaseUrl/oauth/token" `
    -Headers @{
        "Authorization" = "Basic dGVzdC1jbGllbnQ6dGVzdC1zZWNyZXQ="  # test-client:test-secret
        "Content-Type" = "application/x-www-form-urlencoded"
    } `
    -Body "grant_type=client_credentials%26scope=read"

# Test 2.2: Valid client with multiple scopes (demo-client)
Test-Endpoint -Name "2.2 OAuth Token - Multiple scopes (demo-client)" `
    -Method "POST" `
    -Url "$BaseUrl/oauth/token" `
    -Headers @{
        "Authorization" = "Basic ZGVtby1jbGllbnQ6ZGVtby1zZWNyZXQ="  # demo-client:demo-secret
        "Content-Type" = "application/x-www-form-urlencoded"
    } `
    -Body "grant_type=client_credentials%26scope=read%20write"

# Test 2.3: Admin client with all scopes
Test-Endpoint -Name "2.3 OAuth Token - Admin client" `
    -Method "POST" `
    -Url "$BaseUrl/oauth/token" `
    -Headers @{
        "Authorization" = "Basic YWRtaW4tY2xpZW50OmFkbWluLXNlY3JldA=="  # admin-client:admin-secret
        "Content-Type" = "application/x-www-form-urlencoded"
    } `
    -Body "grant_type=client_credentials%26scope=read%20write%20admin"

# Test 2.4: Client credentials in body (not recommended but supported)
Test-Endpoint -Name "2.4 OAuth Token - Credentials in body" `
    -Method "POST" `
    -Url "$BaseUrl/oauth/token" `
    -Headers @{
        "Content-Type" = "application/x-www-form-urlencoded"
    } `
    -Body "grant_type=client_credentials%26client_id=service-client%26client_secret=service-secret%26scope=service%20read"

# Test 2.5: Invalid client secret
Test-Endpoint -Name "2.5 OAuth Token - Wrong client secret" `
    -Method "POST" `
    -Url "$BaseUrl/oauth/token" `
    -Headers @{
        "Authorization" = "Basic dGVzdC1jbGllbnQ6d3JvbmdzZWNyZXQ="  # test-client:wrongsecret
        "Content-Type" = "application/x-www-form-urlencoded"
    } `
    -Body "grant_type=client_credentials%26scope=read"

# Test 2.6: Disabled client
Test-Endpoint -Name "2.6 OAuth Token - Disabled client" `
    -Method "POST" `
    -Url "$BaseUrl/oauth/token" `
    -Headers @{
        "Authorization" = "Basic aW5hY3RpdmUtY2xpZW50OmluYWN0aXZlLXNlY3JldA=="  # inactive-client:inactive-secret
        "Content-Type" = "application/x-www-form-urlencoded"
    } `
    -Body "grant_type=client_credentials%26scope=read"

# Test 2.7: Unsupported grant type
Test-Endpoint -Name "2.7 OAuth Token - Unsupported grant type" `
    -Method "POST" `
    -Url "$BaseUrl/oauth/token" `
    -Headers @{
        "Authorization" = "Basic dGVzdC1jbGllbnQ6dGVzdC1zZWNyZXQ="  # test-client:test-secret
        "Content-Type" = "application/x-www-form-urlencoded"
    } `
    -Body "grant_type=password%26scope=read"

# Test 2.8: Invalid scope
Test-Endpoint -Name "2.8 OAuth Token - Invalid scope" `
    -Method "POST" `
    -Url "$BaseUrl/oauth/token" `
    -Headers @{
        "Authorization" = "Basic dGVzdC1jbGllbnQ6dGVzdC1zZWNyZXQ="  # test-client:test-secret
        "Content-Type" = "application/x-www-form-urlencoded"
    } `
    -Body "grant_type=client_credentials%26scope=admin"

# Test 2.9: Missing grant type
Test-Endpoint -Name "2.9 OAuth Token - Missing grant type" `
    -Method "POST" `
    -Url "$BaseUrl/oauth/token" `
    -Headers @{
        "Authorization" = "Basic dGVzdC1jbGllbnQ6dGVzdC1zZWNyZXQ="  # test-client:test-secret
        "Content-Type" = "application/x-www-form-urlencoded"
    } `
    -Body "scope=read"

# 3. OAUTH 2.0 INTROSPECTION TESTS
Write-Host "`n3. OAUTH 2.0 INTROSPECTION TESTS" -ForegroundColor Magenta
Write-Host "================================" -ForegroundColor Magenta
Write-Host ""

# First, get a valid token for introspection tests
Write-Host "Getting a valid token for introspection tests..." -ForegroundColor Gray
$tokenResponse = Invoke-RestMethod -Method POST -Uri "$BaseUrl/oauth/token" `
    -Headers @{
        "Authorization" = "Basic dGVzdC1jbGllbnQ6dGVzdC1zZWNyZXQ="
        "Content-Type" = "application/x-www-form-urlencoded"
    } `
    -Body "grant_type=client_credentials%26scope=read"

$validToken = $tokenResponse.access_token
Write-Host "Token obtained: $($validToken.Substring(0, 20))..." -ForegroundColor Gray
Write-Host ""

# Test 3.1: Valid token introspection
Test-Endpoint -Name "3.1 Introspect - Valid token" `
    -Method "POST" `
    -Url "$BaseUrl/oauth/introspect" `
    -Headers @{
        "Content-Type" = "application/x-www-form-urlencoded"
    } `
    -Body "token=$validToken"

# Test 3.2: Invalid token
Test-Endpoint -Name "3.2 Introspect - Invalid token" `
    -Method "POST" `
    -Url "$BaseUrl/oauth/introspect" `
    -Headers @{
        "Content-Type" = "application/x-www-form-urlencoded"
    } `
    -Body "token=invalid.jwt.token"

# Test 3.3: Malformed token
Test-Endpoint -Name "3.3 Introspect - Malformed token" `
    -Method "POST" `
    -Url "$BaseUrl/oauth/introspect" `
    -Headers @{
        "Content-Type" = "application/x-www-form-urlencoded"
    } `
    -Body "token=not-even-a-jwt"

# Test 3.4: Missing token parameter
Test-Endpoint -Name "3.4 Introspect - Missing token" `
    -Method "POST" `
    -Url "$BaseUrl/oauth/introspect" `
    -Headers @{
        "Content-Type" = "application/x-www-form-urlencoded"
    } `
    -Body ""

# Test 3.5: Expired token (simulate by modifying the token)
$expiredToken = "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJhdWQiOiJodHRwczovL2FwaS5leGFtcGxlLmNvbSIsInN1YiI6InRlc3QtY2xpZW50Iiwic2NvcGUiOiJyZWFkIiwiaXNzIjoiaHR0cHM6Ly9hdXRoLmV4YW1wbGUuY29tIiwiZXhwIjoxNjAwMDAwMDAwLCJ0b2tlbl90eXBlIjoiQmVhcmVyIiwiaWF0IjoxNjAwMDAwMDAwLCJqdGkiOiJleHBpcmVkLXRva2VuIiwiY2xpZW50X2lkIjoidGVzdC1jbGllbnQifQ.invalid"
Test-Endpoint -Name "3.5 Introspect - Expired token" `
    -Method "POST" `
    -Url "$BaseUrl/oauth/introspect" `
    -Headers @{
        "Content-Type" = "application/x-www-form-urlencoded"
    } `
    -Body "token=$expiredToken"

# 4. EDGE CASES AND ERROR SCENARIOS
Write-Host "`n4. EDGE CASES AND ERROR SCENARIOS" -ForegroundColor Magenta
Write-Host "=================================" -ForegroundColor Magenta
Write-Host ""

# Test 4.1: JSON body for OAuth token (after our update)
Test-Endpoint -Name "4.1 OAuth Token - JSON body format" `
    -Method "POST" `
    -Url "$BaseUrl/oauth/token" `
    -Headers @{
        "Content-Type" = "application/json"
    } `
    -Body '{"grant_type":"client_credentials","client_id":"test-client","client_secret":"test-secret","scope":"read"}'

# Test 4.2: Large scope request
Test-Endpoint -Name "4.2 OAuth Token - Multiple scopes request" `
    -Method "POST" `
    -Url "$BaseUrl/oauth/token" `
    -Headers @{
        "Authorization" = "Basic YWRtaW4tY2xpZW50OmFkbWluLXNlY3JldA=="  # admin-client:admin-secret
        "Content-Type" = "application/x-www-form-urlencoded"
    } `
    -Body "grant_type=client_credentials%26scope=read%20write%20admin%20service"

# Test 4.3: Empty scope
Test-Endpoint -Name "4.3 OAuth Token - Empty scope" `
    -Method "POST" `
    -Url "$BaseUrl/oauth/token" `
    -Headers @{
        "Authorization" = "Basic dGVzdC1jbGllbnQ6dGVzdC1zZWNyZXQ="  # test-client:test-secret
        "Content-Type" = "application/x-www-form-urlencoded"
    } `
    -Body "grant_type=client_credentials%26scope="

# Test 4.4: Special characters in credentials
Test-Endpoint -Name "4.4 Basic Auth - Special characters handling" `
    -Method "POST" `
    -Url "$BaseUrl/auth/validate" `
    -Headers @{
        "Authorization" = "Basic dXNlcjpwYXNzQDEyMyE="  # user:pass@123!
        "Content-Type" = "application/json"
    }

Write-Host "`n======================================" -ForegroundColor Cyan
Write-Host "Test Suite Completed" -ForegroundColor Cyan
Write-Host "======================================" -ForegroundColor Cyan

# Summary
Write-Host "`nSummary:" -ForegroundColor Yellow
Write-Host "- Basic Auth endpoint: $BaseUrl/auth/validate" -ForegroundColor Gray
Write-Host "- OAuth Token endpoint: $BaseUrl/oauth/token" -ForegroundColor Gray
Write-Host "- OAuth Introspect endpoint: $BaseUrl/oauth/introspect" -ForegroundColor Gray
Write-Host ""
Write-Host "Available test users:" -ForegroundColor Gray
Write-Host ('  - demo:demo123 (ACTIVE' + ', roles: user' + ', read)') -ForegroundColor Gray
Write-Host ('  - admin:admin123 (ACTIVE' + ', roles: admin' + ', user' + ', read' + ', write)') -ForegroundColor Gray
Write-Host "  - test:test123 (DISABLED)" -ForegroundColor Gray
Write-Host ('  - service:service123 (ACTIVE' + ', roles: service' + ', read' + ', write)') -ForegroundColor Gray
Write-Host ""
Write-Host "Available OAuth clients:" -ForegroundColor Gray
Write-Host ('  - demo-client:demo-secret (scopes: read' + ', write)') -ForegroundColor Gray
Write-Host "  - test-client:test-secret (scopes: read)" -ForegroundColor Gray
Write-Host ('  - admin-client:admin-secret (scopes: read' + ', write' + ', admin)') -ForegroundColor Gray
Write-Host ('  - service-client:service-secret (scopes: service' + ', read' + ', write)') -ForegroundColor Gray
Write-Host "  - inactive-client:inactive-secret (DISABLED)" -ForegroundColor Gray