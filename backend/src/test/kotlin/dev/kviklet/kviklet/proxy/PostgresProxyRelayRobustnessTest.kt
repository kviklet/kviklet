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
import org.junit.jupiter.api.assertThrows
import org.postgresql.core.PGStream
import org.postgresql.core.QueryExecutorBase
import org.postgresql.jdbc.PgConnection
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.containers.PostgreSQLContainer
import java.net.Socket
import java.nio.ByteBuffer
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.util.Properties
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible

@SpringBootTest
@ActiveProfiles("test")
class PostgresProxyRelayRobustnessTest {
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
            "CREATE TABLE IF NOT EXISTS proxy_test_relay (id INTEGER, payload TEXT);",
        )
    }

    @AfterEach
    fun tearDown() {
        this.proxy.proxy.shutdownServer()
        // Some tests kill the proxied session on purpose, closing it afterwards may fail
        runCatching { this.proxy.connection.close() }
        this.postgresContainer.stop()
    }

    private fun proxyConnection(): Connection {
        val props = Properties()
        props.setProperty("user", "proxyUser")
        props.setProperty("password", "proxyPassword")
        return DriverManager.getConnection(this.proxy.connectionString, props)
    }

    // The pgjdbc API does not expose its socket, but the tests need it to simulate abrupt
    // disconnects and raw protocol messages, so it is extracted the same way the proxy itself
    // extracts the upstream socket in TargetPostgresSocketFactory
    private fun clientSideSocketOf(connection: Connection): Socket {
        val queryExecutor = connection.unwrap(PgConnection::class.java).queryExecutor as QueryExecutorBase
        val pgStreamProperty = QueryExecutorBase::class.memberProperties.first { it.name == "pgStream" }
        pgStreamProperty.isAccessible = true
        return (pgStreamProperty.get(queryExecutor) as PGStream).socket
    }

    private fun awaitConnectionCount(expected: Int, timeoutMillis: Long = 15_000) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline && proxy.proxy.currentConnections != expected) {
            Thread.sleep(100)
        }
        assertEquals(
            expected,
            proxy.proxy.currentConnections,
            "Expected the proxy to be handling $expected connection(s)",
        )
    }

    @Test
    fun `simple queries larger than the socket read buffer are executed and audited`() {
        // A query above 8KB never arrives in a single socket read and must be reassembled
        val payload = "x".repeat(20_000) + "-end-marker"
        val connection = proxyConnection()
        connection.createStatement().executeUpdate(
            "INSERT INTO proxy_test_relay(id, payload) VALUES (1, '$payload');",
        )
        connection.close()

        val result = directConnection.createStatement()
            .executeQuery("SELECT payload FROM proxy_test_relay WHERE id = 1;")
        assertTrue(result.next())
        assertEquals(payload, result.getString("payload"))

        this.proxy.eventService.assertAuditedQueryContains("-end-marker")
    }

    @Test
    fun `prepared statement parameters larger than the socket read buffer are executed and audited`() {
        val payload = "y".repeat(20_000) + "-bind-marker"
        val connection = proxyConnection()
        val statement = connection.prepareStatement("INSERT INTO proxy_test_relay(id, payload) VALUES (?, ?)")
        statement.setInt(1, 2)
        statement.setString(2, payload)
        assertEquals(1, statement.executeUpdate())
        connection.close()

        val result = directConnection.createStatement()
            .executeQuery("SELECT payload FROM proxy_test_relay WHERE id = 2;")
        assertTrue(result.next())
        assertEquals(payload, result.getString("payload"))

        this.proxy.eventService.assertAuditedQueryContains("-bind-marker")
    }

    @Test
    fun `a client disconnecting without a terminate message frees the connection slot`() {
        val baseline = proxy.proxy.currentConnections
        val connection = proxyConnection()
        val result = connection.createStatement().executeQuery("SELECT 1;")
        assertTrue(result.next())
        awaitConnectionCount(baseline + 1)

        // Closing the socket directly sends no Terminate message, the proxy only sees EOF
        clientSideSocketOf(connection).close()

        awaitConnectionCount(baseline)
    }

    @Test
    fun `the server closing the connection frees the connection slot`() {
        val connection = proxyConnection()
        val result = connection.createStatement().executeQuery("SELECT 1;")
        assertTrue(result.next())

        // Kill every proxied upstream session server-side, the proxy sees EOF from the server
        directConnection.createStatement().executeQuery(
            "SELECT pg_terminate_backend(pid) FROM pg_stat_activity " +
                "WHERE usename = 'test' AND pid <> pg_backend_pid();",
        )

        awaitConnectionCount(0)
    }

    @Test
    fun `fast path function calls are blocked and close the session`() {
        val baseline = proxy.proxy.currentConnections
        val connection = proxyConnection()
        val result = connection.createStatement().executeQuery("SELECT 1;")
        assertTrue(result.next())
        awaitConnectionCount(baseline + 1)
        val auditedBefore = proxy.eventService.rawQueries.size

        // A minimal FunctionCall message: function OID 0, no format codes, no arguments,
        // text result format. It executes a function server-side without any Query/Parse/Bind,
        // so the proxy cannot audit it and must reject it.
        val functionCall = ByteBuffer.allocate(15)
        functionCall.put('F'.code.toByte())
        functionCall.putInt(14)
        functionCall.putInt(0)
        functionCall.putShort(0)
        functionCall.putShort(0)
        functionCall.putShort(0)
        val socket = clientSideSocketOf(connection)
        socket.getOutputStream().apply {
            write(functionCall.array())
            flush()
        }

        // The proxy must abort the session without forwarding the call, freeing the slot
        awaitConnectionCount(baseline)
        assertEquals(auditedBefore, proxy.eventService.rawQueries.size)
        assertThrows<SQLException> {
            connection.createStatement().executeQuery("SELECT 1;")
        }
    }
}
