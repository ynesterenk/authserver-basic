### Product Requirements Document (PRD)
## Java Authorization Server (Basic Access Authentication, AWS Lambda)

## 1. Purpose & Scope
Provide a lightweight, cloud-native authorization service that authenticates HTTP requests with Basic access authentication.

Primary Goal: Securely validate a client’s username:password over HTTPS and return an allow / deny outcome fast enough for low-latency, serverless workloads.

Secondary Goal: Lay clean architectural paths to upgrade the service to OAuth 2.0 (client-credentials, PKCE, etc.) without major refactor.

## 2. Success Criteria
KPI	Target
Cold-start latency (P95)	≤ 600 ms
Warm invocation latency (P95)	≤ 120 ms
Availability	≥ 99.9 % (Lambda + API Gateway)
Unit-test coverage (auth core)	≥ 90 % line + branch
Mean time to add new grant type	≤ 2 sprints

## 3. Personas & Use-Cases
Persona	Scenario
Internal micro-service	Calls another internal API and must pass Basic credentials for authentication.
DevOps engineer	Deploys an isolated stack to test new auth rules in a sandbox account.
Security architect	Audits password hashing, IAM least-privilege, rotation policies.
Platform team	Migrates from Basic to OAuth 2.0—expects drop-in extension, not rewrite.

## 4. Functional Requirements
Credential Validation

Accepts Authorization: Basic <base64(username:password)> header.

Decodes and validates against the credential store (AWS Secrets Manager v1).

HTTP Interface

POST /auth/validate → JSON { "allowed": true | false }.

Returns HTTP 200 on success, 401 on invalid creds, 429 on rate-limit, 5xx on server error.

Serverless Execution

Implemented as a single AWS Lambda (Java 21 runtime) behind HTTP API Gateway.

Observability

Structured JSON logs (Lambda Powertools).

CloudWatch metrics: AuthSuccess, AuthFailure, Latency.

Configuration

Parameterized credential store ARN, rate-limit, and stage (dev|prod) via Lambda environment variables / CloudFormation parameters.

Local Testing

Pure-Java core auth library (no AWS SDK) with JUnit 5 + Testcontainers for SecretsManager mock.

Maven wrapper script ./mvnw test.

## 5. Non-Functional Requirements
Category	Requirement
Performance	≤ 120 ms warm; ≤ 600 ms cold.
Scalability	Handle 500 RPS burst (Lambda concurrency & reserved capacity).
Security	All traffic HTTPS; credentials stored only in Secrets Manager; no plaintext logs.
Reliability	Automatic retries (API Gateway → Lambda), DLQ for failed invocations.
Maintainability	Clean-hexagonal architecture, dependency-injection (Micronaut or Spring Cloud Function).
Compliance	Align with CIS AWS Foundations benchmarks; credential rotation policies.

## 6. Architecture Overview
Copy
Edit
Client ──HTTPS──► AWS API Gateway (HTTP API)
                      │
                      ▼
            AWS Lambda (Java 21)
                      │
     AWS Secrets Manager (credentials)
                      │
         CloudWatch Logs & Metrics
Layered Design

Presentation Layer – API Gateway request/response mapping.

Domain Layer – AuthenticatorService, pure Java, unit-testable.

Infrastructure Layer – AWS SDK adapter to Secrets Manager (only in production path).

Extensibility Path to OAuth 2.0

Introduce strategy interface AuthStrategy.

Implement BasicAuthStrategy now; add OAuthClientCredentialsStrategy later.

Swap strategies via request header inspection or stage variable.

## 7. Data Model
Field	Type	Notes
username	string	Case-sensitive; UTF-8.
passwordHash	string	Argon2id, base64.
status	enum (ACTIVE,DISABLED)	Disabled users always denied.
roles	string[]	Reserved for future RBAC / OAuth scopes.

Stored in a single Secrets Manager secret as JSON map:

json
Copy
Edit
{
  "alice": { "passwordHash": "xxx", "status":"ACTIVE", "roles":["admin"] },
  "bob":   { "passwordHash": "yyy", "status":"ACTIVE", "roles":[]     }
}
8. Deployment & CloudFormation Skeleton
yaml
Copy
Edit
AWSTemplateFormatVersion: '2010-09-09'
Parameters:
  StageName:
    Type: String
    Default: dev
Resources:
  AuthFunction:
    Type: AWS::Lambda::Function
    Properties:
      Runtime: java21
      Handler: com.example.auth.LambdaHandler::handleRequest
      Code: 
        S3Bucket: !Ref ArtifactBucket
        S3Key:    authserver-{StageName}.zip
      MemorySize: 512
      Timeout: 5
      Environment:
        Variables:
          CREDENTIAL_SECRET_ARN: !Ref CredentialSecret
  Api:
    Type: AWS::ApiGatewayV2::Api
    Properties:
      ProtocolType: HTTP
  AuthIntegration:
    Type: AWS::ApiGatewayV2::Integration
    Properties:
      ApiId: !Ref Api
      IntegrationType: AWS_PROXY
      IntegrationUri: !GetAtt AuthFunction.Arn
  AuthRoute:
    Type: AWS::ApiGatewayV2::Route
    Properties:
      ApiId: !Ref Api
      RouteKey: 'POST /auth/validate'
      Target: !Join ['/', ['integrations', !Ref AuthIntegration]]
  # + Lambda permission, secret, IAM role, etc.
Outputs:
  ApiUrl:
    Value: !Join ['', ['https://', !Ref Api, '.execute-api.', !Ref AWS::Region, '.amazonaws.com']]
## 9. Testing Strategy
Level	Tooling	Focus
Unit	JUnit 5, Mockito	Auth logic, hash comparison, error paths.
Integration (local)	Testcontainers + LocalStack	Secrets Manager interaction.
Contract	Pact	Ensure request/response schema with consumers.
End-to-End (CI)	AWS SAM CLI sam local invoke	Validate Lambda handler & API contract.

CI pipeline (GitHub Actions / CodeBuild):

Build (mvn package).

Static analysis (SpotBugs, OWASP Dependency-Check).

Unit + Integration tests.

Package & deploy dev stack with CloudFormation change-sets.

Smoke test through API endpoint.

## 10. Observability & Ops
Logging – JSON, one line per request, no PII.

Metrics – custom CloudWatch metrics emitted via Lambda Powertools.

Alarms – Notify Slack / SNS on elevated AuthFailure ratio or latency.

Tracing – AWS X-Ray enabled for Lambda.

Secrets Rotation – 90-day automatic rotation Lambda (future work).

## 11. Risks & Mitigations
Risk	Impact	Mitigation
Credential leakage in logs	High	Never log decoded credentials; redact header.
Lambda cold-starts	Medium	Provisioned concurrency for prod.
Mis-configurable IAM policies	Medium	Use least privilege in CloudFormation, add IAM Access Analyzer checks.
Scale-up blasts SecretsManager limits	Low	Cache secrets in memory (TTL 5 min).

## 12. Timeline (High-Level)
Milestone	Duration
Requirements ✓	0.5 wk
Prototype (local only)	1 wk
AWS Integration & CloudFormation	1 wk
Unit + Integration Tests	0.5 wk
Security Review & Hardening	0.5 wk
MVP GA	~3-3.5 weeks

## 13. Future Extensions
OAuth 2.0: Client-Credentials & PKCE – plug new AuthStrategy; store client secrets in Secrets Manager; issue JWT via AWS KMS-backed signing.

RBAC/ABAC – map roles to IAM policy documents or custom claims.

Multi-tenant Credentials – partition secret by tenant ID; or migrate to DynamoDB table.

Cognito or AWS IAM Identity Center Integration – optional replace for Secrets Manager backing store.

Edge Authentication – Move validation into CloudFront Functions for even lower latency.

## 14. Open Questions
#	Question
1	Should password hashes follow organizational Argon2id parameters or NIST defaults?
2	Do we need IP-based throttling rules inside API Gateway?
3	Any compliance mandates (e.g., PCI, HIPAA) that influence logging / encryption requirements?
4	Who owns secrets rotation Lambda post-MVP?

Owner: Security & Platform Engineering – Auth Sub-team
Document Version: 1.0 (May 30, 2025)

