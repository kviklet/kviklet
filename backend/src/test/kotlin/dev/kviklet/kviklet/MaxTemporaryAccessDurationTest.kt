package dev.kviklet.kviklet

import dev.kviklet.kviklet.controller.CreateDatasourceExecutionRequestRequest
import dev.kviklet.kviklet.controller.UpdateDatasourceConnectionRequest
import dev.kviklet.kviklet.controller.UpdateExecutionRequestRequest
import dev.kviklet.kviklet.db.GroupReviewConfig
import dev.kviklet.kviklet.db.ReviewConfig
import dev.kviklet.kviklet.db.User
import dev.kviklet.kviklet.helper.ConnectionHelper
import dev.kviklet.kviklet.helper.ExecutionRequestHelper
import dev.kviklet.kviklet.helper.UserHelper
import dev.kviklet.kviklet.service.ConnectionService
import dev.kviklet.kviklet.service.ExecutionRequestService
import dev.kviklet.kviklet.service.dto.AuthenticationType
import dev.kviklet.kviklet.service.dto.Connection
import dev.kviklet.kviklet.service.dto.ConnectionId
import dev.kviklet.kviklet.service.dto.DatasourceType
import dev.kviklet.kviklet.service.dto.ExecutionRequestDetails
import dev.kviklet.kviklet.service.dto.RequestType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

@SpringBootTest
@ActiveProfiles("test")
class MaxTemporaryAccessDurationTest {

    @Autowired
    private lateinit var userHelper: UserHelper

    @Autowired
    private lateinit var connectionHelper: ConnectionHelper

    @Autowired
    private lateinit var executionRequestHelper: ExecutionRequestHelper

    @Autowired
    private lateinit var connectionService: ConnectionService

    @Autowired
    private lateinit var executionRequestService: ExecutionRequestService

    private lateinit var testUser: User
    private lateinit var testReviewer: User

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
        testUser = userHelper.createUser(permissions = listOf("*"))
        testReviewer = userHelper.createUser(permissions = listOf("*"))
    }

    @AfterEach
    fun tearDown() {
        executionRequestHelper.deleteAll()
        connectionHelper.deleteAll()
        userHelper.deleteAll()
    }

    @Test
    fun `create connection with max temporary access duration`() {
        val connectionId = ConnectionId("test-connection")
        val connection = connectionService.createDatasourceConnection(
            connectionId = connectionId,
            displayName = "Test Connection",
            databaseName = "test_db",
            maxExecutions = null,
            username = "root",
            password = "root",
            authenticationType = AuthenticationType.USER_PASSWORD,
            description = "Test connection with max duration",
            reviewConfig = ReviewConfig(
                groupConfigs = listOf(
                    GroupReviewConfig(roleId = "*", numRequired = 1),
                ),
            ),
            port = db.firstMappedPort,
            hostname = "localhost",
            type = DatasourceType.POSTGRESQL,
            protocol = DatasourceType.POSTGRESQL.toProtocol(),
            additionalJDBCOptions = "",
            dumpsEnabled = false,
            temporaryAccessEnabled = true,
            explainEnabled = false,
            roleArn = null,
            maxTemporaryAccessDuration = 120L, // 2 hours max
        )

        assertEquals(
            120L,
            (connection as dev.kviklet.kviklet.service.dto.DatasourceConnection).maxTemporaryAccessDuration,
        )
    }

    @Test
    fun `create temporary access request within max duration succeeds`() {
        val connection = createConnectionWithMaxDuration(60L) // 1 hour max

        val request = CreateDatasourceExecutionRequestRequest(
            connectionId = connection.id,
            title = "Test Request",
            type = RequestType.TemporaryAccess,
            description = "Test temporary access",
            statement = null,
            temporaryAccessDuration = 30L, // 30 minutes - within limit
        )

        val executionRequest = executionRequestService.create(
            connectionId = connection.id,
            request = request,
            userId = testUser.getId()!!,
        )

        assertEquals(30L, executionRequest.request.temporaryAccessDuration?.toMinutes())
    }

    @Test
    fun `create temporary access request exceeding max duration fails`() {
        val connection = createConnectionWithMaxDuration(60L) // 1 hour max

        val request = CreateDatasourceExecutionRequestRequest(
            connectionId = connection.id,
            title = "Test Request",
            type = RequestType.TemporaryAccess,
            description = "Test temporary access",
            statement = null,
            temporaryAccessDuration = 120L, // 2 hours - exceeds limit
        )

        val exception = assertThrows<IllegalArgumentException> {
            executionRequestService.create(
                connectionId = connection.id,
                request = request,
                userId = testUser.getId()!!,
            )
        }

        assertEquals("Duration exceeds maximum allowed: 60 minutes", exception.message)
    }

    @Test
    fun `create temporary access request with zero duration fails`() {
        val connection = createConnectionWithMaxDuration(60L)

        val request = CreateDatasourceExecutionRequestRequest(
            connectionId = connection.id,
            title = "Test Request",
            type = RequestType.TemporaryAccess,
            description = "Test temporary access",
            statement = null,
            temporaryAccessDuration = 0L,
        )

        val exception = assertThrows<IllegalArgumentException> {
            executionRequestService.create(
                connectionId = connection.id,
                request = request,
                userId = testUser.getId()!!,
            )
        }

        assertEquals(
            "Duration cannot be 0. Use null for infinite access or a positive value for limited access",
            exception.message,
        )
    }

    @Test
    fun `create infinite temporary access on connection with max duration fails`() {
        val connection = createConnectionWithMaxDuration(60L) // 1 hour max

        val request = CreateDatasourceExecutionRequestRequest(
            connectionId = connection.id,
            title = "Test Request",
            type = RequestType.TemporaryAccess,
            description = "Test temporary access",
            statement = null,
            temporaryAccessDuration = null, // infinite access
        )

        val exception = assertThrows<IllegalArgumentException> {
            executionRequestService.create(
                connectionId = connection.id,
                request = request,
                userId = testUser.getId()!!,
            )
        }

        assertEquals(
            "Infinite access not allowed for this connection. Maximum duration is 60 minutes",
            exception.message,
        )
    }

    @Test
    fun `create infinite temporary access on connection without max duration succeeds`() {
        val connection = createConnectionWithMaxDuration(null) // no max limit

        val request = CreateDatasourceExecutionRequestRequest(
            connectionId = connection.id,
            title = "Test Request",
            type = RequestType.TemporaryAccess,
            description = "Test temporary access",
            statement = null,
            temporaryAccessDuration = null, // infinite access
        )

        val executionRequest = executionRequestService.create(
            connectionId = connection.id,
            request = request,
            userId = testUser.getId()!!,
        )

        assertNull(executionRequest.request.temporaryAccessDuration)
    }

    @Test
    fun `create temporary access with duration on connection without max duration succeeds`() {
        val connection = createConnectionWithMaxDuration(null) // no max limit

        val request = CreateDatasourceExecutionRequestRequest(
            connectionId = connection.id,
            title = "Test Request",
            type = RequestType.TemporaryAccess,
            description = "Test temporary access",
            statement = null,
            temporaryAccessDuration = 240L, // 4 hours
        )

        val executionRequest = executionRequestService.create(
            connectionId = connection.id,
            request = request,
            userId = testUser.getId()!!,
        )

        assertEquals(240L, executionRequest.request.temporaryAccessDuration?.toMinutes())
    }

    @Test
    fun `update connection to add max duration`() {
        val connection = createConnectionWithMaxDuration(null) // start with no limit

        val updateRequest = UpdateDatasourceConnectionRequest(
            maxTemporaryAccessDuration = 120L, // add 2 hour limit
        )

        val updatedConnection = connectionService.updateConnection(
            connectionId = connection.id,
            request = updateRequest,
        )

        assertEquals(
            120L,
            (updatedConnection as dev.kviklet.kviklet.service.dto.DatasourceConnection).maxTemporaryAccessDuration,
        )
    }

    @Test
    fun `update execution request duration within new max duration succeeds`() {
        val connection = createConnectionWithMaxDuration(120L) // 2 hour max
        val executionRequest = createTemporaryAccessRequest(connection, 60L) // start with 1 hour

        val updateRequest = UpdateExecutionRequestRequest(
            title = null,
            description = null,
            temporaryAccessDuration = 90L, // update to 1.5 hours - still within limit
        )

        val updatedRequest = executionRequestService.update(
            id = executionRequest.request.id!!,
            request = updateRequest,
            userId = testUser.getId()!!,
        )

        assertEquals(90L, updatedRequest.request.temporaryAccessDuration?.toMinutes())
    }

    @Test
    fun `update execution request duration exceeding max duration fails`() {
        val connection = createConnectionWithMaxDuration(60L) // 1 hour max
        val executionRequest = createTemporaryAccessRequest(connection, 30L) // start with 30 minutes

        val updateRequest = UpdateExecutionRequestRequest(
            title = null,
            description = null,
            temporaryAccessDuration = 120L, // try to update to 2 hours - exceeds limit
        )

        val exception = assertThrows<IllegalArgumentException> {
            executionRequestService.update(
                id = executionRequest.request.id!!,
                request = updateRequest,
                userId = testUser.getId()!!,
            )
        }

        assertEquals("Duration exceeds maximum allowed: 60 minutes", exception.message)
    }

    private fun createConnectionWithMaxDuration(maxDuration: Long?): Connection {
        val connectionId = ConnectionId("test-connection-${System.currentTimeMillis()}")
        return connectionService.createDatasourceConnection(
            connectionId = connectionId,
            displayName = "Test Connection",
            databaseName = "test_db",
            maxExecutions = null,
            username = "root",
            password = "root",
            authenticationType = AuthenticationType.USER_PASSWORD,
            description = "Test connection",
            reviewConfig = ReviewConfig(
                groupConfigs = listOf(
                    GroupReviewConfig(roleId = "*", numRequired = 1),
                ),
            ),
            port = db.firstMappedPort,
            hostname = "localhost",
            type = DatasourceType.POSTGRESQL,
            protocol = DatasourceType.POSTGRESQL.toProtocol(),
            additionalJDBCOptions = "",
            dumpsEnabled = false,
            temporaryAccessEnabled = true,
            explainEnabled = false,
            roleArn = null,
            maxTemporaryAccessDuration = maxDuration,
        )
    }

    private fun createTemporaryAccessRequest(connection: Connection, duration: Long?): ExecutionRequestDetails {
        val request = CreateDatasourceExecutionRequestRequest(
            connectionId = connection.id,
            title = "Test Request",
            type = RequestType.TemporaryAccess,
            description = "Test temporary access",
            statement = null,
            temporaryAccessDuration = duration,
        )

        return executionRequestService.create(
            connectionId = connection.id,
            request = request,
            userId = testUser.getId()!!,
        )
    }
}
