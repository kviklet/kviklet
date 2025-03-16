package dev.kviklet.kviklet.proxy

import dev.kviklet.kviklet.db.EventAdapter
import dev.kviklet.kviklet.db.ExecutionRequestAdapter
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.containers.PostgreSQLContainer
import dev.kviklet.kviklet.proxy.helpers.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.sql.Connection

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.assertThrows

@SpringBootTest
@ActiveProfiles("test")
class PostgresProxyErrorHandlingTest {
    @Autowired
    lateinit var executionRequestAdapter: ExecutionRequestAdapter
    @Autowired
    lateinit var eventAdapter: EventAdapter
    private lateinit var directConnection : Connection
    private lateinit var proxy : ProxyInstance
    private lateinit var postgresContainer : PostgreSQLContainer<Nothing>
    @BeforeEach
    fun setup() {
        postgresContainer = PostgreSQLContainer<Nothing>("postgres:13").apply {
            withDatabaseName("testdb")
            withUsername("test")
            withPassword("test")
        }
        postgresContainer.start()
        while(!postgresContainer.isRunning) { Thread.sleep(1000) }
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
        val INSERT_QUERY = "INSERT INTO proxy_test_none_existing(id, random) VALUES (1, 'test');"
        val SELECT_QUERY = "SELECT * FROM proxy_test_none_existing;"
        val UPDATE_QUERY = "UPDATE proxy_test_none_existing SET random='TSET' WHERE id = 1;"
        val DELETE_QUERY = "DELETE FROM proxy_test_none_existing WHERE id = 1;"
        // Create/Insert
        val insertStmt = this.proxy.connection.createStatement()
        assertThrows<Exception> {
            insertStmt.executeUpdate(INSERT_QUERY)
        }
        assert(this.proxy.proxy.isRunning)
        assertConnectionIsAlive(this.proxy.connection)
        // Read/Select
        val proxySelectStmt = this.proxy.connection.createStatement()
        assertThrows<Exception> { proxySelectStmt.executeQuery(SELECT_QUERY) }
        assert(this.proxy.proxy.isRunning)
        assertConnectionIsAlive(this.proxy.connection)
        // Update
        val updateStmt = this.proxy.connection.createStatement()
        assertThrows<Exception> { updateStmt.executeUpdate(UPDATE_QUERY) }
        assert(this.proxy.proxy.isRunning)
        assertConnectionIsAlive(this.proxy.connection)
        // Delete
        val deleteStmt = this.proxy.connection.createStatement()
        assertThrows<Exception> { deleteStmt.executeUpdate(DELETE_QUERY) }
        assert(this.proxy.proxy.isRunning)
        assertConnectionIsAlive(this.proxy.connection)

        this.proxy.eventService.assertQueryIsAudited(SELECT_QUERY)
        this.proxy.eventService.assertQueryIsAudited(INSERT_QUERY)
        this.proxy.eventService.assertQueryIsAudited(UPDATE_QUERY)
        this.proxy.eventService.assertQueryIsAudited(DELETE_QUERY)
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