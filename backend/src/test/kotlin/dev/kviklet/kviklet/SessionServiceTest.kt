package dev.kviklet.kviklet.service.websocket

import dev.kviklet.kviklet.db.User
import dev.kviklet.kviklet.helper.ConnectionHelper
import dev.kviklet.kviklet.helper.ExecutionRequestHelper
import dev.kviklet.kviklet.helper.LiveSessionHelper
import dev.kviklet.kviklet.helper.UserHelper
import dev.kviklet.kviklet.service.dto.DBExecutionResult
import dev.kviklet.kviklet.service.dto.LiveSessionId
import dev.kviklet.kviklet.service.dto.RecordsQueryResult
import dev.kviklet.kviklet.service.dto.RequestType
import dev.kviklet.kviklet.service.dto.UpdateQueryResult
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class SessionServiceTest {

    @Autowired
    private lateinit var sessionService: SessionService

    @Autowired
    private lateinit var userHelper: UserHelper

    @Autowired
    private lateinit var connectionHelper: ConnectionHelper

    @Autowired
    private lateinit var executionRequestHelper: ExecutionRequestHelper

    @Autowired
    private lateinit var liveSessionHelper: LiveSessionHelper

    private lateinit var testUser: User
    private lateinit var testLiveSession: LiveSessionId

    companion object {
        @Container
        val postgresContainer = PostgreSQLContainer<Nothing>("postgres:13").apply {
            withDatabaseName("testdb")
            withUsername("test")
            withPassword("test")
        }
    }

    @BeforeEach
    fun setup() {
        // Create test user
        testUser = userHelper.createUser()
        val testApprover = userHelper.createUser()

        val executionRequest = executionRequestHelper.createApprovedRequest(
            dbcontainer = postgresContainer,
            author = testUser,
            approver = testApprover,
            requestType = RequestType.TemporaryAccess,
        )

        val liveSession = liveSessionHelper.createLiveSession(
            executionRequest = executionRequest,
            initialContent = "",
        )
        testLiveSession = liveSession.id!!
    }

    @AfterEach
    fun cleanup() {
        sessionService.executeStatement(testLiveSession, "DROP TABLE IF EXISTS test_table")
        liveSessionHelper.deleteAll()
        executionRequestHelper.deleteAll()
        connectionHelper.deleteAll()
        userHelper.deleteAll()
    }

    @Test
    fun testExecuteStatementSimpleSelect() {
        val result = sessionService.executeStatement(testLiveSession, "SELECT 1 as test")

        assertTrue(result is DBExecutionResult)
        result as DBExecutionResult

        assertEquals(1, result.results.size)
        val executionResult = result.results[0] as RecordsQueryResult
        assertEquals(1, executionResult.columns.size)
        assertEquals("test", executionResult.columns[0].label)
        assertEquals(1, executionResult.data.size)
        assertEquals("1", executionResult.data[0]["test"])
    }

    @Test
    fun testExecuteStatementMultipleStatements() {
        val multipleStatements = """
            CREATE TABLE test_table (id INT, name VARCHAR(50));
            INSERT INTO test_table VALUES (1, 'Alice'), (2, 'Bob');
            SELECT * FROM test_table ORDER BY id;
        """.trimIndent()

        val result = sessionService.executeStatement(testLiveSession, multipleStatements)

        assertTrue(result is DBExecutionResult)
        result as DBExecutionResult

        assertEquals(3, result.results.size)

        // Check CREATE TABLE result
        assertTrue(result.results[0] is UpdateQueryResult)
        assertEquals(0, (result.results[0] as UpdateQueryResult).rowsUpdated)

        // Check INSERT result
        assertTrue(result.results[1] is UpdateQueryResult)
        assertEquals(2, (result.results[1] as UpdateQueryResult).rowsUpdated)

        // Check SELECT result
        assertTrue(result.results[2] is RecordsQueryResult)
        val selectResult = result.results[2] as RecordsQueryResult
        assertEquals(2, selectResult.columns.size)
        assertEquals(2, selectResult.data.size)
        assertEquals("1", selectResult.data[0]["id"])
        assertEquals("Alice", selectResult.data[0]["name"])
        assertEquals("2", selectResult.data[1]["id"])
        assertEquals("Bob", selectResult.data[1]["name"])
    }

    @Test
    fun testExecuteStatementDataModification() {
        // Setup: Create a table
        sessionService.executeStatement(testLiveSession, "CREATE TABLE test_table (id INT, name VARCHAR(50))")

        // Test INSERT
        val insertResult = sessionService.executeStatement(
            testLiveSession,
            "INSERT INTO test_table VALUES (1, 'Alice'), (2, 'Bob')",
        )
        assertTrue(insertResult is DBExecutionResult)
        insertResult as DBExecutionResult
        assertTrue(insertResult.results[0] is UpdateQueryResult)
        assertEquals(2, (insertResult.results[0] as UpdateQueryResult).rowsUpdated)

        // Test UPDATE
        val updateResult = sessionService.executeStatement(
            testLiveSession,
            "UPDATE test_table SET name = 'Charlie' WHERE id = 1",
        )
        assertTrue(updateResult is DBExecutionResult)
        updateResult as DBExecutionResult
        assertTrue(updateResult.results[0] is UpdateQueryResult)
        assertEquals(1, (updateResult.results[0] as UpdateQueryResult).rowsUpdated)

        // Test DELETE
        val deleteResult = sessionService.executeStatement(testLiveSession, "DELETE FROM test_table WHERE id = 2")
        assertTrue(deleteResult is DBExecutionResult)
        deleteResult as DBExecutionResult
        assertTrue(deleteResult.results[0] is UpdateQueryResult)
        assertEquals(1, (deleteResult.results[0] as UpdateQueryResult).rowsUpdated)

        // Verify final state
        val selectResult = sessionService.executeStatement(testLiveSession, "SELECT * FROM test_table")
        assertTrue(selectResult is DBExecutionResult)
        selectResult as DBExecutionResult
        assertTrue(selectResult.results[0] is RecordsQueryResult)
        val finalState = selectResult.results[0] as RecordsQueryResult
        assertEquals(1, finalState.data.size)
        assertEquals("1", finalState.data[0]["id"])
        assertEquals("Charlie", finalState.data[0]["name"])
    }
}
