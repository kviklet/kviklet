// This file is not MIT licensed
package dev.kviklet.kviklet.proxy

import dev.kviklet.kviklet.db.EventAdapter
import dev.kviklet.kviklet.db.ExecutionRequestAdapter
import dev.kviklet.kviklet.proxy.helpers.ProxyInstance
import dev.kviklet.kviklet.proxy.helpers.directConnectionFactory
import dev.kviklet.kviklet.proxy.helpers.proxyServerFactory
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.containers.PostgreSQLContainer
import java.sql.Connection
import java.sql.DriverManager
import java.sql.Types
import java.util.Properties

@SpringBootTest
@ActiveProfiles("test")
class PostgresProxyPreparedStatementTest {
    @Autowired
    lateinit var executionRequestAdapter: ExecutionRequestAdapter

    @Autowired
    lateinit var eventAdapter: EventAdapter
    private lateinit var directConnection: Connection
    private lateinit var proxy: ProxyInstance
    private lateinit var postgresContainer: PostgreSQLContainer<Nothing>

    @BeforeEach
    fun setup() {
        postgresContainer = PostgreSQLContainer<Nothing>("postgres:13").apply {
            withDatabaseName("testdb")
            withUsername("test")
            withPassword("test")
        }
        postgresContainer.start()
        while (!postgresContainer.isRunning) {
            Thread.sleep(1000)
        }
        this.directConnection = directConnectionFactory(postgresContainer)
        this.proxy = proxyServerFactory(postgresContainer, executionRequestAdapter, eventAdapter)
        directConnection.createStatement().executeUpdate(
            "CREATE TABLE IF NOT EXISTS proxy_test_params (id INTEGER, name VARCHAR(64), active BOOLEAN);",
        )
    }

    @AfterEach
    fun tearDown() {
        this.proxy.proxy.shutdownServer()
        this.proxy.connection.close()
        this.postgresContainer.stop()
    }

    private fun proxyConnection(prepareThreshold: Int? = null): Connection {
        val props = Properties()
        props.setProperty("user", "proxyUser")
        props.setProperty("password", "proxyPassword")
        // Pin parameters to the text wire format so the assertions below are deterministic
        props.setProperty("binaryTransfer", "false")
        prepareThreshold?.let { props.setProperty("prepareThreshold", it.toString()) }
        return DriverManager.getConnection(this.proxy.connectionString, props)
    }

    @Test
    fun `prepared statement parameters are executed and audited with their values`() {
        val connection = proxyConnection()
        val statement = connection.prepareStatement("INSERT INTO proxy_test_params(id, name) VALUES (?, ?)")
        statement.setInt(1, 5)
        statement.setString(2, "test")
        assertEquals(1, statement.executeUpdate())
        connection.close()

        val result = directConnection.createStatement()
            .executeQuery("SELECT name FROM proxy_test_params WHERE id = 5;")
        assertTrue(result.next())
        assertEquals("test", result.getString("name"))

        this.proxy.eventService.assertAuditedQueryContains("VALUES ('5', 'test')")
    }

    @Test
    fun `NULL parameters do not kill the session and are audited as NULL`() {
        val connection = proxyConnection()
        val statement = connection.prepareStatement("INSERT INTO proxy_test_params(id, name) VALUES (?, ?)")
        statement.setInt(1, 6)
        statement.setNull(2, Types.VARCHAR)
        assertEquals(1, statement.executeUpdate())
        connection.close()

        val result = directConnection.createStatement()
            .executeQuery("SELECT name FROM proxy_test_params WHERE id = 6;")
        assertTrue(result.next())
        assertEquals(null, result.getString("name"))

        this.proxy.eventService.assertAuditedQueryContains("VALUES ('6', NULL)")
    }

    @Test
    fun `boolean parameters are audited with their text value`() {
        val connection = proxyConnection()
        val statement = connection.prepareStatement(
            "INSERT INTO proxy_test_params(id, name, active) VALUES (?, ?, ?)",
        )
        statement.setInt(1, 7)
        statement.setString(2, "bool-test")
        statement.setBoolean(3, true)
        assertEquals(1, statement.executeUpdate())
        connection.close()

        val result = directConnection.createStatement()
            .executeQuery("SELECT active FROM proxy_test_params WHERE id = 7;")
        assertTrue(result.next())
        assertEquals(true, result.getBoolean("active"))

        this.proxy.eventService.assertAuditedQueryContains("VALUES ('7', 'bool-test', 'TRUE')")
    }

    @Test
    fun `parameter values with quotes and placeholders are audited escaped and unmodified`() {
        val connection = proxyConnection()
        val statement = connection.prepareStatement("INSERT INTO proxy_test_params(id, name) VALUES (?, ?)")
        statement.setInt(1, 8)
        statement.setString(2, "O'Brien costs \$2")
        assertEquals(1, statement.executeUpdate())
        connection.close()

        val result = directConnection.createStatement()
            .executeQuery("SELECT name FROM proxy_test_params WHERE id = 8;")
        assertTrue(result.next())
        assertEquals("O'Brien costs \$2", result.getString("name"))

        this.proxy.eventService.assertAuditedQueryContains("'O''Brien costs \$2'")
    }

    @Test
    fun `queries with ten or more parameters are audited correctly`() {
        val placeholders = (1..10).joinToString(", ") { "?" }
        val connection = proxyConnection()
        val statement = connection.prepareStatement("SELECT ARRAY[$placeholders]::text[]")
        for (i in 1..10) {
            statement.setString(i, "value$i")
        }
        val result = statement.executeQuery()
        assertTrue(result.next())
        connection.close()

        this.proxy.eventService.assertAuditedQueryContains("'value10'")
    }

    @Test
    fun `named server side prepared statements are executed and audited`() {
        // prepareThreshold=1 makes pgjdbc use a named server-side statement from the first execution,
        // like Npgsql or pgx do by default.
        val connection = proxyConnection(prepareThreshold = 1)
        val statement = connection.prepareStatement("SELECT 41 + 1")
        repeat(3) {
            val result = statement.executeQuery()
            assertTrue(result.next())
            assertEquals(42, result.getInt(1))
        }
        connection.close()

        assertEquals(3, this.proxy.eventService.queries.count { it == "select41+1" })
    }

    @Test
    fun `interleaved prepared statements audit the query that was actually executed`() {
        // After prepareThreshold (default 5) executions pgjdbc switches to a named server statement
        // and stops sending Parse. The audit log must still attribute executions to the right query.
        val connection = proxyConnection()
        val statementA = connection.prepareStatement("SELECT 1")
        val statementB = connection.prepareStatement("SELECT 2")
        repeat(5) {
            val result = statementA.executeQuery()
            assertTrue(result.next())
            assertEquals(1, result.getInt(1))
        }
        val resultB = statementB.executeQuery()
        assertTrue(resultB.next())
        assertEquals(2, resultB.getInt(1))
        // Sixth execution of A: named statement, no Parse is sent anymore
        val resultA = statementA.executeQuery()
        assertTrue(resultA.next())
        assertEquals(1, resultA.getInt(1))
        connection.close()

        assertEquals(6, this.proxy.eventService.queries.count { it == "select1" })
        assertEquals(1, this.proxy.eventService.queries.count { it == "select2" })
    }
}
