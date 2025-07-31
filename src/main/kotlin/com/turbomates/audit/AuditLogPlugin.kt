package com.turbomates.audit

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.ktor.util.*
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.launch

/**
 * Attribute key for marking routes that should be audited
 */
val AuditRouteKey = AttributeKey<String>("AuditRoute")

/**
 * Routing extension function to mark routes for auditing
 */
fun Route.audit(name: String, build: Route.() -> Unit): Route {
    val route = createChild(object : RouteSelector() {
        override fun evaluate(context: RoutingResolveContext, segmentIndex: Int): RouteSelectorEvaluation {
            return RouteSelectorEvaluation.Constant
        }

        override fun toString(): String = "(audit $name)"
    })

    // Set the audit attribute on the route itself
    route.attributes.put(AuditRouteKey, name)

    // Install an interceptor on this route to set the call attribute
    route.intercept(ApplicationCallPipeline.Plugins) {
        call.attributes.put(AuditRouteKey, name)
    }

    route.build()
    return route
}

/**
 * Ktor plugin for audit logging
 */
@OptIn(ExperimentalTime::class)
val AuditLog = createApplicationPlugin(
    name = "AuditLog",
    createConfiguration = ::AuditLogConfiguration
) {
    val config = pluginConfig

    // Note: Request body capture using copyTo still consumes the original channel
    // This prevents the application from reading the body, so body capture remains disabled

    onCallRespond { call, _ ->
        val request = call.request
        val method = request.httpMethod.value
        val path = request.path()

        // Check if this route is marked for auditing
        val auditName = call.attributes.getOrNull(AuditRouteKey)
        val shouldAuditByRoute = auditName != null
        val shouldAuditByConfig = config.shouldAudit(method, path)

        // Check if this request should be audited (either by route marking or configuration)
        if (!shouldAuditByRoute && !shouldAuditByConfig) {
            return@onCallRespond
        }

        // Extract request information
        val principal = call.principal<Principal>()
        val timestamp = Clock.System.now()
        val queryParameters = if (config.includeQueryParameters) {
            request.queryParameters.toParametersMap()
        } else {
            emptyMap()
        }

        // Extract and filter headers
        val headers = config.headerFilter(request.headers.toHeadersMap())

        // Extract additional request information
        val remoteHost = call.request.local.remoteHost
        val userAgent = request.headers["User-Agent"]
        var requestBody: String? = null
        if (config.includeRequestBody && call.request.httpMethod == HttpMethod.Post) {
            requestBody =
                kotlin.runCatching { call.receiveText() }.getOrDefault("Need to install DoubleReceive feature")
        }
        val auditEntry = AuditLogEntry(
            principal = config.principal(call),
            timestamp = timestamp,
            method = method,
            path = path,
            queryParameters = queryParameters,
            headers = headers,
            remoteHost = remoteHost,
            userAgent = userAgent,
            requestBody = requestBody
        )

        // Store the audit log entry asynchronously
        call.application.launch {
            try {
                config.storage.store(auditEntry)
            } catch (e: Exception) {
                call.application.log.error("Failed to store audit log entry", e)
            }
        }
    }
}

/**
 * Extension function to convert Headers to Map
 */
private fun Headers.toHeadersMap(): Map<String, List<String>> {
    val map = mutableMapOf<String, List<String>>()
    forEach { name: String, values: List<String> ->
        map[name] = values
    }
    return map
}

/**
 * Extension function to convert Parameters to Map
 */
private fun Parameters.toParametersMap(): Map<String, List<String>> {
    val map = mutableMapOf<String, List<String>>()
    forEach { name: String, values: List<String> ->
        map[name] = values
    }
    return map
}
