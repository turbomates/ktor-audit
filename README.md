# Ktor Audit Log Plugin

A Ktor plugin for comprehensive audit logging that captures principal, time, request information, and path details, storing them through a configurable storage interface.

## Features

- **Principal Capture**: Automatically captures authenticated user information
- **Request Details**: Logs HTTP method, path, query parameters, and headers
- **Request Body Collection**: Request body field available (currently disabled to prevent channel consumption issues)
- **Route-Level Auditing**: Mark specific routes for auditing using `audit()` function
- **Timestamp**: Records precise timing of requests
- **Remote Host**: Captures client IP information
- **Configurable Storage**: Pluggable storage interface for different backends
- **Flexible Filtering**: Customizable filters for paths, headers, and audit conditions
- **Asynchronous**: Non-blocking audit log storage
- **Authentication Integration**: Works seamlessly with Ktor's authentication system

## Installation

Add the following dependencies to your `build.gradle.kts`:

```kotlin
dependencies {
    implementation("io.ktor:ktor-server-core:2.3.7")
    implementation("io.ktor:ktor-server-auth:2.3.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.5.0")
}
```

## Quick Start

### Basic Usage

```kotlin
fun Application.configureAuditLog() {
    install(AuditLog) {
        // Uses in-memory storage by default
        storage = InMemoryAuditLogStorage()
    }
}
```

### Advanced Configuration

```kotlin
fun Application.configureAuditLog() {
    install(AuditLog) {
        // Custom storage implementation
        storage = MyDatabaseAuditStorage()

        // Include/exclude options
        includeHeaders = true
        includeQueryParameters = true
        includeRequestBody = true

        // Exclude sensitive headers
        excludedHeaders = setOf("authorization", "cookie", "x-api-key")

        // Exclude health check endpoints
        excludedPaths = setOf("/health", "/metrics")

        // Custom audit condition
        shouldAudit = { method, path ->
            method in setOf("POST", "PUT", "DELETE") && !excludedPaths.contains(path)
        }

        // Custom header filtering
        headerFilter = { headers ->
            headers.filterKeys { !it.startsWith("x-internal-") }
        }
    }
}
```

## Storage Interface

Implement the `AuditLogStorage` interface for custom storage backends:

```kotlin
class DatabaseAuditLogStorage : AuditLogStorage {
    override suspend fun store(entry: AuditLogEntry) {
        // Store to database
        database.insertAuditLog(entry)
    }

    override suspend fun retrieve(limit: Int, offset: Int): List<AuditLogEntry> {
        // Retrieve from database
        return database.getAuditLogs(limit, offset)
    }
}
```

### Built-in Storage Implementations

- **InMemoryAuditLogStorage**: Simple in-memory storage for development and testing
- **ConsoleAuditLogStorage**: Logs audit entries to console (see example)

## Audit Log Entry Structure

```kotlin
data class AuditLogEntry(
    val principal: Principal?,           // Authenticated user (null if anonymous)
    val timestamp: Instant,              // Request timestamp
    val method: String,                  // HTTP method (GET, POST, etc.)
    val path: String,                    // Request path
    val queryParameters: Map<String, List<String>>, // Query parameters
    val headers: Map<String, List<String>>,         // Request headers (filtered)
    val remoteHost: String?,             // Client IP address
    val userAgent: String?,              // User-Agent header
    val requestBody: String?             // Request body (for POST requests, null otherwise)
)
```

## Configuration Options

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `storage` | `AuditLogStorage` | `InMemoryAuditLogStorage()` | Storage implementation |
| `includeHeaders` | `Boolean` | `true` | Include request headers in audit log |
| `includeQueryParameters` | `Boolean` | `true` | Include query parameters in audit log |
| `includeRequestBody` | `Boolean` | `true` | Include request body for POST requests in audit log |
| `excludedHeaders` | `Set<String>` | `setOf("authorization", "cookie", "set-cookie")` | Headers to exclude from logging |
| `excludedPaths` | `Set<String>` | `setOf("/health", "/metrics")` | Paths to exclude from audit logging |
| `shouldAudit` | `(String, String) -> Boolean` | Excludes paths in `excludedPaths` | Custom function to determine if request should be audited |
| `headerFilter` | `(Map<String, List<String>>) -> Map<String, List<String>>` | Filters based on `excludedHeaders` | Custom header filtering function |

## Authentication Integration

The plugin automatically captures authentication principals when used with Ktor's authentication system:

```kotlin
install(Authentication) {
    basic("basic") {
        validate { credentials ->
            if (validateUser(credentials)) {
                UserIdPrincipal(credentials.name)
            } else null
        }
    }
}

install(AuditLog) {
    storage = MyAuditStorage()
}

routing {
    authenticate("basic") {
        post("/protected") {
            // This request will be audited with principal information
            call.respond("Protected resource accessed")
        }
    }
}
```

## Route-Level Auditing

You can mark specific routes for auditing using the `audit()` function, similar to how `authenticate()` works:

```kotlin
routing {
    // These routes won't be audited (unless configured globally)
    get("/public") {
        call.respond("Public endpoint")
    }

    // Mark specific routes for auditing
    audit("user-operations") {
        get("/users") {
            // This GET request will be audited even if global config excludes GETs
            call.respond("Users list")
        }

        post("/users") {
            // This POST request will be audited with request body captured
            call.respond("User created")
        }

        put("/users/{id}") {
            // This PUT request will be audited
            call.respond("User updated")
        }
    }

    // You can have multiple audit blocks with different names
    audit("admin-operations") {
        delete("/admin/cleanup") {
            call.respond("Cleanup completed")
        }
    }
}
```

The `audit()` function takes a name parameter that can be used to categorize different types of operations. Routes inside an `audit()` block will be audited regardless of the global `shouldAudit` configuration.

## Request Body Collection

**Important Note**: Request body collection is currently disabled to prevent request channel consumption issues that would interfere with application code that needs to read the request body (such as serialization plugins).

```kotlin
install(AuditLog) {
    includeRequestBody = true  // This setting is available but body capture is disabled
}
```

The `includeRequestBody` configuration option exists for future compatibility, but request body capture is currently disabled because:

1. Reading the request body in Ktor consumes the request channel
2. Once consumed, the application cannot read the body again
3. This causes issues with serialization plugins and any application code that needs to access the request body

This limitation ensures that the audit plugin doesn't interfere with normal application functionality. The `requestBody` field in `AuditLogEntry` will always be `null` in the current implementation.

## Example Application

See `src/main/kotlin/com/turbomates/audit/example/ExampleApplication.kt` for a complete example application demonstrating:

- Basic plugin setup
- Authentication integration
- Route-level auditing with `audit()` function
- Request body collection for POST requests
- Custom configuration
- Multiple storage implementations
- Audit log viewing endpoint

## Testing

The plugin includes comprehensive tests covering:

- Basic audit logging functionality
- Query parameter capture
- Authentication principal capture
- Path exclusion filtering
- Header filtering
- Custom configuration options

Run tests with:
```bash
./gradlew test
```

## License

This project is licensed under the MIT License.
