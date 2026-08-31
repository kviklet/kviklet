// This file is not MIT licensed
package dev.kviklet.kviklet.proxy

import dev.kviklet.kviklet.db.EventAdapter
import dev.kviklet.kviklet.db.ExecutionRequestAdapter
import dev.kviklet.kviklet.proxy.core.ProxyServer
import dev.kviklet.kviklet.proxy.helpers.ProxyServerHandle
import dev.kviklet.kviklet.proxy.helpers.startPostgresProxy
import dev.kviklet.kviklet.proxy.helpers.tlsPostgresContainer
import dev.kviklet.kviklet.proxy.helpers.upstreamTlsResourcePath
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.lifecycle.Startables
import java.sql.Connection
import java.sql.SQLException

// Upstream-TLS behavior of the Postgres proxy: the TLS configuration in a connection's additionalOptions
// (the same sslmode/sslrootcert that JDBC executions honor) must apply to the proxy's upstream leg, fail
// closed when the server cannot satisfy it, and support real certificate verification.
@SpringBootTest
@ActiveProfiles("test")
class PostgresProxyUpstreamTLSTest {
    @Autowired
    lateinit var executionRequestAdapter: ExecutionRequestAdapter

    @Autowired
    lateinit var eventAdapter: EventAdapter

    private val startedProxies = mutableListOf<ProxyServer>()
    private val openedConnections = mutableListOf<Connection>()

    companion object {
        private val tlsPostgres = tlsPostgresContainer()
        private val plainPostgres = PostgreSQLContainer<Nothing>("postgres:13").apply {
            withDatabaseName("testdb")
            withUsername("test")
            withPassword("test")
        }

        @JvmStatic
        @BeforeAll
        fun startContainers() {
            Startables.deepStart(listOf(tlsPostgres, plainPostgres)).join()
        }

        @JvmStatic
        @AfterAll
        fun stopContainers() {
            tlsPostgres.stop()
            plainPostgres.stop()
        }
    }

    @AfterEach
    fun tearDown() {
        openedConnections.forEach { runCatching { it.close() } }
        openedConnections.clear()
        startedProxies.forEach { it.shutdownServer() }
        startedProxies.clear()
    }

    private fun startProxy(container: PostgreSQLContainer<Nothing>, additionalOptions: String): ProxyServerHandle {
        val handle = startPostgresProxy(
            container,
            executionRequestAdapter,
            eventAdapter,
            additionalOptions = additionalOptions,
        )
        startedProxies.add(handle.proxy)
        return handle
    }

    private fun connect(handle: ProxyServerHandle): Connection = handle.connect().also { openedConnections.add(it) }

    // Whether the UPSTREAM leg (proxy -> database) of this relayed session is TLS: the query runs on the
    // backend serving the relayed connection, so pg_stat_ssl reports the socket the proxy dialed.
    private fun upstreamUsesTls(connection: Connection): Boolean = connection.createStatement().use { stmt ->
        stmt.executeQuery("SELECT ssl FROM pg_stat_ssl WHERE pid = pg_backend_pid()").use { rs ->
            assertTrue(rs.next())
            rs.getBoolean(1)
        }
    }

    @Test
    fun `sslmode=require encrypts the upstream leg and the session works end-to-end`() {
        val handle = startProxy(tlsPostgres, "?sslmode=require")
        val connection = connect(handle)
        assertTrue(upstreamUsesTls(connection))
        connection.createStatement().use { stmt ->
            stmt.executeQuery("SELECT 42 AS answer").use { rs ->
                assertTrue(rs.next())
                assertEquals(42, rs.getInt("answer"))
            }
        }
        handle.eventService.assertQueryIsAudited("SELECT 42 AS answer")
    }

    @Test
    fun `sslmode=verify-full with the correct root cert verifies and connects`() {
        val handle = startProxy(
            tlsPostgres,
            "?sslmode=verify-full&sslrootcert=${upstreamTlsResourcePath("ca.crt")}",
        )
        val connection = connect(handle)
        assertTrue(upstreamUsesTls(connection))
        connection.createStatement().use { stmt ->
            stmt.executeQuery("SELECT 1").use { rs -> assertTrue(rs.next()) }
        }
    }

    @Test
    fun `certificate verification against the wrong root cert refuses the session`() {
        val handle = startProxy(
            tlsPostgres,
            "?sslmode=verify-ca&sslrootcert=${upstreamTlsResourcePath("wrong-ca.crt")}",
        )
        assertThrows(SQLException::class.java) { connect(handle) }
    }

    @Test
    fun `sslmode=require against a server without TLS refuses the session instead of falling back`() {
        val handle = startProxy(plainPostgres, "?sslmode=require")
        assertThrows(SQLException::class.java) { connect(handle) }
    }

    @Test
    fun `sslmode=disable keeps the upstream leg plaintext`() {
        val handle = startProxy(tlsPostgres, "?sslmode=disable")
        val connection = connect(handle)
        assertFalse(upstreamUsesTls(connection))
    }

    @Test
    fun `a connection without TLS options keeps working`() {
        val handle = startProxy(tlsPostgres, "")
        val connection = connect(handle)
        connection.createStatement().use { stmt ->
            stmt.executeQuery("SELECT 1").use { rs -> assertTrue(rs.next()) }
        }
    }

    @Test
    fun `non-TLS options in additionalOptions are ignored by the proxy`() {
        val handle = startProxy(tlsPostgres, "?connectTimeout=10&ApplicationName=kviklet-test")
        val connection = connect(handle)
        connection.createStatement().use { stmt ->
            stmt.executeQuery("SELECT 1").use { rs -> assertTrue(rs.next()) }
        }
    }
}
