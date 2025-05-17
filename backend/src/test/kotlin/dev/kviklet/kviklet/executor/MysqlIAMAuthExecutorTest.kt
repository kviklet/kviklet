package dev.kviklet.kviklet.executor

import dev.kviklet.kviklet.db.ReviewConfig
import dev.kviklet.kviklet.service.ColumnInfo
import dev.kviklet.kviklet.service.JDBCExecutor
import dev.kviklet.kviklet.service.dto.AuthenticationDetails
import dev.kviklet.kviklet.service.dto.AuthenticationType
import dev.kviklet.kviklet.service.dto.ConnectionId
import dev.kviklet.kviklet.service.dto.DatabaseProtocol
import dev.kviklet.kviklet.service.dto.DatasourceConnection
import dev.kviklet.kviklet.service.dto.DatasourceType
import dev.kviklet.kviklet.service.dto.ExecutionRequestId
import dev.kviklet.kviklet.service.dto.RecordsQueryResult
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles(value = ["local", "test"])
@Tag("aws-integration")
@EnabledIfEnvironmentVariable(named = "RUN_AWS_IAM_TESTS", matches = "true")
class MysqlIAMAuthExecutorTest(
    @Autowired val JDBCExecutorService: JDBCExecutor,
    @Value("\${aws.db.mysqlhost}") private val awsDbHost: String,
) {
    val executionRequestId = ExecutionRequestId("5Wb9WJxCxej5W1Rt6cTBV5")

    companion object {
        private const val AWS_DB_PORT = "3306"
        private const val AWS_DB_NAME = "testdb"
        private const val AWS_DB_USER = "iamdbuser"
    }

    private val jdbcUrl = "jdbc:mysql://$awsDbHost:$AWS_DB_PORT/$AWS_DB_NAME"

    private fun executeQueryWithIam(connectionString: String, query: String) = JDBCExecutorService.execute(
        executionRequestId = executionRequestId,
        connectionString = connectionString,
        authenticationDetails = AuthenticationDetails.AwsIam(
            username = AWS_DB_USER,
        ),
        query = query,
    ).first()

    @Test
    fun `test IAM authentication and simple query`() {
        val connection = DatasourceConnection(
            id = ConnectionId("aws-rds-prod-001"),
            displayName = "AWS RDS Production Database",
            description = "Production PostgreSQL database hosted on AWS RDS",
            reviewConfig = ReviewConfig(
                numTotalRequired = 0,
            ),
            maxExecutions = 10,
            databaseName = AWS_DB_NAME,
            authenticationType = AuthenticationType.AWS_IAM,
            auth = AuthenticationDetails.AwsIam(
                username = AWS_DB_USER,
            ),
            port = AWS_DB_PORT.toInt(),
            hostname = awsDbHost,
            type = DatasourceType.MYSQL,
            protocol = DatabaseProtocol.MYSQL,
            additionalOptions = "",
            dumpsEnabled = false,
            temporaryAccessEnabled = true,
            explainEnabled = false,
        )

        val result = executeQueryWithIam(connection.getConnectionString(), "SELECT 1 as col1, '2' as col2")

        result shouldBe RecordsQueryResult(
            columns = listOf(
                ColumnInfo("col1", "BIGINT", "java.lang.Long"),
                ColumnInfo("col2", "VARCHAR", "java.lang.String"),
            ),
            data = listOf(mapOf("col1" to "1", "col2" to "2")),
        )
    }
}
