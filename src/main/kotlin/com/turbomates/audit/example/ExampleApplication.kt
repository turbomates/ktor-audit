package com.turbomates.audit.example

import com.turbomates.audit.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * Example application demonstrating how to use the AuditLog plugin
 */
fun Application.configureAuditLogExample() {
    // Create a custom storage implementation (in real apps, this might be a database)
    val auditStorage = InMemoryAuditLogStorage()

    // Install and configure the AuditLog plugin
    install(AuditLog) {
        // Set the storage implementation
        storage = auditStorage

        // Configure what to include in audit logs
        includeHeaders = true
        includeQueryParameters = true
        includeRequestBody = true

        // Exclude sensitive headers from logging
        excludedHeaders = setOf("authorization", "cookie", "set-cookie", "x-api-key")

        // Exclude health check and metrics endpoints
        excludedPaths = setOf("/health", "/metrics", "/favicon.ico")

        // Custom audit filter - only audit POST, PUT, DELETE operations
        shouldAudit = { method, path ->
            method in setOf("POST", "PUT", "DELETE") && !excludedPaths.contains(path)
        }

        // Custom header filter - remove all headers starting with "x-internal-"
        headerFilter = { headers ->
            if (includeHeaders) {
                headers.filterKeys { key ->
                    !excludedHeaders.contains(key.lowercase()) && !key.lowercase().startsWith("x-internal-")
                }
            } else {
                emptyMap()
            }
        }
    }

    // Configure authentication (optional)
    install(Authentication) {
        basic("basic") {
            validate { credentials ->
                // In a real app, validate against a database or external service
                if (credentials.name == "admin" && credentials.password == "secret") {
                    UserIdPrincipal("admin")
                } else null
            }
        }
    }

    // Configure routing
    routing {
        // Public endpoints
        get("/") {
            call.respond("Welcome to the Audit Log Example!")
        }

        get("/health") {
            call.respond("OK") // This won't be audited due to excludedPaths
        }

        // Protected endpoints that will be audited
        authenticate("basic") {
            post("/users") {
                // This will be audited with principal information
                call.respond("User created")
            }

            put("/users/{id}") {
                val id = call.parameters["id"]
                // This will be audited with principal and path parameters
                call.respond("User $id updated")
            }

            delete("/users/{id}") {
                val id = call.parameters["id"]
                // This will be audited with principal and path parameters
                call.respond("User $id deleted")
            }
        }

        // Example of route-level auditing using audit() function
        audit("public-api") {
            get("/public/users") {
                // This will be audited even though it's a GET request
                call.respond("Public users list")
            }

            post("/public/feedback") {
                // This will be audited with request body captured
                call.respond("Feedback received")
            }
        }

        // Endpoint to view audit logs (for demonstration purposes)
        get("/audit-logs") {
            val entries = auditStorage.getAllEntries()
            val response = entries.joinToString("\n") { entry ->
                "Time: ${entry.timestamp}, Method: ${entry.method}, Path: ${entry.path}, " +
                "Principal: ${entry.principal?.let { (it as? UserIdPrincipal)?.name ?: it.toString() } ?: "Anonymous"}, " +
                "Remote: ${entry.remoteHost}" +
                (entry.requestBody?.let { ", Body: $it" } ?: "")
            }
            call.respond(response.ifEmpty { "No audit logs found" })
        }
    }
}

/**
 * Custom storage implementation that logs to console
 */
class ConsoleAuditLogStorage : AuditLogStorage {
    override suspend fun store(entry: AuditLogEntry) {
        println("AUDIT LOG: ${entry.timestamp} | ${entry.method} ${entry.path} | " +
                "Principal: ${entry.principal?.let { (it as? UserIdPrincipal)?.name ?: it.toString() } ?: "Anonymous"} | " +
                "Remote: ${entry.remoteHost}")
    }
}
