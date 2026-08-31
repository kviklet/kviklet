// This file is not MIT licensed
package dev.kviklet.kviklet.proxy

import dev.kviklet.kviklet.db.EventAdapter
import dev.kviklet.kviklet.db.ExecutionRequestAdapter
import dev.kviklet.kviklet.proxy.core.ProxyServer
import dev.kviklet.kviklet.proxy.helpers.MySqlProxyInstance
import dev.kviklet.kviklet.proxy.helpers.mysqlProxyServerFactory
import dev.kviklet.kviklet.proxy.helpers.noTlsMariaDbContainer
import dev.kviklet.kviklet.proxy.helpers.tlsMariaDbContainer
import dev.kviklet.kviklet.proxy.helpers.tlsMySqlContainer
import dev.kviklet.kviklet.proxy.helpers.upstreamTlsResourcePath
import dev.kviklet.kviklet.service.dto.DatasourceType
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.containers.JdbcDatabaseContainer
import org.testcontainers.lifecycle.Startables
import java.sql.Connection
import java.sql.SQLException

// Upstream-TLS behavior of the MySQL/MariaDB proxy: the sslMode/serverSslCert a connection carries in its
// additionalOptions must apply to the proxy's upstream leg (today it is pinned to plaintext), fail closed
// when the server cannot satisfy it, and support certificate verification. Covers both flavors, including
// the Connector/J option spellings (REQUIRED/VERIFY_IDENTITY) a MySQL connection would carry.
@SpringBootTest
@ActiveProfiles("test")
class MySqlProxyUpstreamTLSTest {
    @Autowired
    lateinit var executionRequestAdapter: ExecutionRequestAdapter

    @Autowired
    lateinit var eventAdapter: EventAdapter

    private val startedProxies = mutableListOf<ProxyServer>()
    private val openedConnections = mutableListOf<Connection>()

    companion object {
        private val tlsMysql = tlsMySqlContainer()
        private val tlsMariadb = tlsMariaDbContainer()
        private val noTlsMariadb = noTlsMariaDbContainer()

        @JvmStatic
        @BeforeAll
        fun startContainers() {
            Startables.deepStart(listOf(tlsMysql, tlsMariadb, noTlsMariadb)).join()
        }

        @JvmStatic
        @AfterAll
        fun stopContainers() {
            tlsMysql.stop()
            tlsMariadb.stop()
            noTlsMariadb.stop()
        }

        // Each flavor with its own option spelling: a MySQL connection's additionalOptions carry Connector/J
        // values (REQUIRED), a MariaDB connection's carry MariaDB driver values (trust).
        @JvmStatic
        fun tlsFlavors() = listOf(
            arrayOf(DatasourceType.MYSQL, "?sslMode=REQUIRED"),
            arrayOf(DatasourceType.MARIADB, "?sslMode=trust"),
        )

        @JvmStatic
        fun verifyFlavors() = listOf(
            arrayOf(DatasourceType.MYSQL, "?sslMode=VERIFY_IDENTITY&serverSslCert="),
            arrayOf(DatasourceType.MARIADB, "?sslMode=verify-full&serverSslCert="),
        )

        private fun tlsContainer(type: DatasourceType): JdbcDatabaseContainer<*> = when (type) {
            DatasourceType.MYSQL -> tlsMysql
            DatasourceType.MARIADB -> tlsMariadb
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

    private fun startProxy(
        container: JdbcDatabaseContainer<*>,
        type: DatasourceType,
        additionalOptions: String,
    ): MySqlProxyInstance {
        val instance = mysqlProxyServerFactory(
            container,
            type,
            executionRequestAdapter,
            eventAdapter,
            additionalOptions = additionalOptions,
        )
        startedProxies.add(instance.proxy)
        return instance
    }

    private fun connect(proxy: MySqlProxyInstance): Connection = proxy.connect().also { openedConnections.add(it) }

    // The cipher of the UPSTREAM leg: the SHOW runs on the server-side session the proxy dialed, so a
    // non-empty value means the proxy->database socket is TLS regardless of the client leg.
    private fun upstreamSslCipher(connection: Connection): String = connection.createStatement().use { stmt ->
        stmt.executeQuery("SHOW SESSION STATUS LIKE 'Ssl_cipher'").use { rs ->
            assertTrue(rs.next())
            rs.getString("Value") ?: ""
        }
    }

    @ParameterizedTest
    @MethodSource("tlsFlavors")
    fun `requesting TLS encrypts the upstream leg and the session works end-to-end`(
        type: DatasourceType,
        additionalOptions: String,
    ) {
        val proxy = startProxy(tlsContainer(type), type, additionalOptions)
        val connection = connect(proxy)
        assertTrue(
            upstreamSslCipher(connection).isNotEmpty(),
            "expected the upstream leg to be TLS-encrypted",
        )
        connection.createStatement().use { stmt ->
            stmt.executeQuery("SELECT 42 AS answer").use { rs ->
                assertTrue(rs.next())
                assertEquals(42, rs.getInt("answer"))
            }
        }
        proxy.eventService.assertQueryIsAudited("SELECT 42 AS answer")
    }

    @ParameterizedTest
    @MethodSource("verifyFlavors")
    fun `full certificate verification with the correct CA connects`(
        type: DatasourceType,
        additionalOptionsPrefix: String,
    ) {
        val proxy = startProxy(
            tlsContainer(type),
            type,
            additionalOptionsPrefix + upstreamTlsResourcePath("ca.crt"),
        )
        val connection = connect(proxy)
        assertTrue(upstreamSslCipher(connection).isNotEmpty())
        connection.createStatement().use { stmt ->
            stmt.executeQuery("SELECT 1").use { rs -> assertTrue(rs.next()) }
        }
    }

    @Test
    fun `certificate verification against the wrong CA refuses the session`() {
        val proxy = startProxy(
            tlsMariadb,
            DatasourceType.MARIADB,
            "?sslMode=verify-ca&serverSslCert=${upstreamTlsResourcePath("wrong-ca.crt")}",
        )
        assertThrows(SQLException::class.java) {
            connect(proxy).createStatement().use { stmt -> stmt.executeQuery("SELECT 1").close() }
        }
    }

    @Test
    fun `requesting TLS against a server without TLS refuses the session instead of falling back`() {
        val proxy = startProxy(noTlsMariadb, DatasourceType.MARIADB, "?sslMode=trust")
        assertThrows(SQLException::class.java) {
            connect(proxy).createStatement().use { stmt -> stmt.executeQuery("SELECT 1").close() }
        }
    }

    @Test
    fun `an unknown sslMode value refuses the session instead of being ignored`() {
        val proxy = startProxy(tlsMariadb, DatasourceType.MARIADB, "?sslMode=definitely-not-a-mode")
        assertThrows(SQLException::class.java) {
            connect(proxy).createStatement().use { stmt -> stmt.executeQuery("SELECT 1").close() }
        }
    }

    @Test
    fun `a connection without TLS options keeps working over plaintext`() {
        val proxy = startProxy(noTlsMariadb, DatasourceType.MARIADB, "")
        val connection = connect(proxy)
        assertEquals("", upstreamSslCipher(connection))
        connection.createStatement().use { stmt ->
            stmt.executeQuery("SELECT 1").use { rs -> assertTrue(rs.next()) }
        }
        proxy.eventService.assertQueryIsAudited("SELECT 1")
    }

    @Test
    fun `server-side prepared statements work and are audited over an upstream TLS session`() {
        val proxy = startProxy(tlsMariadb, DatasourceType.MARIADB, "?sslMode=trust")
        val connection = java.sql.DriverManager.getConnection(
            proxy.connectionString + "&useServerPrepStmts=true",
            java.util.Properties().apply {
                setProperty("user", proxy.username)
                setProperty("password", proxy.password)
            },
        ).also { openedConnections.add(it) }
        connection.prepareStatement("SELECT ? + ?").use { stmt ->
            stmt.setInt(1, 20)
            stmt.setInt(2, 22)
            stmt.executeQuery().use { rs ->
                assertTrue(rs.next())
                assertEquals(42, rs.getInt(1))
            }
        }
        proxy.eventService.assertAuditedQueryContains("SELECT 20 + 22")
    }
}
