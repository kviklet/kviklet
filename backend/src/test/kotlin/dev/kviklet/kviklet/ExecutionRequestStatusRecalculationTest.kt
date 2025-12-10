package dev.kviklet.kviklet

import dev.kviklet.kviklet.db.User
import dev.kviklet.kviklet.helper.ConnectionHelper
import dev.kviklet.kviklet.helper.ExecutionRequestHelper
import dev.kviklet.kviklet.helper.RoleHelper
import dev.kviklet.kviklet.helper.UserHelper
import dev.kviklet.kviklet.service.dto.Connection
import jakarta.servlet.http.Cookie
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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ExecutionRequestStatusRecalculationTest {

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
        testConnection = connectionHelper.createDummyConnection()
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
    fun `increasing reviewConfig requirements should recalculate request status to AWAITING_APPROVAL`() {
        // Create execution request
        val executionRequest = executionRequestHelper.createExecutionRequest(
            author = testUser,
            statement = "SELECT 1;",
            connection = testConnection,
        )

        // Approve the request with 1 review
        approveRequest(executionRequest.getId(), "Approved", reviewerCookie)

        // Verify the request is APPROVED (reviewConfig initially requires 1 review)
        mockMvc.perform(
            get("/execution-requests/${executionRequest.getId()}").cookie(userCookie),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.reviewStatus").value("APPROVED"))

        // Update connection to require 2 reviews
        mockMvc.perform(
            patch("/connections/${testConnection.getId()}")
                .cookie(userCookie)
                .content(
                    """
                    {
                        "connectionType": "DATASOURCE",
                        "reviewConfig": {
                            "groupConfigs": [{"roleId": "*", "numRequired": 2}]
                        }
                    }
                    """.trimIndent(),
                )
                .contentType("application/json"),
        )
            .andExpect(status().isOk)

        // Verify the request status is now AWAITING_APPROVAL (only has 1 review, needs 2)
        mockMvc.perform(
            get("/execution-requests/${executionRequest.getId()}").cookie(userCookie),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.reviewStatus").value("AWAITING_APPROVAL"))
    }

    @Test
    fun `increasing maxExecutions should recalculate request status`() {
        // Create connection with maxExecutions = 1
        val connectionWithLimit = connectionHelper.createDummyConnection()

        // Create and approve execution request
        val executionRequest = executionRequestHelper.createExecutionRequest(
            author = testUser,
            statement = "SELECT 1;",
            connection = connectionWithLimit,
        )

        // Approve the request
        approveRequest(executionRequest.getId(), "Approved", reviewerCookie)

        // Verify initial execution status
        mockMvc.perform(
            get("/execution-requests/${executionRequest.getId()}").cookie(userCookie),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.executionStatus").value("EXECUTABLE"))

        // Update connection to increase maxExecutions from 1 to 2
        mockMvc.perform(
            patch("/connections/${connectionWithLimit.getId()}")
                .cookie(userCookie)
                .content(
                    """
                    {
                        "connectionType": "DATASOURCE",
                        "maxExecutions": 2
                    }
                    """.trimIndent(),
                )
                .contentType("application/json"),
        )
            .andExpect(status().isOk)

        // Verify the request status is recalculated (should still be EXECUTABLE)
        mockMvc.perform(
            get("/execution-requests/${executionRequest.getId()}").cookie(userCookie),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.executionStatus").value("EXECUTABLE"))
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
}
