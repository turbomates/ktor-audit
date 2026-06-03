package com.turbomates.audit

import io.ktor.http.Headers
import io.ktor.http.HttpMethod
import io.ktor.http.Parameters
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.RouteScopedPlugin
import io.ktor.server.application.call
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.application.hooks.CallSetup
import io.ktor.server.application.hooks.MonitoringEvent
import io.ktor.server.application.hooks.ResponseSent
import io.ktor.server.application.log
import io.ktor.server.application.plugin
import io.ktor.server.auth.AuthenticationChecked
import io.ktor.server.plugins.origin
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.request.receiveText
import io.ktor.server.routing.Route
import io.ktor.server.routing.RouteSelector
import io.ktor.server.routing.RouteSelectorEvaluation
import io.ktor.server.routing.RoutingResolveContext
import io.ktor.server.routing.intercept
import io.ktor.util.AttributeKey
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

/**
 * Attribute key for marking routes that should be audited
 */
val AuditRouteKey = AttributeKey<String>("AuditRoute")
val AuditLogInterceptors: RouteScopedPlugin<RouteName> = createRouteScopedPlugin(
    "AuthenticationInterceptors",
    ::RouteName
) {
    on(CallSetup) { call ->
        call.attributes.put(AuditRouteKey, pluginConfig.name)
    }
}

class RouteName() {
    var name = ""
}

/**
 * Routing extension function to mark routes for auditing
 */
fun Route.audit(name: String, build: Route.() -> Unit): Route {
    val route = createChild(object : RouteSelector() {
        override suspend fun evaluate(context: RoutingResolveContext, segmentIndex: Int): RouteSelectorEvaluation {
            return RouteSelectorEvaluation.Constant
        }

        override fun toString(): String = "(audit $name)"
    })

    // Set the audit attribute on the route itself
    route.install(AuditLogInterceptors) {
        this.name = name
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
    val logger = LoggerFactory.getLogger("AuditLog")

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

        val timestamp = Clock.System.now()
        val queryParameters = if (config.includeQueryParameters) {
            request.queryParameters.toParametersMap()
        } else {
            emptyMap()
        }

        // Extract and filter headers
        val headers = config.headerFilter(request.headers.toHeadersMap())

        // Extract additional request information
        val remoteHost = call.request.origin.remoteHost
        val userAgent = request.headers["User-Agent"]
        var requestBody: String? = null
        if (config.includeRequestBody && call.request.httpMethod == HttpMethod.Post) {
            requestBody =
                kotlin.runCatching { call.receiveText() }.fold(
                    { it },
                    {
                        logger.error("error reading request body: ${it.message}")
                        "Failed to read request body"
                    })
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
                config.storage.store(config.modifyEntry(auditEntry, call))
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
