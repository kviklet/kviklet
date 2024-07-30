package dev.kviklet.kviklet

import com.mongodb.client.MongoClients
import dev.kviklet.kviklet.db.User
import dev.kviklet.kviklet.helper.ConnectionHelper
import dev.kviklet.kviklet.helper.ExecutionRequestHelper
import dev.kviklet.kviklet.helper.RoleHelper
import dev.kviklet.kviklet.helper.UserHelper
import dev.kviklet.kviklet.service.dto.Connection
import dev.kviklet.kviklet.service.dto.ExecutionRequestDetails
import jakarta.servlet.http.Cookie
import org.hamcrest.Matchers.hasSize
import org.hamcrest.Matchers.notNullValue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.utility.DockerImageName
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MongoDBExecutionTest {

    @Autowired private lateinit var mockMvc: MockMvc

    @Autowired private lateinit var userHelper: UserHelper

    @Autowired private lateinit var roleHelper: RoleHelper

    @Autowired private lateinit var connectionHelper: ConnectionHelper

    @Autowired private lateinit var executionRequestHelper: ExecutionRequestHelper

    private lateinit var testUser: User
    private lateinit var testReviewer: User
    private lateinit var testConnection: Connection

    companion object {
        val mongoDb: MongoDBContainer = MongoDBContainer(DockerImageName.parse("mongo:4.0.10"))
            .withReuse(true)

        init {
            mongoDb.start()
        }
    }

    @BeforeEach
    fun setup() {
        testUser = userHelper.createUser(permissions = listOf("*"))
        testReviewer = userHelper.createUser(permissions = listOf("*"))
        testConnection = connectionHelper.createMongoDBConnection(mongoDb, "db")
    }

    @AfterEach
    fun tearDown() {
        MongoClients.create(mongoDb.connectionString).use { client ->
            client.getDatabase("db").drop()
        }
        executionRequestHelper.deleteAll()
        connectionHelper.deleteAll()
        userHelper.deleteAll()
        roleHelper.deleteAll()
    }

    @Nested
    inner class ExecutionTests {

        private lateinit var testExecutionRequest: ExecutionRequestDetails

        @BeforeEach
        fun `setup execution request`() {
            testExecutionRequest = executionRequestHelper.createExecutionRequest(
                author = testUser,
                connection = testConnection,
                statement = """
                    {
                        "find": "testCollection",
                        "filter": {}
                    }
                """.trimIndent(),
            )
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
        fun `when executing insert then succeed`() {
            val insertRequest = executionRequestHelper.createExecutionRequest(
                author = testUser,
                statement = """
                    {
                        "insert": "testCollection",
                        "documents": [
                            {
                                "name": "Test",
                                "value": 123
                            }
                        ]
                    }
                """.trimIndent(),
                connection = testConnection,
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
                author = testUser,
                statement = """
                    {
                        "find": "testCollection",
                        "filter": {
                            "${"$"}invalidOperator": {
                                "field": "value"
                            }
                        }
                    }
                """.trimIndent(),
                connection = testConnection,
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
    fun `when inserting and then finding a document, it should succeed`() {
        val userCookie = userHelper.login(email = testUser.email, mockMvc = mockMvc)
        val reviewerCookie = userHelper.login(email = testReviewer.email, mockMvc = mockMvc)

        // Insert a document
        val insertRequest = executionRequestHelper.createExecutionRequest(
            author = testUser,
            connection = testConnection,
            statement = """
                    {
                        "insert": "testCollection",
                        "documents": [
                            {
                                "name": "John Doe",
                                "age": 30,
                                "city": "New York"
                            }
                        ]
                    }
            """.trimIndent(),
        )
        approveRequest(insertRequest.getId(), "Approved", reviewerCookie)
        executeInsertAndAssert(insertRequest.getId(), userCookie)

        // Find the inserted document
        val findRequest = executionRequestHelper.createExecutionRequest(
            author = testUser,
            connection = testConnection,
            statement = """
                    {
                        "find": "testCollection",
                        "filter": { "name": "John Doe" }
                    }
            """.trimIndent(),
        )
        approveRequest(findRequest.getId(), "Approved", reviewerCookie)
        executeFindAndAssert(findRequest.getId(), userCookie)
    }

    private fun approveRequest(executionRequestId: String, comment: String, cookie: Cookie) = mockMvc.perform(
        post("/execution-requests/$executionRequestId/reviews")
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

    private fun executeRequestAndAssert(executionRequestId: String, cookie: Cookie) {
        mockMvc.perform(
            post("/execution-requests/$executionRequestId/execute")
                .cookie(cookie)
                .contentType("application/json"),
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.results[0].type").value("DOCUMENTS"))
            .andExpect(jsonPath("$.results[0].documents", hasSize<Collection<*>>(0)))
    }

    private fun executeInsertAndAssert(executionRequestId: String, cookie: Cookie) {
        mockMvc.perform(
            post("/execution-requests/$executionRequestId/execute")
                .cookie(cookie)
                .contentType("application/json"),
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.results[0].type").value("UPDATE_COUNT"))
            .andExpect(jsonPath("$.results[0].rowsUpdated").value(1))
    }

    private fun executeErrorRequestAndAssert(executionRequestId: String, cookie: Cookie) {
        mockMvc.perform(
            post("/execution-requests/$executionRequestId/execute")
                .cookie(cookie)
                .contentType("application/json"),
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.results[0].type").value("ERROR"))
            .andExpect(jsonPath("$.results[0].message").isString)
    }

    private fun executeFindAndAssert(executionRequestId: String, cookie: Cookie) {
        mockMvc.perform(
            post("/execution-requests/$executionRequestId/execute")
                .cookie(cookie)
                .contentType("application/json"),
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.results[0].type").value("DOCUMENTS"))
            .andExpect(jsonPath("$.results[0].documents").isArray)
            .andExpect(jsonPath("$.results[0].documents[0].name").value("John Doe"))
            .andExpect(jsonPath("$.results[0].documents[0].age").value(30))
            .andExpect(jsonPath("$.results[0].documents[0].city").value("New York"))
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
}
