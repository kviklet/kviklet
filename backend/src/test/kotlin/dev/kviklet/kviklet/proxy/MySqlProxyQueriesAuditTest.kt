// This file is not MIT licensed
package dev.kviklet.kviklet.proxy

import dev.kviklet.kviklet.db.EventAdapter
import dev.kviklet.kviklet.db.ExecutionRequestAdapter
import dev.kviklet.kviklet.helper.ExecutionRequestFactory
import dev.kviklet.kviklet.proxy.core.ProxyServer
import dev.kviklet.kviklet.proxy.helpers.MySqlProxyInstance
import dev.kviklet.kviklet.proxy.helpers.mysqlClientJdbcUrl
import dev.kviklet.kviklet.proxy.helpers.mysqlDirectConnectionFactory
import dev.kviklet.kviklet.proxy.helpers.mysqlProxyServerFactory
import dev.kviklet.kviklet.proxy.mocks.FailingEventServiceMock
import dev.kviklet.kviklet.proxy.mysql.TargetMySqlSocketFactory
import dev.kviklet.kviklet.proxy.mysql.readPacket
import dev.kviklet.kviklet.service.dto.AuthenticationDetails
import dev.kviklet.kviklet.service.dto.DatasourceType
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
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
import java.sql.SQLException
import java.util.Properties

// End-to-end audit-correctness tests: real MySQL/MariaDB behind the proxy, driven by the real JDBC drivers,
// asserting that what the audit log records matches what was actually sent to the database. Parameterized over
// both flavors so driver quirks in either are caught.
@SpringBootTest
@ActiveProfiles("test")
class MySqlProxyQueriesAuditTest {
    @Autowired
    lateinit var executionRequestAdapter: ExecutionRequestAdapter

    @Autowired
    lateinit var eventAdapter: EventAdapter

    private val startedProxies = mutableListOf<ProxyServer>()
    private val openedConnections = mutableListOf<Connection>()

    companion object {
        // A generous max_allowed_packet so a statement larger than one 16MB wire packet is accepted by the
        // server (MariaDB defaults to 16MB, which would reject the split-packet test at the database).
        private const val LARGE_PACKET_BYTES = "134217728"

        private val mysqlContainer: MySQLContainer<*> = MySQLContainer(DockerImageName.parse("mysql:8.2")).apply {
            withDatabaseName("testdb")
            withUsername("test")
            withPassword("test")
            withCommand("--max_allowed_packet=$LARGE_PACKET_BYTES")
        }
        private val mariadbContainer: MariaDBContainer<*> = MariaDBContainer(DockerImageName.parse("mariadb:11.4"))
            .apply {
                withDatabaseName("testdb")
                withUsername("test")
                withPassword("test")
                withCommand("--max_allowed_packet=$LARGE_PACKET_BYTES")
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

        @JvmStatic
        fun datasourceTypes() = listOf(DatasourceType.MYSQL, DatasourceType.MARIADB)

        private fun container(type: DatasourceType): JdbcDatabaseContainer<*> = when (type) {
            DatasourceType.MYSQL -> mysqlContainer
            DatasourceType.MARIADB -> mariadbContainer
            else -> throw IllegalArgumentException("$type is not served by the MySQL proxy")
        }
    }

    @AfterEach
    fun tearDown() {
        openedConnections.forEach { runCatching { it.close() } }
        openedConnections.clear()
        startedProxies.forEach { it.shutdownServer() }
        startedProxies.clear()
    }

    private fun startProxy(type: DatasourceType): MySqlProxyInstance {
        val instance = mysqlProxyServerFactory(container(type), type, executionRequestAdapter, eventAdapter)
        startedProxies.add(instance.proxy)
        return instance
    }

    private fun directConnection(type: DatasourceType): Connection =
        mysqlDirectConnectionFactory(container(type)).also { openedConnections.add(it) }

    private fun proxyConnection(type: DatasourceType, proxy: MySqlProxyInstance, extraParams: String = ""): Connection =
        DriverManager.getConnection(
            mysqlClientJdbcUrl(type, proxy.port, extraParams),
            Properties().apply {
                setProperty("user", proxy.username)
                setProperty("password", proxy.password)
            },
        ).also { openedConnections.add(it) }

    // --- A. Statement coverage: the right text reaches the audit log ------------------------------------

    @ParameterizedTest
    @MethodSource("datasourceTypes")
    fun `a plain select is audited verbatim`(type: DatasourceType) {
        val proxy = startProxy(type)
        proxyConnection(type, proxy).createStatement().use { stmt ->
            stmt.executeQuery("SeLeCt 1 AS x").use { rs ->
                assertTrue(rs.next())
                assertEquals(1, rs.getInt("x"))
            }
        }
        proxy.eventService.assertQueryIsAudited("SeLeCt 1 AS x")
    }

    @ParameterizedTest
    @MethodSource("datasourceTypes")
    fun `DDL statements are audited`(type: DatasourceType) {
        val proxy = startProxy(type)
        proxyConnection(type, proxy).createStatement().use { stmt ->
            stmt.execute("DROP TABLE IF EXISTS audit_ddl")
            stmt.execute("CREATE TABLE audit_ddl (id INTEGER, name VARCHAR(32))")
            stmt.execute("ALTER TABLE audit_ddl ADD COLUMN created_at DATETIME")
            stmt.execute("DROP TABLE audit_ddl")
        }
        proxy.eventService.assertQueryIsAudited("CREATE TABLE audit_ddl (id INTEGER, name VARCHAR(32))")
        proxy.eventService.assertQueryIsAudited("ALTER TABLE audit_ddl ADD COLUMN created_at DATETIME")
        proxy.eventService.assertQueryIsAudited("DROP TABLE audit_ddl")
    }

    @ParameterizedTest
    @MethodSource("datasourceTypes")
    fun `DML statements are audited`(type: DatasourceType) {
        val proxy = startProxy(type)
        proxyConnection(type, proxy).createStatement().use { stmt ->
            stmt.execute("DROP TABLE IF EXISTS audit_dml")
            stmt.execute("CREATE TABLE audit_dml (id INTEGER, name VARCHAR(32))")
            stmt.executeUpdate("INSERT INTO audit_dml (id, name) VALUES (1, 'alice')")
            stmt.executeUpdate("UPDATE audit_dml SET name = 'bob' WHERE id = 1")
            stmt.executeUpdate("DELETE FROM audit_dml WHERE id = 1")
        }
        proxy.eventService.assertQueryIsAudited("INSERT INTO audit_dml (id, name) VALUES (1, 'alice')")
        proxy.eventService.assertQueryIsAudited("UPDATE audit_dml SET name = 'bob' WHERE id = 1")
        proxy.eventService.assertQueryIsAudited("DELETE FROM audit_dml WHERE id = 1")
    }

    @ParameterizedTest
    @MethodSource("datasourceTypes")
    fun `a schema switch is audited as an explicit USE`(type: DatasourceType) {
        val proxy = startProxy(type)
        val conn = proxyConnection(type, proxy)
        // Driver-issued COM_INIT_DB rather than a COM_QUERY; the proxy records it as an explicit USE so the
        // schema an unqualified name later resolves to is visible in the audit log.
        conn.catalog = "testdb"
        conn.createStatement().use { it.executeQuery("SELECT 1").close() }
        proxy.eventService.assertAuditedQueryContains("USE `testdb`")
    }

    // NOTE: no multi-statement (COM_QUERY "A; B") test. The proxy holds a single upstream connection that it
    // opened itself, and that connection is not negotiated with CLIENT_MULTI_STATEMENTS, so the server rejects
    // the second statement with a syntax error regardless of the client's allowMultiQueries setting. Multi-
    // statement queries are therefore not supported through the proxy today; there is nothing to audit.

    @ParameterizedTest
    @MethodSource("datasourceTypes")
    fun `special characters and quotes in a literal are audited unmodified`(type: DatasourceType) {
        val proxy = startProxy(type)
        // Quotes, backticks, a newline and a multi-byte character must survive into the audit log byte-for-byte.
        val sql = "INSERT INTO audit_special (note) VALUES ('o''brien `x`\nsnowman ☃')"
        proxyConnection(type, proxy).createStatement().use { stmt ->
            stmt.execute("DROP TABLE IF EXISTS audit_special")
            stmt.execute("CREATE TABLE audit_special (note TEXT)")
            stmt.executeUpdate(sql)
        }
        proxy.eventService.assertAuditedQueryContains(sql)
    }

    @ParameterizedTest
    @MethodSource("datasourceTypes")
    fun `transaction control statements are audited`(type: DatasourceType) {
        val proxy = startProxy(type)
        val conn = proxyConnection(type, proxy)
        conn.createStatement().use { stmt ->
            stmt.execute("DROP TABLE IF EXISTS audit_tx")
            stmt.execute("CREATE TABLE audit_tx (id INTEGER)")
        }
        conn.autoCommit = false
        conn.createStatement().use { it.executeUpdate("INSERT INTO audit_tx (id) VALUES (1)") }
        conn.commit()
        conn.createStatement().use { it.executeUpdate("INSERT INTO audit_tx (id) VALUES (2)") }
        conn.rollback()

        assertTrue(
            proxy.eventService.rawQueries.any { it.contains("commit", ignoreCase = true) },
            "Expected a COMMIT in the audit log, got: ${proxy.eventService.rawQueries}",
        )
        assertTrue(
            proxy.eventService.rawQueries.any { it.contains("rollback", ignoreCase = true) },
            "Expected a ROLLBACK in the audit log, got: ${proxy.eventService.rawQueries}",
        )
    }

    @ParameterizedTest
    @MethodSource("datasourceTypes")
    fun `a stored procedure call is audited`(type: DatasourceType) {
        val proxy = startProxy(type)
        proxyConnection(type, proxy).createStatement().use { stmt ->
            stmt.execute("DROP TABLE IF EXISTS audit_proc_log")
            stmt.execute("CREATE TABLE audit_proc_log (id INTEGER)")
            stmt.execute("DROP PROCEDURE IF EXISTS audit_proc")
            // A side-effecting procedure (no result set) so CALL returns a plain OK: a procedure that SELECTs
            // trips an internal NullPointerException in Connector/J when its result set is relayed.
            stmt.execute("CREATE PROCEDURE audit_proc() BEGIN INSERT INTO audit_proc_log (id) VALUES (1); END")
            stmt.execute("CALL audit_proc()")
        }
        proxy.eventService.assertAuditedQueryContains("CALL audit_proc()")
    }

    // --- B. Prepared statement paths: the placeholder-vs-value contract ---------------------------------

    @ParameterizedTest
    @MethodSource("datasourceTypes")
    fun `a server side prepare audits the placeholder text and not the bound value`(type: DatasourceType) {
        // TODO(KVI-220): once parameter interpolation lands, this should audit the bound value (2) as well.
        val proxy = startProxy(type)
        proxyConnection(type, proxy, extraParams = "&useServerPrepStmts=true").use { conn ->
            conn.prepareStatement("SELECT ? + 40").use { stmt ->
                stmt.setInt(1, 2)
                stmt.executeQuery().use { rs ->
                    assertTrue(rs.next())
                    assertEquals(42, rs.getInt(1))
                }
            }
        }
        proxy.eventService.assertAuditedQueryContains("? + 40")
        assertFalse(
            proxy.eventService.rawQueries.any { it.contains("SELECT 2 + 40") },
            "Server-side prepares must not interpolate the bound value until KVI-220; got: " +
                "${proxy.eventService.rawQueries}",
        )
    }

    @ParameterizedTest
    @MethodSource("datasourceTypes")
    fun `a client side prepare audits the value the driver inlined`(type: DatasourceType) {
        val proxy = startProxy(type)
        // useServerPrepStmts=false (the driver default): the parameter is inlined into a COM_QUERY before it
        // ever reaches the proxy, so the audit log sees the concrete value, not a placeholder.
        proxyConnection(type, proxy, extraParams = "&useServerPrepStmts=false").use { conn ->
            conn.createStatement().use { it.execute("DROP TABLE IF EXISTS audit_client_prep") }
            conn.createStatement().use { it.execute("CREATE TABLE audit_client_prep (id INTEGER)") }
            conn.prepareStatement("INSERT INTO audit_client_prep (id) VALUES (?)").use { stmt ->
                stmt.setInt(1, 7)
                stmt.executeUpdate()
            }
        }
        proxy.eventService.assertAuditedQueryContains("7")
        assertTrue(
            proxy.eventService.rawQueries.any { it.contains("audit_client_prep") && it.contains("7") },
            "Expected the inlined value 7 in the audited INSERT, got: ${proxy.eventService.rawQueries}",
        )
    }

    @ParameterizedTest
    @MethodSource("datasourceTypes")
    fun `sequential server side prepares are attributed to their own text`(type: DatasourceType) {
        val proxy = startProxy(type)
        proxyConnection(type, proxy, extraParams = "&useServerPrepStmts=true").use { conn ->
            conn.createStatement().use { it.execute("DROP TABLE IF EXISTS audit_seq") }
            conn.createStatement().use { it.execute("CREATE TABLE audit_seq (id INTEGER)") }
            conn.prepareStatement("INSERT INTO audit_seq (id) VALUES (?)").use { stmt ->
                stmt.setInt(1, 1)
                stmt.executeUpdate()
            }
            conn.prepareStatement("DELETE FROM audit_seq WHERE id = ?").use { stmt ->
                stmt.setInt(1, 1)
                stmt.executeUpdate()
            }
        }
        proxy.eventService.assertAuditedQueryContains("INSERT INTO audit_seq (id) VALUES (?)")
        proxy.eventService.assertAuditedQueryContains("DELETE FROM audit_seq WHERE id = ?")
    }

    @ParameterizedTest
    @MethodSource("datasourceTypes")
    fun `a reused prepared statement audits every execute`(type: DatasourceType) {
        val proxy = startProxy(type)
        proxyConnection(type, proxy, extraParams = "&useServerPrepStmts=true").use { conn ->
            conn.createStatement().use { it.execute("DROP TABLE IF EXISTS audit_reuse") }
            conn.createStatement().use { it.execute("CREATE TABLE audit_reuse (id INTEGER)") }
            conn.prepareStatement("INSERT INTO audit_reuse (id) VALUES (?)").use { stmt ->
                for (value in 1..3) {
                    stmt.setInt(1, value)
                    stmt.executeUpdate()
                }
            }
        }
        // The statement is prepared once and executed three times; each execute is audited with the prepared
        // placeholder text. The "VALUES (?)" signature separates the executes from the DROP/CREATE DDL, which
        // also mention the table name.
        val executes = proxy.eventService.rawQueries.count { it.contains("audit_reuse") && it.contains("VALUES (?)") }
        assertEquals(
            3,
            executes,
            "Each execute of the reused statement must be audited: ${proxy.eventService.rawQueries}",
        )
    }

    @ParameterizedTest
    @MethodSource("datasourceTypes")
    fun `every statement of a batch is audited`(type: DatasourceType) {
        val proxy = startProxy(type)
        proxyConnection(type, proxy).use { conn ->
            conn.createStatement().use { it.execute("DROP TABLE IF EXISTS audit_batch") }
            conn.createStatement().use { it.execute("CREATE TABLE audit_batch (id INTEGER)") }
            conn.createStatement().use { stmt ->
                stmt.addBatch("INSERT INTO audit_batch (id) VALUES (1)")
                stmt.addBatch("INSERT INTO audit_batch (id) VALUES (2)")
                stmt.addBatch("INSERT INTO audit_batch (id) VALUES (3)")
                stmt.executeBatch()
            }
        }
        // Regardless of how the driver frames the batch on the wire, all three rows must be audited.
        directConnection(type).createStatement().use { stmt ->
            stmt.executeQuery("SELECT count(*) FROM audit_batch").use { rs ->
                assertTrue(rs.next())
                assertEquals(3, rs.getInt(1))
            }
        }
        for (value in 1..3) {
            assertTrue(
                proxy.eventService.rawQueries.any { it.contains("audit_batch") && it.contains("($value)") },
                "Batch row $value missing from the audit log: ${proxy.eventService.rawQueries}",
            )
        }
    }

    // --- C. Volume and framing: real payloads, no OOM, no false aborts ----------------------------------

    @ParameterizedTest
    @MethodSource("datasourceTypes")
    fun `a statement larger than one wire packet is audited verbatim`(type: DatasourceType) {
        val proxy = startProxy(type)
        // A literal just over the 16MB (0xFFFFFF) wire-packet boundary forces the client to split it across
        // packets; the proxy must reassemble the whole thing to audit it verbatim.
        val bigValue = "x".repeat(17 * 1024 * 1024)
        val sql = "INSERT INTO audit_big (payload) VALUES ('$bigValue')"
        proxyConnection(type, proxy).createStatement().use { stmt ->
            stmt.execute("DROP TABLE IF EXISTS audit_big")
            stmt.execute("CREATE TABLE audit_big (payload LONGTEXT)")
            stmt.executeUpdate(sql)
        }
        directConnection(type).createStatement().use { stmt ->
            stmt.executeQuery("SELECT LENGTH(payload) FROM audit_big").use { rs ->
                assertTrue(rs.next())
                assertEquals(bigValue.length, rs.getInt(1))
            }
        }
        assertTrue(
            proxy.eventService.rawQueries.any { it.length >= sql.length && it.contains(bigValue) },
            "The oversized statement was not reassembled and audited verbatim",
        )
    }

    @ParameterizedTest
    @MethodSource("datasourceTypes")
    fun `a large result set streams through without buffering or aborting`(type: DatasourceType) {
        val proxy = startProxy(type)
        val conn = proxyConnection(type, proxy)
        conn.createStatement().use { stmt ->
            stmt.execute("DROP TABLE IF EXISTS audit_blob")
            stmt.execute("CREATE TABLE audit_blob (id INTEGER, payload LONGTEXT)")
        }
        val row = "y".repeat(1024 * 1024)
        conn.prepareStatement("INSERT INTO audit_blob (id, payload) VALUES (?, ?)").use { stmt ->
            for (id in 1..20) {
                stmt.setInt(1, id)
                stmt.setString(2, row)
                stmt.executeUpdate()
            }
        }
        // ~20MB of result rows stream back from the server; the proxy relays them past without buffering, so
        // the read completes and the session stays usable afterwards.
        var count = 0
        conn.createStatement().use { stmt ->
            stmt.executeQuery("SELECT id, payload FROM audit_blob ORDER BY id").use { rs ->
                while (rs.next()) {
                    assertEquals(row.length, rs.getString("payload").length)
                    count++
                }
            }
        }
        assertEquals(20, count)
        conn.createStatement().use { stmt ->
            stmt.executeQuery("SELECT 1").use { rs ->
                assertTrue(rs.next())
                assertEquals(1, rs.getInt(1))
            }
        }
    }

    @ParameterizedTest
    @MethodSource("datasourceTypes")
    fun `a hundred statements execute without hanging and are all audited`(type: DatasourceType) {
        val proxy = startProxy(type)
        proxyConnection(type, proxy).createStatement().use { stmt ->
            stmt.execute("DROP TABLE IF EXISTS audit_bulk")
            stmt.execute("CREATE TABLE audit_bulk (id INTEGER)")
            for (id in 1..100) {
                stmt.executeUpdate("INSERT INTO audit_bulk (id) VALUES ($id)")
            }
        }
        directConnection(type).createStatement().use { stmt ->
            stmt.executeQuery("SELECT count(*) FROM audit_bulk").use { rs ->
                assertTrue(rs.next())
                assertEquals(100, rs.getInt(1))
            }
        }
        val inserts = proxy.eventService.rawQueries.count { it.contains("INSERT INTO audit_bulk") }
        assertEquals(100, inserts, "Every one of the 100 inserts must be audited")
    }

    // --- D. Fail-closed behavior end to end -------------------------------------------------------------

    @ParameterizedTest
    @MethodSource("datasourceTypes")
    fun `a query is blocked and the table untouched when the audit write fails`(type: DatasourceType) {
        val failingEventService = FailingEventServiceMock(
            executionRequestAdapter,
            eventAdapter,
            ExecutionRequestFactory().createDatasourceExecutionRequest(),
        )
        val proxy = mysqlProxyServerFactory(
            container(type),
            type,
            executionRequestAdapter,
            eventAdapter,
            eventServiceOverride = failingEventService,
        )
        startedProxies.add(proxy.proxy)

        directConnection(type).createStatement().use { it.execute("DROP TABLE IF EXISTS audit_failclosed") }
        // Connect while auditing still works, so the driver's connection-init queries succeed; only then start
        // failing audit writes, so the failure lands on the CREATE and not on connection setup.
        val conn = proxyConnection(type, proxy)
        failingEventService.failing = true
        val exception = assertThrows(SQLException::class.java) {
            conn.createStatement().use { stmt ->
                stmt.execute("CREATE TABLE audit_failclosed (id INTEGER)")
            }
        }
        assertTrue(
            exception.message!!.contains("audit", ignoreCase = true),
            "Expected an audit-related error, got: ${exception.message}",
        )

        // The blocked statement must never have reached the database.
        directConnection(type).createStatement().use { stmt ->
            stmt.executeQuery(
                "SELECT count(*) FROM information_schema.tables " +
                    "WHERE table_schema = 'testdb' AND table_name = 'audit_failclosed'",
            ).use { rs ->
                assertTrue(rs.next())
                assertEquals(0, rs.getInt(1))
            }
        }
    }

    @ParameterizedTest
    @MethodSource("datasourceTypes")
    fun `a disallowed command aborts an authenticated session`(type: DatasourceType) {
        val proxy = startProxy(type)
        // TargetMySqlSocketFactory authenticates through the proxy and hands back a raw socket, so we can send
        // a command the allowlist rejects (COM_BINLOG_DUMP) the way no normal JDBC call would.
        val client = TargetMySqlSocketFactory(
            type,
            AuthenticationDetails.UserPassword(proxy.username, proxy.password),
            "testdb",
            "localhost",
            proxy.port,
        ).createTargetMySqlConnection()
        try {
            val comBinlogDump = mysqlPacket(0, byteArrayOf(0x12, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0))
            client.socket.getOutputStream().apply {
                write(comBinlogDump)
                flush()
            }
            client.socket.soTimeout = 5000
            val (_, payload) = readPacket(client.socket.getInputStream())
            assertEquals(0xFF, payload[0].toInt() and 0xFF, "Expected an ERR packet aborting the session")
        } finally {
            client.socket.close()
        }
    }

    // --- E. CRUD round-trip correctness -----------------------------------------------------------------

    @ParameterizedTest
    @MethodSource("datasourceTypes")
    fun `a full CRUD lifecycle works through the proxy and is audited at every step`(type: DatasourceType) {
        val proxy = startProxy(type)
        val conn = proxyConnection(type, proxy)
        conn.createStatement().use { stmt ->
            stmt.execute("DROP TABLE IF EXISTS audit_crud")
            stmt.execute("CREATE TABLE audit_crud (id INTEGER PRIMARY KEY, name VARCHAR(32))")
            stmt.executeUpdate("INSERT INTO audit_crud (id, name) VALUES (1, 'alice')")

            stmt.executeQuery("SELECT name FROM audit_crud WHERE id = 1").use { rs ->
                assertTrue(rs.next())
                assertEquals("alice", rs.getString("name"))
            }

            stmt.executeUpdate("UPDATE audit_crud SET name = 'bob' WHERE id = 1")
            stmt.executeQuery("SELECT name FROM audit_crud WHERE id = 1").use { rs ->
                assertTrue(rs.next())
                assertEquals("bob", rs.getString("name"))
            }

            stmt.executeUpdate("DELETE FROM audit_crud WHERE id = 1")
            stmt.executeQuery("SELECT count(*) FROM audit_crud").use { rs ->
                assertTrue(rs.next())
                assertEquals(0, rs.getInt(1))
            }
        }

        proxy.eventService.assertQueryIsAudited("CREATE TABLE audit_crud (id INTEGER PRIMARY KEY, name VARCHAR(32))")
        proxy.eventService.assertQueryIsAudited("INSERT INTO audit_crud (id, name) VALUES (1, 'alice')")
        proxy.eventService.assertQueryIsAudited("UPDATE audit_crud SET name = 'bob' WHERE id = 1")
        proxy.eventService.assertQueryIsAudited("DELETE FROM audit_crud WHERE id = 1")
    }

    private fun mysqlPacket(sequenceId: Int, payload: ByteArray): ByteArray {
        val header = byteArrayOf(
            (payload.size and 0xFF).toByte(),
            ((payload.size ushr 8) and 0xFF).toByte(),
            ((payload.size ushr 16) and 0xFF).toByte(),
            (sequenceId and 0xFF).toByte(),
        )
        return header + payload
    }
}
