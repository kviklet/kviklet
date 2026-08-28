// This file is not MIT licensed
package dev.kviklet.kviklet.proxy

import dev.kviklet.kviklet.db.EventAdapter
import dev.kviklet.kviklet.db.ExecutionRequestAdapter
import dev.kviklet.kviklet.proxy.helpers.ProxyInstance
import dev.kviklet.kviklet.proxy.helpers.directConnectionFactory
import dev.kviklet.kviklet.proxy.helpers.proxyServerFactory
import dev.kviklet.kviklet.proxy.postgres.PostgresProxyServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
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
import java.util.Properties
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible

@SpringBootTest
@ActiveProfiles("test")
class PostgresProxyConnectionLifecycleTest {
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
    }

    @AfterEach
    fun tearDown() {
        this.proxy.proxy.shutdownServer()
        runCatching { this.proxy.connection.close() }
        this.postgresContainer.stop()
    }

    private fun proxyConnection(): Connection {
        val props = Properties()
        props.setProperty("user", "proxyUser")
        props.setProperty("password", "proxyPassword")
        return DriverManager.getConnection(this.proxy.connectionString, props)
    }

    // Counts upstream connections opened by the proxy (all use the target user 'test'), excluding the
    // backend running this very query, which belongs to directConnection.
    private fun upstreamConnectionCount(): Int {
        directConnection.createStatement().executeQuery(
            "SELECT count(*) FROM pg_stat_activity WHERE usename = 'test' AND pid <> pg_backend_pid();",
        ).use { rs ->
            rs.next()
            return rs.getInt(1)
        }
    }

    private fun awaitUpstreamCount(expected: Int, timeoutMillis: Long = 20_000) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        var last = -1
        while (System.currentTimeMillis() < deadline) {
            last = upstreamConnectionCount()
            if (last == expected) return
            Thread.sleep(200)
        }
        assertEquals(expected, last, "Upstream connection count on the target database did not settle")
    }

    private fun awaitConnectionCount(
        expected: Int,
        target: PostgresProxyServer = proxy.proxy,
        timeoutMillis: Long = 20_000,
    ) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline && target.currentConnections != expected) {
            Thread.sleep(100)
        }
        assertEquals(expected, target.currentConnections, "Expected the proxy to be handling $expected connection(s)")
    }

    // Reads the size of the proxy's private clientConnections list. Pruning it on teardown is an
    // internal guarantee (a regression would leak Connection objects, not sockets or slots), so it is
    // verified here by reflection rather than exposing a production counter that would sit confusingly
    // next to currentConnections.
    private fun trackedConnectionCount(): Int {
        val field = PostgresProxyServer::class.java.getDeclaredField("clientConnections")
        field.isAccessible = true
        return (field.get(proxy.proxy) as Collection<*>).size
    }

    private fun awaitTrackedCount(expected: Int, timeoutMillis: Long = 20_000) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline && trackedConnectionCount() != expected) {
            Thread.sleep(100)
        }
        assertEquals(expected, trackedConnectionCount(), "Expected $expected tracked connection(s)")
    }

    // KVI-230: the happy path leaks. Even after a clean disconnect the upstream socket is never
    // closed and the Connection is never removed from the tracking list.
    @Test
    fun `cleanly disconnected sessions close their upstream connections and are pruned from tracking`() {
        val baselineUpstream = upstreamConnectionCount()
        val baselineTracked = trackedConnectionCount()
        val sessionCount = 3

        val connections = (1..sessionCount).map { proxyConnection() }
        connections.forEach { it.createStatement().executeQuery("SELECT 1;").close() }
        awaitUpstreamCount(baselineUpstream + sessionCount)
        awaitTrackedCount(baselineTracked + sessionCount)

        connections.forEach { it.close() }

        awaitUpstreamCount(baselineUpstream)
        awaitTrackedCount(baselineTracked)
    }

    // The pgjdbc API does not expose its socket, but simulating an abrupt disconnect needs it, so it
    // is extracted the same way the proxy extracts the upstream socket in TargetPostgresSocketFactory.
    private fun clientSideSocketOf(connection: Connection): Socket {
        val queryExecutor = connection.unwrap(PgConnection::class.java).queryExecutor as QueryExecutorBase
        val pgStreamProperty = QueryExecutorBase::class.memberProperties.first { it.name == "pgStream" }
        pgStreamProperty.isAccessible = true
        return (pgStreamProperty.get(queryExecutor) as PGStream).socket
    }

    // KVI-230: an abrupt disconnect (no Terminate message) must also close the upstream connection.
    @Test
    fun `an abrupt client disconnect closes the upstream connection`() {
        val baselineUpstream = upstreamConnectionCount()
        val connection = proxyConnection()
        connection.createStatement().executeQuery("SELECT 1;").close()
        awaitUpstreamCount(baselineUpstream + 1)

        // Closing the underlying socket sends no Terminate, the proxy only sees EOF.
        clientSideSocketOf(connection).close()

        awaitUpstreamCount(baselineUpstream)
    }

    // KVI-228: no upstream connection may be opened before the client authenticates, so a bare TCP
    // connect that closes immediately (health check, port scan) leaks nothing and frees its slot.
    @Test
    fun `a bare TCP connection that closes immediately opens no upstream connection and frees the slot`() {
        val baselineUpstream = upstreamConnectionCount()
        val baselineSlots = proxy.proxy.currentConnections

        Socket("localhost", proxy.port).close()

        awaitConnectionCount(baselineSlots)
        awaitUpstreamCount(baselineUpstream)
    }

    // KVI-228: a client that connects but never sends a startup message must be dropped once the
    // handshake deadline elapses, rather than pinning a slot (and a core) forever.
    @Test
    fun `an idle client that never sends a startup message is dropped after the handshake deadline`() {
        val shortProxy = proxyServerFactory(
            postgresContainer,
            executionRequestAdapter,
            eventAdapter,
            handshakeTimeoutMs = 2500,
        )
        try {
            val baseline = shortProxy.proxy.currentConnections
            val socket = Socket("localhost", shortProxy.port)
            try {
                // While the proxy waits for the handshake the slot is occupied.
                awaitConnectionCount(baseline + 1, shortProxy.proxy, timeoutMillis = 2000)
                // After the deadline the proxy gives up and frees the slot.
                awaitConnectionCount(baseline, shortProxy.proxy)
            } finally {
                socket.close()
            }
        } finally {
            shortProxy.proxy.shutdownServer()
            runCatching { shortProxy.connection.close() }
        }
    }

    // KVI-232: a client that leads with a StartupMessage (sslmode=disable) instead of an SSLRequest
    // must not deadlock waiting for a message the client already sent.
    @Test
    fun `a client connecting with sslmode disable completes its handshake and is audited`() {
        val props = Properties()
        props.setProperty("user", "proxyUser")
        props.setProperty("password", "proxyPassword")
        props.setProperty("sslmode", "disable")
        val connection = DriverManager.getConnection(proxy.connectionString, props)
        try {
            connection.createStatement().executeQuery("SELECT 1;").use { rs ->
                assertTrue(rs.next())
                assertEquals(1, rs.getInt(1))
            }
            proxy.eventService.assertQueryIsAudited("SELECT 1;")
        } finally {
            connection.close()
        }
    }

    // KVI-232: a CancelRequest opens a throwaway connection that never authenticates and never sends
    // a StartupMessage. It must be dropped, not left hanging and occupying a slot forever.
    @Test
    fun `a CancelRequest connection is dropped without permanently occupying a slot`() {
        val baseline = proxy.proxy.currentConnections
        val socket = Socket("localhost", proxy.port)
        val cancel = ByteBuffer.allocate(16)
        cancel.putInt(16) // message length
        cancel.putInt(80877102) // CancelRequest code
        cancel.putInt(0) // dummy backend pid
        cancel.putInt(0) // dummy secret key
        socket.getOutputStream().apply {
            write(cancel.array())
            flush()
        }

        // The slot must be freed rather than deadlocked waiting for a startup message.
        awaitConnectionCount(baseline)

        // The proxy closes its side of the connection.
        socket.soTimeout = 5000
        assertEquals(-1, socket.getInputStream().read())
        socket.close()
    }
}
