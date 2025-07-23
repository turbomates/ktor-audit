package com.turbomates.audit

/**
 * Configuration for the AuditLog plugin
 */
class AuditLogConfiguration {
    /**
     * Storage implementation to use for storing audit log entries
     */
    var storage: AuditLogStorage = InMemoryAuditLogStorage()

    /**
     * Whether to include request headers in the audit log
     */
    var includeHeaders: Boolean = true

    /**
     * Whether to include query parameters in the audit log
     */
    var includeQueryParameters: Boolean = true

    /**
     * Whether to include request body in the audit log for POST requests
     */
    var includeRequestBody: Boolean = true

    /**
     * Set of header names to exclude from logging (case-insensitive)
     */
    var excludedHeaders: Set<String> = setOf("authorization", "cookie", "set-cookie")

    /**
     * Set of paths to exclude from audit logging (exact match)
     */
    var excludedPaths: Set<String> = setOf("/health", "/metrics")

    /**
     * Function to determine if a request should be audited
     * Returns true if the request should be audited, false otherwise
     */
    var shouldAudit: (method: String, path: String) -> Boolean = { method, path ->
        !excludedPaths.contains(path)
    }

    /**
     * Function to filter headers before logging
     * Returns a map of headers that should be included in the audit log
     */
    var headerFilter: (Map<String, List<String>>) -> Map<String, List<String>> = { headers ->
        if (includeHeaders) {
            headers.filterKeys { key ->
                !excludedHeaders.contains(key.lowercase())
            }
        } else {
            emptyMap()
        }
    }
}
