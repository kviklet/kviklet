package dev.kviklet.kviklet.migration

import com.fasterxml.jackson.databind.ObjectMapper
import dev.kviklet.kviklet.db.ConnectionAdapter
import dev.kviklet.kviklet.db.EventRepository
import dev.kviklet.kviklet.db.ExecutionRequestRepository
import dev.kviklet.kviklet.db.ReviewConfig
import dev.kviklet.kviklet.db.User
import dev.kviklet.kviklet.db.util.IdGenerator
import dev.kviklet.kviklet.helper.ConnectionHelper
import dev.kviklet.kviklet.helper.RoleHelper
import dev.kviklet.kviklet.helper.UserHelper
import dev.kviklet.kviklet.service.dto.AuthenticationType
import dev.kviklet.kviklet.service.dto.Connection
import dev.kviklet.kviklet.service.dto.ConnectionId
import dev.kviklet.kviklet.service.dto.DatabaseProtocol
import dev.kviklet.kviklet.service.dto.DatasourceType
import dev.kviklet.kviklet.service.dto.RequestType
import liquibase.database.DatabaseFactory
import liquibase.database.jvm.JdbcConnection
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.time.LocalDateTime
import javax.sql.DataSource

@SpringBootTest
@ActiveProfiles("test")
class RecalculateRequestStatusesMigrationTest {

    @Autowired
    private lateinit var dataSource: DataSource

    @Autowired
    private lateinit var connectionHelper: ConnectionHelper

    @Autowired
    private lateinit var userHelper: UserHelper

    @Autowired
    private lateinit var roleHelper: RoleHelper

    @Autowired
    private lateinit var connectionAdapter: ConnectionAdapter

    @Autowired
    private lateinit var executionRequestRepository: ExecutionRequestRepository

    @Autowired
    private lateinit var eventRepository: EventRepository

    private val objectMapper = ObjectMapper()
    private val idGenerator = IdGenerator()

    private lateinit var testUser: User
    private lateinit var reviewer1: User
    private lateinit var reviewer2: User

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
    fun setUp() {
        testUser = userHelper.createUser(permissions = listOf("*"))
        reviewer1 = userHelper.createUser(permissions = listOf("*"))
        reviewer2 = userHelper.createUser(permissions = listOf("*"))
    }

    @AfterEach
    fun tearDown() {
        eventRepository.deleteAllInBatch()
        executionRequestRepository.deleteAllInBatch()
        connectionAdapter.deleteAll()
        userHelper.deleteAll()
        roleHelper.deleteAll()
    }

    @Test
    fun `migration calculates APPROVED status for request with sufficient approvals`() {
        // Create connection with numTotalRequired=1
        val connection = createConnection(numTotalRequired = 1)

        // Create execution request
        val requestId = createExecutionRequest(
            connectionId = connection.getId(),
            authorId = testUser.getId()!!,
            requestType = RequestType.SingleExecution,
        )

        // Create APPROVE review event
        createReviewEvent(requestId, reviewer1.getId()!!, "APPROVE")

        // Run migration
        runMigration()

        // Verify
        val (reviewStatus, executionStatus) = getRequestStatuses(requestId)
        assertEquals("APPROVED", reviewStatus)
        assertEquals("EXECUTABLE", executionStatus)
    }

    @Test
    fun `migration calculates AWAITING_APPROVAL for request with insufficient approvals`() {
        // Create connection with numTotalRequired=2
        val connection = createConnection(numTotalRequired = 2)

        // Create execution request
        val requestId = createExecutionRequest(
            connectionId = connection.getId(),
            authorId = testUser.getId()!!,
            requestType = RequestType.SingleExecution,
        )

        // Create only 1 APPROVE review event (needs 2)
        createReviewEvent(requestId, reviewer1.getId()!!, "APPROVE")

        // Run migration
        runMigration()

        // Verify
        val (reviewStatus, _) = getRequestStatuses(requestId)
        assertEquals("AWAITING_APPROVAL", reviewStatus)
    }

    @Test
    fun `migration calculates REJECTED for request with rejection event`() {
        val connection = createConnection(numTotalRequired = 1)
        val requestId = createExecutionRequest(
            connectionId = connection.getId(),
            authorId = testUser.getId()!!,
            requestType = RequestType.SingleExecution,
        )

        // Create REJECT review event
        createReviewEvent(requestId, reviewer1.getId()!!, "REJECT")

        // Run migration
        runMigration()

        // Verify
        val (reviewStatus, _) = getRequestStatuses(requestId)
        assertEquals("REJECTED", reviewStatus)
    }

    @Test
    fun `migration calculates CHANGE_REQUESTED for active change requests`() {
        val connection = createConnection(numTotalRequired = 1)
        val requestId = createExecutionRequest(
            connectionId = connection.getId(),
            authorId = testUser.getId()!!,
            requestType = RequestType.SingleExecution,
        )

        // Create REQUEST_CHANGE event from reviewer1
        val t1 = LocalDateTime.now().minusHours(2)
        createReviewEvent(requestId, reviewer1.getId()!!, "REQUEST_CHANGE", t1)

        // Create APPROVE event from reviewer2 (different person)
        val t2 = LocalDateTime.now().minusHours(1)
        createReviewEvent(requestId, reviewer2.getId()!!, "APPROVE", t2)

        // Run migration
        runMigration()

        // Verify - should be CHANGE_REQUESTED because reviewer1's change request is still active
        val (reviewStatus, _) = getRequestStatuses(requestId)
        assertEquals("CHANGE_REQUESTED", reviewStatus)
    }

    @Test
    fun `migration calculates APPROVED when change request author approves later`() {
        val connection = createConnection(numTotalRequired = 1)
        val requestId = createExecutionRequest(
            connectionId = connection.getId(),
            authorId = testUser.getId()!!,
            requestType = RequestType.SingleExecution,
        )

        // Create REQUEST_CHANGE event from reviewer1
        val t1 = LocalDateTime.now().minusHours(2)
        createReviewEvent(requestId, reviewer1.getId()!!, "REQUEST_CHANGE", t1)

        // Same reviewer approves later
        val t2 = LocalDateTime.now().minusHours(1)
        createReviewEvent(requestId, reviewer1.getId()!!, "APPROVE", t2)

        // Run migration
        runMigration()

        // Verify - should be APPROVED because reviewer1 resolved their own change request
        val (reviewStatus, _) = getRequestStatuses(requestId)
        assertEquals("APPROVED", reviewStatus)
    }

    @Test
    fun `migration resets approval count after execution error`() {
        val connection = createConnection(numTotalRequired = 1)
        val requestId = createExecutionRequest(
            connectionId = connection.getId(),
            authorId = testUser.getId()!!,
            requestType = RequestType.SingleExecution,
        )

        // Approve first
        val t1 = LocalDateTime.now().minusHours(3)
        createReviewEvent(requestId, reviewer1.getId()!!, "APPROVE", t1)

        // Execute with error
        val t2 = LocalDateTime.now().minusHours(1)
        createExecuteEvent(requestId, testUser.getId()!!, hasError = true, createdAt = t2)

        // Run migration
        runMigration()

        // Verify - should be AWAITING_APPROVAL because error resets approval count
        val (reviewStatus, _) = getRequestStatuses(requestId)
        assertEquals("AWAITING_APPROVAL", reviewStatus)
    }

    @Test
    fun `migration resets approval count after edit event`() {
        val connection = createConnection(numTotalRequired = 1)
        val requestId = createExecutionRequest(
            connectionId = connection.getId(),
            authorId = testUser.getId()!!,
            requestType = RequestType.SingleExecution,
        )

        // Approve first
        val t1 = LocalDateTime.now().minusHours(3)
        createReviewEvent(requestId, reviewer1.getId()!!, "APPROVE", t1)

        // Edit the request
        val t2 = LocalDateTime.now().minusHours(1)
        createEditEvent(requestId, testUser.getId()!!, t2)

        // Run migration
        runMigration()

        // Verify - should be AWAITING_APPROVAL because edit resets approval count
        val (reviewStatus, _) = getRequestStatuses(requestId)
        assertEquals("AWAITING_APPROVAL", reviewStatus)
    }

    @Test
    fun `migration counts unique approvers only once`() {
        val connection = createConnection(numTotalRequired = 2)
        val requestId = createExecutionRequest(
            connectionId = connection.getId(),
            authorId = testUser.getId()!!,
            requestType = RequestType.SingleExecution,
        )

        // Same reviewer approves twice
        createReviewEvent(requestId, reviewer1.getId()!!, "APPROVE", LocalDateTime.now().minusHours(2))
        createReviewEvent(requestId, reviewer1.getId()!!, "APPROVE", LocalDateTime.now().minusHours(1))

        // Run migration
        runMigration()

        // Verify - should still be AWAITING_APPROVAL (only 1 unique approver, needs 2)
        val (reviewStatus, _) = getRequestStatuses(requestId)
        assertEquals("AWAITING_APPROVAL", reviewStatus)
    }

    @Test
    fun `migration calculates EXECUTED for SingleExecution with max executions reached`() {
        val connection = createConnection(numTotalRequired = 1, maxExecutions = 1)
        val requestId = createExecutionRequest(
            connectionId = connection.getId(),
            authorId = testUser.getId()!!,
            requestType = RequestType.SingleExecution,
        )

        // Approve and execute successfully
        createReviewEvent(requestId, reviewer1.getId()!!, "APPROVE")
        createExecuteEvent(requestId, testUser.getId()!!, hasError = false)

        // Run migration
        runMigration()

        // Verify
        val (_, executionStatus) = getRequestStatuses(requestId)
        assertEquals("EXECUTED", executionStatus)
    }

    @Test
    fun `migration calculates EXECUTABLE when execution had error`() {
        val connection = createConnection(numTotalRequired = 1, maxExecutions = 1)
        val requestId = createExecutionRequest(
            connectionId = connection.getId(),
            authorId = testUser.getId()!!,
            requestType = RequestType.SingleExecution,
        )

        // Approve and execute with error
        createReviewEvent(requestId, reviewer1.getId()!!, "APPROVE")
        createExecuteEvent(requestId, testUser.getId()!!, hasError = true)

        // Run migration
        runMigration()

        // Verify - should be EXECUTABLE because error executions don't count
        val (_, executionStatus) = getRequestStatuses(requestId)
        assertEquals("EXECUTABLE", executionStatus)
    }

    @Test
    fun `migration calculates EXECUTABLE for unlimited executions`() {
        val connection = createConnection(numTotalRequired = 1, maxExecutions = 0) // 0 = unlimited
        val requestId = createExecutionRequest(
            connectionId = connection.getId(),
            authorId = testUser.getId()!!,
            requestType = RequestType.SingleExecution,
        )

        // Execute multiple times
        createReviewEvent(requestId, reviewer1.getId()!!, "APPROVE")
        createExecuteEvent(requestId, testUser.getId()!!, hasError = false)
        createExecuteEvent(requestId, testUser.getId()!!, hasError = false)

        // Run migration
        runMigration()

        // Verify - should remain EXECUTABLE
        val (_, executionStatus) = getRequestStatuses(requestId)
        assertEquals("EXECUTABLE", executionStatus)
    }

    @Test
    fun `migration calculates ACTIVE for TemporaryAccess within duration`() {
        val connection = createConnection(numTotalRequired = 1)
        val requestId = createExecutionRequest(
            connectionId = connection.getId(),
            authorId = testUser.getId()!!,
            requestType = RequestType.TemporaryAccess,
            temporaryAccessDuration = 60L, // 60 minutes
        )

        // Execute 30 minutes ago (within duration)
        createReviewEvent(requestId, reviewer1.getId()!!, "APPROVE")

        createExecuteEvent(
            requestId,
            testUser.getId()!!,
            hasError = false,
            createdAt = LocalDateTime.now().minusMinutes(30),
        )

        // Run migration
        runMigration()

        // Verify
        val (_, executionStatus) = getRequestStatuses(requestId)
        assertEquals("ACTIVE", executionStatus)
    }

    @Test
    fun `migration calculates EXECUTED for TemporaryAccess beyond duration`() {
        val connection = createConnection(numTotalRequired = 1)
        val requestId = createExecutionRequest(
            connectionId = connection.getId(),
            authorId = testUser.getId()!!,
            requestType = RequestType.TemporaryAccess,
            temporaryAccessDuration = 60L, // 60 minutes
        )

        // Execute 90 minutes ago (beyond duration)
        createReviewEvent(requestId, reviewer1.getId()!!, "APPROVE")
        createExecuteEvent(
            requestId,
            testUser.getId()!!,
            hasError = false,
            createdAt = LocalDateTime.now().minusMinutes(90),
        )

        // Run migration
        runMigration()

        // Verify
        val (_, executionStatus) = getRequestStatuses(requestId)
        assertEquals("EXECUTED", executionStatus)
    }

    @Test
    fun `migration calculates ACTIVE for TemporaryAccess with NULL duration`() {
        val connection = createConnection(numTotalRequired = 1)
        val requestId = createExecutionRequest(
            connectionId = connection.getId(),
            authorId = testUser.getId()!!,
            requestType = RequestType.TemporaryAccess,
            temporaryAccessDuration = null, // Infinite access
        )

        // Execute long ago
        createReviewEvent(requestId, reviewer1.getId()!!, "APPROVE")
        createExecuteEvent(
            requestId,
            testUser.getId()!!,
            hasError = false,
            createdAt = LocalDateTime.now().minusDays(30),
        )

        // Run migration
        runMigration()

        // Verify - should be ACTIVE because duration is infinite
        val (_, executionStatus) = getRequestStatuses(requestId)
        assertEquals("ACTIVE", executionStatus)
    }

    @Test
    fun `migration handles request with no events`() {
        val connection = createConnection(numTotalRequired = 1)
        val requestId = createExecutionRequest(
            connectionId = connection.getId(),
            authorId = testUser.getId()!!,
            requestType = RequestType.SingleExecution,
        )

        // No events created

        // Run migration
        runMigration()

        // Verify - should have default values
        val (reviewStatus, executionStatus) = getRequestStatuses(requestId)
        assertEquals("AWAITING_APPROVAL", reviewStatus)
        assertEquals("EXECUTABLE", executionStatus)
    }

    @Test
    fun `migration processes multiple requests correctly`() {
        val connection = createConnection(numTotalRequired = 1)

        // Create 3 requests with different statuses
        val request1 = createExecutionRequest(connection.getId(), testUser.getId()!!, RequestType.SingleExecution)
        createReviewEvent(request1, reviewer1.getId()!!, "APPROVE")

        val request2 = createExecutionRequest(connection.getId(), testUser.getId()!!, RequestType.SingleExecution)
        createReviewEvent(request2, reviewer1.getId()!!, "REJECT")

        val request3 = createExecutionRequest(connection.getId(), testUser.getId()!!, RequestType.SingleExecution)
        // No events

        // Run migration
        runMigration()

        // Verify all 3 independently
        val (review1, _) = getRequestStatuses(request1)
        assertEquals("APPROVED", review1)

        val (review2, _) = getRequestStatuses(request2)
        assertEquals("REJECTED", review2)

        val (review3, _) = getRequestStatuses(request3)
        assertEquals("AWAITING_APPROVAL", review3)
    }

    // Helper methods

    private fun createConnection(numTotalRequired: Int = 1, maxExecutions: Int = 1): Connection =
        connectionAdapter.createDatasourceConnection(
            ConnectionId("test-conn-${idGenerator.generateId()}"),
            "Test Connection",
            AuthenticationType.USER_PASSWORD,
            "test_db",
            maxExecutions,
            "root",
            "root",
            "A test connection",
            ReviewConfig(numTotalRequired = numTotalRequired),
            db.getMappedPort(5432),
            db.host,
            DatasourceType.POSTGRESQL,
            DatabaseProtocol.POSTGRESQL,
            additionalJDBCOptions = "",
            dumpsEnabled = false,
            temporaryAccessEnabled = true,
            explainEnabled = false,
        )

    private fun createExecutionRequest(
        connectionId: String,
        authorId: String,
        requestType: RequestType,
        temporaryAccessDuration: Long? = null,
    ): String {
        val requestId = idGenerator.generateId() as String

        dataSource.connection.use { conn ->
            val sql = """
                    INSERT INTO execution_request
                    (id, datasource_id, title, author_id, execution_status, review_status, created_at,
                     execution_request_type, execution_type, description, statement, temporary_access_duration)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, requestId)
                stmt.setString(2, connectionId)
                stmt.setString(3, "Test Request")
                stmt.setString(4, authorId)
                stmt.setString(5, "EXECUTABLE") // Will be recalculated by migration
                stmt.setString(6, "AWAITING_APPROVAL") // Will be recalculated by migration
                stmt.setObject(7, LocalDateTime.now())
                stmt.setString(8, "DATASOURCE")
                stmt.setString(9, requestType.name)
                stmt.setString(10, "Test description")
                stmt.setString(11, "SELECT 1")
                stmt.setObject(12, temporaryAccessDuration)
                stmt.executeUpdate()
            }
        }

        return requestId
    }

    private fun createReviewEvent(
        requestId: String,
        authorId: String,
        action: String,
        createdAt: LocalDateTime = LocalDateTime.now(),
    ) {
        val eventId = idGenerator.generateId() as String
        val payload = mapOf(
            "foobar" to "REVIEW",
            "comment" to "Test comment",
            "action" to action,
        )

        dataSource.connection.use { conn ->
            val sql = """
                    INSERT INTO event (id, execution_request_id, author_id, type, payload, created_at)
                    VALUES (?, ?, ?, ?, ?::json, ?)
                """
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, eventId)
                stmt.setString(2, requestId)
                stmt.setString(3, authorId)
                stmt.setString(4, "REVIEW")
                stmt.setString(5, objectMapper.writeValueAsString(payload))
                stmt.setObject(6, createdAt)
                stmt.executeUpdate()
            }
        }
    }

    private fun createExecuteEvent(
        requestId: String,
        authorId: String,
        hasError: Boolean = false,
        createdAt: LocalDateTime = LocalDateTime.now(),
    ) {
        val eventId = idGenerator.generateId() as String
        val results = if (hasError) {
            listOf(mapOf("type" to "ERROR", "message" to "Test error", "errorCode" to 1))
        } else {
            listOf(mapOf("type" to "QUERY", "columnCount" to 1, "rowCount" to 1))
        }

        val payload = mapOf(
            "foobar" to "EXECUTE",
            "query" to "SELECT 1",
            "results" to results,
        )

        dataSource.connection.use { conn ->
            val sql = """
                    INSERT INTO event (id, execution_request_id, author_id, type, payload, created_at)
                    VALUES (?, ?, ?, ?, ?::json, ?)
                """
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, eventId)
                stmt.setString(2, requestId)
                stmt.setString(3, authorId)
                stmt.setString(4, "EXECUTE")
                stmt.setString(5, objectMapper.writeValueAsString(payload))
                stmt.setObject(6, createdAt)
                stmt.executeUpdate()
            }
        }
    }

    private fun createEditEvent(requestId: String, authorId: String, createdAt: LocalDateTime = LocalDateTime.now()) {
        val eventId = idGenerator.generateId() as String
        val payload = mapOf(
            "foobar" to "EDIT",
            "previousQuery" to "SELECT 1",
        )

        dataSource.connection.use { conn ->
            val sql = """
                    INSERT INTO event (id, execution_request_id, author_id, type, payload, created_at)
                    VALUES (?, ?, ?, ?, ?::json, ?)
                """
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, eventId)
                stmt.setString(2, requestId)
                stmt.setString(3, authorId)
                stmt.setString(4, "EDIT")
                stmt.setString(5, objectMapper.writeValueAsString(payload))
                stmt.setObject(6, createdAt)
                stmt.executeUpdate()
            }
        }
    }

    private fun runMigration() {
        dataSource.connection.use { conn ->
            val migration = RecalculateRequestStatuses()
            val database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(
                JdbcConnection(conn),
            )
            migration.execute(database)
            conn.commit()
        }
    }

    private fun getRequestStatuses(requestId: String): Pair<String, String> {
        dataSource.connection.use { conn ->
            val sql = "SELECT review_status, execution_status FROM execution_request WHERE id = ?"
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, requestId)
                val rs = stmt.executeQuery()
                if (rs.next()) {
                    return Pair(rs.getString("review_status"), rs.getString("execution_status"))
                }
                throw IllegalStateException("Request $requestId not found")
            }
        }
    }
}
