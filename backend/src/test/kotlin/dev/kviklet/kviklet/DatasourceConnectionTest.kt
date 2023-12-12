package dev.kviklet.kviklet

import dev.kviklet.kviklet.controller.CommentEventResponse
import dev.kviklet.kviklet.controller.CreateCommentRequest
import dev.kviklet.kviklet.controller.CreateExecutionRequestRequest
import dev.kviklet.kviklet.controller.DatasourceConnectionController
import dev.kviklet.kviklet.controller.ExecutionRequestController
import dev.kviklet.kviklet.db.DatasourceConnectionRepository
import dev.kviklet.kviklet.db.DatasourceRepository
import dev.kviklet.kviklet.db.EventRepository
import dev.kviklet.kviklet.db.ExecutionRequestRepository
import dev.kviklet.kviklet.security.WithAdminUser
import dev.kviklet.kviklet.service.ExecutionRequestService
import dev.kviklet.kviklet.service.dto.DatasourceConnectionId
import dev.kviklet.kviklet.service.dto.DatasourceType
import dev.kviklet.kviklet.service.dto.RequestType
import io.kotest.matchers.equality.shouldBeEqualToIgnoringFields
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDateTime

@SpringBootTest
@AutoConfigureMockMvc
@WithAdminUser
@ActiveProfiles("test")
class DatasourceConnectionTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val datasourceRepository: DatasourceRepository,
    @Autowired val datasourceConnectionRepository: DatasourceConnectionRepository,
    @Autowired val executionRequestRepository: ExecutionRequestRepository,
    @Autowired val eventRepository: EventRepository,
    @Autowired val datasourceController: dev.kviklet.kviklet.controller.DatasourceController,
    @Autowired val executionRequestController: ExecutionRequestController,
    @Autowired val datasourceConnectionController: DatasourceConnectionController,
    @Autowired val executionRequestService: ExecutionRequestService,
) : TestBase() {

    @AfterEach
    fun tearDownRequests() {
        eventRepository.deleteAllInBatch()
        executionRequestRepository.deleteAllInBatch()
        datasourceRepository.deleteAllInBatch()
        datasourceConnectionRepository.deleteAllInBatch()
    }

    @Test
    fun test1() {
        mockMvc.perform(get("/datasources/"))
            .andExpect(status().isOk)
            .andExpect(content().json("{'databases':  []}"))
    }

    @Test
    fun test2() {
        val response = datasourceController.listDatasources()
        assertEquals(response, dev.kviklet.kviklet.controller.ListDatasourceResponse(databases = emptyList()))
    }

    @Test
    fun `test full setup`() {
        val datasource = datasourceController.createDatasource(
            dev.kviklet.kviklet.controller.CreateDatasourceRequest(
                id = "db",
                displayName = "test",
                datasourceType = DatasourceType.MYSQL,
                hostname = "localhost",
                port = 3306,
            ),
        )

        val connection = datasourceConnectionController.createDatasourceConnection(
            datasource.id.toString(),
            dev.kviklet.kviklet.controller.CreateDatasourceConnectionRequest(
                id = "db-conn",
                displayName = "My Connection",
                username = "root",
                password = "root",
                reviewConfig = dev.kviklet.kviklet.controller.ReviewConfigRequest(
                    numTotalRequired = 1,
                ),
            ),
        )

        val request = executionRequestController.create(
            CreateExecutionRequestRequest(
                datasourceConnectionId = DatasourceConnectionId("db-conn"),
                title = "My Request",
                description = "Request description",
                statement = "SELECT 1",
                readOnly = false,
                type = RequestType.SingleQuery,
            ),
            testUserDetails,
        )

        executionRequestController.createComment(
            request.id,
            CreateCommentRequest(
                comment = """Comment with a "quote"!""",
            ),
            userDetails = testUserDetails,
        )

        val requestDetails = executionRequestController.get(request.id)
        val executionRequest = executionRequestService.get(request.id)

        requestDetails.events[0].shouldBeEqualToIgnoringFields(
            CommentEventResponse(
                id = "id",
                createdAt = LocalDateTime.now(),
                comment = "Comment with a \"quote\"!",
                author = testUser,
            ),
            false,
            CommentEventResponse::createdAt,
            CommentEventResponse::id,
        )
    }
}
