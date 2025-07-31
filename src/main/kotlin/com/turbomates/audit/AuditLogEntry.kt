package com.turbomates.audit

import kotlin.time.ExperimentalTime
import kotlin.time.Instant


/**
 * Represents an audit log entry containing information about a request
 */
@OptIn(ExperimentalTime::class)
data class AuditLogEntry (
    val principal: AuditLogPrincipal?,
    val timestamp: Instant,
    val method: String,
    val path: String,
    val queryParameters: Map<String, List<String>>,
    val headers: Map<String, List<String>>,
    val remoteHost: String?,
    val userAgent: String?,
    val requestBody: String? = null
)

data class AuditLogPrincipal(
    val id: String,
    val type: String,
    val name: String? = null
)
