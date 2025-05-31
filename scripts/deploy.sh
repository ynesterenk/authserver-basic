#!/bin/bash
set -e

# Default values
ENVIRONMENT=${1:-dev}
REGION=${2:-us-east-1}
ARTIFACT_BUCKET_PREFIX=${3:-auth-server-artifacts}
ACCOUNT_ID=${4:-545009823602}

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
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Validate inputs
if [[ ! "$ENVIRONMENT" =~ ^(dev|staging|prod)$ ]]; then
    print_error "Invalid environment: $ENVIRONMENT. Must be dev, staging, or prod."
    exit 1
fi

# Set up variables
STACK_NAME="java-auth-server-${ENVIRONMENT}"
ARTIFACT_BUCKET="${ARTIFACT_BUCKET_PREFIX}-${ACCOUNT_ID}-${REGION}"
TIMESTAMP=$(date +%Y%m%d-%H%M%S)
ARTIFACT_KEY="auth-server-${ENVIRONMENT}-${TIMESTAMP}.zip"

print_status "Starting deployment for environment: $ENVIRONMENT"
print_status "Region: $REGION"
print_status "Account ID: $ACCOUNT_ID"
print_status "Stack Name: $STACK_NAME"
print_status "Artifact Bucket: $ARTIFACT_BUCKET"

# Check if AWS CLI is configured
if ! aws sts get-caller-identity > /dev/null 2>&1; then
    print_error "AWS CLI is not configured or credentials are invalid"
    exit 1
fi

# Verify we're in the correct directory
if [[ ! -f "pom.xml" ]] || [[ ! -d "infrastructure" ]]; then
    print_error "Please run this script from the project root directory"
    exit 1
fi

# Step 1: Build and package the Lambda function
print_status "Building Lambda function..."

# Check if the JAR already exists (e.g., from CI/CD pipeline)
if [[ -f "target/auth-server-lambda.jar" ]]; then
    print_success "Lambda JAR already exists, skipping build"
else
    print_status "Building Lambda function..."
    mvn clean package -DskipTests -q
    
    # Check if the JAR was created
    if [[ ! -f "target/auth-server-lambda.jar" ]]; then
        print_error "Lambda JAR file not found. Build may have failed."
        exit 1
    fi
    
    print_success "Lambda function built successfully"
fi

# Step 2: Create S3 bucket if it doesn't exist
print_status "Checking artifact bucket..."
if ! aws s3 ls "s3://${ARTIFACT_BUCKET}" > /dev/null 2>&1; then
    print_status "Creating artifact bucket: $ARTIFACT_BUCKET"
    if [[ "$REGION" == "us-east-1" ]]; then
        aws s3 mb "s3://${ARTIFACT_BUCKET}"
    else
        aws s3 mb "s3://${ARTIFACT_BUCKET}" --region "${REGION}"
    fi
    
    # Enable versioning
    aws s3api put-bucket-versioning \
        --bucket "${ARTIFACT_BUCKET}" \
        --versioning-configuration Status=Enabled
    
    # Block public access
    aws s3api put-public-access-block \
        --bucket "${ARTIFACT_BUCKET}" \
        --public-access-block-configuration \
        "BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true"
    
    print_success "Artifact bucket created and configured"
else
    print_success "Artifact bucket already exists"
fi

# Step 3: Upload Lambda deployment package
print_status "Uploading Lambda deployment package..."
aws s3 cp "target/auth-server-lambda.jar" "s3://${ARTIFACT_BUCKET}/${ARTIFACT_KEY}" \
    --metadata "environment=${ENVIRONMENT},timestamp=${TIMESTAMP}"

print_success "Lambda package uploaded as: ${ARTIFACT_KEY}"

# Step 4: Upload nested CloudFormation templates
print_status "Uploading nested CloudFormation templates..."
aws s3 sync "infrastructure/nested-stacks/" "s3://${ARTIFACT_BUCKET}/nested-stacks/" \
    --delete --exclude "*.md"

print_success "Nested templates uploaded"

# Step 5: Validate CloudFormation template
print_status "Validating CloudFormation template..."
aws cloudformation validate-template \
    --template-body file://infrastructure/main-template.yaml \
    --region "${REGION}" > /dev/null

print_success "CloudFormation template validation passed"

# Step 6: Deploy the stack
print_status "Deploying CloudFormation stack..."

# Read parameters from environment-specific file
PARAMS_FILE="infrastructure/parameters/${ENVIRONMENT}-params.json"
if [[ ! -f "$PARAMS_FILE" ]]; then
    print_error "Parameter file not found: $PARAMS_FILE"
    exit 1
fi

# Add dynamic parameters
TEMP_PARAMS=$(mktemp)
jq --arg bucket "$ARTIFACT_BUCKET" --arg key "$ARTIFACT_KEY" \
   '. + [{"ParameterKey": "ArtifactBucket", "ParameterValue": $bucket}, {"ParameterKey": "ArtifactKey", "ParameterValue": $key}]' \
   "$PARAMS_FILE" > "$TEMP_PARAMS"

# Deploy using CloudFormation
aws cloudformation deploy \
    --template-file "infrastructure/main-template.yaml" \
    --stack-name "${STACK_NAME}" \
    --parameter-overrides file://"${TEMP_PARAMS}" \
    --capabilities CAPABILITY_NAMED_IAM \
    --region "${REGION}" \
    --tags \
        Environment="${ENVIRONMENT}" \
        Project="JavaAuthServer" \
        DeployedBy="$(whoami)" \
        DeployedAt="${TIMESTAMP}"

# Clean up temp file
rm "$TEMP_PARAMS"

# Step 7: Get stack outputs
print_status "Retrieving stack outputs..."
OUTPUTS=$(aws cloudformation describe-stacks \
    --stack-name "${STACK_NAME}" \
    --region "${REGION}" \
    --query 'Stacks[0].Outputs')

if [[ "$OUTPUTS" != "null" ]]; then
    API_ENDPOINT=$(echo "$OUTPUTS" | jq -r '.[] | select(.OutputKey=="ApiEndpoint") | .OutputValue')
    DASHBOARD_URL=$(echo "$OUTPUTS" | jq -r '.[] | select(.OutputKey=="DashboardUrl") | .OutputValue')
    
    print_success "Deployment completed successfully!"
    echo ""
    echo "=== DEPLOYMENT RESULTS ==="
    echo "Environment: $ENVIRONMENT"
    echo "Stack Name: $STACK_NAME"
    echo "API Endpoint: $API_ENDPOINT"
    echo "CloudWatch Dashboard: $DASHBOARD_URL"
    echo "Lambda Package: s3://${ARTIFACT_BUCKET}/${ARTIFACT_KEY}"
    echo ""
    
    # Test command
    echo "=== TEST COMMAND ==="
    echo "curl -X POST $API_ENDPOINT/auth/validate \\"
    echo "  -H \"Authorization: Basic \$(echo -n 'alice:password123' | base64)\" \\"
    echo "  -H \"Content-Type: application/json\""
    echo ""
    
    # Save deployment info
    DEPLOYMENT_INFO=$(cat <<EOF
{
  "environment": "$ENVIRONMENT",
  "timestamp": "$TIMESTAMP",
  "stackName": "$STACK_NAME",
  "apiEndpoint": "$API_ENDPOINT",
  "dashboardUrl": "$DASHBOARD_URL",
  "artifactLocation": "s3://${ARTIFACT_BUCKET}/${ARTIFACT_KEY}",
  "region": "$REGION",
  "accountId": "$ACCOUNT_ID"
}
EOF
)
    
    echo "$DEPLOYMENT_INFO" > "deployment-info-${ENVIRONMENT}.json"
    print_success "Deployment info saved to: deployment-info-${ENVIRONMENT}.json"
    
else
    print_warning "Could not retrieve stack outputs"
fi

print_success "Script completed successfully!" 