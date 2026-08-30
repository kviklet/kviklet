// This file is not MIT licensed
package dev.kviklet.kviklet.proxy

import dev.kviklet.kviklet.db.EventAdapter
import dev.kviklet.kviklet.db.ExecutionRequestAdapter
import dev.kviklet.kviklet.helper.ExecutionRequestFactory
import dev.kviklet.kviklet.proxy.core.ProxyServer
import dev.kviklet.kviklet.proxy.core.ProxySession
import dev.kviklet.kviklet.proxy.helpers.mysqlClientJdbcUrl
import dev.kviklet.kviklet.proxy.helpers.waitForProxyStart
import dev.kviklet.kviklet.proxy.mocks.EventServiceMock
import dev.kviklet.kviklet.proxy.mysql.MySqlProtocol
import dev.kviklet.kviklet.service.dto.AuthenticationDetails
import dev.kviklet.kviklet.service.dto.DatasourceType
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.containers.JdbcDatabaseContainer
import org.testcontainers.containers.MariaDBContainer
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.lifecycle.Startables
import org.testcontainers.utility.DockerImageName
import java.sql.Connection
import java.sql.DriverManager
import java.time.Duration
import java.time.Instant
import java.util.Properties

@SpringBootTest
@ActiveProfiles("test")
class MySqlProxyExpiryTest {
    @Autowired
    lateinit var executionRequestAdapter: ExecutionRequestAdapter

    @Autowired
    lateinit var eventAdapter: EventAdapter
    private lateinit var server: ProxyServer
    private var port = 0

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
    }

    @BeforeEach
    fun setup() {
        port = (31001..32000).random()
        server = ProxyServer(port, MySqlProtocol(eventServiceMock(), null))
        server.start()
        waitForProxyStart(server)
    }

    @AfterEach
    fun tearDown() {
        server.shutdownServer()
    }

    private fun eventServiceMock() = EventServiceMock(
        executionRequestAdapter,
        eventAdapter,
        ExecutionRequestFactory().createDatasourceExecutionRequest(),
    )

    private fun register(
        username: String,
        password: String,
        container: JdbcDatabaseContainer<*>,
        datasourceType: DatasourceType,
        expiresAt: Instant = Instant.now().plus(Duration.ofMinutes(10)),
    ) {
        server.registerSession(
            ProxySession(
                username = username,
                password = password,
                executionRequest = ExecutionRequestFactory().createDatasourceExecutionRequest(),
                userId = "mock",
                targetHost = container.host,
                targetPort = container.getMappedPort(3306),
                databaseName = "testdb",
                datasourceType = datasourceType,
                authenticationDetails = AuthenticationDetails.UserPassword("test", "test"),
            ),
            expiresAt = expiresAt,
        )
    }

    private fun connect(datasourceType: DatasourceType, username: String, password: String): Connection =
        DriverManager.getConnection(
            mysqlClientJdbcUrl(datasourceType, port),
            Properties().apply {
                setProperty("user", username)
                setProperty("password", password)
            },
        )

    @Test
    fun `an expiring session tears down its live connection while the shared listener keeps serving`() {
        // Expire ~5s from now. The headroom lets the connect + query below finish while the session is
        // still live, even on slow CI.
        register(
            "expiring",
            "pw",
            mysqlContainer,
            DatasourceType.MYSQL,
            expiresAt = Instant.now().plusSeconds(5),
        )
        val conn = connect(DatasourceType.MYSQL, "expiring", "pw")
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
        register("fresh", "pw2", mysqlContainer, DatasourceType.MYSQL)
        connect(DatasourceType.MYSQL, "fresh", "pw2").use { fresh ->
            fresh.createStatement().executeQuery("SELECT 1").close()
        }
    }

    @Test
    fun `one listener routes two sessions to different upstreams by username`() {
        // The datasourceType carried on the session is what picks the upstream flavor: one 3306-style
        // listener serves a MySQL session and a MariaDB session side by side, told apart only by the temp
        // username in the client handshake.
        register("mysqluser", "mysqlpw", mysqlContainer, DatasourceType.MYSQL)
        register("mariauser", "mariapw", mariadbContainer, DatasourceType.MARIADB)

        connect(DatasourceType.MYSQL, "mysqluser", "mysqlpw").use { conn ->
            conn.createStatement().executeQuery("SELECT VERSION()").use { rs ->
                assertTrue(rs.next())
                assertFalse(
                    rs.getString(1).contains("MariaDB", ignoreCase = true),
                    "The MySQL session must land on the MySQL upstream, got: ${rs.getString(1)}",
                )
            }
        }

        connect(DatasourceType.MARIADB, "mariauser", "mariapw").use { conn ->
            conn.createStatement().executeQuery("SELECT VERSION()").use { rs ->
                assertTrue(rs.next())
                assertTrue(
                    rs.getString(1).contains("MariaDB", ignoreCase = true),
                    "The MariaDB session must land on the MariaDB upstream, got: ${rs.getString(1)}",
                )
            }
        }
    }
}
