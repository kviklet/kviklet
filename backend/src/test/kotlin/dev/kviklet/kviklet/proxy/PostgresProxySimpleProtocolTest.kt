package dev.kviklet.kviklet.proxy

import dev.kviklet.kviklet.db.EventAdapter
import dev.kviklet.kviklet.db.ExecutionRequestAdapter
import dev.kviklet.kviklet.helper.ExecutionRequestFactory
import dev.kviklet.kviklet.proxy.helpers.ProxyInstance
import dev.kviklet.kviklet.proxy.helpers.directConnectionFactory
import dev.kviklet.kviklet.proxy.helpers.proxyServerFactory
import dev.kviklet.kviklet.proxy.mocks.FailingEventServiceMock
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.postgresql.PGConnection
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.containers.PostgreSQLContainer
import java.io.StringReader
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.util.Properties

@SpringBootTest
@ActiveProfiles("test")
class PostgresProxySimpleProtocolTest {
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

    private fun simpleProtocolConnection(connectionString: String): Connection {
        val props = Properties()
        props.setProperty("user", "proxyUser")
        props.setProperty("password", "proxyPassword")
        props.setProperty("preferQueryMode", "simple")
        return DriverManager.getConnection(connectionString, props)
    }

    @Test
    fun `simple protocol queries are executed and audited`() {
        val createTableQuery = "CREATE TABLE IF NOT EXISTS proxy_test_simple (id INTEGER,random VARCHAR(32));"
        val insertQuery = "INSERT INTO proxy_test_simple(id, random) VALUES (1, 'test');"
        val selectQuery = "SELECT * FROM proxy_test_simple;"
        val connection = simpleProtocolConnection(this.proxy.connectionString)
        connection.createStatement().executeUpdate(createTableQuery)
        connection.createStatement().executeUpdate(insertQuery)
        val result = connection.createStatement().executeQuery(selectQuery)
        var rows = 0
        while (result.next()) {
            rows++
            assertEquals(1, result.getInt("id"))
            assertEquals("test", result.getString("random"))
        }
        assertEquals(1, rows)
        connection.close()

        this.proxy.eventService.assertQueryIsAudited(createTableQuery)
        this.proxy.eventService.assertQueryIsAudited(insertQuery)
        this.proxy.eventService.assertQueryIsAudited(selectQuery)
    }

    @Test
    fun `multi statement simple protocol strings are audited in full`() {
        val multiStatement = "CREATE TABLE proxy_test_multi (id INTEGER); INSERT INTO proxy_test_multi(id) VALUES (7);"
        val connection = simpleProtocolConnection(this.proxy.connectionString)
        connection.createStatement().execute(multiStatement)
        connection.close()

        val result = directConnection.createStatement().executeQuery("SELECT id FROM proxy_test_multi;")
        assertTrue(result.next())
        assertEquals(7, result.getInt("id"))

        this.proxy.eventService.assertQueryIsAudited(multiStatement)
    }

    @Test
    fun `COPY FROM STDIN command is audited`() {
        directConnection.createStatement()
            .executeUpdate("CREATE TABLE proxy_test_copy (id INTEGER,random VARCHAR(32));")
        val copyCommand = "COPY proxy_test_copy FROM STDIN WITH (FORMAT csv)"
        val copyManager = this.proxy.connection.unwrap(PGConnection::class.java).copyAPI
        val rows = copyManager.copyIn(copyCommand, StringReader("1,test\n2,foo\n"))
        assertEquals(2L, rows)

        val result = directConnection.createStatement().executeQuery("SELECT count(*) AS cnt FROM proxy_test_copy;")
        assertTrue(result.next())
        assertEquals(2, result.getInt("cnt"))

        this.proxy.eventService.assertQueryIsAudited(copyCommand)
    }

    @Test
    fun `queries are blocked when the audit event cannot be saved`() {
        val failingEventService = FailingEventServiceMock(
            executionRequestAdapter,
            eventAdapter,
            ExecutionRequestFactory().createDatasourceExecutionRequest(),
        )
        val failingProxy = proxyServerFactory(
            postgresContainer,
            executionRequestAdapter,
            eventAdapter,
            eventServiceOverride = failingEventService,
        )
        try {
            val connection = simpleProtocolConnection(failingProxy.connectionString)
            failingEventService.failing = true
            val exception = assertThrows<SQLException> {
                connection.createStatement().executeUpdate("CREATE TABLE proxy_test_audit_fail (id INTEGER);")
            }
            assertTrue(exception.message!!.contains("audit", ignoreCase = true))

            // The query must never have reached the target database
            val result = directConnection.createStatement().executeQuery(
                "SELECT EXISTS (SELECT FROM information_schema.tables WHERE table_name = 'proxy_test_audit_fail');",
            )
            assertTrue(result.next())
            assertEquals("f", result.getString("exists"))
        } finally {
            failingProxy.proxy.shutdownServer()
        }
    }
}
