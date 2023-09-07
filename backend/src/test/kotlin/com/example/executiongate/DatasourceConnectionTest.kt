package com.example.executiongate

import com.example.executiongate.controller.CreateCommentRequest
import com.example.executiongate.controller.CreateDatasourceConnectionRequest
import com.example.executiongate.controller.CreateDatasourceRequest
import com.example.executiongate.controller.CreateExecutionRequest
import com.example.executiongate.controller.CreateReviewRequest
import com.example.executiongate.controller.DatasourceController
import com.example.executiongate.controller.ExecutionRequestController
import com.example.executiongate.controller.ListDatasourceResponse
import com.example.executiongate.controller.ReviewConfigRequest
import com.example.executiongate.db.DatasourceRepository
import com.example.executiongate.db.EventRepository
import com.example.executiongate.db.ExecutionRequestRepository
import com.example.executiongate.service.dto.CommentEvent
import com.example.executiongate.service.dto.DatasourceType
import com.example.executiongate.service.dto.ReviewAction
import com.example.executiongate.service.dto.ReviewEvent
import io.kotest.matchers.equality.shouldBeEqualToIgnoringFields
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDateTime

@SpringBootTest
@AutoConfigureMockMvc
class DatasourceConnectionTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val datasourceRepository: DatasourceRepository,
    @Autowired val executionRequestRepository: ExecutionRequestRepository,
    @Autowired val eventRepository: EventRepository,
    @Autowired val datasourceController: DatasourceController,
    @Autowired val executionRequestController: ExecutionRequestController,
) : TestBase() {

    @AfterEach
    fun tearDown() {
        eventRepository.deleteAll()
        executionRequestRepository.deleteAll()
        datasourceRepository.deleteAll()
    }

    @Test
    fun test1() {
        mockMvc.perform(get("/datasources/").with(user(testUser.id)))
            .andExpect(status().isOk)
            .andExpect(content().json("{'databases':  []}"))
    }

    @Test
    fun test2() {
        val response = datasourceController.listDatasources()
        assertEquals(response, ListDatasourceResponse(databases = emptyList()))
    }

    @Test
    fun `test full setup`() {
        val datasource = datasourceController.createDatasource(
            CreateDatasourceRequest(
                displayName = "test",
                datasourceType = DatasourceType.MYSQL,
                hostname = "localhost",
                port = 3306,
            ),
        )

        val connection = datasourceController.createDatasourceConnection(
            datasource.id,
            CreateDatasourceConnectionRequest(
                displayName = "My Connection",
                username = "root",
                password = "root",
                reviewConfig = ReviewConfigRequest(
                    numTotalRequired = 1,
                ),
            ),
        )

        val request = executionRequestController.create(
            CreateExecutionRequest(
                datasourceConnectionId = connection.id,
                title = "My Request",
                description = "Request description",
                statement = "SELECT 1",
                readOnly = false,
            ),
            testUserDetails,
        )

        executionRequestController.createReview(
            request.id,
            CreateReviewRequest(
                comment = "Comment",
                action = ReviewAction.APPROVE,
            ),
            userDetails = testUserDetails,
        )

        executionRequestController.createComment(
            request.id,
            CreateCommentRequest(
                comment = """Comment with a "quote"!""",
            ),
            userDetails = testUserDetails,
        )

        val requestDetails = executionRequestController.get(request.id)

        requestDetails.events[0].shouldBeEqualToIgnoringFields(
            ReviewEvent(
                id = "id",
                createdAt = LocalDateTime.now(),
                comment = "Comment",
                action = ReviewAction.APPROVE,
                author = testUser,
            ),
            ReviewEvent::createdAt,
            ReviewEvent::id,
        )

        requestDetails.events[1].shouldBeEqualToIgnoringFields(
            CommentEvent(
                id = "id",
                createdAt = LocalDateTime.now(),
                comment = "Comment with a \"quote\"!",
                author = testUser,
            ),
            ReviewEvent::createdAt,
            ReviewEvent::id,
        )
    }
}
