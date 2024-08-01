package dev.kviklet.kviklet.executor

import dev.kviklet.kviklet.service.ColumnInfo
import dev.kviklet.kviklet.service.JDBCExecutor
import dev.kviklet.kviklet.service.dto.ErrorQueryResult
import dev.kviklet.kviklet.service.dto.ExecutionRequestId
import dev.kviklet.kviklet.service.dto.RecordsQueryResult
import dev.kviklet.kviklet.service.dto.UpdateQueryResult
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.io.FileUrlResource
import org.springframework.jdbc.datasource.init.ScriptUtils
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.containers.JdbcDatabaseContainer

@SpringBootTest
@ActiveProfiles("test")
abstract class AbstractJDBCExecutorTest(@Autowired val JDBCExecutorService: JDBCExecutor) {

    val executionRequestId = ExecutionRequestId("5Wb9WJxCxej5W1Rt6cTBV4")

    abstract val initScript: String

    abstract fun getDb(): JdbcDatabaseContainer<*>

    fun executeQuery(
        query: String,
        url: String = getDb().jdbcUrl,
        username: String = getDb().username,
        password: String = getDb().password,
    ) = JDBCExecutorService.execute(executionRequestId, url, username, password, query).get(0)

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
            ErrorQueryResult(
                1064,
                "You have an error in your SQL syntax; check the manual that " +
                    "corresponds to your MySQL server version for the right syntax to use near 'FOOBAR' at line 1",
            )
    }

    @Test
    fun testConnectionError() {
        executeQuery("SELECT 1;", username = "root", password = "foo") shouldBe
            ErrorQueryResult(
                1045,
                "Access denied for user 'root'@'172.17.0.1' (using password: YES)",
            )
    }

    @Test
    fun testDdlQuery() {
        executeQuery("CREATE TABLE foo.temp (col INT);") shouldBe UpdateQueryResult(0)
    }

    @Test
    fun testInsert() {
        executeQuery("INSERT INTO foo.simple_table VALUES (1, 'foo');") shouldBe UpdateQueryResult(
            1,
        )
    }

    @Test
    fun testUpdate() {
        // col1=1 exists, 1 row is updated
        executeQuery("UPDATE foo.simple_table SET col2='foobar' WHERE col1 = 1;") shouldBe UpdateQueryResult(
            1,
        )
        // col1=3 does not exist, so nothing is updated:
        executeQuery("UPDATE foo.simple_table SET col2='foobar' WHERE col1 = 3;") shouldBe UpdateQueryResult(
            0,
        )
    }

    @Test
    fun testSelectSimple() {
        executeQuery("SELECT 1 as col1, '2' as col2;") shouldBe RecordsQueryResult(
            columns = listOf(
                ColumnInfo("col1", "BIGINT", "java.lang.Long"),
                ColumnInfo("col2", "VARCHAR", "java.lang.String"),
            ),
            data = listOf(mapOf("col1" to "1", "col2" to "2")),
        )
    }
}
