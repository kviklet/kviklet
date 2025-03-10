package dev.kviklet.kviklet.proxy

import dev.kviklet.kviklet.db.EventAdapter
import dev.kviklet.kviklet.db.ExecutionRequestAdapter
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.containers.PostgreSQLContainer
import dev.kviklet.kviklet.proxy.helpers.*
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.sql.Connection

@SpringBootTest
@ActiveProfiles("test")
class PostgresProxyQueriesSmokeTest {
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
    fun `Postgres proxy must be able to execute 100 DDL statements without hanging up`() {
        for(i in 0..100) {
            val CREATE_TABLE_QUERY = "CREATE TABLE IF NOT EXISTS proxy_test_ddl (id INTEGER,random VARCHAR(32));"
            val stmtCreate = this.proxy.connection.createStatement()
            stmtCreate.executeUpdate(CREATE_TABLE_QUERY)
        }
    }
    @Test
    fun `Postgres proxy must be able to execute 100 statements without hanging up`() {
        val CREATE_TABLE_QUERY = "CREATE TABLE ddl_many_stms (id INTEGER,random VARCHAR(32));"
        val TABEL_EXISTS_QUERY = """
        SELECT EXISTS (
           SELECT FROM information_schema.tables
           WHERE  table_schema = 'public'
           AND    table_name   = 'ddl_many_stms'
        );
    """.trimIndent()
        val stmtCreate = this.proxy.connection.createStatement()
        stmtCreate.executeUpdate(CREATE_TABLE_QUERY)
        for(i in 0..100) {
            val stmtRead = this.proxy.connection.createStatement()
            val result = stmtRead.executeQuery(TABEL_EXISTS_QUERY)
            while (result.next()) {
                val isOne = result.getString("exists")
                assertTrue(isOne == "t")
            }
        }
        this.proxy.eventService.assertQueryIsAudited(CREATE_TABLE_QUERY)
        this.proxy.eventService.assertQueryIsAudited(TABEL_EXISTS_QUERY)
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

    fun `Must support CRUD operations`() {
        // The logic is - the tests executes query against the proxy, and the result is observed via the direct connection.
        // For the select query, the direct connection is no used.
        val CREATE_TABLE_QUERY = "CREATE TABLE IF NOT EXISTS proxy_test_crud (id INTEGER,random VARCHAR(32));"
        val INSERT_QUERY = "INSERT INTO proxy_test_crud(id, random) VALUES (1, 'test');"
        val SELECT_QUERY = "SELECT * FROM proxy_test_crud;"
        val UPDATE_QUERY = "UPDATE proxy_test_crud SET random='TSET' WHERE id = 1;"
        val DELETE_QUERY = "DELETE FROM proxy_test_crud WHERE id = 1;"
        val stmt = this.proxy.connection.createStatement()
        stmt.executeUpdate(CREATE_TABLE_QUERY)
        // Create/Insert
        val insertStmt = this.proxy.connection.createStatement()
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
        val proxySelectStmt = this.proxy.connection.createStatement()
        val resultFromSelect = proxySelectStmt.executeQuery(SELECT_QUERY)
        while (resultFromSelect.next()) {
            val id = resultFromSelect.getInt("id")
            val random = resultFromSelect.getString("random")
            assertTrue(id == 1)
            assertTrue(random == "test")
        }
        // Update
        val updateStmt = this.proxy.connection.createStatement()
        updateStmt.executeUpdate(UPDATE_QUERY)
        val resultPostUpdate = selectStmt.executeQuery(SELECT_QUERY)
        while (resultPostUpdate.next()) {
            val id = resultPostUpdate.getInt("id")
            val random = resultPostUpdate.getString("random")
            assertTrue(id == 1)
            assertTrue(random == "TSET")
        }

        // Delete
        val deleteStmt = this.proxy.connection.createStatement()
        deleteStmt.executeUpdate(DELETE_QUERY)
        val resultPostDelete = selectStmt.executeQuery(SELECT_QUERY)
        var size = 0
        while (resultPostDelete.next()) {
            size++
        }
        assertTrue(size == 0)

        this.proxy.eventService.assertQueryIsAudited(SELECT_QUERY)
        this.proxy.eventService.assertQueryIsAudited(INSERT_QUERY)
        this.proxy.eventService.assertQueryIsAudited(UPDATE_QUERY)
        this.proxy.eventService.assertQueryIsAudited(DELETE_QUERY)
    }

    @Test

    fun `Postgres proxy must support creating and executing stored procedures`() {

        val CREATE_TABLE_QUERY = "CREATE TABLE IF NOT EXISTS proxy_test_procedures (id INTEGER,random VARCHAR(32));"
        val STORED_PROCEDURE = """
        CREATE OR REPLACE PROCEDURE addTestRow(IN random VARCHAR(32))
        LANGUAGE plpgsql
        AS $$
        BEGIN

        INSERT INTO proxy_test_procedures(id, random) VALUES ((select random() * 1000 + 1), random);

        END
        $$;
    """
        val CALL_PROCEDURE = "CALL addTestRow('test-stored-procedure');"
        val SELECT_QUERY = "SELECT * FROM proxy_test_procedures WHERE random = 'test-stored-procedure';"

        var stmt = this.proxy.connection.createStatement()

        stmt.executeUpdate(CREATE_TABLE_QUERY)

        stmt = this.proxy.connection.createStatement()
        stmt.executeUpdate(STORED_PROCEDURE)

        stmt = this.proxy.connection.createStatement()
        stmt.executeUpdate(CALL_PROCEDURE)

        val proxySelectStmt = this.proxy.connection.createStatement()
        val resultFromSelect = proxySelectStmt.executeQuery(SELECT_QUERY)

        while (resultFromSelect.next()) {
            val id = resultFromSelect.getInt("id")
            val random = resultFromSelect.getString("random")
            assertTrue(id < 1000)
            assertTrue(random == "test-stored-procedure")
        }

        this.proxy.eventService.assertQueryIsAudited(CREATE_TABLE_QUERY)
        this.proxy.eventService.assertQueryIsAudited(STORED_PROCEDURE)
        this.proxy.eventService.assertQueryIsAudited(CALL_PROCEDURE)
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

        val stmt = this.proxy.connection.createStatement()
        stmt.executeUpdate(CREATE_TABLE_QUERY)
        stmt.executeUpdate(PREPARED_STATEMENT)
        stmt.executeUpdate(EXECUTE)

        val proxySelectStmt = this.proxy.connection.createStatement()
        val resultFromSelect = proxySelectStmt.executeQuery(SELECT_QUERY)
        while (resultFromSelect.next()) {
            val id = resultFromSelect.getInt("id")
            val random = resultFromSelect.getString("random")
            assertTrue(id < 1000)
            assertTrue(random == "test-prepared-statement")
        }
        this.proxy.eventService.assertQueryIsAudited(CREATE_TABLE_QUERY)
        this.proxy.eventService.assertQueryIsAudited(PREPARED_STATEMENT)
        this.proxy.eventService.assertQueryIsAudited(EXECUTE)
    }

    @Test
    fun `Postgres proxy must be able to execute DDL`() {

        val CREATE_TABLE_QUERY = "CREATE TABLE IF NOT EXISTS proxy_test_ddl (id INTEGER,random VARCHAR(32));"
        val TABEL_EXISTS_QUERY = """
        SELECT EXISTS (
           SELECT FROM information_schema.tables
           WHERE  table_schema = 'public'
           AND    table_name   = 'proxy_test_ddl'
        );
    """.trimIndent()
        val stmt = this.proxy.connection.createStatement()
        stmt.executeUpdate(CREATE_TABLE_QUERY)
        val result = stmt.executeQuery(TABEL_EXISTS_QUERY)
        while (result.next()) {
            val isOne = result.getString("exists")
            assertTrue(isOne == "t")
        }

        this.proxy.eventService.assertQueryIsAudited(CREATE_TABLE_QUERY)
        this.proxy.eventService.assertQueryIsAudited(TABEL_EXISTS_QUERY)
    }

}

