#!/bin/bash
set -e

# FIXED PARAMETERS
ENVIRONMENT=${1:-dev}
REGION=${2:-us-east-1}
STACK_NAME="java-auth-server-${ENVIRONMENT}"

print_status() {
    echo -e "\033[0;34m[INFO]\033[0m $1"
}

print_success() {
    echo -e "\033[0;32m[SUCCESS]\033[0m $1"
}

print_error() {
    echo -e "\033[0;31m[ERROR]\033[0m $1"
}

# Get API endpoint from CloudFormation
print_status "Getting API endpoint from CloudFormation..."
API_ENDPOINT=$(aws cloudformation describe-stacks \
    --stack-name "${STACK_NAME}" \
    --query 'Stacks[0].Outputs[?OutputKey==`ApiEndpoint`].OutputValue' \
    --output text \
    --region "${REGION}")

if [[ -z "$API_ENDPOINT" ]] || [[ "$API_ENDPOINT" == "None" ]]; then
    print_error "Could not retrieve API endpoint from stack: ${STACK_NAME}"
    exit 1
fi

print_status "Running smoke tests against: $API_ENDPOINT"
print_status "Environment: $ENVIRONMENT"
print_status "Region: $REGION"
echo ""

# =================
# BASIC AUTH TESTS
# =================

# Test 1: Valid basic authentication
print_status "Test 1: Valid basic authentication (testuser)..."
RESPONSE=$(curl -s -X POST "${API_ENDPOINT}/auth/validate" \
    -H "Authorization: Basic $(echo -n 'testuser:testpass123' | base64)" \
    -H "Content-Type: application/json")

if echo "$RESPONSE" | grep -q '"allowed":true'; then
    print_success "✓ Valid basic authentication test passed"
else
    print_error "✗ Valid basic authentication test failed. Response: $RESPONSE"
    exit 1
fi

# Test 2: Invalid basic authentication
print_status "Test 2: Invalid basic authentication..."
RESPONSE=$(curl -s -X POST "${API_ENDPOINT}/auth/validate" \
    -H "Authorization: Basic $(echo -n 'invalid:invalid' | base64)" \
    -H "Content-Type: application/json")

if echo "$RESPONSE" | grep -q '"allowed":false'; then
    print_success "✓ Invalid basic authentication test passed"
else
    print_error "✗ Invalid basic authentication test failed. Response: $RESPONSE"
    exit 1
fi

# =================
# OAUTH2 TESTS
# =================

# Test 3: OAuth2 Token Generation
print_status "Test 3: OAuth2 token generation..."
TOKEN_RESPONSE=$(curl -s -X POST "${API_ENDPOINT}/oauth/token" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    -d "grant_type=client_credentials&client_id=test-client-1&client_secret=test-client-1-secret&scope=read")

if echo "$TOKEN_RESPONSE" | grep -q '"access_token"'; then
    ACCESS_TOKEN=$(echo "$TOKEN_RESPONSE" | jq -r '.access_token')
    print_success "✓ OAuth2 token generation test passed"
else
    print_error "✗ OAuth2 token generation test failed. Response: $TOKEN_RESPONSE"
    exit 1
fi

# Test 4: OAuth2 Token Introspection (valid token)
print_status "Test 4: OAuth2 token introspection (valid token)..."
INTROSPECT_RESPONSE=$(curl -s -X POST "${API_ENDPOINT}/oauth/introspect" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    -d "token=${ACCESS_TOKEN}")

if echo "$INTROSPECT_RESPONSE" | grep -q '"active":true'; then
    print_success "✓ OAuth2 token introspection (valid) test passed"
else
    print_error "✗ OAuth2 token introspection (valid) test failed. Response: $INTROSPECT_RESPONSE"
    exit 1
fi

# Test 5: OAuth2 Token Introspection (invalid token)
print_status "Test 5: OAuth2 token introspection (invalid token)..."
INTROSPECT_INVALID_RESPONSE=$(curl -s -X POST "${API_ENDPOINT}/oauth/introspect" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    -d "token=invalid.jwt.token")

if echo "$INTROSPECT_INVALID_RESPONSE" | grep -q '"active":false'; then
    print_success "✓ OAuth2 token introspection (invalid) test passed"
else
    print_error "✗ OAuth2 token introspection (invalid) test failed. Response: $INTROSPECT_INVALID_RESPONSE"
    exit 1
fi

# Test 6: OAuth2 Invalid Client Credentials
print_status "Test 6: OAuth2 invalid client credentials..."
INVALID_CLIENT_RESPONSE=$(curl -s -X POST "${API_ENDPOINT}/oauth/token" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    -d "grant_type=client_credentials&client_id=test-client-1&client_secret=wrong-secret")

if echo "$INVALID_CLIENT_RESPONSE" | grep -q '"error":"invalid_client"'; then
    print_success "✓ OAuth2 invalid client credentials test passed"
else
    print_error "✗ OAuth2 invalid client credentials test failed. Response: $INVALID_CLIENT_RESPONSE"
    exit 1
fi

echo ""
print_success "All smoke tests passed! ✅"
echo ""
print_status "=== SMOKE TEST SUMMARY ==="
echo "✓ Basic Authentication: Valid credentials"
echo "✓ Basic Authentication: Invalid credentials"  
echo "✓ OAuth2: Token generation"
echo "✓ OAuth2: Token introspection (valid)"
echo "✓ OAuth2: Token introspection (invalid)"
echo "✓ OAuth2: Invalid client credentials"
print_success "Both Basic Auth and OAuth2 endpoints working correctly!" 