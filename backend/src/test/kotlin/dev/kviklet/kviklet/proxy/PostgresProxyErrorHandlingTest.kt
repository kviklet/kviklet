package dev.kviklet.kviklet.proxy

import dev.kviklet.kviklet.db.EventAdapter
import dev.kviklet.kviklet.db.ExecutionRequestAdapter
import dev.kviklet.kviklet.proxy.helpers.ProxyInstance
import dev.kviklet.kviklet.proxy.helpers.directConnectionFactory
import dev.kviklet.kviklet.proxy.helpers.proxyServerFactory
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.containers.PostgreSQLContainer
import java.sql.Connection

@SpringBootTest
@ActiveProfiles("test")
class PostgresProxyErrorHandlingTest {
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
    fun `Running CRUD operations against none existing table must not crash the proxy`() {
        // The logic is - the tests executes query against the proxy, and the result is observed via the direct connection.
        // For the select query, the direct connection is no used.
        val insertQuery = "INSERT INTO proxy_test_none_existing(id, random) VALUES (1, 'test');"
        val selectQuery = "SELECT * FROM proxy_test_none_existing;"
        val updateQuery = "UPDATE proxy_test_none_existing SET random='TSET' WHERE id = 1;"
        val deleteQuery = "DELETE FROM proxy_test_none_existing WHERE id = 1;"
        // Create/Insert
        val insertStmt = this.proxy.connection.createStatement()
        assertThrows<Exception> {
            insertStmt.executeUpdate(insertQuery)
        }
        assert(this.proxy.proxy.isRunning)
        assertConnectionIsAlive(this.proxy.connection)
        // Read/Select
        val proxySelectStmt = this.proxy.connection.createStatement()
        assertThrows<Exception> { proxySelectStmt.executeQuery(selectQuery) }
        assert(this.proxy.proxy.isRunning)
        assertConnectionIsAlive(this.proxy.connection)
        // Update
        val updateStmt = this.proxy.connection.createStatement()
        assertThrows<Exception> { updateStmt.executeUpdate(updateQuery) }
        assert(this.proxy.proxy.isRunning)
        assertConnectionIsAlive(this.proxy.connection)
        // Delete
        val deleteStmt = this.proxy.connection.createStatement()
        assertThrows<Exception> { deleteStmt.executeUpdate(deleteQuery) }
        assert(this.proxy.proxy.isRunning)
        assertConnectionIsAlive(this.proxy.connection)

        this.proxy.eventService.assertQueryIsAudited(selectQuery)
        this.proxy.eventService.assertQueryIsAudited(insertQuery)
        this.proxy.eventService.assertQueryIsAudited(updateQuery)
        this.proxy.eventService.assertQueryIsAudited(deleteQuery)
    }
}

fun assertConnectionIsAlive(connection: Connection) {
    val stmt = connection.createStatement()
    val result = stmt.executeQuery("SELECT 1")
    while (result.next()) {
        val isOne = result.getInt("?column?")
        assertTrue(isOne == 1)
    }
}
