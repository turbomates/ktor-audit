package com.turbomates.audit

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.doublereceive.DoubleReceive
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.coroutines.delay
import kotlin.test.*

class AuditLogPluginTest {

    @Test
    fun testAuditLogBasicFunctionality() = testApplication {
        val storage = InMemoryAuditLogStorage()

        application {
            install(AuditLog) {
                this.storage = storage
            }

            routing {
                get("/test") {
                    call.respond("Hello World")
                }
                get("/health") {
                    call.respond("OK")
                }
            }
        }

        // Make a request to /test
        client.get("/test")

        // Wait a bit for async storage
        delay(100)

        // Verify audit log was created
        val entries = storage.getAllEntries()
        assertEquals(1, entries.size)

        val entry = entries.first()
        assertEquals("GET", entry.method)
        assertEquals("/test", entry.path)
        assertNull(entry.principal) // No authentication configured
        assertTrue(entry.queryParameters.isEmpty())
    }

    @Test
    fun testAuditLogWithQueryParameters() = testApplication {
        val storage = InMemoryAuditLogStorage()

        application {
            install(AuditLog) {
                this.storage = storage
            }

            routing {
                get("/search") {
                    call.respond("Search results")
                }
            }
        }

        // Make a request with query parameters
        client.get("/search?q=kotlin&page=1")

        // Wait a bit for async storage
        delay(100)

        // Verify audit log was created with query parameters
        val entries = storage.getAllEntries()
        assertEquals(1, entries.size)

        val entry = entries.first()
        assertEquals("GET", entry.method)
        assertEquals("/search", entry.path)
        assertEquals(mapOf("q" to listOf("kotlin"), "page" to listOf("1")), entry.queryParameters)
    }

    @Test
    fun testAuditLogWithAuthentication() = testApplication {
        val storage = InMemoryAuditLogStorage()

        application {
            install(Authentication) {
                basic("basic") {
                    validate { credentials ->
                        if (credentials.name == "user" && credentials.password == "pass") {
                            UserIdPrincipal("user")
                        } else null
                    }
                }
            }

            install(AuditLog) {
                this.storage = storage
            }

            routing {
                authenticate("basic") {
                    get("/protected") {
                        call.respond("Protected resource")
                    }
                }
            }
        }

        // Make an authenticated request
        client.get("/protected") {
            header(HttpHeaders.Authorization, "Basic dXNlcjpwYXNz") // user:pass in base64
        }

        // Wait a bit for async storage
        delay(100)

        // Verify audit log was created with principal
        val entries = storage.getAllEntries()
        assertEquals(1, entries.size)

        val entry = entries.first()
        assertEquals("GET", entry.method)
        assertEquals("/protected", entry.path)
        assertNotNull(entry.principal)
        assertTrue(entry.principal is UserIdPrincipal)
        assertEquals("user", (entry.principal as UserIdPrincipal).name)
    }

    @Test
    fun testAuditLogExcludedPaths() = testApplication {
        val storage = InMemoryAuditLogStorage()

        application {
            install(AuditLog) {
                this.storage = storage
                excludedPaths = setOf("/health", "/metrics")
            }

            routing {
                get("/health") {
                    call.respond("OK")
                }
                get("/test") {
                    call.respond("Hello")
                }
            }
        }

        // Make requests to both excluded and included paths
        client.get("/health")
        client.get("/test")

        // Wait a bit for async storage
        delay(100)

        // Verify only the non-excluded path was logged
        val entries = storage.getAllEntries()
        assertEquals(1, entries.size)
        assertEquals("/test", entries.first().path)
    }

    @Test
    fun testAuditLogHeaderFiltering() = testApplication {
        val storage = InMemoryAuditLogStorage()

        application {
            install(AuditLog) {
                this.storage = storage
                excludedHeaders = setOf("authorization", "secret-header")
            }

            routing {
                get("/test") {
                    call.respond("Hello")
                }
            }
        }

        // Make a request with various headers
        client.get("/test") {
            header("Authorization", "Bearer token")
            header("Secret-Header", "secret-value")
            header("User-Agent", "Test-Agent")
            header("Accept", "application/json")
        }

        // Wait a bit for async storage
        delay(100)

        // Verify headers were filtered correctly
        val entries = storage.getAllEntries()
        assertEquals(1, entries.size)

        val entry = entries.first()
        assertFalse(entry.headers.containsKey("Authorization"))
        assertFalse(entry.headers.containsKey("Secret-Header"))
        assertTrue(entry.headers.containsKey("User-Agent"))
        assertTrue(entry.headers.containsKey("Accept"))
    }

    @Test
    fun testAuditLogWithRouteMarking() = testApplication {
        val storage = InMemoryAuditLogStorage()

        application {
            install(AuditLog) {
                this.storage = storage
                // Disable global auditing - only audit marked routes
                shouldAudit = { _, _ -> false }
            }

            routing {
                get("/not-audited") {
                    call.respond("Not audited")
                }

                audit("user-operations") {
                    get("/users") {
                        call.respond("Users list")
                    }

                    post("/users") {
                        call.respond("User created")
                    }
                }
            }
        }

        // Make requests to both audited and non-audited routes
        client.get("/not-audited")
        client.get("/users")
        client.post("/users")

        // Wait a bit for async storage
        delay(100)

        // Verify only the marked routes were logged
        val entries = storage.getAllEntries()
        assertEquals(2, entries.size)

        val getUsersEntry = entries.find { it.method == "GET" && it.path == "/users" }
        val postUsersEntry = entries.find { it.method == "POST" && it.path == "/users" }

        assertNotNull(getUsersEntry)
        assertNotNull(postUsersEntry)
    }

    @Test
    fun testAuditLogWithRequestBody() = testApplication {
        val storage = InMemoryAuditLogStorage()

        application {
            install(AuditLog) {
                this.storage = storage
                includeRequestBody = true
            }
            install(DoubleReceive) { cacheRawRequest = true }
            routing {
                post("/users") {
                    call.respond("User created")
                }

                get("/users") {
                    call.respond("Users list")
                }
            }
        }

        val requestBody = """{"name": "John", "email": "john@example.com"}"""

        // Make a POST request with body
        client.post("/users") {
            contentType(ContentType.Application.Json)
            setBody(requestBody)
        }

        // Make a GET request (no body)
        client.get("/users")

        // Wait a bit for async storage
        delay(100)

        // Verify request body was captured for POST but not GET
        val entries = storage.getAllEntries()
        assertEquals(2, entries.size)

        val postEntry = entries.find { it.method == "POST" }
        val getEntry = entries.find { it.method == "GET" }

        assertNotNull(postEntry)
        assertNotNull(getEntry)
        assertEquals(requestBody, postEntry.requestBody)
        assertNull(getEntry.requestBody)
    }

    @Test
    fun testAuditLogWithRequestBodyAndApplicationCanStillReadIt() = testApplication {
        val storage = InMemoryAuditLogStorage()

        application {
            install(DoubleReceive) { cacheRawRequest = true }
            install(AuditLog) {
                this.storage = storage
                includeRequestBody = true
            }

            routing {
                post("/users") {
                    // Application should be able to read the body since audit plugin doesn't consume it
                    val requestBody = call.receiveText()
                    call.respond("Received: $requestBody")
                }
            }
        }

        val requestBody = """{"name": "John", "email": "john@example.com"}"""

        // Make a POST request with body
        val response = client.post("/users") {
            contentType(ContentType.Application.Json)
            setBody(requestBody)
        }

        // Wait a bit for async storage
        delay(100)

        // Verify that audit log was created but body capture is disabled to prevent channel consumption
        val entries = storage.getAllEntries()
        assertEquals(1, entries.size)

        val entry = entries.first()
        assertEquals("POST", entry.method)
        assertEquals(requestBody, entry.requestBody)

        // Application should have been able to read the body without issues
        assertEquals("Received: $requestBody", response.bodyAsText())
    }

    @Test
    fun testAuditLogWithRequestBodyDisabled() = testApplication {
        val storage = InMemoryAuditLogStorage()

        application {
            install(AuditLog) {
                this.storage = storage
                includeRequestBody = false
            }

            routing {
                post("/users") {
                    call.respond("User created")
                }
            }
        }

        val requestBody = """{"name": "John", "email": "john@example.com"}"""

        // Make a POST request with body
        client.post("/users") {
            contentType(ContentType.Application.Json)
            setBody(requestBody)
        }

        // Wait a bit for async storage
        delay(100)

        // Verify request body was not captured when disabled
        val entries = storage.getAllEntries()
        assertEquals(1, entries.size)

        val entry = entries.first()
        assertEquals("POST", entry.method)
        assertNull(entry.requestBody)
    }
}
