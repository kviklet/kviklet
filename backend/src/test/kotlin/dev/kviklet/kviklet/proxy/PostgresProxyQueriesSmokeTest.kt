package dev.kviklet.kviklet.proxy

import dev.kviklet.kviklet.db.EventAdapter
import dev.kviklet.kviklet.db.ExecutionRequestAdapter
import dev.kviklet.kviklet.proxy.helpers.ProxyInstance
import dev.kviklet.kviklet.proxy.helpers.directConnectionFactory
import dev.kviklet.kviklet.proxy.helpers.proxyServerFactory
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.containers.PostgreSQLContainer
import java.sql.Connection

@SpringBootTest
@ActiveProfiles("test")
@Disabled()
class PostgresProxyQueriesSmokeTest {
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
    fun `Postgres proxy must be able to execute 100 DDL statements without hanging up`() {
        for (i in 0..100) {
            val createTableQuery = "CREATE TABLE IF NOT EXISTS proxy_test_ddl (id INTEGER,random VARCHAR(32));"
            val stmtCreate = this.proxy.connection.createStatement()
            stmtCreate.executeUpdate(createTableQuery)
        }
    }

    @Test
    fun `Postgres proxy must be able to execute 100 statements without hanging up`() {
        val createTableQuery = "CREATE TABLE ddl_many_stms (id INTEGER,random VARCHAR(32));"
        val tableExistsQuery = """
        SELECT EXISTS (
           SELECT FROM information_schema.tables
           WHERE  table_schema = 'public'
           AND    table_name   = 'ddl_many_stms'
        );
        """.trimIndent()
        val stmtCreate = this.proxy.connection.createStatement()
        stmtCreate.executeUpdate(createTableQuery)
        for (i in 0..100) {
            val stmtRead = this.proxy.connection.createStatement()
            val result = stmtRead.executeQuery(tableExistsQuery)
            while (result.next()) {
                val isOne = result.getString("exists")
                assertTrue(isOne == "t")
            }
        }
        this.proxy.eventService.assertQueryIsAudited(createTableQuery)
        this.proxy.eventService.assertQueryIsAudited(tableExistsQuery)
    }

    @Test
    fun `Postgres proxy must be able to execute simple query`() {
        val stmt = this.proxy.connection.createStatement()
        val result = stmt.executeQuery("SELECT 1")
        while (result.next()) {
            val isOne = result.getInt("?column?")
            assertTrue(isOne == 1)
        }

        this.proxy.eventService.assertQueryIsAudited("SELECT 1")
    }

    @Test
    fun `Postgres proxy must be able to execute query resulting in output larger than the buffers`() {
        val query = """
            SELECT array_to_string(
                array(select substr('abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789', trunc(random() * 62)::integer + 1, 1)
            FROM   generate_series(1, 100000)), '');
        """.trimIndent()
        val stmt = this.proxy.connection.createStatement()
        val result = stmt.executeQuery(query)
        while (result.next()) {
            val randomString = result.getString("array_to_string")
            println(randomString.length)
            println(randomString.length == 100000)
            assertTrue(randomString.length == 100000)
        }

        this.proxy.eventService.assertQueryIsAudited(query)
    }

    @Test
    fun `Must support CRUD operations`() {
        // The logic is - the tests executes query against the proxy, and the result is observed via the direct connection.
        // For the select query, the direct connection is no used.
        val createTableQuery = "CREATE TABLE IF NOT EXISTS proxy_test_crud (id INTEGER,random VARCHAR(32));"
        val insertQuery = "INSERT INTO proxy_test_crud(id, random) VALUES (1, 'test');"
        val selectQuery = "SELECT * FROM proxy_test_crud;"
        val updateQuery = "UPDATE proxy_test_crud SET random='TSET' WHERE id = 1;"
        val deleteQuery = "DELETE FROM proxy_test_crud WHERE id = 1;"
        val stmt = this.proxy.connection.createStatement()
        stmt.executeUpdate(createTableQuery)
        // Create/Insert
        val insertStmt = this.proxy.connection.createStatement()
        insertStmt.executeUpdate(insertQuery)
        val selectStmt = this.directConnection.createStatement()
        val resultPostInsert = selectStmt.executeQuery(selectQuery)
        while (resultPostInsert.next()) {
            val id = resultPostInsert.getInt("id")
            val random = resultPostInsert.getString("random")
            assertTrue(id == 1)
            assertTrue(random == "test")
        }
        // Read/Select
        val proxySelectStmt = this.proxy.connection.createStatement()
        val resultFromSelect = proxySelectStmt.executeQuery(selectQuery)
        while (resultFromSelect.next()) {
            val id = resultFromSelect.getInt("id")
            val random = resultFromSelect.getString("random")
            assertTrue(id == 1)
            assertTrue(random == "test")
        }
        // Update
        val updateStmt = this.proxy.connection.createStatement()
        updateStmt.executeUpdate(updateQuery)
        val resultPostUpdate = selectStmt.executeQuery(selectQuery)
        while (resultPostUpdate.next()) {
            val id = resultPostUpdate.getInt("id")
            val random = resultPostUpdate.getString("random")
            assertTrue(id == 1)
            assertTrue(random == "TSET")
        }

        // Delete
        val deleteStmt = this.proxy.connection.createStatement()
        deleteStmt.executeUpdate(deleteQuery)
        val resultPostDelete = selectStmt.executeQuery(selectQuery)
        var size = 0
        while (resultPostDelete.next()) {
            size++
        }
        assertTrue(size == 0)

        this.proxy.eventService.assertQueryIsAudited(selectQuery)
        this.proxy.eventService.assertQueryIsAudited(insertQuery)
        this.proxy.eventService.assertQueryIsAudited(updateQuery)
        this.proxy.eventService.assertQueryIsAudited(deleteQuery)
    }

    @Test
    fun `Postgres proxy must support creating and executing stored procedures`() {
        val createTableQuery = "CREATE TABLE IF NOT EXISTS proxy_test_procedures (id INTEGER,random VARCHAR(32));"
        val storedProcedure = """
        CREATE OR REPLACE PROCEDURE addTestRow(IN random VARCHAR(32))
        LANGUAGE plpgsql
        AS $$
        BEGIN

        INSERT INTO proxy_test_procedures(id, random) VALUES ((select random() * 1000 + 1), random);

        END
        $$;
    """
        val callProcedure = "CALL addTestRow('test-stored-procedure');"
        val selectQuery = "SELECT * FROM proxy_test_procedures WHERE random = 'test-stored-procedure';"

        var stmt = this.proxy.connection.createStatement()

        stmt.executeUpdate(createTableQuery)

        stmt = this.proxy.connection.createStatement()
        stmt.executeUpdate(storedProcedure)

        stmt = this.proxy.connection.createStatement()
        stmt.executeUpdate(callProcedure)

        val proxySelectStmt = this.proxy.connection.createStatement()
        val resultFromSelect = proxySelectStmt.executeQuery(selectQuery)

        while (resultFromSelect.next()) {
            val id = resultFromSelect.getInt("id")
            val random = resultFromSelect.getString("random")
            assertTrue(id < 1000)
            assertTrue(random == "test-stored-procedure")
        }

        this.proxy.eventService.assertQueryIsAudited(createTableQuery)
        this.proxy.eventService.assertQueryIsAudited(storedProcedure)
        this.proxy.eventService.assertQueryIsAudited(callProcedure)
    }

    @Test
    fun `Postgres proxy must support creating and executing prepared statements`() {
        val createTableQuery = "CREATE TABLE IF NOT EXISTS proxy_test_statements (id INTEGER,random VARCHAR(32));\n"
        val preparedStatement = """
        PREPARE proxy_test_insert (int, text) AS
        INSERT INTO proxy_test_statements VALUES($1, $2);
        """.trimIndent()
        val execute = "EXECUTE proxy_test_insert(1, 'test-prepared-statement');"
        val selectQuery = "SELECT * FROM proxy_test_statements WHERE random = 'test-prepared-statement';"

        val stmt = this.proxy.connection.createStatement()
        stmt.executeUpdate(createTableQuery)
        stmt.executeUpdate(preparedStatement)
        stmt.executeUpdate(execute)

        val proxySelectStmt = this.proxy.connection.createStatement()
        val resultFromSelect = proxySelectStmt.executeQuery(selectQuery)
        while (resultFromSelect.next()) {
            val id = resultFromSelect.getInt("id")
            val random = resultFromSelect.getString("random")
            assertTrue(id < 1000)
            assertTrue(random == "test-prepared-statement")
        }
        this.proxy.eventService.assertQueryIsAudited(createTableQuery)
        this.proxy.eventService.assertQueryIsAudited(preparedStatement)
        this.proxy.eventService.assertQueryIsAudited(execute)
    }

    @Test
    fun `Postgres proxy must be able to execute DDL`() {
        val createTableQuery = "CREATE TABLE IF NOT EXISTS proxy_test_ddl (id INTEGER,random VARCHAR(32));"
        val tableExistsQuery = """
        SELECT EXISTS (
           SELECT FROM information_schema.tables
           WHERE  table_schema = 'public'
           AND    table_name   = 'proxy_test_ddl'
        );
        """.trimIndent()
        val stmt = this.proxy.connection.createStatement()
        stmt.executeUpdate(createTableQuery)
        val result = stmt.executeQuery(tableExistsQuery)
        while (result.next()) {
            val isOne = result.getString("exists")
            assertTrue(isOne == "t")
        }

        this.proxy.eventService.assertQueryIsAudited(createTableQuery)
        this.proxy.eventService.assertQueryIsAudited(tableExistsQuery)
    }
}
