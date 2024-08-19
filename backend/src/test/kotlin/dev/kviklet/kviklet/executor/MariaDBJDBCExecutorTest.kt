package dev.kviklet.kviklet.executor

import dev.kviklet.kviklet.service.ColumnInfo
import dev.kviklet.kviklet.service.JDBCExecutor
import dev.kviklet.kviklet.service.dto.ErrorQueryResult
import dev.kviklet.kviklet.service.dto.RecordsQueryResult
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.containers.JdbcDatabaseContainer
import org.testcontainers.containers.MariaDBContainer
import org.testcontainers.utility.DockerImageName

@SpringBootTest
@ActiveProfiles("test")
class MariaDBJDBCExecutorTest(@Autowired override val JDBCExecutorService: JDBCExecutor) :
    AbstractJDBCExecutorTest(
        JDBCExecutorService = JDBCExecutorService,
    ) {

    companion object {
        val db: MariaDBContainer<*> = MariaDBContainer(DockerImageName.parse("mariadb:11.4"))
            .withUsername("root")
            .withPassword("")
            .withReuse(true)
            .withDatabaseName("")

        init {
            db.start()
        }
    }

    override fun getDb(): JdbcDatabaseContainer<*> = db

    override val initScript: String = "mysql_init.sql" // compatible to also use for mariadb

    @Test
    override fun testSelectSimple() {
        executeQuery("SELECT 1 as col1, '2' as col2;") shouldBe RecordsQueryResult(
            columns = listOf(
                ColumnInfo("col1", "INTEGER", "java.lang.Integer"),
                ColumnInfo("col2", "VARCHAR", "java.lang.String"),
            ),
            data = listOf(mapOf("col1" to "1", "col2" to "2")),
        )
    }

    @Test
    override fun testConnectionError() {
        val result = executeQuery("SELECT 1;", username = "root", password = "foo") as ErrorQueryResult
        result.errorCode shouldBe 1045
        result.message shouldContain "Access denied for user 'root'@"
        result.message shouldContain "(using password: YES)"
    }

    @Test
    override fun testDatabaseError() {
        val result = executeQuery("SELECT * FROM foo.non_existent_table;") as ErrorQueryResult
        result.errorCode shouldBe 1146
        result.message shouldContain "Table 'foo.non_existent_table' doesn't exist"
    }

    @Test
    fun testSelectAllDataytpes() {
        val queryResult = executeQuery("SELECT * FROM foo.all_datatypes;") as RecordsQueryResult
        queryResult.columns shouldBe listOf(
            ColumnInfo(label = "int_column", typeName = "INTEGER", typeClass = "java.lang.Integer"),
            ColumnInfo(label = "tinyint_column", typeName = "TINYINT", typeClass = "java.lang.Integer"),
            ColumnInfo(label = "smallint_column", typeName = "SMALLINT", typeClass = "java.lang.Short"),
            ColumnInfo(label = "mediumint_column", typeName = "MEDIUMINT", typeClass = "java.lang.Integer"),
            ColumnInfo(label = "bigint_column", typeName = "BIGINT", typeClass = "java.lang.Long"),
            ColumnInfo(label = "float_column", typeName = "FLOAT", typeClass = "java.lang.Float"),
            ColumnInfo(label = "double_column", typeName = "DOUBLE", typeClass = "java.lang.Double"),
            ColumnInfo(label = "decimal_column", typeName = "DECIMAL", typeClass = "java.math.BigDecimal"),
            ColumnInfo(label = "date_column", typeName = "DATE", typeClass = "java.sql.Date"),
            ColumnInfo(label = "datetime_column", typeName = "DATETIME", typeClass = "java.sql.Timestamp"),
            ColumnInfo(label = "timestamp_column", typeName = "TIMESTAMP", typeClass = "java.sql.Timestamp"),
            ColumnInfo(label = "time_column", typeName = "TIME", typeClass = "java.sql.Time"),
            ColumnInfo(label = "year_column", typeName = "YEAR", typeClass = "java.sql.Date"),
            ColumnInfo(label = "char_column", typeName = "CHAR", typeClass = "java.lang.String"),
            ColumnInfo(label = "varchar_column", typeName = "VARCHAR", typeClass = "java.lang.String"),
            ColumnInfo(label = "binary_column", typeName = "BINARY", typeClass = "byte[]"),
            ColumnInfo(label = "varbinary_column", typeName = "VARBINARY", typeClass = "byte[]"),
            ColumnInfo(label = "tinyblob_column", typeName = "TINYBLOB", typeClass = "java.sql.Blob"),
            ColumnInfo(label = "blob_column", typeName = "BLOB", typeClass = "java.sql.Blob"),
            ColumnInfo(label = "mediumblob_column", typeName = "MEDIUMBLOB", typeClass = "java.sql.Blob"),
            ColumnInfo(label = "longblob_column", typeName = "LONGBLOB", typeClass = "java.sql.Blob"),
            ColumnInfo(label = "tinytext_column", typeName = "TINYTEXT", typeClass = "java.lang.String"),
            ColumnInfo(label = "text_column", typeName = "TEXT", typeClass = "java.lang.String"),
            ColumnInfo(label = "mediumtext_column", typeName = "MEDIUMTEXT", typeClass = "java.lang.String"),
            ColumnInfo(label = "longtext_column", typeName = "LONGTEXT", typeClass = "java.lang.String"),
            ColumnInfo(label = "enum_column", typeName = "CHAR", typeClass = "java.lang.String"),
            ColumnInfo(label = "set_column", typeName = "CHAR", typeClass = "java.lang.String"),
            ColumnInfo(label = "bit_column", typeName = "BIT", typeClass = "byte[]"),
            ColumnInfo(label = "bool_column", typeName = "BOOLEAN", typeClass = "java.lang.Boolean"),
            ColumnInfo(label = "json_column", typeName = "JSON", typeClass = "java.lang.String"),
        )

        val row = queryResult.data[0]
        row["int_column"] shouldBe "1"
        row["tinyint_column"] shouldBe "1"
        row["smallint_column"] shouldBe "1"
        row["mediumint_column"] shouldBe "1"
        row["bigint_column"] shouldBe "1"
        row["float_column"] shouldBe "1.23"
        row["double_column"] shouldBe "1.23"
        row["decimal_column"] shouldBe "1.23"
        row["date_column"] shouldBe "2023-01-01"
        row["datetime_column"] shouldBe "2023-01-01 00:00:00.000000"
        row["timestamp_column"] shouldBe "2023-01-01 00:00:00.000000"
        row["time_column"] shouldBe "00:00:00.000000"
        row["year_column"] shouldBe "2023"
        row["char_column"] shouldBe "char"
        row["varchar_column"] shouldBe "varchar"
        row["binary_column"] shouldStartWith "*"
        row["varbinary_column"] shouldStartWith "*"
        row["tinyblob_column"] shouldBe "tinyblob"
        row["blob_column"] shouldBe "blob"
        row["mediumblob_column"] shouldBe "mediumblob"
        row["longblob_column"] shouldBe "longblob"
        row["tinytext_column"] shouldBe "tinytext"
        row["text_column"] shouldBe "text"
        row["mediumtext_column"] shouldBe "mediumtext"
        row["longtext_column"] shouldBe "longtext"
        row["enum_column"] shouldBe "option1"
        row["set_column"] shouldBe "option1"
        row["bit_column"] shouldBe "b'10101010'"
        row["bool_column"] shouldBe "1"
        row["json_column"] shouldBe """{"key": "value"}"""
    }
}
