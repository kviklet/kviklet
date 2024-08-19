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
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.hasSize
import org.hamcrest.Matchers.notNullValue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.io.FileUrlResource
import org.springframework.jdbc.datasource.init.ScriptUtils
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ExecutionTest {

    @Autowired private lateinit var mockMvc: MockMvc

    @Autowired private lateinit var userHelper: UserHelper

    @Autowired private lateinit var roleHelper: RoleHelper

    @Autowired private lateinit var connectionHelper: ConnectionHelper

    @Autowired private lateinit var executionRequestHelper: ExecutionRequestHelper

    private lateinit var testUser: User
    private lateinit var testReviewer: User
    private lateinit var testConnection: Connection

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
        testReviewer = userHelper.createUser(permissions = listOf("*"))
        testConnection = connectionHelper.createPostgresConnection(db)
    }

    @AfterEach
    fun tearDown() {
        executionRequestHelper.deleteAll()
        connectionHelper.deleteAll()
        userHelper.deleteAll()
        roleHelper.deleteAll()
    }

    @Nested
    inner class ExecutionRequestCreationTests {
        @Test
        fun `creating execution request returns 200`() {
            val cookie = userHelper.login(email = testUser.email, mockMvc = mockMvc)
            createExecutionRequestAndAssert(cookie)
        }

        @Test
        fun `creating execution request with specific connection permissions`() {
            roleHelper.removeDefaultRolePermissions()
            val userWithSpecificPermissions = createUserWithSpecificPermissions()
            val cookie = userHelper.login(email = userWithSpecificPermissions.email, mockMvc = mockMvc)

            createExecutionRequestAndAssert(cookie)
            verifyExecutionRequestList(cookie)
        }
    }

    @Nested
    inner class ReviewActionTests {

        private lateinit var testExecutionRequest: ExecutionRequestDetails

        @BeforeEach
        fun `setup execution request`() {
            testExecutionRequest =
                executionRequestHelper.createExecutionRequest(db, testUser, connection = testConnection)
        }

        @ParameterizedTest
        @ValueSource(strings = ["APPROVE", "REJECT", "REQUEST_CHANGE"])
        fun `when performing review action then update request status`(action: String) {
            val cookie = userHelper.login(email = testReviewer.email, mockMvc = mockMvc)

            performReviewActionAndAssert(testExecutionRequest.getId(), action, "Test comment", cookie)

            val expectedStatus = when (action) {
                "APPROVE" -> "APPROVED"
                "REJECT" -> "REJECTED"
                "REQUEST_CHANGE" -> "CHANGE_REQUESTED"
                else -> throw IllegalArgumentException("Invalid action")
            }
            verifyRequestStatus(testExecutionRequest.getId(), expectedStatus, cookie)
        }

        @Test
        fun `Users cant request changes on own requests`() {
            val cookie = userHelper.login(email = testUser.email, mockMvc = mockMvc)

            requestChanges(testExecutionRequest.getId(), "Test comment", cookie)
                .andExpect(status().is4xxClientError)
        }

        @Test
        fun `Request is locked from further events after rejection`() {
            val cookie = userHelper.login(email = testReviewer.email, mockMvc = mockMvc)
            rejectRequest(testExecutionRequest.getId(), "This request is too sensitive.", cookie)
                .andExpect(status().isOk)
            verifyRequestStatus(testExecutionRequest.getId(), "REJECTED", cookie)

            performCommentAction(testExecutionRequest.getId(), "This comment should not be added", cookie)
                .andExpect(status().is4xxClientError)
            approveRequest(testExecutionRequest.getId(), "This should also not work.", cookie)
                .andExpect(status().is4xxClientError)
            executeRequest(testExecutionRequest.getId(), cookie)
                .andExpect(status().is4xxClientError)

            // Verify that no new events were added after rejection
            verifyRequestEvents(testExecutionRequest.getId(), 1, cookie)
            verifyLatestEvent(testExecutionRequest.getId(), "REVIEW", "REJECT", cookie)
        }

        @Test
        fun `General Request changes functionality test`() {
            val reviewerCookie = userHelper.login(email = testReviewer.email, mockMvc = mockMvc)
            val userCookie = userHelper.login(email = testUser.email, mockMvc = mockMvc)

            // Request changes
            requestChanges(
                testExecutionRequest.getId(),
                "Please modify the query to include additional conditions.",
                reviewerCookie,
            )
                .andExpect(status().isOk)

            // Verify the request status is CHANGE_REQUESTED
            verifyRequestStatus(testExecutionRequest.getId(), "CHANGE_REQUESTED", reviewerCookie)
            verifyRequestEvents(testExecutionRequest.getId(), 1, reviewerCookie)
            verifyLatestEvent(testExecutionRequest.getId(), "REVIEW", "REQUEST_CHANGE", reviewerCookie)

            // Simulate user updating the request
            val updatedStatement = "SELECT * FROM users WHERE active = true;"
            updateExecutionRequest(testExecutionRequest.getId(), updatedStatement, userCookie)
                .andExpect(status().isOk)

            // Verify events and status is still CHANGE_REQUESTED
            verifyRequestStatus(testExecutionRequest.getId(), "CHANGE_REQUESTED", reviewerCookie)
            verifyRequestEvents(testExecutionRequest.getId(), 2, reviewerCookie)
            verifyLatestEvent(testExecutionRequest.getId(), "EDIT", null, reviewerCookie)

            // Approve the updated request
            approveRequest(testExecutionRequest.getId(), "Changes look good. Approved.", reviewerCookie)
                .andExpect(status().isOk)

            // Verify is approved now
            verifyRequestStatus(testExecutionRequest.getId(), "APPROVED", reviewerCookie)
            verifyRequestEvents(testExecutionRequest.getId(), 3, reviewerCookie)
            verifyLatestEvent(testExecutionRequest.getId(), "REVIEW", "APPROVE", reviewerCookie)
        }

        @Test
        fun `Review permissions are necessary for reviews`() {
            val reviewerWithReviewPermissions = userHelper.createUser(
                permissions = listOf("execution_request:get", "execution_request:review"),
                resources = listOf("*", "*"),
            )
            val reviewerWithoutReviewPermissions = userHelper.createUser(
                permissions = listOf("execution_request:get"),
                resources = listOf("*"),
            )
            val reviewerWithReviewPermissionsCookie = userHelper.login(
                reviewerWithReviewPermissions.email,
                mockMvc = mockMvc,
            )
            val reviewerWithoutReviewPermissionsCookie = userHelper.login(
                reviewerWithoutReviewPermissions.email,
                mockMvc = mockMvc,
            )

            // Request changes
            requestChanges(
                testExecutionRequest.getId(),
                "Please modify the query to include additional conditions.",
                reviewerWithReviewPermissionsCookie,
            )
                .andExpect(status().isOk)

            // Request changes
            requestChanges(
                testExecutionRequest.getId(),
                "Please modify the query to include additional conditions.",
                reviewerWithoutReviewPermissionsCookie,
            )
                .andExpect(status().isForbidden)
        }
    }

    @Nested
    inner class ExecutionTests {

        private lateinit var testExecutionRequest: ExecutionRequestDetails

        @BeforeEach
        fun `setup execution request`() {
            testExecutionRequest =
                executionRequestHelper.createExecutionRequest(db, testUser, connection = testConnection)
        }

        @Test
        fun `when executing simple query then succeed`() {
            val userCookie = userHelper.login(email = testUser.email, mockMvc = mockMvc)
            val reviewerCookie = userHelper.login(email = testReviewer.email, mockMvc = mockMvc)
            approveRequest(testExecutionRequest.getId(), "Approved", reviewerCookie)

            executeRequestAndAssert(testExecutionRequest.getId(), userCookie)
            verifyExecutionsList(testExecutionRequest.getId(), userCookie)
            verifyExecutionRequestDetails(testExecutionRequest.getId(), userCookie)
        }

        @Test
        fun `only request creator can execute the request`() {
            // Create and approve an execution request
            val executionRequest = executionRequestHelper.createExecutionRequest(
                db,
                testUser,
                connection = testConnection,
            )
            val reviewerCookie = userHelper.login(email = testReviewer.email, mockMvc = mockMvc)
            approveRequest(executionRequest.getId(), "Approved", reviewerCookie)

            mockMvc.perform(
                post("/execution-requests/${executionRequest.getId()}/execute")
                    .cookie(reviewerCookie)
                    .contentType("application/json"),
            ).andExpect(status().isForbidden)
        }

        @Test
        fun `only request creator can edit the request`() {
            // Create an execution request
            val executionRequest = executionRequestHelper.createExecutionRequest(
                db,
                testUser,
                connection = testConnection,
            )

            // Login as the creator
            val creatorCookie = userHelper.login(email = testUser.email, mockMvc = mockMvc)

            // Creator should be able to edit the request
            mockMvc.perform(
                patch("/execution-requests/${executionRequest.getId()}")
                    .cookie(creatorCookie)
                    .content(
                        """
                {
                    "statement": "SELECT * FROM updated_table"
                }
                        """.trimIndent(),
                    )
                    .contentType("application/json"),
            ).andExpect(status().isOk)

            val reviewerCookie = userHelper.login(email = testReviewer.email, mockMvc = mockMvc)

            mockMvc.perform(
                patch("/execution-requests/${executionRequest.getId()}")
                    .cookie(reviewerCookie)
                    .content(
                        """
                {
                    "statement": "SELECT * FROM another_table"
                }
                        """.trimIndent(),
                    )
                    .contentType("application/json"),
            ).andExpect(status().isForbidden)
        }

        @Test
        fun `when executing simple insert then succeed`() {
            val insertRequest = executionRequestHelper.createExecutionRequest(
                db,
                testUser,
                "INSERT INTO foo.simple_table VALUES (1, 'test');",
            )
            val reviewerCookie = userHelper.login(email = testReviewer.email, mockMvc = mockMvc)
            val userCookie = userHelper.login(email = testUser.email, mockMvc = mockMvc)
            approveRequest(insertRequest.getId(), "Approved", reviewerCookie)

            executeInsertAndAssert(insertRequest.getId(), userCookie)
            verifyExecutionsList(insertRequest.getId(), userCookie)
            verifyExecutionRequestDetails(insertRequest.getId(), userCookie)
        }

        @Test
        fun `when execution errors then handle gracefully`() {
            val errorRequest = executionRequestHelper.createExecutionRequest(
                db,
                testUser,
                "INSERT INTO non_existent_table VALUES (1, 'test');",
            )
            val reviewerCookie = userHelper.login(email = testReviewer.email, mockMvc = mockMvc)
            val userCookie = userHelper.login(email = testUser.email, mockMvc = mockMvc)
            approveRequest(errorRequest.getId(), "Approved", reviewerCookie)

            executeErrorRequestAndAssert(errorRequest.getId(), userCookie)
            verifyExecutionsList(errorRequest.getId(), userCookie)
            verifyErrorExecutionRequestDetails(errorRequest.getId(), userCookie)
        }
    }

    @Test
    fun `when downloading CSV then succeed`() {
        val csvRequest = executionRequestHelper.createApprovedRequest(
            db,
            testUser,
            testReviewer,
            "SELECT * FROM foo.simple_table",
        )
        val userCookie = userHelper.login(email = testUser.email, mockMvc = mockMvc)

        val response = downloadCSV(csvRequest.getId(), userCookie)
        verifyCSVContent(response)
        verifyExecutionsList(csvRequest.getId(), userCookie)
    }

    @Test
    fun `test even wrong sql can be executed`() {
        val executionRequest = executionRequestHelper.createApprovedRequest(
            db,
            testUser,
            testReviewer,
            sql = "test",
        )
        val cookie = userHelper.login(mockMvc = mockMvc)

        mockMvc.perform(
            get("/execution-requests/${executionRequest.getId()}").cookie(cookie).contentType(
                "application/json",
            ),
        ).andExpect(status().isOk).andReturn()
    }

    private fun createUserWithSpecificPermissions(): User {
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
        return userHelper.createUser(policies = userPolicies.toSet())
    }

    private fun createExecutionRequestAndAssert(cookie: Cookie) {
        mockMvc.perform(
            post("/execution-requests/")
                .cookie(cookie)
                .content(
                    """
                    {
                        "connectionId": "${testConnection.id}",
                        "title": "Test Execution",
                        "type": "SingleExecution",
                        "statement": "SELECT * FROM test",
                        "description": "A test execution request",
                        "connectionType": "DATASOURCE"
                    }
                    """.trimIndent(),
                )
                .contentType("application/json"),
        ).andExpect(status().isOk)
    }

    private fun verifyExecutionRequestList(cookie: Cookie) {
        mockMvc.perform(
            get("/execution-requests/").cookie(cookie),
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$[0].title").value("Test Execution"))
            .andExpect(jsonPath("$[0].type").value("SingleExecution"))
            .andExpect(jsonPath("$[0].description").value("A test execution request"))
            .andExpect(jsonPath("$[0].statement").value("SELECT * FROM test"))
    }

    private fun performReviewActionAndAssert(
        executionRequestId: String,
        action: String,
        comment: String,
        cookie: Cookie,
    ) {
        mockMvc.perform(
            post("/execution-requests/$executionRequestId/reviews")
                .cookie(cookie)
                .content(
                    """
                    {
                        "comment": "$comment",
                        "action": "$action"
                    }
                    """.trimIndent(),
                )
                .contentType("application/json"),
        ).andExpect(status().isOk)
    }

    private fun verifyRequestStatus(executionRequestId: String, expectedStatus: String, cookie: Cookie) {
        mockMvc.perform(
            get("/execution-requests/$executionRequestId")
                .cookie(cookie),
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.reviewStatus").value(expectedStatus))
    }

    private fun executeRequestAndAssert(executionRequestId: String, cookie: Cookie) {
        mockMvc.perform(
            post("/execution-requests/$executionRequestId/execute")
                .cookie(cookie)
                .contentType("application/json"),
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.results[0].columns[0].label").value("?column?"))
            .andExpect(jsonPath("$.results[0].data[0]['?column?']").value("1"))
    }

    private fun executeInsertAndAssert(executionRequestId: String, cookie: Cookie) {
        mockMvc.perform(
            post("/execution-requests/$executionRequestId/execute")
                .cookie(cookie)
                .contentType("application/json"),
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.results[0].rowsUpdated").value(1))
            .andExpect(jsonPath("$.results[0].type").value("UPDATE_COUNT"))
    }

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

    private fun verifyExecutionsList(executionRequestId: String, cookie: Cookie) {
        mockMvc.perform(
            get("/executions/").cookie(cookie),
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.executions[0].requestId").value(executionRequestId))
            .andExpect(jsonPath("$.executions[0].name").value(testUser.fullName))
            .andExpect(jsonPath("$.executions[0].connectionId").value(testConnection.id.toString()))
            .andExpect(jsonPath("$.executions[0].executionTime", notNullValue()))
    }

    private fun verifyExecutionRequestDetails(executionRequestId: String, cookie: Cookie) {
        mockMvc.perform(
            get("/execution-requests/$executionRequestId").cookie(cookie),
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.executionStatus").value("EXECUTED"))
            .andExpect(jsonPath("$.reviewStatus").value("APPROVED"))
            .andExpect(jsonPath("$.events", hasSize<Collection<*>>(2)))
            .andExpect(jsonPath("$.events[0].type").value("REVIEW"))
            .andExpect(jsonPath("$.events[1].type").value("EXECUTE"))
    }

    private fun verifyErrorExecutionRequestDetails(executionRequestId: String, cookie: Cookie) {
        mockMvc.perform(
            get("/execution-requests/$executionRequestId").cookie(cookie),
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.executionStatus").value("EXECUTED"))
            .andExpect(jsonPath("$.reviewStatus").value("APPROVED"))
            .andExpect(jsonPath("$.events", hasSize<Collection<*>>(2)))
            .andExpect(jsonPath("$.events[0].type").value("REVIEW"))
            .andExpect(jsonPath("$.events[1].type").value("EXECUTE"))
            .andExpect(jsonPath("$.events[1].results[0].type").value("ERROR"))
    }

    private fun downloadCSV(executionRequestId: String, cookie: Cookie): String {
        val result = mockMvc.perform(
            get("/execution-requests/$executionRequestId/download")
                .cookie(cookie)
                .contentType("application/json"),
        ).andExpect(status().isOk).andReturn()
        return result.response.contentAsString
    }

    private fun verifyCSVContent(content: String) {
        assert(content.contains("col1,col2"))
        assert(content.contains("1,foo"))
        assert(content.contains("2,bar"))
    }

    private fun requestChanges(executionRequestId: String, comment: String, cookie: Cookie) =
        performReviewAction(executionRequestId, "REQUEST_CHANGE", comment, cookie)

    private fun approveRequest(executionRequestId: String, comment: String, cookie: Cookie) =
        performReviewAction(executionRequestId, "APPROVE", comment, cookie)

    private fun rejectRequest(executionRequestId: String, comment: String, cookie: Cookie) =
        performReviewAction(executionRequestId, "REJECT", comment, cookie)

    private fun performReviewAction(executionRequestId: String, action: String, comment: String, cookie: Cookie) =
        mockMvc.perform(
            post("/execution-requests/$executionRequestId/reviews")
                .cookie(cookie)
                .content(
                    """
                {
                    "comment": "$comment",
                    "action": "$action"
                }
                    """.trimIndent(),
                )
                .contentType("application/json"),
        )

    private fun executeRequest(executionRequestId: String, cookie: Cookie) = mockMvc.perform(
        post("/execution-requests/$executionRequestId/execute")
            .cookie(cookie)
            .contentType("application/json"),
    )

    private fun performCommentAction(executionRequestId: String, comment: String, cookie: Cookie) = mockMvc.perform(
        post("/execution-requests/$executionRequestId/comments")
            .cookie(cookie)
            .content(
                """
                {
                    "comment": "$comment"
                }
                """.trimIndent(),
            )
            .contentType("application/json"),
    )

    private fun updateExecutionRequest(executionRequestId: String, updatedStatement: String, cookie: Cookie) =
        mockMvc.perform(
            patch("/execution-requests/$executionRequestId")
                .cookie(cookie)
                .content(
                    """
                {
                    "statement": "$updatedStatement"
                }
                    """.trimIndent(),
                )
                .contentType("application/json"),
        )

    private fun verifyRequestEvents(executionRequestId: String, expectedEventCount: Int, cookie: Cookie) =
        mockMvc.perform(
            get("/execution-requests/$executionRequestId")
                .cookie(cookie),
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.events", hasSize<Collection<*>>(expectedEventCount)))

    private fun verifyLatestEvent(
        executionRequestId: String,
        expectedType: String,
        expectedAction: String?,
        cookie: Cookie,
    ) {
        val response = mockMvc.perform(
            get("/execution-requests/$executionRequestId")
                .cookie(cookie),
        ).andExpect(status().isOk)
        response.andExpect(jsonPath("$.events[-1].type").value(expectedType))
        if (expectedAction != null) {
            response.andExpect(jsonPath("$.events[-1].action").value(expectedAction))
        }
    }
}
