package dev.kviklet.kviklet

import dev.kviklet.kviklet.controller.CommentEventResponse
import dev.kviklet.kviklet.controller.ConnectionController
import dev.kviklet.kviklet.controller.CreateCommentRequest
import dev.kviklet.kviklet.controller.CreateDatasourceConnectionRequest
import dev.kviklet.kviklet.controller.CreateDatasourceExecutionRequestRequest
import dev.kviklet.kviklet.controller.DatasourceConnectionResponse
import dev.kviklet.kviklet.controller.ExecutionRequestController
import dev.kviklet.kviklet.controller.GroupReviewConfigRequest
import dev.kviklet.kviklet.controller.ReviewConfigRequest
import dev.kviklet.kviklet.controller.UpdateDatasourceConnectionRequest
import dev.kviklet.kviklet.controller.UserResponse
import dev.kviklet.kviklet.db.ConnectionRepository
import dev.kviklet.kviklet.db.EventRepository
import dev.kviklet.kviklet.db.ExecutionRequestRepository
import dev.kviklet.kviklet.helper.UserHelper
import dev.kviklet.kviklet.security.UserDetailsWithId
import dev.kviklet.kviklet.service.ExecutionRequestService
import dev.kviklet.kviklet.service.dto.AuthenticationType
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
import org.junit.jupiter.api.assertThrows
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
                    groupConfigs = listOf(GroupReviewConfigRequest(roleId = "*", numRequired = 1)),
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
                    groupConfigs = listOf(GroupReviewConfigRequest(roleId = "*", numRequired = 1)),
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
                    groupConfigs = listOf(GroupReviewConfigRequest(roleId = "*", numRequired = 1)),
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
                    groupConfigs = listOf(GroupReviewConfigRequest(roleId = "*", numRequired = 1)),
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
                    groupConfigs = listOf(GroupReviewConfigRequest(roleId = "*", numRequired = 1)),
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

    @Test
    fun `test update authentication method`() {
        val connection = datasourceConnectionController.createConnection(
            CreateDatasourceConnectionRequest(
                id = "postgres-connection",
                displayName = "Postgres Connection",
                username = "postgres",
                password = "postgres",
                reviewConfig = ReviewConfigRequest(
                    groupConfigs = listOf(GroupReviewConfigRequest(roleId = "*", numRequired = 1)),
                ),
                type = DatasourceType.POSTGRESQL,
                hostname = "localhost",
                port = 5432,
                authenticationType = AuthenticationType.USER_PASSWORD,
            ),
        )

        val editedConnection = datasourceConnectionController.updateConnection(
            "postgres-connection",
            UpdateDatasourceConnectionRequest(
                username = "postgres",
                authenticationType = AuthenticationType.AWS_IAM,
            ),
        )

        editedConnection as DatasourceConnectionResponse

        editedConnection.authenticationType shouldBe AuthenticationType.AWS_IAM
        editedConnection.username shouldBe "postgres"
        editedConnection.displayName shouldBe "Postgres Connection"
    }

    @Test
    fun `test temporary access flag`() {
        // Create a connection with temporary access disabled
        val connection = datasourceConnectionController.createConnection(
            CreateDatasourceConnectionRequest(
                id = "temp-access-conn",
                displayName = "Temp Access Test Connection",
                username = "root",
                password = "root",
                reviewConfig = ReviewConfigRequest(
                    groupConfigs = listOf(GroupReviewConfigRequest(roleId = "*", numRequired = 1)),
                ),
                type = DatasourceType.MYSQL,
                hostname = "localhost",
                port = 3306,
                temporaryAccessEnabled = false,
            ),
        ) as DatasourceConnectionResponse

        // Verify the flag is false in the created connection
        connection.temporaryAccessEnabled shouldBe false

        // Get the connection and verify the flag is still false
        val retrievedConnection = datasourceConnectionController.getConnection(
            "temp-access-conn",
        ) as DatasourceConnectionResponse
        retrievedConnection.temporaryAccessEnabled shouldBe false

        // Update the connection to enable temporary access
        val updatedConnection = datasourceConnectionController.updateConnection(
            "temp-access-conn",
            UpdateDatasourceConnectionRequest(
                temporaryAccessEnabled = true,
            ),
        ) as DatasourceConnectionResponse

        // Verify the flag is now true
        updatedConnection.temporaryAccessEnabled shouldBe true

        // Get all connections and verify the flag is correct
        val allConnections = datasourceConnectionController.getDatasourceConnections()
        val ourConnection = allConnections.find {
            (it as DatasourceConnectionResponse).id.toString() ==
                "temp-access-conn"
        } as DatasourceConnectionResponse
        ourConnection.temporaryAccessEnabled shouldBe true
    }

    @Test
    fun `test clearMaxTempDuration with raw JSON`() {
        val testUser = userHelper.createUser(listOf("*"))
        val cookie = userHelper.login(mockMvc = mockMvc)

        // Create a connection with max duration using raw JSON
        val createJson = """
            {
                "connectionType": "DATASOURCE",
                "id": "json-max-duration-conn",
                "displayName": "JSON Max Duration Test",
                "username": "root",
                "password": "root",
                "type": "MYSQL",
                "hostname": "localhost",
                "port": 3306,
                "temporaryAccessEnabled": true,
                "maxTemporaryAccessDuration": 180,
                "reviewConfig": {
                    "numTotalRequired": 1
                }
            }
        """.trimIndent()

        mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/connections/")
                .cookie(cookie)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(createJson),
        )
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk)
            .andExpect(
                org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                    "$.maxTemporaryAccessDuration",
                )
                    .value(180),
            )

        // Update with clearMaxTempDuration=true using raw JSON
        val updateJson = """
            {
                "connectionType": "DATASOURCE",
                "clearMaxTempDuration": true
            }
        """.trimIndent()

        mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .patch("/connections/json-max-duration-conn")
                .cookie(cookie)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(updateJson),
        )
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk)
            .andExpect(
                org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                    "$.maxTemporaryAccessDuration",
                )
                    .doesNotExist(),
            )

        // Verify the value is cleared by fetching the connection
        mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                "/connections/json-max-duration-conn",
            )
                .cookie(cookie),
        )
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk)
            .andExpect(
                org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                    "$.maxTemporaryAccessDuration",
                )
                    .doesNotExist(),
            )
    }

    @Test
    fun `test role ARN is passed correctly`() {
        // Create a connection with AWS IAM authentication and role ARN
        val roleArn = "arn:aws:iam::123456789012:role/example-role"
        val connection = datasourceConnectionController.createConnection(
            CreateDatasourceConnectionRequest(
                id = "aws-iam-conn",
                displayName = "AWS IAM Connection",
                username = "iam_user",
                reviewConfig = ReviewConfigRequest(
                    groupConfigs = listOf(GroupReviewConfigRequest(roleId = "*", numRequired = 1)),
                ),
                type = DatasourceType.POSTGRESQL,
                hostname = "example.amazonaws.com",
                port = 5432,
                authenticationType = AuthenticationType.AWS_IAM,
                roleArn = roleArn,
            ),
        ) as DatasourceConnectionResponse

        // Verify the role ARN is set correctly in the created connection
        connection.roleArn shouldBe roleArn

        // Get the connection and verify the role ARN is still correct
        val retrievedConnection = datasourceConnectionController.getConnection(
            "aws-iam-conn",
        ) as DatasourceConnectionResponse
        retrievedConnection.roleArn shouldBe roleArn

        // Update the connection with a new role ARN
        val newRoleArn = "arn:aws:iam::987654321098:role/new-example-role"
        val updatedConnection = datasourceConnectionController.updateConnection(
            "aws-iam-conn",
            UpdateDatasourceConnectionRequest(
                roleArn = newRoleArn,
            ),
        ) as DatasourceConnectionResponse

        // Verify the new role ARN is set correctly
        updatedConnection.roleArn shouldBe newRoleArn

        // Get all connections and verify the role ARN is correct
        val allConnections = datasourceConnectionController.getDatasourceConnections()
        val ourConnection = allConnections.find {
            (it as DatasourceConnectionResponse).id.toString() == "aws-iam-conn"
        } as DatasourceConnectionResponse
        ourConnection.roleArn shouldBe newRoleArn
    }

    @Test
    fun `test temporary access execution requests are not allowed on connections with temporary access disabled`() {
        // Create a connection with temporary access disabled
        val connection = datasourceConnectionController.createConnection(
            CreateDatasourceConnectionRequest(
                id = "temp-access-conn",
                displayName = "Temp Access Test Connection",
                username = "root",
                password = "root",
                reviewConfig = ReviewConfigRequest(
                    groupConfigs = listOf(
                        GroupReviewConfigRequest(roleId = "*", numRequired = 1),
                    ),
                ),
                type = DatasourceType.MYSQL,
                hostname = "localhost",
                port = 3306,
                temporaryAccessEnabled = false,
            ),
        ) as DatasourceConnectionResponse

        // Create a test user
        val testUser = userHelper.createUser(listOf("*"))
        val testUserDetails = UserDetailsWithId(
            id = testUser.getId()!!,
            email = testUser.email,
            password = testUser.password,
            authorities = emptyList(),
        )

        // Attempt to create a temporary access execution request
        val exception = assertThrows<IllegalStateException> {
            executionRequestController.create(
                CreateDatasourceExecutionRequestRequest(
                    connectionId = ConnectionId("temp-access-conn"),
                    title = "Test Temporary Access",
                    description = "This should fail",
                    statement = "SELECT 1",
                    type = RequestType.TemporaryAccess,
                ),
                userDetails = testUserDetails,
            )
        }

        exception.message shouldBe "Temporary access is not enabled for this connection"

        // Try to create a normal execution request
        executionRequestController.create(
            CreateDatasourceExecutionRequestRequest(
                connectionId = ConnectionId("temp-access-conn"),
                title = "Test Normal Execution",
                description = "This should succeed",
                statement = "SELECT 1",
                type = RequestType.SingleExecution,
            ),
            userDetails = testUserDetails,
        )
    }
}
