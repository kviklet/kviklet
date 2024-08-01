package dev.kviklet.kviklet.executor

import dev.kviklet.kviklet.service.ColumnInfo
import dev.kviklet.kviklet.service.JDBCExecutor
import dev.kviklet.kviklet.service.dto.ErrorQueryResult
import dev.kviklet.kviklet.service.dto.RecordsQueryResult
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.containers.JdbcDatabaseContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

@SpringBootTest
@ActiveProfiles("test")
class PostgresJDBCExecutorTest(@Autowired override val JDBCExecutorService: JDBCExecutor) :
    AbstractJDBCExecutorTest(
        JDBCExecutorService = JDBCExecutorService,
    ) {

    companion object {
        val db: PostgreSQLContainer<*> = PostgreSQLContainer(DockerImageName.parse("postgres:11.1"))
            .withUsername("root")
            .withPassword("root")
            .withReuse(true)
            .withDatabaseName("")

        init {
            db.start()
        }
    }

    override fun getDb(): JdbcDatabaseContainer<*> = db

    override val initScript: String = "psql_init.sql"

    @Test
    override fun testSelectSimple() {
        executeQuery("SELECT 1 as col1, '2' as col2;") shouldBe RecordsQueryResult(
            columns = listOf(
                ColumnInfo("col1", "int4", "java.lang.Integer"),
                ColumnInfo("col2", "text", "java.lang.String"),
            ),
            data = listOf(mapOf("col1" to "1", "col2" to "2")),
        )
    }

    @Test
    override fun testDatabaseError() {
        executeQuery("SELECT * FROM foo.non_existent_table;") shouldBe
            ErrorQueryResult(
                0,
                "ERROR: relation \"foo.non_existent_table\" does not exist\n  Position: 15",
            )

        executeQuery("FOOBAR") shouldBe
            ErrorQueryResult(
                0,
                "ERROR: syntax error at or near \"FOOBAR\"\n  Position: 1",
            )
    }

    @Test
    override fun testConnectionError() {
        executeQuery("SELECT 1;", username = "root", password = "foo") shouldBe
            ErrorQueryResult(
                0,
                "FATAL: password authentication failed for user \"root\"",
            )
    }

    @Test
    fun testSelectAllDataytpes() {
        val queryResult = executeQuery("SELECT * FROM foo.all_datatypes;")
        queryResult shouldBe RecordsQueryResult(
            columns = listOf(
                ColumnInfo(label = "int_column", typeName = "int4", typeClass = "java.lang.Integer"),
                ColumnInfo(
                    label = "smallint_column",
                    typeName = "int2",
                    typeClass = "java.lang.Integer",
                ),
                ColumnInfo(label = "bigint_column", typeName = "int8", typeClass = "java.lang.Long"),
                ColumnInfo(
                    label = "real_column",
                    typeName = "float4",
                    typeClass = "java.lang.Float",
                ),
                ColumnInfo(label = "double_column", typeName = "float8", typeClass = "java.lang.Double"),
                ColumnInfo(
                    label = "decimal_column",
                    typeName = "numeric",
                    typeClass = "java.math.BigDecimal",
                ),
                ColumnInfo(label = "numeric_column", typeName = "numeric", typeClass = "java.math.BigDecimal"),
                ColumnInfo(
                    label = "boolean_column",
                    typeName = "bool",
                    typeClass = "java.lang.Boolean",
                ),
                ColumnInfo(label = "char_column", typeName = "bpchar", typeClass = "java.lang.String"),
                ColumnInfo(
                    label = "varchar_column",
                    typeName = "varchar",
                    typeClass = "java.lang.String",
                ),
                ColumnInfo(label = "text_column", typeName = "text", typeClass = "java.lang.String"),
                ColumnInfo(
                    label = "name_column",
                    typeName = "name",
                    typeClass = "java.lang.String",
                ),
                ColumnInfo(label = "bytea_column", typeName = "bytea", typeClass = "[B"),
                ColumnInfo(
                    label = "bit_column",
                    typeName = "bit",
                    typeClass = "java.lang.Boolean",
                ),
                ColumnInfo(label = "bit_varying_column", typeName = "varbit", typeClass = "java.lang.String"),
                ColumnInfo(
                    label = "timestamp_column",
                    typeName = "timestamp",
                    typeClass = "java.sql.Timestamp",
                ),
                ColumnInfo(
                    label = "interval_column",
                    typeName = "interval",
                    typeClass = "org.postgresql.util.PGInterval",
                ),
                ColumnInfo(label = "date_column", typeName = "date", typeClass = "java.sql.Date"),
                ColumnInfo(
                    label = "time_column",
                    typeName = "time",
                    typeClass = "java.sql.Time",
                ),
                ColumnInfo(label = "timez_column", typeName = "timetz", typeClass = "java.sql.Time"),
                ColumnInfo(
                    label = "money_column",
                    typeName = "money",
                    typeClass = "org.postgresql.util.PGmoney",
                ),
                ColumnInfo(label = "uuid_column", typeName = "uuid", typeClass = "java.util.UUID"),
                ColumnInfo(
                    label = "cidr_column",
                    typeName = "cidr",
                    typeClass = "java.lang.String",
                ),
                ColumnInfo(label = "inet_column", typeName = "inet", typeClass = "java.lang.String"),
                ColumnInfo(
                    label = "macaddr_column",
                    typeName = "macaddr",
                    typeClass = "java.lang.String",
                ),
                ColumnInfo(label = "macaddr8_column", typeName = "macaddr8", typeClass = "java.lang.String"),
                ColumnInfo(
                    label = "json_column",
                    typeName = "json",
                    typeClass = "org.postgresql.util.PGobject",
                ),
                ColumnInfo(label = "jsonb_column", typeName = "jsonb", typeClass = "java.lang.String"),
                ColumnInfo(
                    label = "xml_column",
                    typeName = "xml",
                    typeClass = "java.sql.SQLXML",
                ),
                ColumnInfo(
                    label = "point_column",
                    typeName = "point",
                    typeClass = "org.postgresql.geometric.PGpoint",
                ),
                ColumnInfo(
                    label = "line_column",
                    typeName = "line",
                    typeClass = "org.postgresql.geometric.PGline",
                ),
                ColumnInfo(label = "box_column", typeName = "box", typeClass = "org.postgresql.geometric.PGbox"),
                ColumnInfo(
                    label = "path_column",
                    typeName = "path",
                    typeClass = "org.postgresql.geometric.PGpath",
                ),
                ColumnInfo(
                    label = "polygon_column",
                    typeName = "polygon",
                    typeClass = "org.postgresql.geometric.PGpolygon",
                ),
                ColumnInfo(
                    label = "circle_column",
                    typeName = "circle",
                    typeClass = "org.postgresql.geometric.PGcircle",
                ),
                ColumnInfo(label = "tsvector_column", typeName = "tsvector", typeClass = "java.lang.String"),
                ColumnInfo(
                    label = "tsquery_column",
                    typeName = "tsquery",
                    typeClass = "java.lang.String",
                ),
            ),
            data = listOf(
                @Suppress("ktlint:standard:max-line-length")
                mapOf(
                    "int_column" to "1",
                    "smallint_column" to "1",
                    "bigint_column" to "1",
                    "real_column" to "1.23000002",
                    "double_column" to "1.22999999999999998",
                    "decimal_column" to "1.23",
                    "numeric_column" to "1.23",
                    "boolean_column" to "t",
                    "char_column" to
                        "char                                                                                                                                                                                                                                                           ",
                    "varchar_column" to "varchar",
                    "text_column" to "text",
                    "name_column" to "name",
                    "bytea_column" to "0xdeadbeef",
                    "bit_column" to "10101010",
                    "bit_varying_column" to "10101010",
                    "timestamp_column" to "2023-01-01 00:00:00",
                    "interval_column" to "1 year 2 mons 3 days 04:05:06",
                    "date_column" to "2023-01-01",
                    "time_column" to "00:00:00",
                    "timez_column" to "00:00:00+00",
                    "money_column" to "$1,234.56",
                    "uuid_column" to "b3bae92c-3c3b-11ec-8d3d-0242ac130003",
                    "cidr_column" to "192.168.100.128/25",
                    "inet_column" to "192.168.100.128",
                    "macaddr_column" to "08:00:2b:01:02:03",
                    "macaddr8_column" to "08:00:2b:01:02:03:04:05",
                    "json_column" to "{\"key\": \"value\"}",
                    "jsonb_column" to "{\"key\": \"value\"}",
                    "xml_column" to "<root></root>",
                    "point_column" to "(0,0)",
                    "line_column" to "{1,-1,0}",
                    "box_column" to "(1,1),(0,0)",
                    "path_column" to "[(0,0),(1,1)]",
                    "polygon_column" to "((0,0),(1,1))",
                    "circle_column" to "<(0,0),1>",
                    "tsvector_column" to "'simple'",
                    "tsquery_column" to "'simple'",
                ),
            ),
        )
    }

    @Test
    fun testQueryCancellation() {
        // Create a long-running query
        val longRunningQuery = """
        SELECT pg_sleep(5); -- This will sleep for 5 seconds
    """

        // Create a separate thread to execute the long-running query
        val executionThread = Thread {
            try {
                executeQuery(longRunningQuery)
            } catch (e: Exception) {
                assert(e is IllegalStateException)
                assert(e.message?.contains("cancelled") == true)
            }
        }

        executionThread.start()
        Thread.sleep(1000) // wait for the query to start
        JDBCExecutorService.cancelQuery(executionRequestId)
        executionThread.join(500) // Wait half a second for the cancellation to kick in
        assert(!executionThread.isAlive) { "The query was not cancelled within the expected time frame" }
    }
}
