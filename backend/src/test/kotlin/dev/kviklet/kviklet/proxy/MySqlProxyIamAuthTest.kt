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
import dev.kviklet.kviklet.proxy.mocks.RecordingRdsIamTokenProvider
import dev.kviklet.kviklet.service.dto.AuthenticationDetails
import dev.kviklet.kviklet.service.dto.DatasourceType
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.containers.JdbcDatabaseContainer
import org.testcontainers.lifecycle.Startables
import java.sql.Connection
import java.sql.SQLException

// AWS IAM sessions through the MySQL/MariaDB proxy, with the AWS signer swapped for a fake that hands out
// the container's password (an RDS token is just a short-lived password on the wire). What a testcontainer
// cannot reproduce is RDS switching the client to mysql_clear_password; the proxy's answer to that, never
// dialing an IAM upstream without TLS, is what the fail-closed test pins down.
@SpringBootTest
@ActiveProfiles("test")
class MySqlProxyIamAuthTest {
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

    private fun startIamProxy(
        container: JdbcDatabaseContainer<*>,
        type: DatasourceType,
        tokenProvider: RecordingRdsIamTokenProvider,
        additionalOptions: String = "",
        roleArn: String? = null,
    ): MySqlProxyInstance {
        val instance = mysqlProxyServerFactory(
            container,
            type,
            executionRequestAdapter,
            eventAdapter,
            additionalOptions = additionalOptions,
            connAuth = AuthenticationDetails.AwsIam("test", roleArn),
            rdsIamTokenProvider = tokenProvider,
        )
        startedProxies.add(instance.proxy)
        return instance
    }

    private fun connect(proxy: MySqlProxyInstance): Connection = proxy.connect().also { openedConnections.add(it) }

    private fun upstreamSslCipher(connection: Connection): String = connection.createStatement().use { stmt ->
        stmt.executeQuery("SHOW SESSION STATUS LIKE 'Ssl_cipher'").use { rs ->
            assertTrue(rs.next())
            rs.getString("Value") ?: ""
        }
    }

    // The MySQL proxy completes the client handshake before dialing the upstream, so an upstream failure
    // surfaces on the first statement rather than on connect.
    private fun assertRefused(proxy: MySqlProxyInstance) {
        assertThrows(SQLException::class.java) {
            connect(proxy).createStatement().use { stmt -> stmt.executeQuery("SELECT 1").close() }
        }
    }

    @ParameterizedTest
    @EnumSource(DatasourceType::class, names = ["MYSQL", "MARIADB"])
    fun `an IAM session authenticates upstream with a minted token over TLS and works end-to-end`(
        type: DatasourceType,
    ) {
        val container = tlsContainer(type)
        val tokenProvider = RecordingRdsIamTokenProvider("test")
        val proxy = startIamProxy(container, type, tokenProvider, roleArn = "arn:aws:iam::123456789012:role/db")
        val connection = connect(proxy)

        assertTrue(upstreamSslCipher(connection).isNotEmpty(), "expected the upstream leg to be TLS-encrypted")
        connection.createStatement().use { stmt ->
            stmt.executeQuery("SELECT 42 AS answer").use { rs ->
                assertTrue(rs.next())
                assertEquals(42, rs.getInt("answer"))
            }
        }
        proxy.eventService.assertQueryIsAudited("SELECT 42 AS answer")
        assertEquals(
            listOf(
                RecordingRdsIamTokenProvider.TokenRequest(
                    container.host,
                    container.getMappedPort(3306),
                    "test",
                    "arn:aws:iam::123456789012:role/db",
                ),
            ),
            tokenProvider.requests,
        )
    }

    @Test
    fun `an IAM session raises a configured sslMode=disable to trust`() {
        val proxy = startIamProxy(
            tlsMariadb,
            DatasourceType.MARIADB,
            RecordingRdsIamTokenProvider("test"),
            additionalOptions = "?sslMode=disable",
        )
        assertTrue(upstreamSslCipher(connect(proxy)).isNotEmpty())
    }

    @Test
    fun `an IAM session against a server without TLS is refused instead of sending the token in the clear`() {
        val proxy = startIamProxy(noTlsMariadb, DatasourceType.MARIADB, RecordingRdsIamTokenProvider("test"))
        assertRefused(proxy)
    }

    @Test
    fun `an IAM session keeps a configured verification mode`() {
        val verified = startIamProxy(
            tlsMariadb,
            DatasourceType.MARIADB,
            RecordingRdsIamTokenProvider("test"),
            additionalOptions = "?sslMode=verify-full&serverSslCert=${upstreamTlsResourcePath("ca.crt")}",
        )
        assertTrue(upstreamSslCipher(connect(verified)).isNotEmpty())

        val wrongCa = startIamProxy(
            tlsMariadb,
            DatasourceType.MARIADB,
            RecordingRdsIamTokenProvider("test"),
            additionalOptions = "?sslMode=verify-ca&serverSslCert=${upstreamTlsResourcePath("wrong-ca.crt")}",
        )
        assertRefused(wrongCa)
    }

    @Test
    fun `every upstream connection of a session gets a freshly minted token`() {
        val tokenProvider = RecordingRdsIamTokenProvider("test")
        val proxy = startIamProxy(tlsMariadb, DatasourceType.MARIADB, tokenProvider)
        connect(proxy).createStatement().use { stmt -> stmt.executeQuery("SELECT 1").close() }
        connect(proxy).createStatement().use { stmt -> stmt.executeQuery("SELECT 1").close() }
        assertEquals(2, tokenProvider.requests.size)
    }

    @Test
    fun `a rejected token refuses the client instead of hanging`() {
        val proxy = startIamProxy(tlsMariadb, DatasourceType.MARIADB, RecordingRdsIamTokenProvider("wrong"))
        assertRefused(proxy)
    }
}
