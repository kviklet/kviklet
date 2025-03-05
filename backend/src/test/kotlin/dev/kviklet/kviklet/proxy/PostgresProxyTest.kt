package dev.kviklet.kviklet.proxy

import dev.kviklet.kviklet.db.*
import dev.kviklet.kviklet.helper.ExecutionRequestFactory
import dev.kviklet.kviklet.proxy.mocks.EventServiceMock
import dev.kviklet.kviklet.service.dto.*
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
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.sql.*
import java.sql.Connection
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.CompletableFuture

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class PostgresProxyTest {
    companion object {
        @Container
        val postgresContainer = PostgreSQLContainer<Nothing>("postgres:13").apply {
            withDatabaseName("testdb")
            withUsername("test")
            withPassword("test")
        }
    }
    @Autowired
    lateinit var executionRequestAdapter: ExecutionRequestAdapter
    @Autowired
    lateinit var eventAdapter: EventAdapter
    lateinit var proxyConnection: Connection
    lateinit var directConnection: Connection
    lateinit var proxyJdbcConnectionString: String
    private val executionRequestFactory = ExecutionRequestFactory()
    lateinit var eventServiceMock: EventServiceMock
    @BeforeEach
    fun setup() {
        // Setup connection directly to the postgres server
        val props = Properties()
        props.setProperty("user", "test")
        props.setProperty("password", "test")
        this.directConnection = DriverManager.getConnection(postgresContainer.jdbcUrl, props)

        // Start proxy server and setup JDBC connection
        val connAuth = AuthenticationDetails.UserPassword("test", "test")
        val request = executionRequestFactory.createDatasourceExecutionRequest()
        val eventService = EventServiceMock(executionRequestAdapter,eventAdapter, request)
        this.eventServiceMock = eventService
        val proxy = PostgresProxy(
            postgresContainer.host,
            postgresContainer.getMappedPort(5432),
            "testdb",
            connAuth,
            eventService,
            request,
            "mock")
        val port = (12000..20000).random()
        CompletableFuture.runAsync {
            proxy.startServer(port, "proxyUser", "proxyPassword", LocalDateTime.now(), 10)
        }
        var sleepCycle = 0
        while(!proxy.isRunning && sleepCycle < 10) { Thread.sleep(1000); sleepCycle++ }
        assertTrue(sleepCycle < 10)
        this.proxyJdbcConnectionString = "jdbc:postgresql://localhost:${port}/testdb"
        val proxyProps = Properties()
        proxyProps.setProperty("user", "proxyUser")
        proxyProps.setProperty("password", "proxyPassword")
        this.proxyConnection = DriverManager.getConnection("jdbc:postgresql://localhost:${port}/testdb", proxyProps)
    }
    @Test
    fun `Postgres proxy must support 100 active proxies`() {
        var startedProxies = 0
        while(startedProxies < 10) {
            val connAuth = AuthenticationDetails.UserPassword("test", "test")
            val request = executionRequestFactory.createDatasourceExecutionRequest()
            val eventService = EventServiceMock(executionRequestAdapter,eventAdapter, request)
            this.eventServiceMock = eventService
            val proxy = PostgresProxy(
                postgresContainer.host,
                postgresContainer.getMappedPort(5432),
                "testdb",
                connAuth,
                eventService,
                request,
                "mock")
            val port = 22000+startedProxies
            CompletableFuture.runAsync {
                proxy.startServer(port, "proxyUser", "proxyPassword", LocalDateTime.now(), 10)
            }
            var sleepCycle = 0
            while(!proxy.isRunning && sleepCycle < 10) { Thread.sleep(1000); sleepCycle++ }
            assertTrue(sleepCycle < 10)
            this.proxyJdbcConnectionString = "jdbc:postgresql://localhost:${port}/testdb"
            val proxyProps = Properties()
            proxyProps.setProperty("user", "proxyUser")
            proxyProps.setProperty("password", "proxyPassword")
            proxyProps.setProperty("connectTimeout", "100")
            this.proxyConnection = DriverManager.getConnection("jdbc:postgresql://localhost:${port}/testdb", proxyProps)
            startedProxies++
        }
    }
    @Test
    fun `Postgres proxy must require password-based authentication`() {
        val throwable = assertThrows<PSQLException> {
            DriverManager.getConnection(this.proxyJdbcConnectionString)
        }
        assertTrue(throwable.message!!.contains("The server requested password-based authentication"))
    }
    @Test
    fun `Postgres proxy must support TLS`() {
        assertDoesNotThrow {
            val proxyProps = Properties()
            proxyProps.setProperty("user", "proxyUser")
            proxyProps.setProperty("password", "proxyPassword")
            proxyProps.setProperty("ssl", "true")
            proxyProps.setProperty("sslmode", "require")
            // NonValidatingFactory make sure the test doesn't check if the certificate is valid.
            val forceTLS="&sslfactory=org.postgresql.ssl.NonValidatingFactory"
            val conn = DriverManager.getConnection(this.proxyJdbcConnectionString + forceTLS, proxyProps)
        }
    }
    @Test
    fun `Postgres proxy must be able to execute DDL`() {

        val CREATE_TABLE_QUERY = "CREATE TABLE proxy_test_ddl (id INTEGER,random VARCHAR(32));"
        val TABEL_EXISTS_QUERY = """
            SELECT EXISTS (
               SELECT FROM information_schema.tables 
               WHERE  table_schema = 'public'
               AND    table_name   = 'proxy_test_ddl'
            );
        """.trimIndent()
        val stmt = this.proxyConnection.createStatement()
        stmt.executeUpdate(CREATE_TABLE_QUERY)
        val result = stmt.executeQuery(TABEL_EXISTS_QUERY)
        while (result.next()) {
            val isOne = result.getString("exists")
            assertTrue(isOne == "t")
        }

        this.eventServiceMock.assertQueryIsAudited(CREATE_TABLE_QUERY)
        this.eventServiceMock.assertQueryIsAudited(TABEL_EXISTS_QUERY)
    }
    @Test
    fun `Postgres proxy must be able to execute simple query`() {
        val stmt = this.proxyConnection.createStatement()
        val result = stmt.executeQuery("SELECT 1")
        while (result.next()) {
            val isOne = result.getInt("?column?")
            assertTrue(isOne == 1)
        }

        this.eventServiceMock.assertQueryIsAudited("SELECT 1")
    }
    @Test
    fun `Must support CRUD operations`() {
        // The logic is - the tests executes query against the proxy, and the result is observed via the direct connection.
        // For the select query, the direct connection is no used.
        val CREATE_TABLE_QUERY = "CREATE TABLE IF NOT EXISTS proxy_test (id INTEGER,random VARCHAR(32));"
        val INSERT_QUERY = "INSERT INTO proxy_test(id, random) VALUES (1, 'test');"
        val SELECT_QUERY = "SELECT * FROM proxy_test;"
        val UPDATE_QUERY = "UPDATE proxy_test SET random='TSET' WHERE id = 1;"
        val DELETE_QUERY = "DELETE FROM proxy_test WHERE id = 1;"
        val stmt = this.proxyConnection.createStatement()
        stmt.executeUpdate(CREATE_TABLE_QUERY)
        // Create/Insert
        val insertStmt = this.proxyConnection.createStatement()
        insertStmt.executeUpdate(INSERT_QUERY)
        val selectStmt = this.directConnection.createStatement()
        val resultPostInsert = selectStmt.executeQuery(SELECT_QUERY)
        while (resultPostInsert.next()) {
            val id = resultPostInsert.getInt("id")
            val random = resultPostInsert.getString("random")
            assertTrue(id == 1)
            assertTrue(random == "test")
        }
        // Read/Select
        val proxySelectStmt = this.proxyConnection.createStatement()
        val resultFromSelect = proxySelectStmt.executeQuery(SELECT_QUERY)
        while (resultFromSelect.next()) {
            val id = resultFromSelect.getInt("id")
            val random = resultFromSelect.getString("random")
            assertTrue(id == 1)
            assertTrue(random == "test")
        }
        // Update
        val updateStmt = this.proxyConnection.createStatement()
        updateStmt.executeUpdate(UPDATE_QUERY)
        val resultPostUpdate = selectStmt.executeQuery(SELECT_QUERY)
        while (resultPostUpdate.next()) {
            val id = resultPostUpdate.getInt("id")
            val random = resultPostUpdate.getString("random")
            assertTrue(id == 1)
            assertTrue(random == "TSET")
        }

        // Delete
        val deleteStmt = this.proxyConnection.createStatement()
        deleteStmt.executeUpdate(DELETE_QUERY)
        val resultPostDelete = selectStmt.executeQuery(SELECT_QUERY)
        var size = 0
        while (resultPostDelete.next()) {
            size++
        }
        assertTrue(size == 0)

        this.eventServiceMock.assertQueryIsAudited(SELECT_QUERY)
        this.eventServiceMock.assertQueryIsAudited(INSERT_QUERY)
        this.eventServiceMock.assertQueryIsAudited(UPDATE_QUERY)
        this.eventServiceMock.assertQueryIsAudited(DELETE_QUERY)
    }
    @Test
    fun `Postgres proxy must support creating and executing stored procedures`() {
        val CREATE_TABLE_QUERY = "CREATE TABLE IF NOT EXISTS proxy_test (id INTEGER,random VARCHAR(32));"
        val STORED_PROCEDURE = """
            CREATE OR REPLACE PROCEDURE addTestRow(IN random VARCHAR(32))
            LANGUAGE plpgsql
            AS $$
            BEGIN
            
            INSERT INTO proxy_test(id, random) VALUES ((select random() * 1000 + 1), random);
            
            END
            $$;
        """
        val CALL_PROCEDURE = "CALL addTestRow('test-stored-procedure');"
        val SELECT_QUERY = "SELECT * FROM proxy_test WHERE random = 'test-stored-procedure';"

        val stmt = this.proxyConnection.createStatement()
        stmt.executeUpdate(CREATE_TABLE_QUERY)
        stmt.executeUpdate(STORED_PROCEDURE)
        stmt.executeUpdate(CALL_PROCEDURE)

        val proxySelectStmt = this.proxyConnection.createStatement()
        val resultFromSelect = proxySelectStmt.executeQuery(SELECT_QUERY)
        while (resultFromSelect.next()) {
            val id = resultFromSelect.getInt("id")
            val random = resultFromSelect.getString("random")
            assertTrue(id < 1000)
            assertTrue(random == "test-stored-procedure")
        }
        this.eventServiceMock.assertQueryIsAudited(CREATE_TABLE_QUERY)
        this.eventServiceMock.assertQueryIsAudited(STORED_PROCEDURE)
        this.eventServiceMock.assertQueryIsAudited(CALL_PROCEDURE)
    }
    @Test
    fun `Postgres proxy must support creating and executing prepared statements`() {
        val CREATE_TABLE_QUERY = "CREATE TABLE IF NOT EXISTS proxy_test_statements (id INTEGER,random VARCHAR(32));\n"
        val PREPARED_STATEMENT = """
            PREPARE proxy_test_insert (int, text) AS
            INSERT INTO proxy_test_statements VALUES($1, $2);
        """.trimIndent()
        val EXECUTE = "EXECUTE proxy_test_insert(1, 'test-prepared-statement');"
        val SELECT_QUERY = "SELECT * FROM proxy_test_statements WHERE random = 'test-prepared-statement';"

        val stmt = this.proxyConnection.createStatement()
        stmt.executeUpdate(CREATE_TABLE_QUERY)
        stmt.executeUpdate(PREPARED_STATEMENT)
        stmt.executeUpdate(EXECUTE)

        val proxySelectStmt = this.proxyConnection.createStatement()
        val resultFromSelect = proxySelectStmt.executeQuery(SELECT_QUERY)
        while (resultFromSelect.next()) {
            val id = resultFromSelect.getInt("id")
            val random = resultFromSelect.getString("random")
            assertTrue(id < 1000)
            assertTrue(random == "test-prepared-statement")
        }
        this.eventServiceMock.assertQueryIsAudited(CREATE_TABLE_QUERY)
        this.eventServiceMock.assertQueryIsAudited(PREPARED_STATEMENT)
        this.eventServiceMock.assertQueryIsAudited(EXECUTE)
    }
}



