#!/bin/bash
# Azure Functions Auth Server - Curl Commands Reference
# Run these commands to test all endpoints

BASE_URL="http://localhost:7071/api"

echo "======================================"
echo "Azure Functions Auth Server Test Guide"
echo "======================================"
echo ""

# Color codes
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${YELLOW}1. BASIC AUTHENTICATION TESTS${NC}"
echo "============================="
echo ""

echo "# 1.1 Valid Basic Auth (demo user)"
echo "curl -X POST $BASE_URL/auth/validate \\"
echo "  -H \"Authorization: Basic ZGVtbzpkZW1vMTIz\" \\"
echo "  -H \"Content-Type: application/json\""
echo ""

echo "# 1.2 Valid Basic Auth (admin user)"
echo "curl -X POST $BASE_URL/auth/validate \\"
echo "  -H \"Authorization: Basic YWRtaW46YWRtaW4xMjM=\" \\"
echo "  -H \"Content-Type: application/json\""
echo ""

echo "# 1.3 Basic Auth with disabled user"
echo "curl -X POST $BASE_URL/auth/validate \\"
echo "  -H \"Authorization: Basic dGVzdDp0ZXN0MTIz\" \\"
echo "  -H \"Content-Type: application/json\""
echo ""

echo "# 1.4 Basic Auth with wrong password"
echo "curl -X POST $BASE_URL/auth/validate \\"
echo "  -H \"Authorization: Basic ZGVtbzp3cm9uZ3Bhc3M=\" \\"
echo "  -H \"Content-Type: application/json\""
echo ""

echo "# 1.5 Basic Auth without Authorization header"
echo "curl -X POST $BASE_URL/auth/validate \\"
echo "  -H \"Content-Type: application/json\""
echo ""

echo -e "${YELLOW}2. OAUTH 2.0 TOKEN ENDPOINT TESTS${NC}"
echo "================================="
echo ""

echo "# 2.1 OAuth Token - Valid client (test-client)"
echo "curl -X POST $BASE_URL/oauth/token \\"
echo "  -H \"Authorization: Basic dGVzdC1jbGllbnQ6dGVzdC1zZWNyZXQ=\" \\"
echo "  -H \"Content-Type: application/x-www-form-urlencoded\" \\"
echo "  -d \"grant_type=client_credentials&scope=read\""
echo ""

echo "# 2.2 OAuth Token - Multiple scopes (demo-client)"
echo "curl -X POST $BASE_URL/oauth/token \\"
echo "  -H \"Authorization: Basic ZGVtby1jbGllbnQ6ZGVtby1zZWNyZXQ=\" \\"
echo "  -H \"Content-Type: application/x-www-form-urlencoded\" \\"
echo "  -d \"grant_type=client_credentials&scope=read write\""
echo ""

echo "# 2.3 OAuth Token - Admin client"
echo "curl -X POST $BASE_URL/oauth/token \\"
echo "  -H \"Authorization: Basic YWRtaW4tY2xpZW50OmFkbWluLXNlY3JldA==\" \\"
echo "  -H \"Content-Type: application/x-www-form-urlencoded\" \\"
echo "  -d \"grant_type=client_credentials&scope=read write admin\""
echo ""

echo "# 2.4 OAuth Token - Credentials in body"
echo "curl -X POST $BASE_URL/oauth/token \\"
echo "  -H \"Content-Type: application/x-www-form-urlencoded\" \\"
echo "  -d \"grant_type=client_credentials&client_id=service-client&client_secret=service-secret&scope=service read\""
echo ""

echo "# 2.5 OAuth Token - Wrong client secret"
echo "curl -X POST $BASE_URL/oauth/token \\"
echo "  -H \"Authorization: Basic dGVzdC1jbGllbnQ6d3JvbmdzZWNyZXQ=\" \\"
echo "  -H \"Content-Type: application/x-www-form-urlencoded\" \\"
echo "  -d \"grant_type=client_credentials&scope=read\""
echo ""

echo "# 2.6 OAuth Token - Disabled client"
echo "curl -X POST $BASE_URL/oauth/token \\"
echo "  -H \"Authorization: Basic aW5hY3RpdmUtY2xpZW50OmluYWN0aXZlLXNlY3JldA==\" \\"
echo "  -H \"Content-Type: application/x-www-form-urlencoded\" \\"
echo "  -d \"grant_type=client_credentials&scope=read\""
echo ""

echo "# 2.7 OAuth Token - JSON body format (after our update)"
echo "curl -X POST $BASE_URL/oauth/token \\"
echo "  -H \"Content-Type: application/json\" \\"
echo "  -d '{\"grant_type\":\"client_credentials\",\"client_id\":\"test-client\",\"client_secret\":\"test-secret\",\"scope\":\"read\"}'"
echo ""

echo -e "${YELLOW}3. OAUTH 2.0 INTROSPECTION TESTS${NC}"
echo "================================"
echo ""

echo "# First, get a token for testing:"
echo "TOKEN=\$(curl -s -X POST $BASE_URL/oauth/token \\"
echo "  -H \"Authorization: Basic dGVzdC1jbGllbnQ6dGVzdC1zZWNyZXQ=\" \\"
echo "  -H \"Content-Type: application/x-www-form-urlencoded\" \\"
echo "  -d \"grant_type=client_credentials&scope=read\" | jq -r '.access_token')"
echo ""

echo "# 3.1 Introspect - Valid token"
echo "curl -X POST $BASE_URL/oauth/introspect \\"
echo "  -H \"Content-Type: application/x-www-form-urlencoded\" \\"
echo "  -d \"token=\$TOKEN\""
echo ""

echo "# 3.2 Introspect - Invalid token"
echo "curl -X POST $BASE_URL/oauth/introspect \\"
echo "  -H \"Content-Type: application/x-www-form-urlencoded\" \\"
echo "  -d \"token=invalid.jwt.token\""
echo ""

echo "# 3.3 Introspect - Missing token"
echo "curl -X POST $BASE_URL/oauth/introspect \\"
echo "  -H \"Content-Type: application/x-www-form-urlencoded\" \\"
echo "  -d \"\""
echo ""

echo -e "${YELLOW}REFERENCE: BASE64 ENCODED CREDENTIALS${NC}"
echo "====================================="
echo ""
echo -e "${GREEN}Basic Auth Users:${NC}"
echo "demo:demo123         -> Basic ZGVtbzpkZW1vMTIz"
echo "admin:admin123       -> Basic YWRtaW46YWRtaW4xMjM="
echo "test:test123         -> Basic dGVzdDp0ZXN0MTIz"
echo "service:service123   -> Basic c2VydmljZTpzZXJ2aWNlMTIz"
echo ""
echo -e "${GREEN}OAuth Clients:${NC}"
echo "demo-client:demo-secret         -> Basic ZGVtby1jbGllbnQ6ZGVtby1zZWNyZXQ="
echo "test-client:test-secret         -> Basic dGVzdC1jbGllbnQ6dGVzdC1zZWNyZXQ="
echo "admin-client:admin-secret       -> Basic YWRtaW4tY2xpZW50OmFkbWluLXNlY3JldA=="
echo "service-client:service-secret   -> Basic c2VydmljZS1jbGllbnQ6c2VydmljZS1zZWNyZXQ="
echo "inactive-client:inactive-secret -> Basic aW5hY3RpdmUtY2xpZW50OmluYWN0aXZlLXNlY3JldA=="
echo ""

echo -e "${YELLOW}QUICK TIPS:${NC}"
echo "============"
echo "1. Use jq for pretty JSON output: curl ... | jq"
echo "2. Save token to variable: TOKEN=\$(curl ... | jq -r '.access_token')"
echo "3. Use -v flag for verbose output to see headers"
echo "4. Use -w '\\n' to add newline after response"
echo ""

echo -e "${YELLOW}EXPECTED RESPONSES:${NC}"
echo "=================="
echo "• Successful auth: HTTP 200 with JSON response"
echo "• Failed auth: HTTP 200 with {\"authenticated\": false}"
echo "• OAuth errors: HTTP 200 with {\"error\": \"...\", \"error_description\": \"...\"}"
echo "• Server errors: HTTP 400/500 with error details" 