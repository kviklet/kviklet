package dev.kviklet.kviklet

import dev.kviklet.kviklet.controller.CommentEventResponse
import dev.kviklet.kviklet.controller.ConnectionController
import dev.kviklet.kviklet.controller.CreateCommentRequest
import dev.kviklet.kviklet.controller.CreateDatasourceConnectionRequest
import dev.kviklet.kviklet.controller.CreateDatasourceExecutionRequestRequest
import dev.kviklet.kviklet.controller.ExecutionRequestController
import dev.kviklet.kviklet.controller.ReviewConfigRequest
import dev.kviklet.kviklet.controller.UserResponse
import dev.kviklet.kviklet.db.ConnectionRepository
import dev.kviklet.kviklet.db.EventRepository
import dev.kviklet.kviklet.db.ExecutionRequestRepository
import dev.kviklet.kviklet.helper.UserHelper
import dev.kviklet.kviklet.security.UserDetailsWithId
import dev.kviklet.kviklet.service.ExecutionRequestService
import dev.kviklet.kviklet.service.dto.ConnectionId
import dev.kviklet.kviklet.service.dto.DatasourceType
import dev.kviklet.kviklet.service.dto.RequestType
import dev.kviklet.kviklet.service.dto.utcTimeNow
import io.kotest.matchers.equality.shouldBeEqualToIgnoringFields
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DatasourceConnectionTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val connectionRepository: ConnectionRepository,
    @Autowired val executionRequestRepository: ExecutionRequestRepository,
    @Autowired val eventRepository: EventRepository,
    @Autowired val executionRequestController: ExecutionRequestController,
    @Autowired val datasourceConnectionController: ConnectionController,
    @Autowired val executionRequestService: ExecutionRequestService,

) {

    @Autowired
    private lateinit var userHelper: UserHelper

    @AfterEach
    fun tearDownRequests() {
        eventRepository.deleteAllInBatch()
        executionRequestRepository.deleteAllInBatch()
        connectionRepository.deleteAllInBatch()
        userHelper.deleteAll()
    }

    @Test
    fun `test full setup`() {
        val testUser = userHelper.createUser(listOf("*"))

        val testUserDetails = UserDetailsWithId(
            id = testUser.getId()!!,
            email = testUser.email,
            password = testUser.password,
            authorities = emptyList(),
        )

        val connection = datasourceConnectionController.createConnection(
            CreateDatasourceConnectionRequest(
                id = "db-conn",
                displayName = "My Connection",
                username = "root",
                password = "root",
                reviewConfig = ReviewConfigRequest(
                    numTotalRequired = 1,
                ),
                type = DatasourceType.MYSQL,
                hostname = "localhost",
                port = 3306,
            ),
        )

        val request = executionRequestController.create(
            CreateDatasourceExecutionRequestRequest(
                connectionId = ConnectionId("db-conn"),
                title = "My Request",
                description = "Request description",
                statement = "SELECT 1",
                readOnly = false,
                type = RequestType.SingleExecution,
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
        val executionRequest = executionRequestService.get(request.id)

        requestDetails.events[0].shouldBeEqualToIgnoringFields(
            CommentEventResponse(
                id = "id",
                createdAt = utcTimeNow(),
                comment = "Comment with a \"quote\"!",
                author = UserResponse(testUser),
            ),
            false,
            CommentEventResponse::createdAt,
            CommentEventResponse::id,
        )
    }
}
