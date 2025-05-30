#!/bin/bash
set -e

# Default values
ENVIRONMENT=${1:-dev}
REGION=${2:-us-east-1}
API_ENDPOINT=${3}

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Function to print colored output
print_status() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[PASS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

print_error() {
    echo -e "${RED}[FAIL]${NC} $1"
}

print_test() {
    echo -e "${YELLOW}[TEST]${NC} $1"
}

# Test counters
TOTAL_TESTS=0
PASSED_TESTS=0
FAILED_TESTS=0

# Function to run a test
run_test() {
    local test_name="$1"
    local test_command="$2"
    local expected_pattern="$3"
    local test_description="$4"
    
    TOTAL_TESTS=$((TOTAL_TESTS + 1))
    print_test "Test $TOTAL_TESTS: $test_name"
    
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
run_test \
    "Health Check" \
    "curl -s -f -X GET '$API_ENDPOINT/health'" \
    "200" \
    "Verify the health endpoint responds with HTTP 200"

# Test 2: Valid Authentication - Alice
run_test \
    "Valid Authentication (alice)" \
    "curl -s -X POST '$API_ENDPOINT/auth/validate' -H 'Authorization: Basic $(echo -n 'alice:password123' | base64)' -H 'Content-Type: application/json'" \
    '"allowed":true' \
    "Test successful authentication with valid credentials"

# Test 3: Valid Authentication - Charlie  
run_test \
    "Valid Authentication (charlie)" \
    "curl -s -X POST '$API_ENDPOINT/auth/validate' -H 'Authorization: Basic $(echo -n 'charlie:charlie789' | base64)' -H 'Content-Type: application/json'" \
    '"allowed":true' \
    "Test successful authentication with different valid user"

# Test 4: Invalid Password
run_test \
    "Invalid Password" \
    "curl -s -X POST '$API_ENDPOINT/auth/validate' -H 'Authorization: Basic $(echo -n 'alice:wrongpassword' | base64)' -H 'Content-Type: application/json'" \
    '"allowed":false' \
    "Test failed authentication with invalid password"

# Test 5: Non-existent User
run_test \
    "Non-existent User" \
    "curl -s -X POST '$API_ENDPOINT/auth/validate' -H 'Authorization: Basic $(echo -n 'nonexistent:password' | base64)' -H 'Content-Type: application/json'" \
    '"allowed":false' \
    "Test failed authentication with non-existent user"

# Test 6: Missing Authorization Header
run_test \
    "Missing Authorization Header" \
    "curl -s -w '%{http_code}' -X POST '$API_ENDPOINT/auth/validate' -H 'Content-Type: application/json'" \
    "400" \
    "Test request without Authorization header returns 400"

# Test 7: Invalid Authorization Format (Bearer instead of Basic)
run_test \
    "Invalid Authorization Format" \
    "curl -s -w '%{http_code}' -X POST '$API_ENDPOINT/auth/validate' -H 'Authorization: Bearer invalidtoken' -H 'Content-Type: application/json'" \
    "400" \
    "Test request with Bearer token instead of Basic auth returns 400"

# Test 8: Malformed Base64
run_test \
    "Malformed Base64" \
    "curl -s -w '%{http_code}' -X POST '$API_ENDPOINT/auth/validate' -H 'Authorization: Basic invalidbase64!!!' -H 'Content-Type: application/json'" \
    "400" \
    "Test request with malformed Base64 encoding returns 400"

# Test 9: Invalid HTTP Method
run_test \
    "Invalid HTTP Method" \
    "curl -s -w '%{http_code}' -X GET '$API_ENDPOINT/auth/validate' -H 'Authorization: Basic $(echo -n 'alice:password123' | base64)'" \
    "405" \
    "Test GET request to auth endpoint returns 405 Method Not Allowed"

# Test 10: CORS Preflight Request
run_test \
    "CORS Preflight" \
    "curl -s -w '%{http_code}' -X OPTIONS '$API_ENDPOINT/auth/validate' -H 'Origin: https://example.com' -H 'Access-Control-Request-Method: POST'" \
    "200" \
    "Test CORS preflight request returns 200"

# Test 11: Response Time Check
print_test "Test $((TOTAL_TESTS + 1)): Response Time Check"
TOTAL_TESTS=$((TOTAL_TESTS + 1))

start_time=$(date +%s%N)
response=$(curl -s -X POST "$API_ENDPOINT/auth/validate" \
    -H "Authorization: Basic $(echo -n 'alice:password123' | base64)" \
    -H "Content-Type: application/json")
end_time=$(date +%s%N)

response_time_ms=$(( (end_time - start_time) / 1000000 ))
echo "  Response time: ${response_time_ms}ms"

if [[ $response_time_ms -lt 5000 ]]; then  # Less than 5 seconds
    print_success "Response Time Check (${response_time_ms}ms < 5000ms)"
    PASSED_TESTS=$((PASSED_TESTS + 1))
else
    print_error "Response Time Check (${response_time_ms}ms >= 5000ms)"
    FAILED_TESTS=$((FAILED_TESTS + 1))
fi

# Test 12: JSON Response Structure
run_test \
    "JSON Response Structure" \
    "curl -s -X POST '$API_ENDPOINT/auth/validate' -H 'Authorization: Basic $(echo -n 'alice:password123' | base64)' -H 'Content-Type: application/json' | jq -e '.allowed != null and .message != null and .timestamp != null'" \
    "" \
    "Verify response contains required JSON fields"

# Test 13: Security Headers Check
print_test "Test $((TOTAL_TESTS + 1)): Security Headers Check"
TOTAL_TESTS=$((TOTAL_TESTS + 1))

headers=$(curl -s -I -X POST "$API_ENDPOINT/auth/validate" \
    -H "Authorization: Basic $(echo -n 'alice:password123' | base64)" \
    -H "Content-Type: application/json")

if echo "$headers" | grep -qi "cache-control"; then
    print_success "Security Headers Check (Cache-Control header present)"
    PASSED_TESTS=$((PASSED_TESTS + 1))
else
    print_warning "Security Headers Check (No Cache-Control header found)"
    print_status "Headers received:"
    echo "$headers"
    FAILED_TESTS=$((FAILED_TESTS + 1))
fi

# Test 14: Load Test (Light)
print_test "Test $((TOTAL_TESTS + 1)): Light Load Test (10 concurrent requests)"
TOTAL_TESTS=$((TOTAL_TESTS + 1))

# Create a temporary script for parallel requests
temp_script=$(mktemp)
cat > "$temp_script" << 'EOF'
#!/bin/bash
curl -s -X POST "$1/auth/validate" \
    -H "Authorization: Basic $(echo -n 'alice:password123' | base64)" \
    -H "Content-Type: application/json" \
    -w "%{http_code}\n" -o /dev/null
EOF
chmod +x "$temp_script"

# Run 10 parallel requests
successful_requests=0
for i in {1..10}; do
    if "$temp_script" "$API_ENDPOINT" | grep -q "200"; then
        successful_requests=$((successful_requests + 1))
    fi &
done
wait

rm "$temp_script"

if [[ $successful_requests -ge 8 ]]; then  # At least 80% success rate
    print_success "Light Load Test ($successful_requests/10 requests successful)"
    PASSED_TESTS=$((PASSED_TESTS + 1))
else
    print_error "Light Load Test ($successful_requests/10 requests successful)"
    FAILED_TESTS=$((FAILED_TESTS + 1))
fi

# Print summary
echo ""
echo "=========================================="
echo "           SMOKE TEST SUMMARY"
echo "=========================================="
echo "Environment: $ENVIRONMENT"
echo "API Endpoint: $API_ENDPOINT"
echo "Total Tests: $TOTAL_TESTS"
echo "Passed: $PASSED_TESTS"
echo "Failed: $FAILED_TESTS"
echo "Success Rate: $(( PASSED_TESTS * 100 / TOTAL_TESTS ))%"
echo "=========================================="

if [[ $FAILED_TESTS -eq 0 ]]; then
    print_success "All smoke tests passed! ✅"
    exit 0
else
    print_error "Some smoke tests failed! ❌"
    exit 1
fi 