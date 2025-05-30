param(
    [Parameter(Position=0)]
    [ValidateSet("dev", "staging", "prod")]
    [string]$Environment = "dev",
    
    [Parameter(Position=1)]
    [string]$Region = "us-east-1"
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

$StackName = "java-auth-server-$Environment"

Write-Status "Cleaning up failed CloudFormation stack: $StackName"

try {
    # Check if stack exists
    $stackStatus = aws cloudformation describe-stacks --stack-name $StackName --region $Region --query 'Stacks[0].StackStatus' --output text 2>$null
    
    if ($LASTEXITCODE -ne 0) {
        Write-Warning "Stack $StackName does not exist or cannot be accessed"
        exit 0
    }
    
    Write-Status "Current stack status: $stackStatus"
    
    if ($stackStatus -eq "ROLLBACK_COMPLETE" -or $stackStatus -eq "CREATE_FAILED" -or $stackStatus -eq "DELETE_FAILED") {
        Write-Status "Deleting failed stack..."
        aws cloudformation delete-stack --stack-name $StackName --region $Region
        
        if ($LASTEXITCODE -eq 0) {
            Write-Status "Waiting for stack deletion to complete..."
            aws cloudformation wait stack-delete-complete --stack-name $StackName --region $Region
            
            if ($LASTEXITCODE -eq 0) {
                Write-Success "Stack $StackName deleted successfully"
            } else {
                Write-Error "Stack deletion timed out or failed"
                exit 1
            }
        } else {
            Write-Error "Failed to initiate stack deletion"
            exit 1
        }
    } else {
        Write-Warning "Stack is in status: $stackStatus - not cleaning up"
    }
    
} catch {
    Write-Error "Failed to clean up stack: $_"
    exit 1
}

Write-Success "Cleanup completed successfully!"
Write-Status "You can now run the deployment script again:"
Write-Status ".\scripts\deploy.ps1 -Environment $Environment" 