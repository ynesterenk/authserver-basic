# Secrets Management Guide

## Overview
The Java Authorization Server uses AWS Secrets Manager to store user credentials with Argon2id password hashing for security.

## Secret Format

### Required JSON Structure
```json
{
  "username": {
    "passwordHash": "argon2id_hash_here",
    "status": "ACTIVE|DISABLED", 
    "roles": ["role1", "role2"]
  }
}
```

### Example
```json
{
  "testuser": {
    "passwordHash": "$argon2id$v=19$m=65536,t=3,p=1$tAfnCOGvfoqtpA8fdehxjQ$xeVLzYR+9PcmvjOfYBvblNEIUlVSV4s/PeRKvNU3HGY",
    "status": "ACTIVE",
    "roles": ["user"]
  },
  "admin": {
    "passwordHash": "$argon2id$v=19$m=65536,t=3,p=1$...",
    "status": "ACTIVE", 
    "roles": ["admin", "user"]
  }
}
```

## Generating Password Hashes

### Method 1: Using Test Utility
```bash
mvn test -Dtest=HashGeneratorTest#generateAndVerifyPasswordHash
```

### Method 2: Manual Process
1. Update `HashGeneratorTest.java` with your password
2. Run the test to get the Argon2id hash
3. Copy the hash to your secret JSON

## Updating Secrets

### Via AWS CLI
```bash
aws secretsmanager update-secret \
  --secret-id "java-auth-server-dev-credentials-dev" \
  --secret-string '{
    "username": {
      "passwordHash": "new_argon2id_hash",
      "status": "ACTIVE",
      "roles": ["user"]
    }
  }'
```

### Via CloudFormation
Update the `SecretString` in `secrets-stack-simple.yaml` and redeploy.

## Security Notes

- **Never store plain text passwords**
- **Always use Argon2id hashing**
- **Passwords have 5-minute cache TTL**
- **Force cache refresh by updating Lambda environment variables**

## Test Credentials

### Default Test User
- **Username**: `testuser`
- **Password**: `testpass123`
- **Base64**: `dGVzdHVzZXI6dGVzdHBhc3MxMjM=`

### Postman Configuration
```
POST https://ya8l2od9y5.execute-api.us-east-1.amazonaws.com/dev/auth/validate
Authorization: Basic dGVzdHVzZXI6dGVzdHBhc3MxMjM=
Content-Type: application/json
```

### Expected Response
```json
{
  "allowed": true,
  "message": "Authentication successful",
  "timestamp": 1748641558512
}
``` 