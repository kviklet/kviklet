package dev.kviklet.kviklet

import dev.kviklet.kviklet.controller.CommentEventResponse
import dev.kviklet.kviklet.controller.ConnectionController
import dev.kviklet.kviklet.controller.CreateCommentRequest
import dev.kviklet.kviklet.controller.CreateDatasourceConnectionRequest
import dev.kviklet.kviklet.controller.CreateDatasourceExecutionRequestRequest
import dev.kviklet.kviklet.controller.DatasourceConnectionResponse
import dev.kviklet.kviklet.controller.ExecutionRequestController
import dev.kviklet.kviklet.controller.ReviewConfigRequest
import dev.kviklet.kviklet.controller.UpdateDatasourceConnectionRequest
import dev.kviklet.kviklet.controller.UserResponse
import dev.kviklet.kviklet.db.ConnectionRepository
import dev.kviklet.kviklet.db.EventRepository
import dev.kviklet.kviklet.db.ExecutionRequestRepository
import dev.kviklet.kviklet.helper.UserHelper
import dev.kviklet.kviklet.security.UserDetailsWithId
import dev.kviklet.kviklet.service.ExecutionRequestService
import dev.kviklet.kviklet.service.dto.ConnectionId
import dev.kviklet.kviklet.service.dto.DatabaseProtocol
import dev.kviklet.kviklet.service.dto.DatasourceExecutionRequest
import dev.kviklet.kviklet.service.dto.DatasourceType
import dev.kviklet.kviklet.service.dto.RequestType
import dev.kviklet.kviklet.service.dto.utcTimeNow
import io.kotest.matchers.equality.shouldBeEqualToIgnoringFields
import io.kotest.matchers.shouldBe
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
class ConnectionTest(
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

    @Test
    fun `test create MongoDB Connection`() {
        val connection = datasourceConnectionController.createConnection(
            CreateDatasourceConnectionRequest(
                id = "mongo-connection",
                displayName = "Mongo Connection",
                username = "root",
                password = "root",
                reviewConfig = ReviewConfigRequest(
                    numTotalRequired = 1,
                ),
                type = DatasourceType.MONGODB,
                protocol = DatabaseProtocol.MONGODB,
                hostname = "localhost",
                port = 27017,
            ),
        )

        datasourceConnectionController.getConnection("mongo-connection").shouldBe(
            connection,
        )
    }

    @Test
    fun `edit MongoDB Connection`() {
        val connection = datasourceConnectionController.createConnection(
            CreateDatasourceConnectionRequest(
                id = "mongo-connection",
                displayName = "Mongo Connection",
                username = "root",
                password = "root",
                reviewConfig = ReviewConfigRequest(
                    numTotalRequired = 1,
                ),
                type = DatasourceType.MONGODB,
                protocol = DatabaseProtocol.MONGODB,
                hostname = "localhost",
                port = 27017,
            ),
        )

        val editedConnection = datasourceConnectionController.updateConnection(
            "mongo-connection",
            UpdateDatasourceConnectionRequest(
                displayName = "Mongo Connection",
                username = "root",
                password = "root",
                reviewConfig = ReviewConfigRequest(
                    numTotalRequired = 1,
                ),
                type = DatasourceType.MONGODB,
                protocol = DatabaseProtocol.MONGODB_SRV,
                hostname = "localhost",
                port = 27017,
            ),
        )

        datasourceConnectionController.getConnection("mongo-connection").shouldBe(
            editedConnection,
        )

        (
            datasourceConnectionController.getConnection(
                "mongo-connection",
            ) as DatasourceConnectionResponse
            ).protocol.shouldBe(DatabaseProtocol.MONGODB_SRV)
    }

    @Test
    fun `add MongoDB execution Request to Mongo Connection`() {
        val testUser = userHelper.createUser(listOf("*"))

        val testUserDetails = UserDetailsWithId(
            id = testUser.getId()!!,
            email = testUser.email,
            password = testUser.password,
            authorities = emptyList(),
        )

        val connection = datasourceConnectionController.createConnection(
            CreateDatasourceConnectionRequest(
                id = "mongo-connection",
                displayName = "Mongo Connection",
                username = "root",
                password = "root",
                reviewConfig = ReviewConfigRequest(
                    numTotalRequired = 1,
                ),
                type = DatasourceType.MONGODB,
                protocol = DatabaseProtocol.MONGODB,
                hostname = "localhost",
                port = 27017,
            ),
        )

        val mongoQuery = """
        {
            find: "users",
            filter: { age: { ${"$"}gte: 18 } },
            sort: { name: 1 },
            limit: 10
        }
        """.trimIndent()

        val request = executionRequestController.create(
            CreateDatasourceExecutionRequestRequest(
                connectionId = ConnectionId("mongo-connection"),
                title = "Find Adult Users",
                description = "Retrieve users aged 18 and above, sorted by name",
                statement = mongoQuery,
                type = RequestType.SingleExecution,
            ),
            userDetails = testUserDetails,
        )

        executionRequestController.createComment(
            request.id,
            CreateCommentRequest(
                comment = """Comment on MongoDB query: This finds users 18 and older.""",
            ),
            userDetails = testUserDetails,
        )

        val requestDetails = executionRequestController.get(request.id)
        val executionRequest = executionRequestService.get(request.id)

        requestDetails.events[0].shouldBeEqualToIgnoringFields(
            CommentEventResponse(
                id = "id",
                createdAt = utcTimeNow(),
                comment = "Comment on MongoDB query: This finds users 18 and older.",
                author = UserResponse(testUser),
            ),
            false,
            CommentEventResponse::createdAt,
            CommentEventResponse::id,
        )

        val updatedRequest = executionRequest.request as DatasourceExecutionRequest

        // Additional assertions specific to MongoDB
        updatedRequest.statement shouldBe mongoQuery
        updatedRequest.title shouldBe "Find Adult Users"
        updatedRequest.description shouldBe "Retrieve users aged 18 and above, sorted by name"
    }
}
