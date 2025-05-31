# CI/CD Configuration Validation Script
# Validates all required components for the Java Authorization Server CI/CD pipeline

param(
    [switch]$Verbose
)

# Global validation status
$script:ValidationFailed = $false

# Output colors
$Colors = @{
    Success = "Green"
    Warning = "Yellow" 
    Error = "Red"
    Info = "Cyan"
    Header = "Magenta"
}

function Write-ValidationMessage {
    param(
        [string]$Message,
        [string]$Status = "Info"
    )
    
    $color = $Colors[$Status]
    Write-Host "[$Status] $Message" -ForegroundColor $color
    
    if ($Status -eq "Error") {
        $script:ValidationFailed = $true
    }
}

function Test-RequiredFile {
    param(
        [string]$FilePath,
        [string]$Description
    )
    
    if (Test-Path $FilePath) {
        Write-ValidationMessage "OK: $Description found: $FilePath" "Success"
        return $true
    } else {
        Write-ValidationMessage "MISSING: $Description missing: $FilePath" "Error"
        return $false
    }
}

# Start validation
Write-Host "`n============================================" -ForegroundColor $Colors.Header
Write-Host "    CI/CD CONFIGURATION VALIDATION" -ForegroundColor $Colors.Header
Write-Host "============================================`n" -ForegroundColor $Colors.Header

# 1. GitHub Actions Workflow
Write-ValidationMessage "Checking GitHub Actions workflow..." "Info"
$workflowExists = Test-RequiredFile ".github/workflows/deploy.yml" "GitHub Actions workflow"

if ($workflowExists) {
    try {
        $workflowContent = Get-Content ".github/workflows/deploy.yml" -Raw
        $requiredJobs = @("test", "deploy-dev", "deploy-staging", "deploy-prod")
        foreach ($job in $requiredJobs) {
            if ($workflowContent -match "${job}:") {
                Write-ValidationMessage "OK: Job '$job' found in workflow" "Success"
            } else {
                Write-ValidationMessage "MISSING: Job '$job' missing from workflow" "Error"
            }
        }
    }
    catch {
        Write-ValidationMessage "ERROR: Could not read workflow file" "Error"
    }
}

Write-Host ""

# 2. Infrastructure Templates
Write-ValidationMessage "Checking CloudFormation infrastructure..." "Info"
$infraFiles = @{
    "infrastructure/main-template.yaml" = "Main CloudFormation template"
    "infrastructure/nested-stacks/iam-stack-simple.yaml" = "IAM stack template"
    "infrastructure/nested-stacks/lambda-stack-simple.yaml" = "Lambda stack template"
    "infrastructure/nested-stacks/api-gateway-stack-simple.yaml" = "API Gateway stack template"
    "infrastructure/nested-stacks/secrets-stack-simple.yaml" = "Secrets stack template"
}

foreach ($file in $infraFiles.GetEnumerator()) {
    Test-RequiredFile $file.Key $file.Value | Out-Null
}

Write-Host ""

# 3. Environment Parameters
Write-ValidationMessage "Checking environment parameter files..." "Info"
$paramFiles = @{
    "infrastructure/parameters/dev-params.json" = "Development parameters"
    "infrastructure/parameters/staging-params.json" = "Staging parameters"
    "infrastructure/parameters/prod-params.json" = "Production parameters"
}

foreach ($file in $paramFiles.GetEnumerator()) {
    Test-RequiredFile $file.Key $file.Value | Out-Null
}

Write-Host ""

# 4. Deployment Scripts
Write-ValidationMessage "Checking deployment scripts..." "Info"
$scriptFiles = @{
    "scripts/deploy.sh" = "Bash deployment script"
    "scripts/deploy.ps1" = "PowerShell deployment script"
    "scripts/smoke-test.sh" = "Smoke test script"
}

foreach ($file in $scriptFiles.GetEnumerator()) {
    Test-RequiredFile $file.Key $file.Value | Out-Null
}

Write-Host ""

# 5. Project Configuration
Write-ValidationMessage "Checking project configuration..." "Info"
Test-RequiredFile "pom.xml" "Maven POM file" | Out-Null
Test-RequiredFile "CICD-SETUP.md" "CI/CD setup documentation" | Out-Null

Write-Host ""

# 6. Build Artifacts (Optional)
Write-ValidationMessage "Checking build artifacts..." "Info"
if (Test-Path "target/auth-server-lambda.jar") {
    Write-ValidationMessage "OK: Lambda JAR already built" "Success"
} else {
    Write-ValidationMessage "INFO: Lambda JAR not found (will be built during CI)" "Warning"
}

Write-Host ""

# 7. AWS CLI Check
Write-ValidationMessage "Checking AWS CLI availability..." "Info"
try {
    $awsVersion = aws --version 2>&1
    if ($?) {
        Write-ValidationMessage "OK: AWS CLI available" "Success"
    } else {
        Write-ValidationMessage "WARNING: AWS CLI not found (needed for local deployment)" "Warning"
    }
}
catch {
    Write-ValidationMessage "WARNING: AWS CLI not available (needed for local deployment)" "Warning"
}

Write-Host ""

# Summary
Write-Host "============================================" -ForegroundColor $Colors.Header
if ($script:ValidationFailed) {
    Write-Host "    CICD VALIDATION FAILED" -ForegroundColor $Colors.Error
    Write-Host "============================================`n" -ForegroundColor $Colors.Header
    Write-ValidationMessage "Some required components are missing or misconfigured." "Error"
    Write-Host ""
    Write-Host "Please fix the issues above and run the validation again."
    Write-Host "Refer to CICD-SETUP.md for detailed setup instructions."
    Write-Host ""
    exit 1
} else {
    Write-Host "    CICD VALIDATION PASSED" -ForegroundColor $Colors.Success
    Write-Host "============================================`n" -ForegroundColor $Colors.Header
    Write-ValidationMessage "All CI/CD components are properly configured!" "Success"
    Write-Host ""
    Write-Host "Next steps:"
    Write-Host "1. Configure GitHub repository secrets (see CICD-SETUP.md)"
    Write-Host "2. Push to develop branch to test development deployment"
    Write-Host "3. Create PR to main branch for staging/production deployment"
    Write-Host ""
    exit 0
} 