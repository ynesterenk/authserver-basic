#!/usr/bin/env pwsh
# Test Script for Deployed Azure Functions

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "Testing Deployed Azure Functions" -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host ""

$AzureUrl = "https://func-authserver-dev-06211401.azurewebsites.net/api"

Write-Host "Target URL: $AzureUrl" -ForegroundColor Yellow
Write-Host ""

# Test 1: Basic Auth - Valid credentials
Write-Host "Test 1: Basic Auth - Valid credentials (demo:demo123)" -ForegroundColor Green
$basicAuthResponse = curl -X POST "$AzureUrl/auth/validate" `
    -H "Authorization: Basic ZGVtbzpkZW1vMTIz" `
    -H "Content-Type: application/json" `
    -s

Write-Host "Response: $basicAuthResponse" -ForegroundColor Gray
Write-Host ""

# Test 2: OAuth Token - Using JSON format (which we know works)
Write-Host "Test 2: OAuth Token - Client Credentials Flow (JSON)" -ForegroundColor Green
$tokenResponse = curl -X POST "$AzureUrl/oauth/token" `
    -H "Content-Type: application/json" `
    -d '{"grant_type":"client_credentials","client_id":"test-client","client_secret":"test-secret","scope":"read"}' `
    -s

Write-Host "Response: $tokenResponse" -ForegroundColor Gray
Write-Host ""

# Test 3: OAuth Token - Using form-encoded format
Write-Host "Test 3: OAuth Token - Client Credentials Flow (Form-encoded)" -ForegroundColor Green
Invoke-RestMethod -Method POST -Uri "$AzureUrl/oauth/token" `
    -Headers @{
        "Authorization" = "Basic dGVzdC1jbGllbnQ6dGVzdC1zZWNyZXQ="  # test-client:test-secret
    } `
    -Body @{
        grant_type = "client_credentials"
        scope = "read"
    } `
    -ContentType "application/x-www-form-urlencoded" | ConvertTo-Json -Depth 10

Write-Host ""

# Test 4: OAuth Introspect
Write-Host "Test 4: OAuth Introspect - Check token validity" -ForegroundColor Green
Write-Host "First, getting a token..." -ForegroundColor Gray

$tokenObj = Invoke-RestMethod -Method POST -Uri "$AzureUrl/oauth/token" `
    -Headers @{
        "Content-Type" = "application/json"
    } `
    -Body (@{
        grant_type = "client_credentials"
        client_id = "test-client"
        client_secret = "test-secret"
        scope = "read"
    } | ConvertTo-Json)

if ($tokenObj.access_token) {
    Write-Host "Token obtained, now introspecting..." -ForegroundColor Gray
    
    $introspectResponse = Invoke-RestMethod -Method POST -Uri "$AzureUrl/oauth/introspect" `
        -Body @{
            token = $tokenObj.access_token
        } `
        -ContentType "application/x-www-form-urlencoded"
    
    $introspectResponse | ConvertTo-Json -Depth 10 | Write-Host
} else {
    Write-Host "Failed to obtain token" -ForegroundColor Red
}

Write-Host ""
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "All tests completed!" -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan 