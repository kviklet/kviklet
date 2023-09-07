package com.example.executiongate.executor

import com.example.executiongate.service.ColumnInfo
import com.example.executiongate.service.ErrorQueryResult
import com.example.executiongate.service.ExecutorService
import com.example.executiongate.service.RecordsQueryResult
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.testcontainers.containers.JdbcDatabaseContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

@SpringBootTest
class PostgresExecutorTest(
    @Autowired override val executorService: ExecutorService,
) : AbstractExecutorTest(
    executorService = executorService,
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
            ErrorQueryResult(0, "ERROR: relation \"foo.non_existent_table\" does not exist\n  Position: 15")

        executeQuery("FOOBAR") shouldBe
            ErrorQueryResult(0, "ERROR: syntax error at or near \"FOOBAR\"\n  Position: 1")
    }

    @Test
    override fun testConnectionError() {
        executeQuery("SELECT 1;", username = "root", password = "foo") shouldBe
            ErrorQueryResult(0, "FATAL: password authentication failed for user \"root\"")
    }
}
