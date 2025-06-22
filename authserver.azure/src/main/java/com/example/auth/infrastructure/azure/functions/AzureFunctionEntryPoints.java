package com.example.auth.infrastructure.azure.functions;

import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import org.springframework.cloud.function.adapter.azure.FunctionInvoker;

import java.util.Optional;

/**
 * Azure Function entry points that delegate to Spring Cloud Functions.
 * These provide the @FunctionName annotations needed by Azure Functions runtime
 * while delegating actual processing to Spring Cloud Function beans.
 */
public class AzureFunctionEntryPoints extends FunctionInvoker<String, String> {

    /**
     * Azure Function entry point for Basic Authentication.
     * Delegates to the 'basicAuth' Spring Cloud Function.
     */
    @FunctionName("basicAuth")
    public HttpResponseMessage basicAuth(
            @HttpTrigger(
                name = "req",
                methods = {HttpMethod.POST},
                authLevel = AuthorizationLevel.ANONYMOUS,
                route = "auth/validate"
            ) HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {
        
        try {
            // Extract Basic Auth credentials and convert to JSON
            String jsonInput = extractBasicAuthAsJson(request);
            
            // Delegate to Spring Cloud Function
            String result = handleRequest(jsonInput, context);
            
            // Return result as HTTP response
            return request.createResponseBuilder(HttpStatus.OK)
                .header("Content-Type", "application/json")
                .header("Cache-Control", "no-cache, no-store, must-revalidate")
                .body(result)
                .build();
                
        } catch (Exception e) {
            context.getLogger().severe("Basic Auth error: " + e.getMessage());
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                .header("Content-Type", "application/json")
                .body("{\"authenticated\":false,\"error\":\"internal_error\"}")
                .build();
        }
    }

    /**
     * Azure Function entry point for OAuth Token generation.
     * Delegates to the 'oauthToken' Spring Cloud Function.
     */
    @FunctionName("oauthToken")
    public HttpResponseMessage oauthToken(
            @HttpTrigger(
                name = "req",
                methods = {HttpMethod.POST},
                authLevel = AuthorizationLevel.ANONYMOUS,
                route = "oauth/token"
            ) HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {
        
        try {
            // Extract OAuth request and convert to JSON
            String jsonInput = extractOAuthTokenRequestAsJson(request);
            
            // Delegate to Spring Cloud Function
            String result = handleRequest(jsonInput, context);
            
            // Return result as HTTP response
            return request.createResponseBuilder(HttpStatus.OK)
                .header("Content-Type", "application/json")
                .header("Cache-Control", "no-store")
                .body(result)
                .build();
                
        } catch (Exception e) {
            context.getLogger().severe("OAuth Token error: " + e.getMessage());
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                .header("Content-Type", "application/json")
                .body("{\"error\":\"server_error\",\"error_description\":\"Internal server error\"}")
                .build();
        }
    }

    /**
     * Azure Function entry point for OAuth Token Introspection.
     * Delegates to the 'oauthIntrospect' Spring Cloud Function.
     */
    @FunctionName("oauthIntrospect")
    public HttpResponseMessage oauthIntrospect(
            @HttpTrigger(
                name = "req",
                methods = {HttpMethod.POST},
                authLevel = AuthorizationLevel.ANONYMOUS,
                route = "oauth/introspect"
            ) HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {
        
        try {
            // Extract introspection request and convert to JSON
            String jsonInput = extractIntrospectionRequestAsJson(request);
            
            // Delegate to Spring Cloud Function
            String result = handleRequest(jsonInput, context);
            
            // Return result as HTTP response
            return request.createResponseBuilder(HttpStatus.OK)
                .header("Content-Type", "application/json")
                .header("Cache-Control", "no-store")
                .body(result)
                .build();
                
        } catch (Exception e) {
            context.getLogger().severe("OAuth Introspect error: " + e.getMessage());
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                .header("Content-Type", "application/json")
                .body("{\"error\":\"server_error\",\"error_description\":\"Internal server error\"}")
                .build();
        }
    }

    // Helper methods for converting HTTP requests to JSON input for Spring Cloud Functions

    private String extractBasicAuthAsJson(HttpRequestMessage<Optional<String>> request) {
        String authHeader = getAuthHeader(request);
        if (authHeader == null || !authHeader.startsWith("Basic ")) {
            throw new IllegalArgumentException("Missing or invalid Authorization header");
        }

        try {
            String encoded = authHeader.substring("Basic ".length()).trim();
            String decoded = new String(java.util.Base64.getDecoder().decode(encoded), java.nio.charset.StandardCharsets.UTF_8);
            
            int colonIndex = decoded.indexOf(':');
            if (colonIndex == -1) {
                throw new IllegalArgumentException("Invalid Basic Auth format");
            }
            
            String username = decoded.substring(0, colonIndex);
            String password = decoded.substring(colonIndex + 1);
            
            return String.format("{\"username\":\"%s\",\"password\":\"%s\"}", 
                escapeJson(username), escapeJson(password));
                
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse Basic Auth header", e);
        }
    }

    private String extractOAuthTokenRequestAsJson(HttpRequestMessage<Optional<String>> request) {
        String body = request.getBody().orElse("");
        String contentType = request.getHeaders().getOrDefault("content-type", 
                            request.getHeaders().getOrDefault("Content-Type", ""));
        
        // Check if the request body is JSON
        if (contentType.toLowerCase().contains("application/json")) {
            // If it's already JSON, return it as-is
            return body.isEmpty() ? "{}" : body;
        }
        
        // Otherwise, parse as form data
        StringBuilder json = new StringBuilder("{");
        boolean first = true;

        // Parse form data from body
        if (!body.isEmpty()) {
            String[] pairs = body.split("&");
            for (String pair : pairs) {
                String[] keyValue = pair.split("=", 2);
                if (keyValue.length == 2) {
                    if (!first) json.append(",");
                    String key = urlDecode(keyValue[0]);
                    String value = urlDecode(keyValue[1]);
                    
                    // Use snake_case field names to match OAuth 2.0 spec
                    json.append("\"").append(escapeJson(key)).append("\":\"")
                        .append(escapeJson(value)).append("\"");
                    first = false;
                }
            }
        }

        // Parse Basic Auth for client credentials
        String authHeader = getAuthHeader(request);
        if (authHeader != null && authHeader.startsWith("Basic ")) {
            try {
                String encoded = authHeader.substring("Basic ".length()).trim();
                String decoded = new String(java.util.Base64.getDecoder().decode(encoded), java.nio.charset.StandardCharsets.UTF_8);
                
                int colonIndex = decoded.indexOf(':');
                if (colonIndex != -1) {
                    String clientId = decoded.substring(0, colonIndex);
                    String clientSecret = decoded.substring(colonIndex + 1);
                    
                    if (!first) json.append(",");
                    // Use OAuth 2.0 spec field names
                    json.append("\"client_id\":\"").append(escapeJson(clientId)).append("\",");
                    json.append("\"client_secret\":\"").append(escapeJson(clientSecret)).append("\"");
                }
            } catch (Exception e) {
                // Ignore Basic Auth parsing errors
            }
        }

        json.append("}");
        return json.toString();
    }

    private String extractIntrospectionRequestAsJson(HttpRequestMessage<Optional<String>> request) {
        String body = request.getBody().orElse("");
        if (!body.isEmpty()) {
            String[] pairs = body.split("&");
            for (String pair : pairs) {
                String[] keyValue = pair.split("=", 2);
                if (keyValue.length == 2 && "token".equals(urlDecode(keyValue[0]))) {
                    String token = urlDecode(keyValue[1]);
                    return String.format("{\"token\":\"%s\"}", escapeJson(token));
                }
            }
        }
        
        throw new IllegalArgumentException("Missing token parameter");
    }

    private String getAuthHeader(HttpRequestMessage<Optional<String>> request) {
        String authHeader = request.getHeaders().get("authorization");
        if (authHeader == null) {
            authHeader = request.getHeaders().get("Authorization");
        }
        return authHeader;
    }

    private String urlDecode(String value) {
        try {
            return java.net.URLDecoder.decode(value, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return value;
        }
    }

    private String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\b", "\\b")
                   .replace("\f", "\\f")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t");
    }
} 