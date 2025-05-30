param(
    [Parameter(Position=0)]
    [ValidateSet("dev", "staging", "prod")]
    [string]$Environment = "dev",
    
    [Parameter(Position=1)]
    [string]$Region = "us-east-1",
    
    [Parameter(Position=2)]
    [string]$ArtifactBucketPrefix = "auth-server-artifacts",
    
    [Parameter(Position=3)]
    [string]$AccountId = "545009823602"
)

# Colors for output
$colors = @{
    Info = "Cyan"
    Success = "Green"
    Warning = "Yellow"
    Error = "Red"
}

function Write-Status {
    param($Message)
    Write-Host "[INFO] $Message" -ForegroundColor $colors.Info
}

function Write-Success {
    param($Message)
    Write-Host "[SUCCESS] $Message" -ForegroundColor $colors.Success
}

function Write-Warning {
    param($Message)
    Write-Host "[WARNING] $Message" -ForegroundColor $colors.Warning
}

function Write-Error {
    param($Message)
    Write-Host "[ERROR] $Message" -ForegroundColor $colors.Error
}

# Check if AWS CLI is installed
try {
    $awsVersion = aws --version 2>$null
    if (-not $awsVersion) {
        throw "AWS CLI not found"
    }
} catch {
    Write-Error "AWS CLI is not installed or not in PATH"
    Write-Status "Please install AWS CLI from: https://aws.amazon.com/cli/"
    Write-Status "Or run: winget install Amazon.AWSCLI"
    exit 1
}

# Set up variables
$StackName = "java-auth-server-$Environment"
$ArtifactBucket = "$ArtifactBucketPrefix-$AccountId-$Region"
$Timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$ArtifactKey = "auth-server-$Environment-$Timestamp.zip"

Write-Status "Starting deployment for environment: $Environment"
Write-Status "Region: $Region"
Write-Status "Account ID: $AccountId"
Write-Status "Stack Name: $StackName"
Write-Status "Artifact Bucket: $ArtifactBucket"

# Check if AWS CLI is configured
try {
    $callerIdentity = aws sts get-caller-identity --query 'Account' --output text 2>$null
    if (-not $callerIdentity) {
        throw "No credentials"
    }
    Write-Status "AWS Account: $callerIdentity"
} catch {
    Write-Error "AWS CLI is not configured or credentials are invalid"
    Write-Status "Please run: aws configure"
    exit 1
}

# Verify we're in the correct directory
if (-not (Test-Path "pom.xml") -or -not (Test-Path "infrastructure")) {
    Write-Error "Please run this script from the project root directory"
    exit 1
}

# Step 1: Build and package the Lambda function
Write-Status "Building Lambda function..."
try {
    mvn clean package -DskipTests -q
    if ($LASTEXITCODE -ne 0) {
        throw "Maven build failed"
    }
} catch {
    Write-Error "Failed to build Lambda function: $_"
    exit 1
}

# Check if the JAR was created
if (-not (Test-Path "target/auth-server-lambda.jar")) {
    Write-Error "Lambda JAR file not found. Build may have failed."
    exit 1
}

Write-Success "Lambda function built successfully"

# Step 2: Create S3 bucket if it doesn't exist
Write-Status "Checking artifact bucket..."
try {
    aws s3 ls "s3://$ArtifactBucket" 2>$null | Out-Null
    if ($LASTEXITCODE -ne 0) {
        Write-Status "Creating artifact bucket: $ArtifactBucket"
        
        if ($Region -eq "us-east-1") {
            aws s3 mb "s3://$ArtifactBucket"
        } else {
            aws s3 mb "s3://$ArtifactBucket" --region $Region
        }
        
        if ($LASTEXITCODE -ne 0) {
            throw "Failed to create bucket"
        }
        
        # Enable versioning
        aws s3api put-bucket-versioning --bucket $ArtifactBucket --versioning-configuration Status=Enabled
        
        # Block public access
        aws s3api put-public-access-block --bucket $ArtifactBucket --public-access-block-configuration "BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true"
        
        Write-Success "Artifact bucket created and configured"
    } else {
        Write-Success "Artifact bucket already exists"
    }
} catch {
    Write-Error "Failed to create or access S3 bucket: $_"
    exit 1
}

# Step 3: Upload Lambda deployment package
Write-Status "Uploading Lambda deployment package..."
try {
    aws s3 cp "target/auth-server-lambda.jar" "s3://$ArtifactBucket/$ArtifactKey" --metadata "environment=$Environment,timestamp=$Timestamp"
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to upload Lambda package"
    }
} catch {
    Write-Error "Failed to upload Lambda package: $_"
    exit 1
}

Write-Success "Lambda package uploaded as: $ArtifactKey"

# Step 4: Upload nested CloudFormation templates
Write-Status "Uploading nested CloudFormation templates..."
try {
    aws s3 sync "infrastructure/nested-stacks/" "s3://$ArtifactBucket/nested-stacks/" --delete --exclude "*.md"
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to upload nested templates"
    }
} catch {
    Write-Error "Failed to upload nested templates: $_"
    exit 1
}

Write-Success "Nested templates uploaded"

# Step 5: Validate CloudFormation template
Write-Status "Validating CloudFormation template..."
try {
    aws cloudformation validate-template --template-body file://infrastructure/main-template.yaml --region $Region | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "Template validation failed"
    }
} catch {
    Write-Error "CloudFormation template validation failed: $_"
    exit 1
}

Write-Success "CloudFormation template validation passed"

# Step 6: Deploy the stack
Write-Status "Deploying CloudFormation stack..."

# Read parameters from environment-specific file
$ParamsFile = "infrastructure/parameters/$Environment-params.json"
if (-not (Test-Path $ParamsFile)) {
    Write-Error "Parameter file not found: $ParamsFile"
    exit 1
}

try {
    # Read existing parameters
    $existingParams = Get-Content $ParamsFile | ConvertFrom-Json
    
    # Create new parameters array with additional dynamic parameters
    $allParams = @()
    
    # Add existing parameters
    foreach ($param in $existingParams) {
        $allParams += @{
            ParameterKey = $param.ParameterKey
            ParameterValue = $param.ParameterValue
        }
    }
    
    # Add dynamic parameters
    $allParams += @{
        ParameterKey = "ArtifactBucket"
        ParameterValue = $ArtifactBucket
    }
    
    $allParams += @{
        ParameterKey = "ArtifactKey"
        ParameterValue = $ArtifactKey
    }
    
    # Create temporary parameter file
    $tempParamsFile = New-TemporaryFile
    $allParams | ConvertTo-Json -Depth 2 | Set-Content $tempParamsFile.FullName
    
    Write-Status "Using parameters file: $($tempParamsFile.FullName)"
    Write-Status "Parameters: ArtifactBucket=$ArtifactBucket, ArtifactKey=$ArtifactKey"
    
    # Deploy using CloudFormation with parameter file
    aws cloudformation deploy `
        --template-file "infrastructure/main-template.yaml" `
        --stack-name $StackName `
        --parameter-overrides file://$($tempParamsFile.FullName) `
        --capabilities CAPABILITY_NAMED_IAM `
        --region $Region `
        --tags Environment=$Environment Project=JavaAuthServer DeployedBy=$env:USERNAME DeployedAt=$Timestamp
    
    if ($LASTEXITCODE -ne 0) {
        # Show parameter file content for debugging
        Write-Status "Parameter file content:"
        Get-Content $tempParamsFile.FullName | Write-Host
        throw "CloudFormation deployment failed"
    }
    
    # Clean up temp file
    Remove-Item $tempParamsFile.FullName
} catch {
    Write-Error "Failed to deploy CloudFormation stack: $_"
    if (Test-Path $tempParamsFile.FullName) {
        Remove-Item $tempParamsFile.FullName
    }
    exit 1
}

# Step 7: Get stack outputs
Write-Status "Retrieving stack outputs..."
try {
    $outputs = aws cloudformation describe-stacks --stack-name $StackName --region $Region --query 'Stacks[0].Outputs' | ConvertFrom-Json
    
    if ($outputs) {
        $apiEndpoint = ($outputs | Where-Object { $_.OutputKey -eq "ApiEndpoint" }).OutputValue
        $dashboardUrl = ($outputs | Where-Object { $_.OutputKey -eq "DashboardUrl" }).OutputValue
        
        Write-Success "Deployment completed successfully!"
        Write-Host ""
        Write-Host "=== DEPLOYMENT RESULTS ===" -ForegroundColor Yellow
        Write-Host "Environment: $Environment"
        Write-Host "Stack Name: $StackName"
        Write-Host "API Endpoint: $apiEndpoint"
        Write-Host "CloudWatch Dashboard: $dashboardUrl"
        Write-Host "Lambda Package: s3://$ArtifactBucket/$ArtifactKey"
        Write-Host ""
        
        # Test command
        Write-Host "=== TEST COMMAND ===" -ForegroundColor Yellow
        $basicAuth = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("alice:password123"))
        Write-Host "curl -X POST $apiEndpoint/auth/validate \"
        Write-Host "  -H `"Authorization: Basic $basicAuth`" \"
        Write-Host "  -H `"Content-Type: application/json`""
        Write-Host ""
        
        # Save deployment info
        $deploymentInfo = @{
            environment = $Environment
            timestamp = $Timestamp
            stackName = $StackName
            apiEndpoint = $apiEndpoint
            dashboardUrl = $dashboardUrl
            artifactLocation = "s3://$ArtifactBucket/$ArtifactKey"
            region = $Region
            accountId = $AccountId
        } | ConvertTo-Json -Depth 2
        
        $deploymentInfo | Set-Content "deployment-info-$Environment.json"
        Write-Success "Deployment info saved to: deployment-info-$Environment.json"
        
    } else {
        Write-Warning "Could not retrieve stack outputs"
    }
} catch {
    Write-Warning "Failed to retrieve stack outputs: $_"
}

Write-Success "Script completed successfully!" 