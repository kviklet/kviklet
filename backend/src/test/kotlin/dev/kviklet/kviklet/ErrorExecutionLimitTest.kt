package dev.kviklet.kviklet

import dev.kviklet.kviklet.db.User
import dev.kviklet.kviklet.helper.ConnectionHelper
import dev.kviklet.kviklet.helper.ExecutionRequestHelper
import dev.kviklet.kviklet.helper.RoleHelper
import dev.kviklet.kviklet.helper.UserHelper
import dev.kviklet.kviklet.service.dto.Connection
import dev.kviklet.kviklet.service.dto.RequestType
import jakarta.servlet.http.Cookie
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class ErrorExecutionLimitTest {

    @Container
    val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:11.1"))
        .withUsername("root")
        .withPassword("root")
        .withReuse(true)
        .withDatabaseName("test_db")

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var connectionHelper: ConnectionHelper

    @Autowired
    private lateinit var userHelper: UserHelper

    @Autowired
    private lateinit var roleHelper: RoleHelper

    @Autowired
    private lateinit var executionRequestHelper: ExecutionRequestHelper

    private lateinit var testConnection: Connection
    private lateinit var testUser: User
    private lateinit var testReviewer: User
    private lateinit var userCookie: Cookie
    private lateinit var reviewerCookie: Cookie

    @BeforeEach
    fun setUp() {
        postgres.start()
        testConnection = connectionHelper.createPostgresConnection(postgres, maxExecutions = 1)
        testUser = userHelper.createUser(permissions = listOf("*"))
        testReviewer = userHelper.createUser(permissions = listOf("*"))
        userCookie = userHelper.login(email = testUser.email, mockMvc = mockMvc)
        reviewerCookie = userHelper.login(email = testReviewer.email, mockMvc = mockMvc)
    }

    @AfterEach
    fun tearDown() {
        executionRequestHelper.deleteAll()
        connectionHelper.deleteAll()
        userHelper.deleteAll()
        roleHelper.deleteAll()
    }

    @Test
    fun `when execution errors it should not count toward the execution limit`() {
        // Create an execution request with a failing query
        val executionRequest = executionRequestHelper.createExecutionRequest(
            author = testUser,
            statement = "SELECT * FROM non_existent_table;",
            connection = testConnection,
        )

        // Approve the request
        approveRequest(executionRequest.getId(), "Approved", reviewerCookie)

        // Execute the request (should fail but still allow retry)
        executeErrorRequestAndAssert(executionRequest.getId(), userCookie)

        // Verify the execution status is still EXECUTABLE
        mockMvc.perform(
            get("/execution-requests/${executionRequest.getId()}").cookie(userCookie),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.executionStatus").value("EXECUTABLE"))

        // Update the execution request with a valid query
        mockMvc.perform(
            patch("/execution-requests/${executionRequest.getId()}")
                .cookie(userCookie)
                .content(
                    """
                    {
                        "statement": "SELECT 1 AS value;"
                    }
                    """.trimIndent(),
                )
                .contentType("application/json"),
        ).andExpect(status().isOk)

        // Approve the request again
        approveRequest(executionRequest.getId(), "Approved after fix", reviewerCookie)

        // Execute the request (should succeed)
        mockMvc.perform(
            post("/execution-requests/${executionRequest.getId()}/execute")
                .cookie(userCookie)
                .contentType("application/json"),
        ).andExpect(status().isOk)

        // Verify the execution status is now EXECUTED
        mockMvc.perform(
            get("/execution-requests/${executionRequest.getId()}").cookie(userCookie),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.executionStatus").value("EXECUTED"))
    }

    @Test
    fun `temporary access request should not change status after a failed statement`() {
        // Create a temporary access execution request with a failing query
        val temporaryAccessRequest = executionRequestHelper.createExecutionRequest(
            author = testUser,
            connection = testConnection,
            statement = null,
            requestType = RequestType.TemporaryAccess,
        )

        // Approve the request
        approveRequest(temporaryAccessRequest.getId(), "Approved", reviewerCookie)

        // Execute the request (should fail)
        executeOnTemporaryAccessRequest(temporaryAccessRequest.getId(), userCookie, "SELECT * FROM non_existent_table;")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.results[0].errorCode").value(0))
            .andExpect(
                jsonPath(
                    "$.results[0].message",
                ).value(containsString("relation \"non_existent_table\" does not exist")),
            )
            .andExpect(jsonPath("$.results[0].type").value("ERROR"))

        // Verify the status is still EXECUTABLE (not changed by the error)
        mockMvc.perform(
            get("/execution-requests/${temporaryAccessRequest.getId()}").cookie(userCookie),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.executionStatus").value("ACTIVE"))
            .andExpect(jsonPath("$.reviewStatus").value("APPROVED"))

        // Try again with a valid query without needing re-approval
        executeOnTemporaryAccessRequest(
            temporaryAccessRequest.getId(),
            userCookie,
            "SELECT 1 AS value;",
        ).andExpect(status().isOk)
    }

    private fun approveRequest(executionRequestId: String, comment: String, cookie: Cookie) = mockMvc.perform(
        post("/execution-requests/$executionRequestId/reviews")
            .cookie(cookie)
            .content(
                """
                    {
                        "action": "APPROVE",
                        "comment": "$comment"
                    }
                """.trimIndent(),
            )
            .contentType("application/json"),
    ).andExpect(status().isOk)

    private fun executeOnTemporaryAccessRequest(executionRequestId: String, cookie: Cookie, query: String) =
        mockMvc.perform(
            post("/execution-requests/$executionRequestId/execute")
                .cookie(cookie)
                .content("""{"query": "$query"}""")
                .contentType("application/json"),
        )

    private fun executeErrorRequestAndAssert(executionRequestId: String, cookie: Cookie) {
        mockMvc.perform(
            post("/execution-requests/$executionRequestId/execute")
                .cookie(cookie)
                .contentType("application/json"),
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.results[0].errorCode").value(0))
            .andExpect(
                jsonPath(
                    "$.results[0].message",
                ).value(containsString("relation \"non_existent_table\" does not exist")),
            )
            .andExpect(jsonPath("$.results[0].type").value("ERROR"))
    }
}
