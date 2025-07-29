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

The `AuditLogStorage` interface is generic and requires you to define a `SearchCriteria` type for type-safe searching. Implement both the storage interface and a corresponding search criteria class for custom storage backends:

```kotlin
// Define custom search criteria for your storage
data class DatabaseSearchCriteria(
    val method: String? = null,
    val path: String? = null,
    val userId: String? = null,
    val dateFrom: LocalDateTime? = null,
    val dateTo: LocalDateTime? = null
) : SearchCriteria

class DatabaseAuditLogStorage : AuditLogStorage<DatabaseSearchCriteria> {
    override suspend fun store(entry: AuditLogEntry) {
        // Store to database
        database.insertAuditLog(entry)
    }

    override suspend fun retrieve(limit: Int, offset: Int): List<AuditLogEntry> {
        // Retrieve from database
        return database.getAuditLogs(limit, offset)
    }

    override suspend fun search(
        criteria: DatabaseSearchCriteria,
        limit: Int,
        offset: Int
    ): List<AuditLogEntry> {
        // Search database with criteria
        return database.searchAuditLogs(criteria, limit, offset)
    }
}
```

### Built-in Storage Implementations

- **InMemoryAuditLogStorage**: Simple in-memory storage for development and testing with `InMemorySearchCriteria`
- **ConsoleAuditLogStorage**: Logs audit entries to console with `EmptySearchCriteria` (see example)

### Searching Audit Logs

The `AuditLogStorage` interface is now generic and uses typed search criteria. Each storage implementation defines its own `SearchCriteria` class for type-safe searching.

#### InMemoryAuditLogStorage Search

The `InMemoryAuditLogStorage` uses `InMemorySearchCriteria` for searching:

```kotlin
val storage = InMemoryAuditLogStorage()

// Search by HTTP method
val postRequests = storage.search(InMemorySearchCriteria(method = "POST"))

// Search by path
val userRequests = storage.search(InMemorySearchCriteria(path = "/users"))

// Search by principal name
val userActions = storage.search(InMemorySearchCriteria(principal = "john.doe"))

// Search by remote host
val hostRequests = storage.search(InMemorySearchCriteria(remoteHost = "192.168.1.100"))

// Search by user agent
val mobileRequests = storage.search(InMemorySearchCriteria(userAgent = "Mobile App"))

// Search by time range
val recentRequests = storage.search(InMemorySearchCriteria(
    timestampFrom = Clock.System.now().minus(1.hours),
    timestampTo = Clock.System.now()
))

// Combined search with pagination
val results = storage.search(
    criteria = InMemorySearchCriteria(
        method = "POST",
        path = "/users"
    ),
    limit = 50,
    offset = 0
)

// Search with empty criteria (returns all entries)
val allEntries = storage.search(InMemorySearchCriteria())
```

**InMemorySearchCriteria Properties:**
- `method: String?`: HTTP method (GET, POST, PUT, DELETE, etc.)
- `path: String?`: Request path
- `principal: String?`: Principal name (authenticated user)
- `remoteHost: String?`: Client IP address
- `userAgent: String?`: User-Agent header value
- `timestampFrom: Instant?`: Search entries after this timestamp
- `timestampTo: Instant?`: Search entries before this timestamp

#### Creating Custom Search Criteria

When implementing your own storage, define a custom search criteria class:

```kotlin
data class MyCustomSearchCriteria(
    val userId: String? = null,
    val action: String? = null,
    val severity: LogLevel? = null,
    val tags: Set<String> = emptySet()
) : SearchCriteria

class MyCustomStorage : AuditLogStorage<MyCustomSearchCriteria> {
    override suspend fun search(
        criteria: MyCustomSearchCriteria,
        limit: Int,
        offset: Int
    ): List<AuditLogEntry> {
        // Implement search logic using criteria properties
        return searchImplementation(criteria, limit, offset)
    }
    // ... other methods
}
```

## Audit Log Entry Structure

```kotlin
data class AuditLogEntry(
    val principal: AuditLogPrincipal?,   // Authenticated user info (null if anonymous)
    val timestamp: Instant,              // Request timestamp
    val method: String,                  // HTTP method (GET, POST, etc.)
    val path: String,                    // Request path
    val queryParameters: Map<String, List<String>>, // Query parameters
    val headers: Map<String, List<String>>,         // Request headers (filtered)
    val remoteHost: String?,             // Client IP address
    val userAgent: String?,              // User-Agent header
    val requestBody: String?             // Request body (for POST requests, null otherwise)
)

data class AuditLogPrincipal(
    val id: String,        // Unique identifier for the principal
    val type: String,      // Type/role of the principal (e.g., "User", "Admin", "Service")
    val name: String?      // Display name or username (optional)
)
```

## Configuration Options

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `storage` | `AuditLogStorage<*>` | `InMemoryAuditLogStorage()` | Storage implementation |
| `includeHeaders` | `Boolean` | `true` | Include request headers in audit log |
| `includeQueryParameters` | `Boolean` | `true` | Include query parameters in audit log |
| `includeRequestBody` | `Boolean` | `true` | Include request body for POST requests in audit log |
| `excludedHeaders` | `Set<String>` | `setOf("authorization", "cookie", "set-cookie")` | Headers to exclude from logging |
| `excludedPaths` | `Set<String>` | `setOf("/health", "/metrics")` | Paths to exclude from audit logging |
| `principal` | `(ApplicationCall) -> AuditLogPrincipal?` | `{ null }` | Function to extract principal information from authenticated requests |
| `shouldAudit` | `(String, String) -> Boolean` | Excludes paths in `excludedPaths` | Custom function to determine if request should be audited |
| `headerFilter` | `(Map<String, List<String>>) -> Map<String, List<String>>` | Filters based on `excludedHeaders` | Custom header filtering function |

## Principal Configuration

The `principal` configuration option allows you to extract user information from authenticated requests and include it in audit logs. This callback function receives the current `ApplicationCall` and should return an `AuditLogPrincipal` object or `null`.

### AuditLogPrincipal Structure

```kotlin
data class AuditLogPrincipal(
    val id: String,        // Unique identifier for the principal
    val type: String,      // Type/role of the principal (e.g., "User", "Admin", "Service")
    val name: String? = null  // Display name or username (optional)
)
```

### Basic Principal Configuration

```kotlin
install(AuditLog) {
    storage = InMemoryAuditLogStorage()
    
    // Extract principal from Ktor's authentication system
    principal = { call ->
        val userPrincipal = call.principal<UserIdPrincipal>()
        if (userPrincipal != null) {
            AuditLogPrincipal(
                id = userPrincipal.name,
                type = "User",
                name = userPrincipal.name
            )
        } else {
            null // No authenticated user
        }
    }
}
```

### Advanced Principal Configuration

```kotlin
install(AuditLog) {
    storage = DatabaseAuditLogStorage()
    
    // Extract detailed principal information with role-based typing
    principal = { call ->
        when (val principal = call.principal()) {
            is UserIdPrincipal -> AuditLogPrincipal(
                id = principal.name,
                type = "User",
                name = principal.name
            )
            
            is JWTPrincipal -> {
                val userId = principal.payload.getClaim("sub").asString()
                val userName = principal.payload.getClaim("name").asString()
                val role = principal.payload.getClaim("role").asString() ?: "User"
                
                AuditLogPrincipal(
                    id = userId,
                    type = role,
                    name = userName
                )
            }
            
            is UserPrincipal -> AuditLogPrincipal(
                id = principal.user.id.toString(),
                type = when (principal.user.role) {
                    UserRole.ADMIN -> "Admin"
                    UserRole.USER -> "User"
                    UserRole.SERVICE -> "Service"
                },
                name = principal.user.displayName
            )
            
            else -> null // Unknown or unauthenticated
        }
    }
}
```

### Service-to-Service Authentication

For API keys or service authentication:

```kotlin
install(AuditLog) {
    principal = { call ->
        // Check for API key in headers
        val apiKey = call.request.headers["X-API-Key"]
        if (apiKey != null) {
            // Look up service by API key
            val service = serviceRegistry.findByApiKey(apiKey)
            if (service != null) {
                AuditLogPrincipal(
                    id = service.id,
                    type = "Service",
                    name = service.name
                )
            } else {
                null
            }
        } else {
            // Fall back to regular user authentication
            val userPrincipal = call.principal<UserIdPrincipal>()
            userPrincipal?.let { 
                AuditLogPrincipal(
                    id = it.name,
                    type = "User", 
                    name = it.name
                )
            }
        }
    }
}
```

### Anonymous Request Handling

When the principal callback returns `null`, the audit log entry will have `principal = null`, indicating an anonymous or unauthenticated request. This is useful for tracking public API usage or failed authentication attempts.

## Authentication Integration

The plugin works seamlessly with Ktor's authentication system:

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
    
    // Configure principal extraction
    principal = { call ->
        val userPrincipal = call.principal<UserIdPrincipal>()
        userPrincipal?.let {
            AuditLogPrincipal(
                id = it.name,
                type = "User",
                name = it.name
            )
        }
    }
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
- Authentication integration with principal configuration
- Route-level auditing with `audit()` function
- Request body collection for POST requests
- Custom configuration options
- Multiple storage implementations
- Audit log viewing endpoint
- Principal extraction from authenticated requests

## Testing

The plugin includes comprehensive tests covering:

- Basic audit logging functionality
- Query parameter capture
- Authentication principal capture
- Path exclusion filtering
- Header filtering
- Custom configuration options
- Search functionality with typed criteria
- Pagination support
- Combined search parameters

Run tests with:
```bash
./gradlew test
```

## License

This project is licensed under the MIT License.
