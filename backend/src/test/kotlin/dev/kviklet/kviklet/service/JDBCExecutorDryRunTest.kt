package dev.kviklet.kviklet.service

import dev.kviklet.kviklet.service.dto.AuthenticationDetails
import dev.kviklet.kviklet.service.dto.ErrorQueryResult
import dev.kviklet.kviklet.service.dto.ExecutionRequestId
import dev.kviklet.kviklet.service.dto.RecordsQueryResult
import dev.kviklet.kviklet.service.dto.UpdateQueryResult
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.io.FileUrlResource
import org.springframework.jdbc.datasource.init.ScriptUtils
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.sql.DriverManager

@SpringBootTest
@ActiveProfiles("test")
class JDBCExecutorDryRunTest {

    private val executor = JDBCExecutor()

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
        val initScript = this::class.java.classLoader.getResource("psql_init.sql")!!
        ScriptUtils.executeSqlScript(db.createConnection(""), FileUrlResource(initScript))
    }

    @AfterEach
    fun tearDown() {
        // Clean up the test table
        val connection = DriverManager.getConnection(db.jdbcUrl, db.username, db.password)
        connection.createStatement().use { statement ->
            statement.execute("DROP TABLE IF EXISTS test_users")
        }
        connection.close()
    }

    @Test
    fun `executeDryRun should rollback INSERT statement`() {
        // Setup: Create test table and insert initial data
        val setupConnection = DriverManager.getConnection(db.jdbcUrl, db.username, db.password)
        setupConnection.createStatement().use { statement ->
            statement.execute("CREATE TABLE test_users (id SERIAL PRIMARY KEY, name VARCHAR(100))")
            statement.execute("INSERT INTO test_users (name) VALUES ('Initial User')")
        }
        setupConnection.close()

        // Execute dry run INSERT
        val auth = AuthenticationDetails.UserPassword(db.username, db.password)
        val query = "INSERT INTO test_users (name) VALUES ('Dry Run User')"
        val results = executor.executeDryRun(
            ExecutionRequestId("test-request-1"),
            db.jdbcUrl,
            auth,
            query,
            isMSSQL = false,
        )

        // Verify the dry run returned success
        assertEquals(1, results.size)
        assertTrue(results[0] is UpdateQueryResult)
        assertEquals(1, (results[0] as UpdateQueryResult).rowsUpdated)

        // Verify the data was NOT actually inserted (rolled back)
        val verifyConnection = DriverManager.getConnection(db.jdbcUrl, db.username, db.password)
        val resultSet = verifyConnection.createStatement().executeQuery("SELECT COUNT(*) FROM test_users")
        resultSet.next()
        val count = resultSet.getInt(1)
        assertEquals(1, count, "Should only have the initial user, dry run insert should be rolled back")
        verifyConnection.close()
    }

    @Test
    fun `executeDryRun should rollback UPDATE statement`() {
        // Setup: Create test table and insert initial data
        val setupConnection = DriverManager.getConnection(db.jdbcUrl, db.username, db.password)
        setupConnection.createStatement().use { statement ->
            statement.execute("CREATE TABLE test_users (id SERIAL PRIMARY KEY, name VARCHAR(100))")
            statement.execute("INSERT INTO test_users (name) VALUES ('Original Name')")
        }
        setupConnection.close()

        // Execute dry run UPDATE
        val auth = AuthenticationDetails.UserPassword(db.username, db.password)
        val query = "UPDATE test_users SET name = 'Changed Name' WHERE id = 1"
        val results = executor.executeDryRun(
            ExecutionRequestId("test-request-2"),
            db.jdbcUrl,
            auth,
            query,
            isMSSQL = false,
        )

        // Verify the dry run returned success
        assertEquals(1, results.size)
        assertTrue(results[0] is UpdateQueryResult)
        assertEquals(1, (results[0] as UpdateQueryResult).rowsUpdated)

        // Verify the data was NOT actually updated (rolled back)
        val verifyConnection = DriverManager.getConnection(db.jdbcUrl, db.username, db.password)
        val resultSet = verifyConnection.createStatement()
            .executeQuery("SELECT name FROM test_users WHERE id = 1")
        resultSet.next()
        val name = resultSet.getString(1)
        assertEquals("Original Name", name, "Name should not be changed, dry run update should be rolled back")
        verifyConnection.close()
    }

    @Test
    fun `executeDryRun should rollback DELETE statement`() {
        // Setup: Create test table and insert initial data
        val setupConnection = DriverManager.getConnection(db.jdbcUrl, db.username, db.password)
        setupConnection.createStatement().use { statement ->
            statement.execute("CREATE TABLE test_users (id SERIAL PRIMARY KEY, name VARCHAR(100))")
            statement.execute("INSERT INTO test_users (name) VALUES ('User 1')")
            statement.execute("INSERT INTO test_users (name) VALUES ('User 2')")
        }
        setupConnection.close()

        // Execute dry run DELETE
        val auth = AuthenticationDetails.UserPassword(db.username, db.password)
        val query = "DELETE FROM test_users WHERE id = 1"
        val results = executor.executeDryRun(
            ExecutionRequestId("test-request-3"),
            db.jdbcUrl,
            auth,
            query,
            isMSSQL = false,
        )

        // Verify the dry run returned success
        assertEquals(1, results.size)
        assertTrue(results[0] is UpdateQueryResult)
        assertEquals(1, (results[0] as UpdateQueryResult).rowsUpdated)

        // Verify the data was NOT actually deleted (rolled back)
        val verifyConnection = DriverManager.getConnection(db.jdbcUrl, db.username, db.password)
        val resultSet = verifyConnection.createStatement().executeQuery("SELECT COUNT(*) FROM test_users")
        resultSet.next()
        val count = resultSet.getInt(1)
        assertEquals(2, count, "Should still have both users, dry run delete should be rolled back")
        verifyConnection.close()
    }

    @Test
    fun `executeDryRun should return results for SELECT query`() {
        // Setup: Create test table and insert initial data
        val setupConnection = DriverManager.getConnection(db.jdbcUrl, db.username, db.password)
        setupConnection.createStatement().use { statement ->
            statement.execute("CREATE TABLE test_users (id SERIAL PRIMARY KEY, name VARCHAR(100))")
            statement.execute("INSERT INTO test_users (name) VALUES ('User 1')")
            statement.execute("INSERT INTO test_users (name) VALUES ('User 2')")
        }
        setupConnection.close()

        // Execute dry run SELECT
        val auth = AuthenticationDetails.UserPassword(db.username, db.password)
        val query = "SELECT * FROM test_users ORDER BY id"
        val results = executor.executeDryRun(
            ExecutionRequestId("test-request-4"),
            db.jdbcUrl,
            auth,
            query,
            isMSSQL = false,
        )

        // Verify the dry run returned correct results
        assertEquals(1, results.size)
        assertTrue(results[0] is RecordsQueryResult)
        val recordsResult = results[0] as RecordsQueryResult
        assertEquals(2, recordsResult.data.size)
        assertEquals("User 1", recordsResult.data[0]["name"])
        assertEquals("User 2", recordsResult.data[1]["name"])
    }

    @Test
    fun `executeDryRun should handle SQL errors`() {
        // Execute dry run with invalid SQL
        val auth = AuthenticationDetails.UserPassword(db.username, db.password)
        val query = "SELECT * FROM nonexistent_table"
        val results = executor.executeDryRun(
            ExecutionRequestId("test-request-5"),
            db.jdbcUrl,
            auth,
            query,
            isMSSQL = false,
        )

        // Verify the dry run returned an error
        assertEquals(1, results.size)
        assertTrue(results[0] is ErrorQueryResult)
        val errorResult = results[0] as ErrorQueryResult
        assertTrue(errorResult.message.contains("nonexistent_table") || errorResult.message.contains("does not exist"))
    }

    @Test
    fun `executeDryRun should handle multiple statements and rollback all`() {
        // Setup: Create test table
        val setupConnection = DriverManager.getConnection(db.jdbcUrl, db.username, db.password)
        setupConnection.createStatement().use { statement ->
            statement.execute("CREATE TABLE test_users (id SERIAL PRIMARY KEY, name VARCHAR(100))")
        }
        setupConnection.close()

        // Execute dry run with multiple INSERT statements
        val auth = AuthenticationDetails.UserPassword(db.username, db.password)
        val query = """
            INSERT INTO test_users (name) VALUES ('User 1');
            INSERT INTO test_users (name) VALUES ('User 2');
            INSERT INTO test_users (name) VALUES ('User 3');
        """.trimIndent()
        val results = executor.executeDryRun(
            ExecutionRequestId("test-request-6"),
            db.jdbcUrl,
            auth,
            query,
            isMSSQL = false,
        )

        // Verify all statements executed
        assertEquals(3, results.size)
        results.forEach { result ->
            assertTrue(result is UpdateQueryResult)
            assertEquals(1, (result as UpdateQueryResult).rowsUpdated)
        }

        // Verify all changes were rolled back
        val verifyConnection = DriverManager.getConnection(db.jdbcUrl, db.username, db.password)
        val resultSet = verifyConnection.createStatement().executeQuery("SELECT COUNT(*) FROM test_users")
        resultSet.next()
        val count = resultSet.getInt(1)
        assertEquals(0, count, "All inserts should be rolled back")
        verifyConnection.close()
    }
}
