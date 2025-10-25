package dev.kviklet.kviklet

import dev.kviklet.kviklet.db.User
import dev.kviklet.kviklet.helper.ConnectionHelper
import dev.kviklet.kviklet.helper.ExecutionRequestHelper
import dev.kviklet.kviklet.helper.RoleHelper
import dev.kviklet.kviklet.helper.UserHelper
import dev.kviklet.kviklet.service.dto.Connection
import dev.kviklet.kviklet.service.dto.ExecutionRequestDetails
import dev.kviklet.kviklet.service.dto.Policy
import dev.kviklet.kviklet.service.dto.PolicyEffect
import jakarta.servlet.http.Cookie
import org.hamcrest.Matchers.hasSize
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.io.FileUrlResource
import org.springframework.jdbc.datasource.init.ScriptUtils
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.time.format.DateTimeFormatter

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ExecutionRequestPaginationTest {

    @Autowired private lateinit var mockMvc: MockMvc

    @Autowired private lateinit var userHelper: UserHelper

    @Autowired private lateinit var roleHelper: RoleHelper

    @Autowired private lateinit var connectionHelper: ConnectionHelper

    @Autowired private lateinit var executionRequestHelper: ExecutionRequestHelper

    private lateinit var testUser: User
    private lateinit var testConnection: Connection
    private lateinit var testConnection2: Connection

    companion object {
        val db: PostgreSQLContainer<*> = PostgreSQLContainer(DockerImageName.parse("postgres:11.1"))
            .withUsername("root")
            .withPassword("root")
            .withReuse(true)
            .withDatabaseName("test_db")

        init {
            db.start()
        }
    }

    @BeforeEach
    fun setup() {
        val initScript = this::class.java.classLoader.getResource("psql_init.sql")!!
        ScriptUtils.executeSqlScript(db.createConnection(""), FileUrlResource(initScript))

        testUser = userHelper.createUser(permissions = listOf("*"))
        testConnection = connectionHelper.createPostgresConnection(db)
        testConnection2 = connectionHelper.createPostgresConnection(db)
    }

    @AfterEach
    fun tearDown() {
        executionRequestHelper.deleteAll()
        connectionHelper.deleteAll()
        userHelper.deleteAll()
        roleHelper.deleteAll()
    }

    @Nested
    inner class FilterTests {

        @Test
        fun `filter by review status returns only matching requests`() {
            val cookie = userHelper.login(email = testUser.email, mockMvc = mockMvc)

            // Create requests with different states
            val req1 = executionRequestHelper.createExecutionRequest(db, testUser, connection = testConnection)
            val req2 = executionRequestHelper.createExecutionRequest(db, testUser, connection = testConnection)
            val reviewer = userHelper.createUser(permissions = listOf("*"))
            val reviewerCookie = userHelper.login(email = reviewer.email, mockMvc = mockMvc)

            // Approve one request
            approveRequest(req1.getId(), "Approved", reviewerCookie)

            // Filter for AWAITING_APPROVAL
            mockMvc.perform(
                get("/execution-requests/")
                    .param("reviewStatuses", "AWAITING_APPROVAL")
                    .cookie(cookie),
            ).andExpect(status().isOk)
                .andExpect(jsonPath("$.requests", hasSize<Collection<*>>(1)))
                .andExpect(jsonPath("$.requests[0].id").value(req2.getId()))
                .andExpect(jsonPath("$.requests[0].reviewStatus").value("AWAITING_APPROVAL"))

            // Filter for APPROVED
            mockMvc.perform(
                get("/execution-requests/")
                    .param("reviewStatuses", "APPROVED")
                    .cookie(cookie),
            ).andExpect(status().isOk)
                .andExpect(jsonPath("$.requests", hasSize<Collection<*>>(1)))
                .andExpect(jsonPath("$.requests[0].id").value(req1.getId()))
                .andExpect(jsonPath("$.requests[0].reviewStatus").value("APPROVED"))
        }

        @Test
        fun `filter by execution status returns only matching requests`() {
            val cookie = userHelper.login(email = testUser.email, mockMvc = mockMvc)
            val reviewer = userHelper.createUser(permissions = listOf("*"))
            val reviewerCookie = userHelper.login(email = reviewer.email, mockMvc = mockMvc)

            // Create and execute one request
            val req1 = executionRequestHelper.createExecutionRequest(db, testUser, connection = testConnection)
            approveRequest(req1.getId(), "Approved", reviewerCookie)
            executeRequest(req1.getId(), cookie)

            // Create another that's not executed
            val req2 = executionRequestHelper.createExecutionRequest(db, testUser, connection = testConnection)

            // Filter for EXECUTED
            mockMvc.perform(
                get("/execution-requests/")
                    .param("executionStatuses", "EXECUTED")
                    .cookie(cookie),
            ).andExpect(status().isOk)
                .andExpect(jsonPath("$.requests", hasSize<Collection<*>>(1)))
                .andExpect(jsonPath("$.requests[0].id").value(req1.getId()))
                .andExpect(jsonPath("$.requests[0].executionStatus").value("EXECUTED"))

            // Filter for EXECUTABLE
            mockMvc.perform(
                get("/execution-requests/")
                    .param("executionStatuses", "EXECUTABLE")
                    .cookie(cookie),
            ).andExpect(status().isOk)
                .andExpect(jsonPath("$.requests", hasSize<Collection<*>>(1)))
                .andExpect(jsonPath("$.requests[0].id").value(req2.getId()))
                .andExpect(jsonPath("$.requests[0].executionStatus").value("EXECUTABLE"))
        }

        @Test
        fun `filter by connection returns only requests for that connection`() {
            val cookie = userHelper.login(email = testUser.email, mockMvc = mockMvc)

            // Create requests for different connections
            val req1 = executionRequestHelper.createExecutionRequest(db, testUser, connection = testConnection)
            val req2 = executionRequestHelper.createExecutionRequest(db, testUser, connection = testConnection2)

            // Filter by connection1
            mockMvc.perform(
                get("/execution-requests/")
                    .param("connectionId", testConnection.id.toString())
                    .cookie(cookie),
            ).andExpect(status().isOk)
                .andExpect(jsonPath("$.requests", hasSize<Collection<*>>(1)))
                .andExpect(jsonPath("$.requests[0].id").value(req1.getId()))
                .andExpect(jsonPath("$.requests[0].connection.id").value(testConnection.id.toString()))

            // Filter by connection2
            mockMvc.perform(
                get("/execution-requests/")
                    .param("connectionId", testConnection2.id.toString())
                    .cookie(cookie),
            ).andExpect(status().isOk)
                .andExpect(jsonPath("$.requests", hasSize<Collection<*>>(1)))
                .andExpect(jsonPath("$.requests[0].id").value(req2.getId()))
                .andExpect(jsonPath("$.requests[0].connection.id").value(testConnection2.id.toString()))
        }
    }

    @Nested
    inner class PaginationTests {

        @Test
        fun `after parameter returns only requests created before the given timestamp`() {
            val cookie = userHelper.login(email = testUser.email, mockMvc = mockMvc)

            // Create three requests
            val req1 = executionRequestHelper.createExecutionRequest(db, testUser, connection = testConnection)
            Thread.sleep(10) // Ensure different timestamps
            val req2 = executionRequestHelper.createExecutionRequest(db, testUser, connection = testConnection)
            Thread.sleep(10)
            val req3 = executionRequestHelper.createExecutionRequest(db, testUser, connection = testConnection)

            // Format req2's timestamp in ISO 8601 format with Z
            // Pagination is reverse chronological (newest first), so "after" means "older than"
            val afterTimestamp = req2.request.createdAt.atZone(java.time.ZoneOffset.UTC)
                .format(DateTimeFormatter.ISO_INSTANT)

            // Get requests older than req2 (should return req1 only)
            mockMvc.perform(
                get("/execution-requests/")
                    .param("after", afterTimestamp)
                    .cookie(cookie),
            ).andExpect(status().isOk)
                .andExpect(jsonPath("$.requests", hasSize<Collection<*>>(1)))
                .andExpect(jsonPath("$.requests[0].id").value(req1.getId()))
        }

        @Test
        fun `limit parameter restricts number of results`() {
            val cookie = userHelper.login(email = testUser.email, mockMvc = mockMvc)

            // Create 5 requests
            repeat(5) {
                executionRequestHelper.createExecutionRequest(db, testUser, connection = testConnection)
                Thread.sleep(5) // Ensure different timestamps
            }

            // Request only 2 results
            mockMvc.perform(
                get("/execution-requests/")
                    .param("limit", "2")
                    .cookie(cookie),
            ).andExpect(status().isOk)
                .andExpect(jsonPath("$.requests", hasSize<Collection<*>>(2)))
                .andExpect(jsonPath("$.hasMore").value(true))
                .andExpect(jsonPath("$.cursor").exists())
        }

        @Test
        fun `no limit returns all requests`() {
            val cookie = userHelper.login(email = testUser.email, mockMvc = mockMvc)

            // Create 5 requests
            repeat(5) {
                executionRequestHelper.createExecutionRequest(db, testUser, connection = testConnection)
            }

            // Don't send limit parameter
            mockMvc.perform(
                get("/execution-requests/")
                    .cookie(cookie),
            ).andExpect(status().isOk)
                .andExpect(jsonPath("$.requests", hasSize<Collection<*>>(5)))
                .andExpect(jsonPath("$.hasMore").value(false))
        }

        @Test
        fun `cursor-based pagination works correctly`() {
            val cookie = userHelper.login(email = testUser.email, mockMvc = mockMvc)

            // Create 5 requests
            val requests = mutableListOf<ExecutionRequestDetails>()
            repeat(5) {
                requests.add(executionRequestHelper.createExecutionRequest(db, testUser, connection = testConnection))
                Thread.sleep(5) // Ensure different timestamps
            }

            // First page: limit=2
            val firstPage = mockMvc.perform(
                get("/execution-requests/")
                    .param("limit", "2")
                    .cookie(cookie),
            ).andExpect(status().isOk)
                .andExpect(jsonPath("$.requests", hasSize<Collection<*>>(2)))
                .andExpect(jsonPath("$.hasMore").value(true))
                .andExpect(jsonPath("$.cursor").exists())
                .andReturn()

            val cursorRaw = com.jayway.jsonpath.JsonPath.read<String>(
                firstPage.response.contentAsString,
                "$.cursor",
            )
            // Add 'Z' timezone to cursor for use in next request
            val cursor = java.time.LocalDateTime.parse(cursorRaw)
                .atZone(java.time.ZoneOffset.UTC)
                .format(DateTimeFormatter.ISO_INSTANT)

            // Second page: use cursor
            mockMvc.perform(
                get("/execution-requests/")
                    .param("after", cursor)
                    .param("limit", "2")
                    .cookie(cookie),
            ).andExpect(status().isOk)
                .andExpect(jsonPath("$.requests", hasSize<Collection<*>>(2)))
                .andExpect(jsonPath("$.hasMore").value(true))

            // Third page: should have 1 remaining
            val secondPage = mockMvc.perform(
                get("/execution-requests/")
                    .param("after", cursor)
                    .param("limit", "2")
                    .cookie(cookie),
            ).andReturn()

            val cursor2Raw = com.jayway.jsonpath.JsonPath.read<String>(
                secondPage.response.contentAsString,
                "$.cursor",
            )
            // Add 'Z' timezone to cursor for use in next request
            val cursor2 = java.time.LocalDateTime.parse(cursor2Raw)
                .atZone(java.time.ZoneOffset.UTC)
                .format(DateTimeFormatter.ISO_INSTANT)

            mockMvc.perform(
                get("/execution-requests/")
                    .param("after", cursor2)
                    .param("limit", "2")
                    .cookie(cookie),
            ).andExpect(status().isOk)
                .andExpect(jsonPath("$.requests", hasSize<Collection<*>>(1)))
                .andExpect(jsonPath("$.hasMore").value(false))
        }
    }

    @Nested
    inner class AuthorizationFilteringTests {

        @Test
        fun `user without connection access does not see requests for that connection`() {
            roleHelper.removeDefaultRolePermissions()

            // Create a user with access only to connection1
            val userPolicies = listOf(
                Policy(
                    resource = testConnection.id.toString(),
                    action = "datasource_connection:get",
                    effect = PolicyEffect.ALLOW,
                ),
                Policy(
                    resource = testConnection.id.toString(),
                    action = "execution_request:get",
                    effect = PolicyEffect.ALLOW,
                ),
                Policy(
                    resource = testConnection.id.toString(),
                    action = "execution_request:edit",
                    effect = PolicyEffect.ALLOW,
                ),
            )
            val restrictedUser = userHelper.createUser(policies = userPolicies.toSet())
            val restrictedCookie = userHelper.login(email = restrictedUser.email, mockMvc = mockMvc)

            // Create requests for both connections (as admin)
            val req1 = executionRequestHelper.createExecutionRequest(
                db,
                restrictedUser,
                connection = testConnection,
            )
            val req2 = executionRequestHelper.createExecutionRequest(
                db,
                restrictedUser,
                connection = testConnection2,
            )

            // Restricted user should only see req1
            mockMvc.perform(
                get("/execution-requests/")
                    .cookie(restrictedCookie),
            ).andExpect(status().isOk)
                .andExpect(jsonPath("$.requests", hasSize<Collection<*>>(1)))
                .andExpect(jsonPath("$.requests[0].id").value(req1.getId()))
                .andExpect(jsonPath("$.requests[0].connection.id").value(testConnection.id.toString()))
        }

        @Test
        fun `authorization filtering works with pagination`() {
            roleHelper.removeDefaultRolePermissions()

            // Create a user with access only to connection1
            val userPolicies = listOf(
                Policy(
                    resource = testConnection.id.toString(),
                    action = "datasource_connection:get",
                    effect = PolicyEffect.ALLOW,
                ),
                Policy(
                    resource = testConnection.id.toString(),
                    action = "execution_request:get",
                    effect = PolicyEffect.ALLOW,
                ),
                Policy(
                    resource = testConnection.id.toString(),
                    action = "execution_request:edit",
                    effect = PolicyEffect.ALLOW,
                ),
            )
            val restrictedUser = userHelper.createUser(policies = userPolicies.toSet())
            val restrictedCookie = userHelper.login(email = restrictedUser.email, mockMvc = mockMvc)

            // Create 3 requests for connection1 and 2 for connection2
            repeat(3) {
                executionRequestHelper.createExecutionRequest(
                    db,
                    restrictedUser,
                    connection = testConnection,
                )
                Thread.sleep(5)
            }
            repeat(2) {
                executionRequestHelper.createExecutionRequest(
                    db,
                    restrictedUser,
                    connection = testConnection2,
                )
                Thread.sleep(5)
            }

            // User should only see 3 requests (for connection1), not 5
            mockMvc.perform(
                get("/execution-requests/")
                    .cookie(restrictedCookie),
            ).andExpect(status().isOk)
                .andExpect(jsonPath("$.requests", hasSize<Collection<*>>(3)))
                .andExpect(jsonPath("$.requests[0].connection.id").value(testConnection.id.toString()))
                .andExpect(jsonPath("$.requests[1].connection.id").value(testConnection.id.toString()))
                .andExpect(jsonPath("$.requests[2].connection.id").value(testConnection.id.toString()))
        }

        @Test
        fun `authorization filtering with limit respects both filtering and limit`() {
            roleHelper.removeDefaultRolePermissions()

            // Create a user with access only to connection1
            val userPolicies = listOf(
                Policy(
                    resource = testConnection.id.toString(),
                    action = "datasource_connection:get",
                    effect = PolicyEffect.ALLOW,
                ),
                Policy(
                    resource = testConnection.id.toString(),
                    action = "execution_request:get",
                    effect = PolicyEffect.ALLOW,
                ),
                Policy(
                    resource = testConnection.id.toString(),
                    action = "execution_request:edit",
                    effect = PolicyEffect.ALLOW,
                ),
            )
            val restrictedUser = userHelper.createUser(policies = userPolicies.toSet())
            val restrictedCookie = userHelper.login(email = restrictedUser.email, mockMvc = mockMvc)

            // Create 5 requests for connection1
            repeat(5) {
                executionRequestHelper.createExecutionRequest(
                    db,
                    restrictedUser,
                    connection = testConnection,
                )
                Thread.sleep(5)
            }

            // Request with limit=2
            mockMvc.perform(
                get("/execution-requests/")
                    .param("limit", "2")
                    .cookie(restrictedCookie),
            ).andExpect(status().isOk)
                .andExpect(jsonPath("$.requests", hasSize<Collection<*>>(2)))
                .andExpect(jsonPath("$.hasMore").value(true))
                .andExpect(jsonPath("$.cursor").exists())
        }
    }

    private fun approveRequest(executionRequestId: String, comment: String, cookie: Cookie) = mockMvc.perform(
        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
            "/execution-requests/$executionRequestId/reviews",
        )
            .cookie(cookie)
            .content(
                """
                    {
                        "comment": "$comment",
                        "action": "APPROVE"
                    }
                """.trimIndent(),
            )
            .contentType("application/json"),
    )

    private fun executeRequest(executionRequestId: String, cookie: Cookie) = mockMvc.perform(
        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
            "/execution-requests/$executionRequestId/execute",
        )
            .cookie(cookie)
            .contentType("application/json"),
    )
}
