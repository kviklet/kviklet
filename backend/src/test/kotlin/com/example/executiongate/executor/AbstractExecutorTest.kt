package com.example.executiongate.executor

import com.example.executiongate.service.ColumnInfo
import com.example.executiongate.service.ErrorQueryResult
import com.example.executiongate.service.ExecutorService
import com.example.executiongate.service.RecordsQueryResult
import com.example.executiongate.service.UpdateQueryResult
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.io.FileUrlResource
import org.springframework.jdbc.datasource.init.ScriptUtils
import org.testcontainers.containers.JdbcDatabaseContainer
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.utility.DockerImageName


@SpringBootTest
abstract class AbstractExecutorTest(
    @Autowired val executorService: ExecutorService
) {

    abstract val initScript: String

    abstract fun getDb(): JdbcDatabaseContainer<*>

    fun executeQuery(
        query: String,
        url: String = getDb().jdbcUrl,
        username: String = getDb().username,
        password: String = getDb().password
    ) = executorService.execute(url, username, password, query)


    @BeforeEach
    fun setup() {
        val initScript = this::class.java.classLoader.getResource(initScript)!!
        ScriptUtils.executeSqlScript(getDb().createConnection(""), FileUrlResource(initScript))
    }

    @Test
    fun testDatabaseError() {
        executeQuery("SELECT * FROM foo.non_existent_table;") shouldBe
            ErrorQueryResult(1146, "Table 'foo.non_existent_table' doesn't exist")

        executeQuery("FOOBAR") shouldBe
            ErrorQueryResult(1064, "You have an error in your SQL syntax; check the manual that corresponds to your MySQL server version for the right syntax to use near 'FOOBAR' at line 1")
    }

    @Test
    fun testConnectionError() {
        executeQuery("SELECT 1;", username = "root", password = "foo") shouldBe
            ErrorQueryResult(1045, "Access denied for user 'root'@'172.17.0.1' (using password: YES)")
    }

    @Test
    fun testDdlQuery() {
        executeQuery("CREATE TABLE foo.temp (col INT);") shouldBe UpdateQueryResult(0)
    }

    @Test
    fun testInsert() {
        executeQuery("INSERT INTO foo.simple_table VALUES (1, 'foo');") shouldBe UpdateQueryResult(1)
    }

    @Test
    fun testUpdate() {
        // col1=1 exists, 1 row is updated
        executeQuery("UPDATE foo.simple_table SET col2='foobar' WHERE col1 = 1;") shouldBe UpdateQueryResult(1)
        // col1=3 does not exist, so nothing is updated:
        executeQuery("UPDATE foo.simple_table SET col2='foobar' WHERE col1 = 3;") shouldBe UpdateQueryResult(0)
    }

    @Test
    fun testSelectSimple() {
        executeQuery("SELECT 1 as col1, '2' as col2;") shouldBe RecordsQueryResult(
            columns = listOf(
                ColumnInfo("col1", "BIGINT", "java.lang.Long"),
                ColumnInfo("col2", "VARCHAR", "java.lang.String")
            ),
            data = listOf(mapOf("col1" to "1", "col2" to "2"))
        )
    }

    @Test
    fun testSelectAllDataytpes() {
        val queryResult = executeQuery("SELECT * FROM foo.all_datatypes;")
        queryResult shouldBe RecordsQueryResult(
            columns = listOf(
                ColumnInfo(label="int_column", typeName="INT", typeClass="java.lang.Integer"),
                ColumnInfo(label="tinyint_column", typeName="TINYINT", typeClass="java.lang.Integer"),
                ColumnInfo(label="smallint_column", typeName="SMALLINT", typeClass="java.lang.Integer"),
                ColumnInfo(label="mediumint_column", typeName="MEDIUMINT", typeClass="java.lang.Integer"),
                ColumnInfo(label="bigint_column", typeName="BIGINT", typeClass="java.lang.Long"),
                ColumnInfo(label="float_column", typeName="FLOAT", typeClass="java.lang.Float"),
                ColumnInfo(label="double_column", typeName="DOUBLE", typeClass="java.lang.Double"),
                ColumnInfo(label="decimal_column", typeName="DECIMAL", typeClass="java.math.BigDecimal"),
                ColumnInfo(label="date_column", typeName="DATE", typeClass="java.sql.Date"),
                ColumnInfo(label="datetime_column", typeName="DATETIME", typeClass="java.time.LocalDateTime"),
                ColumnInfo(label="timestamp_column", typeName="TIMESTAMP", typeClass="java.sql.Timestamp"),
                ColumnInfo(label="time_column", typeName="TIME", typeClass="java.sql.Time"),
                ColumnInfo(label="year_column", typeName="YEAR", typeClass="java.sql.Date"),
                ColumnInfo(label="char_column", typeName="CHAR", typeClass="java.lang.String"),
                ColumnInfo(label="varchar_column", typeName="VARCHAR", typeClass="java.lang.String"),
                ColumnInfo(label="binary_column", typeName="BINARY", typeClass="[B"),
                ColumnInfo(label="varbinary_column", typeName="VARBINARY", typeClass="[B"),
                ColumnInfo(label="tinyblob_column", typeName="TINYBLOB", typeClass="[B"),
                ColumnInfo(label="blob_column", typeName="BLOB", typeClass="[B"),
                ColumnInfo(label="mediumblob_column", typeName="MEDIUMBLOB", typeClass="[B"),
                ColumnInfo(label="longblob_column", typeName="LONGBLOB", typeClass="[B"),
                ColumnInfo(label="tinytext_column", typeName="TINYTEXT", typeClass="java.lang.String"),
                ColumnInfo(label="text_column", typeName="TEXT", typeClass="java.lang.String"),
                ColumnInfo(label="mediumtext_column", typeName="MEDIUMTEXT", typeClass="java.lang.String"),
                ColumnInfo(label="longtext_column", typeName="LONGTEXT", typeClass="java.lang.String"),
                ColumnInfo(label="enum_column", typeName="CHAR", typeClass="java.lang.String"),
                ColumnInfo(label="set_column", typeName="CHAR", typeClass="java.lang.String"),
                ColumnInfo(label="bit_column", typeName="BIT", typeClass="java.lang.Boolean"),
                ColumnInfo(label="bool_column", typeName="BIT", typeClass="java.lang.Boolean"),
                ColumnInfo(label="json_column", typeName="JSON", typeClass="java.lang.String"),
            ),
            data = listOf(mapOf(
                "int_column" to "1",
                "tinyint_column" to "1",
                "smallint_column" to "1",
                "mediumint_column" to "1",
                "bigint_column" to "1",
                "float_column" to "1.23",
                "double_column" to "1.23",
                "decimal_column" to "1.23",
                "date_column" to "2023-01-01",
                "datetime_column" to "2023-01-01 00:00:00",
                "timestamp_column" to "2023-01-01 00:00:00",
                "time_column" to "00:00:00",
                "year_column" to "2023-01-01",
                "char_column" to "char",
                "varchar_column" to "varchar",
                "binary_column" to "0x2a0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000",
                "varbinary_column" to "0x2a",
                "tinyblob_column" to "0x74696e79626c6f62",
                "blob_column" to "0x626c6f62",
                "mediumblob_column" to "0x6d656469756d626c6f62",
                "longblob_column" to "0x6c6f6e67626c6f62",
                "tinytext_column" to "tinytext",
                "text_column" to "text",
                "mediumtext_column" to "mediumtext",
                "longtext_column" to "longtext",
                "enum_column" to "option1",
                "set_column" to "option1",
                "bit_column" to "170",
                "bool_column" to "1",
                "json_column" to "{\"key\": \"value\"}",
            ))
        )
    }

}