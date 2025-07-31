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
import java.util.UUID
import kotlinx.coroutines.delay
import kotlin.test.*
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

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
                principal = { call ->
                    val principal = call.principal<UserIdPrincipal>()
                    if (principal != null) {
                        AuditLogPrincipal(UUID.randomUUID().toString(), principal.name, "User")
                    } else {
                        null
                    }
                }
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
        assertTrue(entry.principal is AuditLogPrincipal)
        assertEquals("User", (entry.principal as AuditLogPrincipal).name)
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

    @OptIn(ExperimentalTime::class)
    @Test
    fun testAuditLogSearchByMethod() = testApplication {
        val storage = InMemoryAuditLogStorage()

        application {
            install(AuditLog) {
                this.storage = storage
            }

            routing {
                get("/test") { call.respond("GET response") }
                post("/test") { call.respond("POST response") }
                put("/test") { call.respond("PUT response") }
            }
        }

        // Make requests with different methods
        client.get("/test")
        client.post("/test")
        client.put("/test")

        delay(100)

        // Search for POST requests only
        val postResults = storage.search(InMemorySearchCriteria(method = "POST"))
        assertEquals(1, postResults.size)
        assertEquals("POST", postResults.first().method)

        // Search for GET requests only
        val getResults = storage.search(InMemorySearchCriteria(method = "GET"))
        assertEquals(1, getResults.size)
        assertEquals("GET", getResults.first().method)
    }

    @OptIn(ExperimentalTime::class)
    @Test
    fun testAuditLogSearchByPath() = testApplication {
        val storage = InMemoryAuditLogStorage()

        application {
            install(AuditLog) {
                this.storage = storage
            }

            routing {
                get("/users") { call.respond("Users") }
                get("/orders") { call.respond("Orders") }
                get("/products") { call.respond("Products") }
            }
        }

        // Make requests to different paths
        client.get("/users")
        client.get("/orders")
        client.get("/products")

        delay(100)

        // Search for specific path
        val userResults = storage.search(InMemorySearchCriteria(path = "/users"))
        assertEquals(1, userResults.size)
        assertEquals("/users", userResults.first().path)

        // Search for non-existent path
        val nonExistentResults = storage.search(InMemorySearchCriteria(path = "/nonexistent"))
        assertEquals(0, nonExistentResults.size)
    }

    @OptIn(ExperimentalTime::class)
    @Test
    fun testAuditLogSearchByPrincipal() = testApplication {
        val storage = InMemoryAuditLogStorage()

        application {
            install(Authentication) {
                basic("basic") {
                    validate { credentials ->
                        when (credentials.name) {
                            "user1" -> UserIdPrincipal("user1")
                            "user2" -> UserIdPrincipal("user2")
                            else -> null
                        }
                    }
                }
            }

            install(AuditLog) {
                this.storage = storage
                principal = { call ->
                    val principal = call.principal<UserIdPrincipal>()
                    if (principal != null) {
                        AuditLogPrincipal(UUID.randomUUID().toString(), "User", principal.name)
                    } else {
                        null
                    }
                }
            }

            routing {
                authenticate("basic") {
                    get("/protected") { call.respond("Protected resource") }
                }
            }
        }

        // Make authenticated requests with different users
        client.get("/protected") {
            header(HttpHeaders.Authorization, "Basic dXNlcjE6cGFzcw==") // user1:pass
        }
        client.get("/protected") {
            header(HttpHeaders.Authorization, "Basic dXNlcjI6cGFzcw==") // user2:pass
        }

        delay(100)

        // Search by principal name
        val user1Results = storage.search(InMemorySearchCriteria(principal = "user1"))
        assertEquals(1, user1Results.size)
        assertEquals("user1", (user1Results.first().principal as AuditLogPrincipal).name)

        val user2Results = storage.search(InMemorySearchCriteria(principal = "user2"))
        assertEquals(1, user2Results.size)
        assertEquals("user2", (user2Results.first().principal as AuditLogPrincipal).name)
    }

    @OptIn(ExperimentalTime::class)
    @Test
    fun testAuditLogSearchByTimestamp() = testApplication {
        val storage = InMemoryAuditLogStorage()

        application {
            install(AuditLog) {
                this.storage = storage
            }

            routing {
                get("/test") { call.respond("Test") }
            }
        }

        val beforeTime = Clock.System.now()
        
        // Make a request
        client.get("/test")
        delay(100)
        
        val afterTime = Clock.System.now()

        // Search by timestamp range
        val results = storage.search(InMemorySearchCriteria(
            timestampFrom = beforeTime,
            timestampTo = afterTime
        ))
        assertEquals(1, results.size)

        // Search with timestamp range that excludes the entry
        val futureTime = afterTime.plus(kotlin.time.Duration.parse("1h"))
        val noResults = storage.search(InMemorySearchCriteria(
            timestampFrom = futureTime
        ))
        assertEquals(0, noResults.size)
    }

    @OptIn(ExperimentalTime::class)
    @Test
    fun testAuditLogSearchCombinedParameters() = testApplication {
        val storage = InMemoryAuditLogStorage()

        application {
            install(AuditLog) {
                this.storage = storage
            }

            routing {
                get("/users") { call.respond("GET Users") }
                post("/users") { call.respond("POST Users") }
                get("/orders") { call.respond("GET Orders") }
            }
        }

        // Make multiple requests
        client.get("/users")
        client.post("/users")
        client.get("/orders")

        delay(100)

        // Search with combined parameters
        val results = storage.search(InMemorySearchCriteria(
            method = "GET",
            path = "/users"
        ))
        assertEquals(1, results.size)
        assertEquals("GET", results.first().method)
        assertEquals("/users", results.first().path)

        // Search that should return no results
        val noResults = storage.search(InMemorySearchCriteria(
            method = "DELETE",
            path = "/users"
        ))
        assertEquals(0, noResults.size)
    }

    @OptIn(ExperimentalTime::class)
    @Test
    fun testAuditLogSearchPagination() = testApplication {
        val storage = InMemoryAuditLogStorage()

        application {
            install(AuditLog) {
                this.storage = storage
            }

            routing {
                get("/test") { call.respond("Test") }
            }
        }

        // Make multiple requests to have enough entries for pagination
        repeat(5) {
            client.get("/test")
        }

        delay(100)

        // Test pagination
        val firstPage = storage.search(InMemorySearchCriteria(method = "GET"), limit = 2, offset = 0)
        assertEquals(2, firstPage.size)

        val secondPage = storage.search(InMemorySearchCriteria(method = "GET"), limit = 2, offset = 2)
        assertEquals(2, secondPage.size)

        val thirdPage = storage.search(InMemorySearchCriteria(method = "GET"), limit = 2, offset = 4)
        assertEquals(1, thirdPage.size)

        // Verify no duplicate entries between pages
        val allIds = (firstPage + secondPage + thirdPage).map { it.timestamp }.toSet()
        assertEquals(5, allIds.size)
    }

    @OptIn(ExperimentalTime::class)
    @Test
    fun testAuditLogSearchWithEmptyCriteria() = testApplication {
        val storage = InMemoryAuditLogStorage()

        application {
            install(AuditLog) {
                this.storage = storage
            }

            routing {
                get("/test") { call.respond("Test") }
            }
        }

        client.get("/test")
        delay(100)

        // Search with empty criteria should return all entries
        val results = storage.search(InMemorySearchCriteria())
        assertEquals(1, results.size)
    }
}
