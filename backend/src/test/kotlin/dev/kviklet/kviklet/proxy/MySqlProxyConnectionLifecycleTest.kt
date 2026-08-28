// This file is not MIT licensed
package dev.kviklet.kviklet.proxy

import dev.kviklet.kviklet.db.EventAdapter
import dev.kviklet.kviklet.db.ExecutionRequestAdapter
import dev.kviklet.kviklet.proxy.core.ProxyServer
import dev.kviklet.kviklet.proxy.helpers.MySqlProxyInstance
import dev.kviklet.kviklet.proxy.helpers.mysqlClientJdbcUrl
import dev.kviklet.kviklet.proxy.helpers.mysqlDirectConnectionFactory
import dev.kviklet.kviklet.proxy.helpers.mysqlProxyServerFactory
import dev.kviklet.kviklet.proxy.mysql.TargetMySqlSocketFactory
import dev.kviklet.kviklet.service.dto.AuthenticationDetails
import dev.kviklet.kviklet.service.dto.DatasourceType
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.containers.JdbcDatabaseContainer
import org.testcontainers.containers.MariaDBContainer
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.lifecycle.Startables
import org.testcontainers.utility.DockerImageName
import java.net.Socket
import java.sql.Connection
import java.sql.DriverManager
import java.util.Properties

@SpringBootTest
@ActiveProfiles("test")
class MySqlProxyConnectionLifecycleTest {
    @Autowired
    lateinit var executionRequestAdapter: ExecutionRequestAdapter

    @Autowired
    lateinit var eventAdapter: EventAdapter
    private val startedProxies = mutableListOf<ProxyServer>()
    private val openedConnections = mutableListOf<Connection>()

    companion object {
        private val mysqlContainer: MySQLContainer<*> = MySQLContainer(DockerImageName.parse("mysql:8.2")).apply {
            withDatabaseName("testdb")
            withUsername("test")
            withPassword("test")
        }
        private val mariadbContainer: MariaDBContainer<*> = MariaDBContainer(DockerImageName.parse("mariadb:11.4"))
            .apply {
                withDatabaseName("testdb")
                withUsername("test")
                withPassword("test")
            }

        @JvmStatic
        @BeforeAll
        fun startContainers() {
            Startables.deepStart(listOf(mysqlContainer, mariadbContainer)).join()
        }

        @JvmStatic
        @AfterAll
        fun stopContainers() {
            mysqlContainer.stop()
            mariadbContainer.stop()
        }

        @JvmStatic
        fun datasourceTypes() = listOf(DatasourceType.MYSQL, DatasourceType.MARIADB)

        private fun container(type: DatasourceType): JdbcDatabaseContainer<*> = when (type) {
            DatasourceType.MYSQL -> mysqlContainer
            DatasourceType.MARIADB -> mariadbContainer
            else -> throw IllegalArgumentException("$type is not served by the MySQL proxy")
        }
    }

    @AfterEach
    fun tearDown() {
        openedConnections.forEach { runCatching { it.close() } }
        openedConnections.clear()
        startedProxies.forEach { it.shutdownServer() }
        startedProxies.clear()
    }

    private fun startProxy(type: DatasourceType, handshakeTimeoutMs: Int = 10_000): MySqlProxyInstance {
        val instance = mysqlProxyServerFactory(
            container(type),
            type,
            executionRequestAdapter,
            eventAdapter,
            handshakeTimeoutMs = handshakeTimeoutMs,
        )
        startedProxies.add(instance.proxy)
        return instance
    }

    private fun directConnection(type: DatasourceType): Connection =
        mysqlDirectConnectionFactory(container(type)).also { openedConnections.add(it) }

    // Counts upstream connections opened by the proxy (all use the target user 'test'), excluding the
    // connection running this very query. Both MySQL and MariaDB expose them in
    // information_schema.processlist, and without the PROCESS privilege the 'test' account sees exactly
    // its own account's threads, which is all the proxy ever opens.
    private fun upstreamConnectionCount(direct: Connection): Int {
        direct.createStatement().executeQuery(
            "SELECT count(*) FROM information_schema.processlist WHERE user = 'test' AND id <> CONNECTION_ID()",
        ).use { rs ->
            rs.next()
            return rs.getInt(1)
        }
    }

    private fun awaitUpstreamCount(direct: Connection, expected: Int, timeoutMillis: Long = 20_000) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        var last = -1
        while (System.currentTimeMillis() < deadline) {
            last = upstreamConnectionCount(direct)
            if (last == expected) return
            Thread.sleep(200)
        }
        assertEquals(expected, last, "Upstream connection count on the target database did not settle")
    }

    private fun awaitConnectionCount(target: ProxyServer, expected: Int, timeoutMillis: Long = 20_000) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline && target.currentConnections != expected) {
            Thread.sleep(100)
        }
        assertEquals(expected, target.currentConnections, "Expected the proxy to be handling $expected connection(s)")
    }

    private fun awaitPendingHandshakeCount(target: ProxyServer, expected: Int, timeoutMillis: Long = 20_000) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline && target.pendingHandshakes != expected) {
            Thread.sleep(100)
        }
        assertEquals(expected, target.pendingHandshakes, "Expected $expected pending handshake(s)")
    }

    @ParameterizedTest
    @MethodSource("datasourceTypes")
    fun `cleanly disconnected sessions close their upstream connections and are pruned`(type: DatasourceType) {
        val proxy = startProxy(type)
        val direct = directConnection(type)
        val baselineUpstream = upstreamConnectionCount(direct)
        val sessionCount = 2

        val connections = (1..sessionCount).map { proxy.connect() }
        connections.forEach { it.createStatement().executeQuery("SELECT 1").close() }
        awaitUpstreamCount(direct, baselineUpstream + sessionCount)
        awaitConnectionCount(proxy.proxy, sessionCount)

        connections.forEach { it.close() }

        awaitUpstreamCount(direct, baselineUpstream)
        awaitConnectionCount(proxy.proxy, 0)
    }

    @ParameterizedTest
    @MethodSource("datasourceTypes")
    fun `an abrupt client disconnect closes the upstream connection`(type: DatasourceType) {
        val proxy = startProxy(type)
        val direct = directConnection(type)
        val baselineUpstream = upstreamConnectionCount(direct)

        // TargetMySqlSocketFactory is a full wire-protocol client, so pointing it at the proxy gives an
        // authenticated raw socket that can be closed without any Quit message, like a crashed client.
        val client = TargetMySqlSocketFactory(
            type,
            AuthenticationDetails.UserPassword(proxy.username, proxy.password),
            "testdb",
            "localhost",
            proxy.port,
        ).createTargetMySqlConnection()
        awaitUpstreamCount(direct, baselineUpstream + 1)

        client.socket.close()

        awaitUpstreamCount(direct, baselineUpstream)
        awaitConnectionCount(proxy.proxy, 0)
    }

    @ParameterizedTest
    @MethodSource("datasourceTypes")
    fun `a bare TCP connection that closes immediately opens no upstream connection and frees the slot`(
        type: DatasourceType,
    ) {
        val proxy = startProxy(type)
        val direct = directConnection(type)
        val baselineUpstream = upstreamConnectionCount(direct)

        Socket("localhost", proxy.port).close()

        awaitConnectionCount(proxy.proxy, 0)
        awaitPendingHandshakeCount(proxy.proxy, 0)
        awaitUpstreamCount(direct, baselineUpstream)
    }

    @ParameterizedTest
    @MethodSource("datasourceTypes")
    fun `an idle client that never completes the handshake is dropped after the deadline`(type: DatasourceType) {
        val proxy = startProxy(type, handshakeTimeoutMs = 2500)
        val direct = directConnection(type)
        val baselineUpstream = upstreamConnectionCount(direct)
        val socket = Socket("localhost", proxy.port)
        try {
            // The idle client never answers the proxy's initial handshake, so it occupies a
            // pending-handshake slot (not a relay slot) until the deadline.
            awaitPendingHandshakeCount(proxy.proxy, 1, timeoutMillis = 2000)
            awaitPendingHandshakeCount(proxy.proxy, 0)
            awaitUpstreamCount(direct, baselineUpstream)
        } finally {
            socket.close()
        }
    }

    @ParameterizedTest
    @MethodSource("datasourceTypes")
    fun `queries through the proxy are audited`(type: DatasourceType) {
        val proxy = startProxy(type)
        proxy.connect().use { conn ->
            conn.createStatement().executeQuery("SELECT 42").use { rs ->
                assertTrue(rs.next())
                assertEquals(42, rs.getInt(1))
            }
        }
        proxy.eventService.assertAuditedQueryContains("SELECT 42")
    }

    @ParameterizedTest
    @MethodSource("datasourceTypes")
    fun `server-side prepared statements are audited on execute`(type: DatasourceType) {
        val proxy = startProxy(type)
        val url = mysqlClientJdbcUrl(type, proxy.port, extraParams = "&useServerPrepStmts=true")
        DriverManager.getConnection(
            url,
            Properties().apply {
                setProperty("user", proxy.username)
                setProperty("password", proxy.password)
            },
        ).use { conn ->
            conn.prepareStatement("SELECT ? + 40").use { stmt ->
                stmt.setInt(1, 2)
                stmt.executeQuery().use { rs ->
                    assertTrue(rs.next())
                    assertEquals(42, rs.getInt(1))
                }
            }
        }
        proxy.eventService.assertAuditedQueryContains("? + 40")
    }
}
