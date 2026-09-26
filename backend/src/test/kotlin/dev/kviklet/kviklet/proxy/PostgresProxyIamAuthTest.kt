// This file is not MIT licensed
package dev.kviklet.kviklet.proxy

import dev.kviklet.kviklet.db.EventAdapter
import dev.kviklet.kviklet.db.ExecutionRequestAdapter
import dev.kviklet.kviklet.proxy.core.ProxyServer
import dev.kviklet.kviklet.proxy.helpers.ProxyServerHandle
import dev.kviklet.kviklet.proxy.helpers.startPostgresProxy
import dev.kviklet.kviklet.proxy.helpers.tlsPostgresContainer
import dev.kviklet.kviklet.proxy.helpers.upstreamTlsResourcePath
import dev.kviklet.kviklet.proxy.mocks.RecordingRdsIamTokenProvider
import dev.kviklet.kviklet.service.dto.AuthenticationDetails
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
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

// AWS IAM sessions through the Postgres proxy. An RDS IAM token is just a short-lived password on the wire,
// so the whole path (session auth type, per-connection token minting, forced upstream TLS, relay) runs
// against a testcontainer with the AWS signer swapped for a fake that hands out the container's password.
// Only the signing itself (AwsRdsIamTokenProviderTest) and RDS accepting a real token (the aws-integration
// tagged tests) live elsewhere.
@SpringBootTest
@ActiveProfiles("test")
class PostgresProxyIamAuthTest {
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

    private fun startIamProxy(
        container: PostgreSQLContainer<Nothing>,
        tokenProvider: RecordingRdsIamTokenProvider,
        additionalOptions: String = "",
        roleArn: String? = null,
    ): ProxyServerHandle {
        val handle = startPostgresProxy(
            container,
            executionRequestAdapter,
            eventAdapter,
            additionalOptions = additionalOptions,
            connAuth = AuthenticationDetails.AwsIam("test", roleArn),
            rdsIamTokenProvider = tokenProvider,
        )
        startedProxies.add(handle.proxy)
        return handle
    }

    private fun connect(handle: ProxyServerHandle): Connection = handle.connect().also { openedConnections.add(it) }

    private fun upstreamUsesTls(connection: Connection): Boolean = connection.createStatement().use { stmt ->
        stmt.executeQuery("SELECT ssl FROM pg_stat_ssl WHERE pid = pg_backend_pid()").use { rs ->
            assertTrue(rs.next())
            rs.getBoolean(1)
        }
    }

    @Test
    fun `an IAM session authenticates upstream with a minted token and works end-to-end`() {
        val tokenProvider = RecordingRdsIamTokenProvider("test")
        val handle = startIamProxy(tlsPostgres, tokenProvider, roleArn = "arn:aws:iam::123456789012:role/db")
        val connection = connect(handle)

        connection.createStatement().use { stmt ->
            stmt.executeQuery("SELECT 42 AS answer").use { rs ->
                assertTrue(rs.next())
                assertEquals(42, rs.getInt("answer"))
            }
        }
        handle.eventService.assertQueryIsAudited("SELECT 42 AS answer")
        assertEquals(
            listOf(
                RecordingRdsIamTokenProvider.TokenRequest(
                    tlsPostgres.host,
                    tlsPostgres.getMappedPort(5432),
                    "test",
                    "arn:aws:iam::123456789012:role/db",
                ),
            ),
            tokenProvider.requests,
        )
    }

    @Test
    fun `an IAM session forces TLS on the upstream leg even without any sslmode configured`() {
        val handle = startIamProxy(tlsPostgres, RecordingRdsIamTokenProvider("test"))
        assertTrue(upstreamUsesTls(connect(handle)))
    }

    @Test
    fun `an IAM session against a server without TLS is refused instead of sending the token in the clear`() {
        val handle = startIamProxy(plainPostgres, RecordingRdsIamTokenProvider("test"))
        assertThrows(SQLException::class.java) { connect(handle) }
    }

    @Test
    fun `an IAM session keeps a configured verification mode`() {
        val handle = startIamProxy(
            tlsPostgres,
            RecordingRdsIamTokenProvider("test"),
            additionalOptions = "?sslmode=verify-full&sslrootcert=${upstreamTlsResourcePath("ca.crt")}",
        )
        assertTrue(upstreamUsesTls(connect(handle)))

        val wrongCa = startIamProxy(
            tlsPostgres,
            RecordingRdsIamTokenProvider("test"),
            additionalOptions = "?sslmode=verify-ca&sslrootcert=${upstreamTlsResourcePath("wrong-ca.crt")}",
        )
        assertThrows(SQLException::class.java) { connect(wrongCa) }
    }

    @Test
    fun `every upstream connection of a session gets a freshly minted token`() {
        val tokenProvider = RecordingRdsIamTokenProvider("test")
        val handle = startIamProxy(tlsPostgres, tokenProvider)
        connect(handle)
        connect(handle)
        assertEquals(2, tokenProvider.requests.size)
    }

    @Test
    fun `a rejected token refuses the client instead of hanging`() {
        val handle = startIamProxy(tlsPostgres, RecordingRdsIamTokenProvider("not-the-password"))
        assertThrows(SQLException::class.java) { connect(handle) }
    }
}
