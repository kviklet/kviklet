package dev.kviklet.kviklet.executor

import dev.kviklet.kviklet.service.ColumnInfo
import dev.kviklet.kviklet.service.JDBCExecutor
import dev.kviklet.kviklet.service.dto.RecordsQueryResult
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.containers.JdbcDatabaseContainer
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.utility.DockerImageName

@SpringBootTest
@ActiveProfiles("test")
class MysqlJDBCExecutorTest(@Autowired override val JDBCExecutorService: JDBCExecutor) :
    AbstractJDBCExecutorTest(
        JDBCExecutorService = JDBCExecutorService,
    ) {

    companion object {
        val db: MySQLContainer<*> = MySQLContainer(DockerImageName.parse("mysql:8.2"))
            .withUsername("root")
            .withPassword("")
            .withReuse(true)
            .withDatabaseName("")

        init {
            db.start()/**/
        }
    }

    override fun getDb(): JdbcDatabaseContainer<*> = db

    override val initScript: String = "mysql_init.sql"

    @Test
    fun testSelectAllDataytpes() {
        val queryResult = executeQuery("SELECT * FROM foo.all_datatypes;")
        queryResult shouldBe RecordsQueryResult(
            columns = listOf(
                ColumnInfo(label = "int_column", typeName = "INT", typeClass = "java.lang.Integer"),
                ColumnInfo(label = "tinyint_column", typeName = "TINYINT", typeClass = "java.lang.Integer"),
                ColumnInfo(label = "smallint_column", typeName = "SMALLINT", typeClass = "java.lang.Integer"),
                ColumnInfo(label = "mediumint_column", typeName = "MEDIUMINT", typeClass = "java.lang.Integer"),
                ColumnInfo(label = "bigint_column", typeName = "BIGINT", typeClass = "java.lang.Long"),
                ColumnInfo(label = "float_column", typeName = "FLOAT", typeClass = "java.lang.Float"),
                ColumnInfo(label = "double_column", typeName = "DOUBLE", typeClass = "java.lang.Double"),
                ColumnInfo(label = "decimal_column", typeName = "DECIMAL", typeClass = "java.math.BigDecimal"),
                ColumnInfo(label = "date_column", typeName = "DATE", typeClass = "java.sql.Date"),
                ColumnInfo(
                    label = "datetime_column",
                    typeName = "DATETIME",
                    typeClass = "java.time.LocalDateTime",
                ),
                ColumnInfo(label = "timestamp_column", typeName = "TIMESTAMP", typeClass = "java.sql.Timestamp"),
                ColumnInfo(label = "time_column", typeName = "TIME", typeClass = "java.sql.Time"),
                ColumnInfo(label = "year_column", typeName = "YEAR", typeClass = "java.sql.Date"),
                ColumnInfo(label = "char_column", typeName = "CHAR", typeClass = "java.lang.String"),
                ColumnInfo(label = "varchar_column", typeName = "VARCHAR", typeClass = "java.lang.String"),
                ColumnInfo(label = "binary_column", typeName = "BINARY", typeClass = "[B"),
                ColumnInfo(label = "varbinary_column", typeName = "VARBINARY", typeClass = "[B"),
                ColumnInfo(label = "tinyblob_column", typeName = "TINYBLOB", typeClass = "[B"),
                ColumnInfo(label = "blob_column", typeName = "BLOB", typeClass = "[B"),
                ColumnInfo(label = "mediumblob_column", typeName = "MEDIUMBLOB", typeClass = "[B"),
                ColumnInfo(label = "longblob_column", typeName = "LONGBLOB", typeClass = "[B"),
                ColumnInfo(label = "tinytext_column", typeName = "TINYTEXT", typeClass = "java.lang.String"),
                ColumnInfo(label = "text_column", typeName = "TEXT", typeClass = "java.lang.String"),
                ColumnInfo(label = "mediumtext_column", typeName = "MEDIUMTEXT", typeClass = "java.lang.String"),
                ColumnInfo(label = "longtext_column", typeName = "LONGTEXT", typeClass = "java.lang.String"),
                ColumnInfo(label = "enum_column", typeName = "CHAR", typeClass = "java.lang.String"),
                ColumnInfo(label = "set_column", typeName = "CHAR", typeClass = "java.lang.String"),
                ColumnInfo(label = "bit_column", typeName = "BIT", typeClass = "java.lang.Boolean"),
                ColumnInfo(label = "bool_column", typeName = "BIT", typeClass = "java.lang.Boolean"),
                ColumnInfo(label = "json_column", typeName = "JSON", typeClass = "java.lang.String"),
            ),
            data = listOf(
                mapOf(
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
                    "binary_column" to "0x2a" + "0".repeat(508),
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
                ),
            ),
        )
    }
}
