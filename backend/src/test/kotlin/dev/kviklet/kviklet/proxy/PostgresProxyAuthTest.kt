// This file is not MIT licensed
package dev.kviklet.kviklet.proxy

import dev.kviklet.kviklet.db.EventAdapter
import dev.kviklet.kviklet.db.ExecutionRequestAdapter
import dev.kviklet.kviklet.helper.ExecutionRequestFactory
import dev.kviklet.kviklet.proxy.helpers.ProxyInstance
import dev.kviklet.kviklet.proxy.helpers.directConnectionFactory
import dev.kviklet.kviklet.proxy.helpers.proxyServerFactory
import dev.kviklet.kviklet.proxy.helpers.waitForProxyStart
import dev.kviklet.kviklet.proxy.mocks.EventServiceMock
import dev.kviklet.kviklet.proxy.postgres.PostgresProxy
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
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.CompletableFuture

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
        var proxy = PostgresProxy(
            postgresContainer.host,
            postgresContainer.getMappedPort(5432),
            "testdb",
            connAuth,
            this.proxy.eventService,
            executionRequestFactory.createDatasourceExecutionRequest(),
            "mock",
        )

        CompletableFuture.runAsync {
            proxy.startServer(port, randomUsername, randomPassword, LocalDateTime.now(), 10)
        }
        waitForProxyStart(proxy)
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

        var proxy = PostgresProxy(
            postgresContainer.host,
            postgresContainer.getMappedPort(5432),
            "testdb",
            connAuth,
            eventService,
            executionRequestFactory.createDatasourceExecutionRequest(),
            "mock",
        )

        CompletableFuture.runAsync {
            // the proxyUsername and proxyPassword below must be less than the randomly generated ones to gurantee failure
            proxy.startServer(port, "notuser", "notpass", LocalDateTime.now(), 10)
        }
        var sleepCycle = 0
        while (!proxy.isRunning && sleepCycle < 10) {
            Thread.sleep(1000)
            sleepCycle++
        }
        assert(sleepCycle < 10)
        assertThrows<Exception> {
            val proxyProps = Properties()
            proxyProps.setProperty("user", randomUsername)
            proxyProps.setProperty("password", randomPassword)
            DriverManager.getConnection("jdbc:postgresql://localhost:$port/testdb", proxyProps)
        }
    }

    @Test
    fun `a wrong password fails with 28P01 password authentication failed, not a connection reset`() {
        val port = startProxy(proxyUsername = getRandomString(8), proxyPassword = getRandomString(16))

        val throwable = assertThrows<PSQLException> {
            val proxyProps = Properties()
            proxyProps.setProperty("user", "someuser")
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

    private fun startProxy(proxyUsername: String, proxyPassword: String): Int {
        val port = (22020..22060).random()
        val executionRequestFactory = ExecutionRequestFactory()
        val request = executionRequestFactory.createDatasourceExecutionRequest()
        val eventService = EventServiceMock(executionRequestAdapter, eventAdapter, request)
        val proxy = PostgresProxy(
            postgresContainer.host,
            postgresContainer.getMappedPort(5432),
            "testdb",
            AuthenticationDetails.UserPassword("test", "test"),
            eventService,
            executionRequestFactory.createDatasourceExecutionRequest(),
            "mock",
        )
        CompletableFuture.runAsync {
            proxy.startServer(port, proxyUsername, proxyPassword, LocalDateTime.now(), 10)
        }
        waitForProxyStart(proxy)
        return port
    }
}

fun getRandomString(length: Int): String {
    val allowedChars = ('A'..'Z') + ('a'..'z') + ('0'..'9')
    return (1..length)
        .map { allowedChars.random() }
        .joinToString("")
}
