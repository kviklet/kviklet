package dev.kviklet.kviklet

import dev.kviklet.kviklet.db.ExecutePayload
import dev.kviklet.kviklet.db.ExecutionRequestAdapter
import dev.kviklet.kviklet.db.ReviewPayload
import dev.kviklet.kviklet.db.User
import dev.kviklet.kviklet.helper.ConnectionHelper
import dev.kviklet.kviklet.helper.ExecutionRequestHelper
import dev.kviklet.kviklet.helper.RoleHelper
import dev.kviklet.kviklet.helper.UserHelper
import dev.kviklet.kviklet.service.ExecutionRequestService
import dev.kviklet.kviklet.service.dto.Connection
import dev.kviklet.kviklet.service.dto.ExecutionRequestDetails
import dev.kviklet.kviklet.service.dto.ExecutionRequestId
import dev.kviklet.kviklet.service.dto.ExecutionStatus
import dev.kviklet.kviklet.service.dto.RequestType
import dev.kviklet.kviklet.service.dto.ReviewAction
import dev.kviklet.kviklet.service.dto.ReviewStatus
import dev.kviklet.kviklet.service.dto.utcTimeNow
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.io.FileUrlResource
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.init.ScriptUtils
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.sql.Timestamp
import java.time.Duration

@SpringBootTest
@ActiveProfiles("test")
class ExecutionRequestStatusRefreshTest {

    @Autowired private lateinit var executionRequestService: ExecutionRequestService

    @Autowired private lateinit var executionRequestAdapter: ExecutionRequestAdapter

    @Autowired private lateinit var executionRequestHelper: ExecutionRequestHelper

    @Autowired private lateinit var connectionHelper: ConnectionHelper

    @Autowired private lateinit var userHelper: UserHelper

    @Autowired private lateinit var roleHelper: RoleHelper

    @Autowired private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired private lateinit var entityManager: EntityManager

    private lateinit var testUser: User
    private lateinit var testConnection: Connection

    companion object {
        private val db: PostgreSQLContainer<*> =
            PostgreSQLContainer(DockerImageName.parse("postgres:11.1"))
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
        val initScript = this::class.java.classLoader.getResource("psql_init.sql")!!
        ScriptUtils.executeSqlScript(db.createConnection(""), FileUrlResource(initScript))

        testUser = userHelper.createUser(permissions = listOf("*"))
        testConnection = connectionHelper.createPostgresConnection(db)
    }

    @AfterEach
    fun tearDown() {
        executionRequestHelper.deleteAll()
        connectionHelper.deleteAll()
        userHelper.deleteAll()
        roleHelper.deleteAll()
    }

    @Test
    fun `temporary access status is refreshed to EXECUTED when listing after expiry`() {
        val createdRequest: ExecutionRequestDetails = executionRequestAdapter.createExecutionRequest(
            connectionId = testConnection.id,
            title = "Temporary access status refresh",
            type = RequestType.TemporaryAccess,
            description = "Test temporary access request",
            executionStatus = ExecutionStatus.EXECUTABLE,
            reviewStatus = ReviewStatus.AWAITING_APPROVAL,
            authorId = testUser.getId()!!,
            temporaryAccessDuration = Duration.ofMinutes(60),
        )
        val requestId: ExecutionRequestId = createdRequest.request.id!!

        executionRequestAdapter.addEvent(
            requestId,
            testUser.getId()!!,
            ReviewPayload(
                action = ReviewAction.APPROVE,
                comment = "approved",
            ),
        )

        val (_, executeEvent) = executionRequestAdapter.addEvent(
            requestId,
            testUser.getId()!!,
            ExecutePayload(
                query = "SELECT 1",
            ),
        )

        val expiredAt = utcTimeNow().minusMinutes(90)
        jdbcTemplate.update(
            "UPDATE event SET created_at = ? WHERE id = ?",
            Timestamp.valueOf(expiredAt),
            executeEvent.eventId.toString(),
        )
        entityManager.clear()

        val storedBefore: String = jdbcTemplate.queryForObject(
            "SELECT execution_status FROM execution_request WHERE id = ?",
            String::class.java,
            requestId.toString(),
        )!!
        assertEquals("ACTIVE", storedBefore)

        entityManager.clear()
        val listResult = executionRequestService.list(null, null, null, null, 20)
        val listDetails = listResult.requests.first { it.request.id == requestId }
        val refreshedStatus = listDetails.request.executionStatus
        assertEquals("EXECUTED", refreshedStatus, "expected EXECUTED in list but was $refreshedStatus")
        assertEquals(
            ExecutionStatus.EXECUTED,
            listDetails.resolveExecutionStatus(),
            "recalculated status should also be EXECUTED",
        )

        val storedAfterList: String = jdbcTemplate.queryForObject(
            "SELECT execution_status FROM execution_request WHERE id = ?",
            String::class.java,
            requestId.toString(),
        )!!
        assertEquals("EXECUTED", storedAfterList)

        entityManager.clear()
        val details = executionRequestService.get(requestId)
        assertEquals("EXECUTED", details.request.executionStatus)
    }

    @Test
    fun `get refreshes stale execution and review statuses`() {
        val reviewer = userHelper.createUser(permissions = listOf("*"))
        val connection = connectionHelper.createPostgresConnection(db)

        val createdRequest = executionRequestAdapter.createExecutionRequest(
            connectionId = connection.id,
            title = "Stale status request",
            type = RequestType.SingleExecution,
            description = "Test stale status",
            statement = "SELECT 1",
            executionStatus = ExecutionStatus.EXECUTABLE,
            reviewStatus = ReviewStatus.AWAITING_APPROVAL,
            authorId = testUser.getId()!!,
        )
        val requestId = createdRequest.request.id!!

        executionRequestAdapter.addEvent(
            requestId,
            reviewer.getId()!!,
            ReviewPayload(
                action = ReviewAction.APPROVE,
                comment = "approved",
            ),
        )

        jdbcTemplate.update(
            "UPDATE execution_request SET execution_status = ?, review_status = ? WHERE id = ?",
            "ACTIVE",
            "AWAITING_APPROVAL",
            requestId.toString(),
        )

        entityManager.clear()

        val details = executionRequestService.get(requestId)

        assertEquals("EXECUTABLE", details.request.executionStatus)
        assertEquals(ReviewStatus.APPROVED, details.resolveReviewStatus())

        val stored = jdbcTemplate.queryForMap(
            "SELECT execution_status, review_status FROM execution_request WHERE id = ?",
            requestId.toString(),
        )
        assertEquals("EXECUTABLE", stored["execution_status"])
        assertEquals("APPROVED", stored["review_status"])
    }

    @Test
    fun `list refreshes stale review status`() {
        val reviewer = userHelper.createUser(permissions = listOf("*"))
        val createdRequest = executionRequestAdapter.createExecutionRequest(
            connectionId = testConnection.id,
            title = "Review status refresh",
            type = RequestType.SingleExecution,
            description = "Test review refresh",
            statement = "SELECT 1",
            executionStatus = ExecutionStatus.EXECUTABLE,
            reviewStatus = ReviewStatus.AWAITING_APPROVAL,
            authorId = testUser.getId()!!,
        )
        val requestId = createdRequest.request.id!!

        executionRequestAdapter.addEvent(
            requestId,
            reviewer.getId()!!,
            ReviewPayload(
                action = ReviewAction.APPROVE,
                comment = "approved",
            ),
        )

        jdbcTemplate.update(
            "UPDATE execution_request SET review_status = ? WHERE id = ?",
            "AWAITING_APPROVAL",
            requestId.toString(),
        )

        entityManager.clear()

        val listResult = executionRequestService.list(null, null, null, null, 20)
        val refreshed = listResult.requests.first { it.request.id == requestId }

        assertEquals(ReviewStatus.APPROVED, refreshed.resolveReviewStatus())
        assertEquals(
            ReviewStatus.APPROVED.name,
            refreshed.request.reviewStatus,
        )

        val stored = jdbcTemplate.queryForMap(
            "SELECT execution_status, review_status FROM execution_request WHERE id = ?",
            requestId.toString(),
        )
        assertEquals("EXECUTABLE", stored["execution_status"])
        assertEquals("APPROVED", stored["review_status"])
    }
}
