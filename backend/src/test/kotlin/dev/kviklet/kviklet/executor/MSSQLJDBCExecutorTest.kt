package dev.kviklet.kviklet.executor

import dev.kviklet.kviklet.service.ColumnInfo
import dev.kviklet.kviklet.service.JDBCExecutor
import dev.kviklet.kviklet.service.dto.ErrorQueryResult
import dev.kviklet.kviklet.service.dto.RecordsQueryResult
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.kotest.matchers.types.instanceOf
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.containers.JdbcDatabaseContainer
import org.testcontainers.containers.MSSQLServerContainer
import org.testcontainers.utility.DockerImageName

@SpringBootTest
@ActiveProfiles("test")
class MSSQLJDBCExecutorTest(@Autowired override val JDBCExecutorService: JDBCExecutor) :
    AbstractJDBCExecutorTest(
        JDBCExecutorService = JDBCExecutorService,
    ) {

    companion object {
        val db: MSSQLServerContainer<*> = MSSQLServerContainer(
            DockerImageName.parse("mcr.microsoft.com/mssql/server:2022-latest"),
        )
            .acceptLicense()

        init {
            db.start()
        }
    }

    override fun getDb(): JdbcDatabaseContainer<*> = db

    override val initScript: String = "mssql_init.sql"

    @Test
    override fun testSelectSimple() {
        executeQuery("SELECT 1 as col1, '2' as col2;") shouldBe RecordsQueryResult(
            columns = listOf(
                ColumnInfo("col1", "int", "java.lang.Integer"),
                ColumnInfo("col2", "varchar", "java.lang.String"),
            ),
            data = listOf(mapOf("col1" to "1", "col2" to "2")),
        )
    }

    @Test
    override fun testDatabaseError() {
        executeQuery("SELECT * FROM foo.non_existent_table;") shouldBe
            ErrorQueryResult(
                208,
                "Invalid object name 'foo.non_existent_table'.",
            )

        executeQuery("FOOBAR") shouldBe
            ErrorQueryResult(
                2812,
                "Could not find stored procedure 'FOOBAR'.",
            )
    }

    @Test
    override fun testConnectionError() {
        val result = executeQuery("SELECT 1;", username = "root", password = "foo")
        result shouldBe instanceOf<ErrorQueryResult>()
        result as ErrorQueryResult
        result.errorCode shouldBe 18456
        result.message shouldStartWith "Login failed for user 'root'."
    }

    @Test
    fun testSelectAllDataytpes() {
        val queryResult = executeQuery("SELECT * FROM foo.all_datatypes;")
        queryResult shouldBe RecordsQueryResult(
            columns = listOf(
                ColumnInfo(label = "int_column", typeName = "int", typeClass = "java.lang.Integer"),
                ColumnInfo(label = "tinyint_column", typeName = "tinyint", typeClass = "java.lang.Short"),
                ColumnInfo(label = "smallint_column", typeName = "smallint", typeClass = "java.lang.Short"),
                ColumnInfo(label = "mediumint_column", typeName = "int", typeClass = "java.lang.Integer"),
                ColumnInfo(label = "bigint_column", typeName = "bigint", typeClass = "java.lang.Long"),
                ColumnInfo(label = "float_column", typeName = "float", typeClass = "java.lang.Double"),
                ColumnInfo(label = "double_column", typeName = "float", typeClass = "java.lang.Double"),
                ColumnInfo(label = "decimal_column", typeName = "decimal", typeClass = "java.math.BigDecimal"),
                ColumnInfo(label = "date_column", typeName = "date", typeClass = "java.sql.Date"),
                ColumnInfo(label = "datetime_column", typeName = "datetime2", typeClass = "java.sql.Timestamp"),
                ColumnInfo(
                    label = "timestamp_column",
                    typeName = "datetime2",
                    typeClass = "java.sql.Timestamp",
                ),
                ColumnInfo(label = "time_column", typeName = "time", typeClass = "java.sql.Time"),
                ColumnInfo(label = "year_column", typeName = "smallint", typeClass = "java.lang.Short"),
                ColumnInfo(label = "char_column", typeName = "char", typeClass = "java.lang.String"),
                ColumnInfo(label = "varchar_column", typeName = "varchar", typeClass = "java.lang.String"),
                ColumnInfo(label = "binary_column", typeName = "binary", typeClass = "[B"),
                ColumnInfo(label = "varbinary_column", typeName = "varbinary", typeClass = "[B"),
                ColumnInfo(label = "tinyblob_column", typeName = "varbinary", typeClass = "[B"),
                ColumnInfo(label = "blob_column", typeName = "varbinary", typeClass = "[B"),
                ColumnInfo(label = "mediumblob_column", typeName = "varbinary", typeClass = "[B"),
                ColumnInfo(label = "longblob_column", typeName = "varbinary", typeClass = "[B"),
                ColumnInfo(
                    label = "tinytext_column",
                    typeName = "varchar",
                    typeClass = "java.lang.String",
                ),
                ColumnInfo(label = "text_column", typeName = "varchar", typeClass = "java.lang.String"),
                ColumnInfo(
                    label = "mediumtext_column",
                    typeName = "varchar",
                    typeClass = "java.lang.String",
                ),
                ColumnInfo(
                    label = "longtext_column",
                    typeName = "varchar",
                    typeClass = "java.lang.String",
                ),
                ColumnInfo(label = "enum_column", typeName = "varchar", typeClass = "java.lang.String"),
                ColumnInfo(label = "set_column", typeName = "varchar", typeClass = "java.lang.String"),
                ColumnInfo(label = "bit_column", typeName = "bit", typeClass = "java.lang.Boolean"),
                ColumnInfo(label = "bool_column", typeName = "bit", typeClass = "java.lang.Boolean"),
                ColumnInfo(label = "json_column", typeName = "nvarchar", typeClass = "java.lang.String"),
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
                    "datetime_column" to "2023-01-01 00:00:00.000000",
                    "timestamp_column" to "2023-01-01 00:00:00.000000",
                    "time_column" to "00:00:00.000000",
                    "year_column" to "2023",
                    "char_column" to "char",
                    "varchar_column" to "varchar",
                    "binary_column" to "",
                    "varbinary_column" to "",
                    "tinyblob_column" to "",
                    "blob_column" to "",
                    "mediumblob_column" to "",
                    "longblob_column" to "",
                    "tinytext_column" to "tinytext",
                    "text_column" to "text",
                    "mediumtext_column" to "mediumtext",
                    "longtext_column" to "longtext",
                    "enum_column" to "option1",
                    "set_column" to "option1",
                    "bit_column" to "1",
                    "bool_column" to "1",
                    "json_column" to "{\"key\": \"value\"}",
                ),
            ),
        )
    }
}
