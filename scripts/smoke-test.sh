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
    
    if [[ -n "$test_description" ]]; then
        echo "  Description: $test_description"
    fi
    
    # Run the test command and capture output
    local output
    local exit_code
    
    set +e
    output=$(eval "$test_command" 2>&1)
    exit_code=$?
    set -e
    
    # Check if test passed
    if [[ $exit_code -eq 0 ]] && [[ -n "$expected_pattern" ]] && echo "$output" | grep -q "$expected_pattern"; then
        print_success "$test_name"
        PASSED_TESTS=$((PASSED_TESTS + 1))
        return 0
    elif [[ $exit_code -eq 0 ]] && [[ -z "$expected_pattern" ]]; then
        print_success "$test_name"
        PASSED_TESTS=$((PASSED_TESTS + 1))
        return 0
    else
        print_error "$test_name"
        echo "  Expected pattern: $expected_pattern"
        echo "  Actual output: $output"
        echo "  Exit code: $exit_code"
        FAILED_TESTS=$((FAILED_TESTS + 1))
        return 1
    fi
}

# Get API endpoint if not provided
if [[ -z "$API_ENDPOINT" ]]; then
    print_status "Retrieving API endpoint from CloudFormation..."
    STACK_NAME="java-auth-server-${ENVIRONMENT}"
    
    API_ENDPOINT=$(aws cloudformation describe-stacks \
        --stack-name "${STACK_NAME}" \
        --region "${REGION}" \
        --query 'Stacks[0].Outputs[?OutputKey==`ApiEndpoint`].OutputValue' \
        --output text 2>/dev/null || echo "")
    
    if [[ -z "$API_ENDPOINT" ]] || [[ "$API_ENDPOINT" == "None" ]]; then
        print_error "Could not retrieve API endpoint from CloudFormation stack: $STACK_NAME"
        print_status "Please provide the API endpoint as the third parameter"
        exit 1
    fi
fi

print_status "Running smoke tests against: $API_ENDPOINT"
print_status "Environment: $ENVIRONMENT"
print_status "Region: $REGION"
echo ""

# Test 1: Health Check
print_status "Test 1: Health endpoint..."
if curl -f -s "${API_ENDPOINT}/health" > /dev/null; then
    print_success "✓ Health check passed"
else
    print_error "✗ Health check failed"
    exit 1
fi

# Test 2: Valid authentication - FIXED CREDENTIALS
print_status "Test 2: Valid authentication (testuser)..."
RESPONSE=$(curl -s -X POST "${API_ENDPOINT}/auth/validate" \
    -H "Authorization: Basic $(echo -n 'testuser:testpass' | base64)" \
    -H "Content-Type: application/json")

if echo "$RESPONSE" | grep -q '"allowed":true'; then
    print_success "✓ Valid authentication test passed"
else
    print_error "✗ Valid authentication test failed. Response: $RESPONSE"
    exit 1
fi

# Test 3: Invalid authentication
print_status "Test 3: Invalid authentication..."
RESPONSE=$(curl -s -X POST "${API_ENDPOINT}/auth/validate" \
    -H "Authorization: Basic $(echo -n 'invalid:invalid' | base64)" \
    -H "Content-Type: application/json")

if echo "$RESPONSE" | grep -q '"allowed":false'; then
    print_success "✓ Invalid authentication test passed"
else
    print_error "✗ Invalid authentication test failed. Response: $RESPONSE"
    exit 1
fi

print_success "All smoke tests passed! ✅" 