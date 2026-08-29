// This file is not MIT licensed
package dev.kviklet.kviklet.proxy

import dev.kviklet.kviklet.db.EventAdapter
import dev.kviklet.kviklet.db.ExecutionRequestAdapter
import dev.kviklet.kviklet.helper.ExecutionRequestFactory
import dev.kviklet.kviklet.proxy.core.ProxyServer
import dev.kviklet.kviklet.proxy.core.ProxySession
import dev.kviklet.kviklet.proxy.helpers.ProxyInstance
import dev.kviklet.kviklet.proxy.helpers.directConnectionFactory
import dev.kviklet.kviklet.proxy.helpers.proxyServerFactory
import dev.kviklet.kviklet.proxy.helpers.waitForProxyStart
import dev.kviklet.kviklet.proxy.mocks.EventServiceMock
import dev.kviklet.kviklet.proxy.postgres.PostgresProtocol
import dev.kviklet.kviklet.service.dto.AuthenticationDetails
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.postgresql.util.PSQLException
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.containers.PostgreSQLContainer
import java.sql.Connection
import java.sql.DriverManager
import java.time.Duration
import java.time.Instant
import java.util.*

@SpringBootTest
@ActiveProfiles("test")
class PostgresProxyAuthTest {
    @Autowired
    lateinit var executionRequestAdapter: ExecutionRequestAdapter

    @Autowired
    lateinit var eventAdapter: EventAdapter
    private lateinit var directConnection: Connection
    private lateinit var proxy: ProxyInstance
    private lateinit var postgresContainer: PostgreSQLContainer<Nothing>
    private val startedProxies = mutableListOf<ProxyServer>()

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
        this.startedProxies.forEach { it.shutdownServer() }
        this.proxy.proxy.shutdownServer()
        this.proxy.connection.close()
        this.postgresContainer.stop()
    }

    @Test
    fun `Postgres proxy must require password-based authentication`() {
        val throwable = assertThrows<PSQLException> {
            DriverManager.getConnection(this.proxy.connectionString)
        }
        assertTrue(
            throwable.message!!.contains(
                "The server requested SCRAM-based authentication, but no password was provided.",
            ),
        )
    }

    @Test
    fun `Postgres proxy must use externally injected username and password`() {
        val port = (22000..22010).random()
        val executionRequestFactory = ExecutionRequestFactory()
        val randomUsername = getRandomString(8)
        val randomPassword = getRandomString(16)
        val connAuth = AuthenticationDetails.UserPassword("test", "test")
        val proxy = ProxyServer(port, PostgresProtocol(this.proxy.eventService, null))
        proxy.start()
        waitForProxyStart(proxy)
        startedProxies.add(proxy)
        proxy.registerSession(
            ProxySession(
                username = randomUsername,
                password = randomPassword,
                executionRequest = executionRequestFactory.createDatasourceExecutionRequest(),
                userId = "mock",
                targetHost = postgresContainer.host,
                targetPort = postgresContainer.getMappedPort(5432),
                databaseName = "testdb",
                authenticationDetails = connAuth,
            ),
            expiresAt = Instant.now().plus(Duration.ofMinutes(10)),
        )
        assertDoesNotThrow {
            val proxyProps = Properties()
            proxyProps.setProperty("user", randomUsername)
            proxyProps.setProperty("password", randomPassword)
            DriverManager.getConnection("jdbc:postgresql://localhost:$port/testdb", proxyProps)
        }
    }

    @Test
    fun `Postgres proxy must reject bad username and password`() {
        val port = (22010..22020).random()
        val executionRequestFactory = ExecutionRequestFactory()
        val randomUsername = getRandomString(8)
        val randomPassword = getRandomString(16)
        val connAuth = AuthenticationDetails.UserPassword("test", "test")
        val request = executionRequestFactory.createDatasourceExecutionRequest()
        val eventService = EventServiceMock(executionRequestAdapter, eventAdapter, request)

        val proxy = ProxyServer(port, PostgresProtocol(eventService, null))
        proxy.start()
        waitForProxyStart(proxy)
        startedProxies.add(proxy)
        // The registered session's credentials differ from the ones the client connects with below, so the
        // connection must be rejected.
        proxy.registerSession(
            ProxySession(
                username = "notuser",
                password = "notpass",
                executionRequest = request,
                userId = "mock",
                targetHost = postgresContainer.host,
                targetPort = postgresContainer.getMappedPort(5432),
                databaseName = "testdb",
                authenticationDetails = connAuth,
            ),
            expiresAt = Instant.now().plus(Duration.ofMinutes(10)),
        )
        assertThrows<Exception> {
            val proxyProps = Properties()
            proxyProps.setProperty("user", randomUsername)
            proxyProps.setProperty("password", randomPassword)
            DriverManager.getConnection("jdbc:postgresql://localhost:$port/testdb", proxyProps)
        }
    }

    @Test
    fun `the correct username with a wrong password fails with 28P01, not a connection reset`() {
        // The username is correct, so this exercises the wrong-password path alone (proof verification
        // fails while the user is valid) -- the path the always-run-the-proof timing parity is about.
        val username = getRandomString(8)
        val port = startProxy(proxyUsername = username, proxyPassword = getRandomString(16))

        val throwable = assertThrows<PSQLException> {
            val proxyProps = Properties()
            proxyProps.setProperty("user", username)
            proxyProps.setProperty("password", "definitely-the-wrong-password")
            DriverManager.getConnection("jdbc:postgresql://localhost:$port/testdb", proxyProps)
        }
        assertEquals("28P01", throwable.sqlState)
    }

    @Test
    fun `a wrong username with the correct password is rejected even when the username is a substring of the packet`() {
        // The proxy user "test" is a substring of the database name "testdb"; the old substring check would
        // see "test" in the startup packet and wrongly accept the connection despite the actual user being
        // "wronguser". With the correct password supplied, only the username gate can reject this.
        val password = getRandomString(16)
        val port = startProxy(proxyUsername = "test", proxyPassword = password)

        val throwable = assertThrows<PSQLException> {
            val proxyProps = Properties()
            proxyProps.setProperty("user", "wronguser")
            proxyProps.setProperty("password", password)
            DriverManager.getConnection("jdbc:postgresql://localhost:$port/testdb", proxyProps)
        }
        assertEquals("28P01", throwable.sqlState)
    }

    @Test
    fun `one listener routes each client to its own session by username`() {
        // The core of the single-stable-port model: many sessions share one listener and are told apart by
        // the username the client sends in its startup message, each with its own temp password.
        val port = (30001..31000).random()
        val server = ProxyServer(port, PostgresProtocol(this.proxy.eventService, null))
        server.start()
        waitForProxyStart(server)
        startedProxies.add(server)

        val connAuth = AuthenticationDetails.UserPassword("test", "test")
        val requestFactory = ExecutionRequestFactory()
        val userA = getRandomString(8)
        val passA = getRandomString(16)
        val userB = getRandomString(8)
        val passB = getRandomString(16)
        listOf(userA to passA, userB to passB).forEach { (user, pass) ->
            server.registerSession(
                ProxySession(
                    username = user,
                    password = pass,
                    executionRequest = requestFactory.createDatasourceExecutionRequest(),
                    userId = "mock",
                    targetHost = postgresContainer.host,
                    targetPort = postgresContainer.getMappedPort(5432),
                    databaseName = "testdb",
                    authenticationDetails = connAuth,
                ),
                expiresAt = Instant.now().plus(Duration.ofMinutes(10)),
            )
        }

        // Each registered username authenticates with its own password and reaches the target through the
        // one shared listener.
        listOf(userA to passA, userB to passB).forEach { (user, pass) ->
            assertDoesNotThrow {
                val props = Properties()
                props.setProperty("user", user)
                props.setProperty("password", pass)
                DriverManager.getConnection("jdbc:postgresql://localhost:$port/testdb", props).use { conn ->
                    conn.createStatement().executeQuery("SELECT 1").close()
                }
            }
        }

        // A username with no session on the same listener is rejected.
        assertThrows<PSQLException> {
            val props = Properties()
            props.setProperty("user", getRandomString(8))
            props.setProperty("password", passA)
            DriverManager.getConnection("jdbc:postgresql://localhost:$port/testdb", props)
        }

        // Crossed credentials (A's name, B's password) fail: routing picks A's session, B's password fails
        // A's proof.
        assertThrows<PSQLException> {
            val props = Properties()
            props.setProperty("user", userA)
            props.setProperty("password", passB)
            DriverManager.getConnection("jdbc:postgresql://localhost:$port/testdb", props)
        }
    }

    private fun startProxy(proxyUsername: String, proxyPassword: String): Int {
        val port = (22100..30000).random()
        val executionRequestFactory = ExecutionRequestFactory()
        val request = executionRequestFactory.createDatasourceExecutionRequest()
        val eventService = EventServiceMock(executionRequestAdapter, eventAdapter, request)
        val proxy = ProxyServer(port, PostgresProtocol(eventService, null))
        startedProxies.add(proxy)
        proxy.start()
        waitForProxyStart(proxy)
        proxy.registerSession(
            ProxySession(
                username = proxyUsername,
                password = proxyPassword,
                executionRequest = request,
                userId = "mock",
                targetHost = postgresContainer.host,
                targetPort = postgresContainer.getMappedPort(5432),
                databaseName = "testdb",
                authenticationDetails = AuthenticationDetails.UserPassword("test", "test"),
            ),
            expiresAt = Instant.now().plus(Duration.ofMinutes(10)),
        )
        return port
    }
}

fun getRandomString(length: Int): String {
    val allowedChars = ('A'..'Z') + ('a'..'z') + ('0'..'9')
    return (1..length)
        .map { allowedChars.random() }
        .joinToString("")
}
