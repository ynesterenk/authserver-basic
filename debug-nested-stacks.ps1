param(
    [Parameter(Position=0)]
    [string]$Environment = "dev",
    [Parameter(Position=1)]
    [string]$Region = "us-east-1"
)

$StackName = "java-auth-server-$Environment"

Write-Host "Getting detailed error information for stack: $StackName" -ForegroundColor Cyan

# Get main stack events
Write-Host "`n=== MAIN STACK EVENTS ===" -ForegroundColor Yellow
aws cloudformation describe-stack-events --stack-name $StackName --region $Region --query "StackEvents[?ResourceStatus=='CREATE_FAILED' || ResourceStatus=='ROLLBACK_IN_PROGRESS'].{Resource:LogicalResourceId,Status:ResourceStatus,Reason:ResourceStatusReason,Time:Timestamp}" --output table

# Get nested stack names
Write-Host "`n=== FINDING NESTED STACKS ===" -ForegroundColor Yellow
$nestedStacks = aws cloudformation describe-stack-resources --stack-name $StackName --region $Region --query "StackResources[?ResourceType=='AWS::CloudFormation::Stack'].PhysicalResourceId" --output text 2>$null

if ($nestedStacks) {
    $nestedStacks.Split("`n") | ForEach-Object {
        if ($_.Trim()) {
            $nestedStackArn = $_.Trim()
            $nestedStackName = $nestedStackArn.Split("/")[1]
            
            Write-Host "`n=== NESTED STACK: $nestedStackName ===" -ForegroundColor Green
            aws cloudformation describe-stack-events --stack-name $nestedStackName --region $Region --query "StackEvents[?ResourceStatus=='CREATE_FAILED'].{Resource:LogicalResourceId,Status:ResourceStatus,Reason:ResourceStatusReason,Time:Timestamp}" --output table
        }
    }
} else {
    Write-Host "No nested stacks found or stack doesn't exist" -ForegroundColor Red
}

Write-Host "`n=== MANUAL CLEANUP COMMANDS ===" -ForegroundColor Yellow
Write-Host "If you need to manually clean up:"
Write-Host "aws cloudformation delete-stack --stack-name $StackName --region $Region"
Write-Host "aws cloudformation wait stack-delete-complete --stack-name $StackName --region $Region" 