// This file is not MIT licensed
package dev.kviklet.kviklet.proxy

import dev.kviklet.kviklet.db.EventAdapter
import dev.kviklet.kviklet.db.ExecutionRequestAdapter
import dev.kviklet.kviklet.helper.ExecutionRequestFactory
import dev.kviklet.kviklet.proxy.core.ProxyServer
import dev.kviklet.kviklet.proxy.core.ProxySession
import dev.kviklet.kviklet.proxy.core.getShutdownDate
import dev.kviklet.kviklet.proxy.helpers.waitForProxyStart
import dev.kviklet.kviklet.proxy.mocks.EventServiceMock
import dev.kviklet.kviklet.proxy.postgres.PostgresProtocol
import dev.kviklet.kviklet.service.dto.AuthenticationDetails
import dev.kviklet.kviklet.service.dto.DatasourceType
import dev.kviklet.kviklet.service.dto.ExecutionRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.containers.PostgreSQLContainer
import java.sql.DriverManager
import java.sql.SQLException
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Properties

@SpringBootTest
@ActiveProfiles("test")
class PostgresProxyExpiryTest {
    @Autowired
    lateinit var executionRequestAdapter: ExecutionRequestAdapter

    @Autowired
    lateinit var eventAdapter: EventAdapter
    private lateinit var postgresContainer: PostgreSQLContainer<Nothing>
    private lateinit var server: ProxyServer
    private var port = 0

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
        port = (31001..32000).random()
        server = ProxyServer(port, PostgresProtocol(eventServiceMock(), null))
        server.start()
        waitForProxyStart(server)
    }

    @AfterEach
    fun tearDown() {
        server.shutdownServer()
        postgresContainer.stop()
    }

    private fun eventServiceMock() = EventServiceMock(
        executionRequestAdapter,
        eventAdapter,
        ExecutionRequestFactory().createDatasourceExecutionRequest(),
    )

    private fun register(
        username: String,
        password: String,
        startTime: LocalDateTime?,
        maxTimeMinutes: Long,
        executionRequest: ExecutionRequest = ExecutionRequestFactory().createDatasourceExecutionRequest(),
    ) {
        server.registerSession(
            ProxySession(
                username = username,
                password = password,
                executionRequest = executionRequest,
                userId = "mock",
                targetHost = postgresContainer.host,
                targetPort = postgresContainer.getMappedPort(5432),
                databaseName = "testdb",
                datasourceType = DatasourceType.POSTGRESQL,
                authenticationDetails = AuthenticationDetails.UserPassword("test", "test"),
            ),
            expiresAt = startTime?.let { getShutdownDate(it, maxTimeMinutes).toInstant() },
        )
    }

    private fun connectAndQuery(username: String, password: String) = DriverManager.getConnection(
        "jdbc:postgresql://localhost:$port/testdb",
        Properties().apply {
            setProperty("user", username)
            setProperty("password", password)
        },
    )

    @Test
    fun `ending a request's sessions tears down its live connection and refuses the old credentials`() {
        // No expiry at all: the only way this session ends is the request being rejected or closed.
        val request = ExecutionRequestFactory().createDatasourceExecutionRequest()
        register("infinite", "pw", null, 0, executionRequest = request)
        register("bystander", "pw2", LocalDateTime.now(ZoneOffset.UTC), 10)
        val conn = connectAndQuery("infinite", "pw")
        conn.createStatement().executeQuery("SELECT 1").close()
        val bystander = connectAndQuery("bystander", "pw2")
        bystander.createStatement().executeQuery("SELECT 1").close()
        assertEquals(2, server.currentConnections)

        server.expireSessionsForRequest(request.id!!)

        val deadline = System.currentTimeMillis() + 15_000
        while (server.currentConnections != 1 && System.currentTimeMillis() < deadline) {
            Thread.sleep(100)
        }
        assertEquals(1, server.currentConnections, "The ended session's live connection was not torn down")
        assertThrows(SQLException::class.java) { connectAndQuery("infinite", "pw").close() }
        runCatching { conn.close() }

        // Only the ended request's session is gone: the other one keeps serving.
        bystander.createStatement().executeQuery("SELECT 1").close()
        bystander.close()
    }

    @Test
    fun `an expiring session tears down its live connection while the shared listener keeps serving`() {
        // Expire ~5s from now: a 1-minute window starting (now - 1min + 5s), resolved as UTC by getShutdownDate.
        // The headroom lets the connect + query below finish while the session is still live, even on slow CI.
        register("expiring", "pw", LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1).plusSeconds(5), 1)
        val conn = connectAndQuery("expiring", "pw")
        conn.createStatement().executeQuery("SELECT 1").close()
        assertTrue(server.currentConnections >= 1, "The session's connection should be live before it expires")

        val deadline = System.currentTimeMillis() + 15_000
        while (server.currentConnections != 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(100)
        }
        assertEquals(0, server.currentConnections, "The expired session's live connection was not torn down")
        assertTrue(server.isRunning, "Session expiry must not shut the server down")
        runCatching { conn.close() }

        // The listener still serves a fresh session on the same port after the other one expired.
        register("fresh", "pw2", LocalDateTime.now(ZoneOffset.UTC), 10)
        connectAndQuery("fresh", "pw2").use { fresh ->
            fresh.createStatement().executeQuery("SELECT 1").close()
        }
    }
}
