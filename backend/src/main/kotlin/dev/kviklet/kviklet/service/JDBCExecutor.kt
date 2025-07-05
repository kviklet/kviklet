package dev.kviklet.kviklet.service

import com.zaxxer.hikari.HikariDataSource
import dev.kviklet.kviklet.service.dto.AuthenticationDetails
import dev.kviklet.kviklet.service.dto.ErrorQueryResult
import dev.kviklet.kviklet.service.dto.ExecutionRequestId
import dev.kviklet.kviklet.service.dto.QueryResult
import dev.kviklet.kviklet.service.dto.RecordsQueryResult
import dev.kviklet.kviklet.service.dto.UpdateQueryResult
import org.slf4j.LoggerFactory
import org.springframework.boot.jdbc.DataSourceBuilder
import org.springframework.stereotype.Service
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.rds.RdsUtilities
import software.amazon.awssdk.services.sts.StsClient
import software.amazon.awssdk.services.sts.auth.StsAssumeRoleCredentialsProvider
import java.net.URI
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Statement
import java.util.*
import java.util.concurrent.ConcurrentHashMap

data class TestCredentialsResult(val success: Boolean, val message: String)

data class ColumnInfo(
    val label: String,
    // "int4", "text", "timestamptz"
    val typeName: String,
    // "java.lang.Integer", "java.lang.String", "java.sql.Timestamp"
    val typeClass: String,
)

@Service
class JDBCExecutor {

    private val activeStatements = ConcurrentHashMap<String, Statement>()

    companion object {
        val DEFAULT_POSTGRES_DATABASES = listOf("template0", "template1")
    }

    fun execute(
        executionRequestId: ExecutionRequestId,
        connectionString: String,
        authenticationDetails: AuthenticationDetails,
        query: String,
        MSSQLexplain: Boolean = false,
    ): List<QueryResult> {
        createConnection(connectionString, authenticationDetails).use { dataSource: HikariDataSource ->
            try {
                dataSource.connection.createStatement().use { statement ->
                    val previousValues = activeStatements.putIfAbsent(executionRequestId.toString(), statement)
                    if (previousValues != null) {
                        throw IllegalStateException("Request $executionRequestId already is executing a query")
                    }
                    if (MSSQLexplain) {
                        statement.execute("SET SHOWPLAN_TEXT ON")
                    }
                    var hasResults = statement.execute(query)
                    val queryResults = mutableListOf<QueryResult>()

                    while (hasResults || statement.updateCount != -1) {
                        statement.resultSet?.use { resultSet ->
                            queryResults.add(createRecordsQueryResult(resultSet))
                        } ?: queryResults.add(UpdateQueryResult(statement.updateCount))

                        hasResults = statement.moreResults
                    }
                    return queryResults
                }
            } catch (e: SQLException) {
                return listOf(sqlExceptionToResult(e))
            } finally {
                activeStatements.remove(executionRequestId.toString())
            }
        }
    }

    fun cancelQuery(executionRequestId: ExecutionRequestId) {
        try {
            activeStatements[executionRequestId.toString()]?.cancel()
        } catch (e: SQLException) {
            throw IllegalStateException("Error cancelling Query", e)
        }
    }

    private fun sqlExceptionToResult(e: SQLException): ErrorQueryResult {
        var message = e.message ?: ""
        // adding all the cause messages to the original message as well
        var cause = e.cause
        while (cause != null) {
            message += "--> ${cause.javaClass}: ${cause.message} "
            cause = cause.cause
        }
        return ErrorQueryResult(e.errorCode, message)
    }

    fun testCredentials(connectionString: String, authenticationDetails: AuthenticationDetails): TestCredentialsResult {
        try {
            val datasource = createConnection(connectionString, authenticationDetails)
            datasource.connection.use { connection ->
                connection.isValid(5)
            }
            return TestCredentialsResult(success = true, message = "Connection successful")
        } catch (e: SQLException) {
            val result = sqlExceptionToResult(e)
            return TestCredentialsResult(success = false, message = result.message)
        }
    }

    fun getAccessibleDatabasesPostgres(
        connectionString: String,
        authenticationDetails: AuthenticationDetails,
    ): List<String> {
        createConnection(connectionString, authenticationDetails).use { dataSource: HikariDataSource ->
            try {
                dataSource.connection.createStatement().use { statement ->
                    val query = """
                    SELECT d.datname 
                    FROM pg_catalog.pg_database d
                    WHERE pg_catalog.has_database_privilege(current_user, d.datname, 'CONNECT')
                """

                    val resultSet = statement.executeQuery(query)
                    val accessibleDatabases = mutableListOf<String>()

                    while (resultSet.next()) {
                        val databaseName = resultSet.getString("datname")
                        if (!DEFAULT_POSTGRES_DATABASES.contains(databaseName)) {
                            accessibleDatabases.add(databaseName)
                        }
                    }

                    return accessibleDatabases
                }
            } catch (_: SQLException) {
                return emptyList()
            }
        }
    }

    fun executeAndStreamDbResponse(
        connectionString: String,
        authenticationDetails: AuthenticationDetails,
        query: String,
        callback: (List<String>) -> Unit,
    ) {
        createConnection(connectionString, authenticationDetails).use { dataSource: HikariDataSource ->
            try {
                dataSource.connection.createStatement().use { statement ->
                    val hasResults = statement.execute(query)
                    if (hasResults) {
                        statement.resultSet?.use { resultSet ->
                            streamResultSet(resultSet, callback)
                        } ?: throw IllegalStateException("Can't stream a non-Select statement")
                    } else {
                        throw IllegalStateException("Can't stream a non-Select statement")
                    }
                }
            } catch (e: SQLException) {
                throw IllegalStateException("Error executing query: ${e.message}", e)
            }
        }
    }

    private fun streamResultSet(resultSet: ResultSet, callback: (List<String>) -> Unit) {
        val metadata = resultSet.metaData
        val columns = (1..metadata.columnCount).map { i ->
            ColumnInfo(
                label = metadata.getColumnLabel(i),
                typeName = metadata.getColumnTypeName(i),
                typeClass = metadata.getColumnClassName(i),
            )
        }

        callback(columns.map { it.label })

        iterateResultSet(resultSet, columns, forEachRow = { resultMap ->
            callback(columns.map { resultMap[it.label] ?: "" })
        })
    }

    private fun createRecordsQueryResult(resultSet: ResultSet): RecordsQueryResult {
        val results: MutableList<Map<String, String>> = mutableListOf()

        val metadata = resultSet.metaData
        val columns = (1..metadata.columnCount).map { i ->
            ColumnInfo(
                label = metadata.getColumnLabel(i),
                typeName = metadata.getColumnTypeName(i),
                typeClass = metadata.getColumnClassName(i),
            )
        }

        iterateResultSet(
            resultSet,
            columns,
            forEachRow = { resultMap ->
                results.add(
                    resultMap,
                )
            },
        )
        return RecordsQueryResult(
            columns = columns,
            data = results,
        )
    }

    private fun iterateResultSet(
        resultSet: ResultSet,
        columns: List<ColumnInfo>,
        forEachRow: (Map<String, String>) -> Unit,
    ) {
        while (resultSet.next()) {
            forEachRow.invoke(
                columns.associate {
                    if (it.typeClass == "[B") {
                        Pair(it.label, resultSet.getBytes(it.label)?.let { "0x" + HexFormat.of().formatHex(it) } ?: "")
                    } else {
                        Pair(it.label, resultSet.getString(it.label))
                    }
                },
            )
        }
    }

    class AwsIamDataSource(private val username: String, private val roleArn: String? = null) : HikariDataSource() {
        private lateinit var rdsUtilities: RdsUtilities
        private lateinit var uri: URI
        private lateinit var region: Region
        private val logger = LoggerFactory.getLogger(javaClass)

        fun initialize() {
            uri = URI.create(jdbcUrl.removePrefix("jdbc:"))
            region = extractRegionFromHost(uri.host)

            rdsUtilities = RdsUtilities.builder()
                .region(region)
                .credentialsProvider(createCredentialsProvider())
                .build()
        }

        private fun createCredentialsProvider(): AwsCredentialsProvider = if (!roleArn.isNullOrEmpty()) {
            logger.info("Using IAM role {} for authentication", roleArn)
            StsAssumeRoleCredentialsProvider.builder()
                .asyncCredentialUpdateEnabled(true)
                .stsClient(StsClient.builder().region(region).build())
                .refreshRequest { r ->
                    r.roleArn(roleArn).roleSessionName("KvikletRdsIamSession").build()
                }
                .build()
        } else {
            logger.info("Using default credentials for authentication")
            DefaultCredentialsProvider.create()
        }

        private fun extractRegionFromHost(host: String): Region {
            // Expected format: <db-instance>.<region>.rds.amazonaws.com
            val parts = host.split(".")
            if (parts.size < 5 ||
                parts[parts.size - 3] != "rds" ||
                parts[parts.size - 2] != "amazonaws" ||
                parts[parts.size - 1] != "com"
            ) {
                throw IllegalArgumentException(
                    "Invalid RDS endpoint format. Expected: <db-instance>.<region>.rds.amazonaws.com",
                )
            }

            // The region is the second-to-last segment before "rds.amazonaws.com"
            val regionString = parts[parts.size - 4]

            return try {
                Region.of(regionString)
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("Invalid AWS region: $regionString", e)
            }
        }

        override fun getPassword(): String {
            val token = rdsUtilities.generateAuthenticationToken { builder ->
                builder.hostname(uri.host)
                    .port(uri.port)
                    .username(username)
            }
            return token
        }
    }

    fun createConnection(url: String, authenticationDetails: AuthenticationDetails): HikariDataSource =
        when (authenticationDetails) {
            is AuthenticationDetails.UserPassword -> createUserPasswordConnection(url, authenticationDetails)
            is AuthenticationDetails.AwsIam -> createAwsIamConnection(url, authenticationDetails)
        }

    private fun createUserPasswordConnection(url: String, auth: AuthenticationDetails.UserPassword): HikariDataSource =
        DataSourceBuilder.create()
            .url(url)
            .username(auth.username)
            .password(auth.password)
            .type(HikariDataSource::class.java)
            .build()
            .apply {
                maximumPoolSize = 1
            }

    private fun createAwsIamConnection(url: String, auth: AuthenticationDetails.AwsIam): HikariDataSource =
        AwsIamDataSource(auth.username, auth.roleArn).apply {
            jdbcUrl = url
            this.username = auth.username
            maximumPoolSize = 1

            // Token lifetime is 15 minutes, so set max lifetime to 14 minutes
            maxLifetime = 840000
            initialize()
        }
}
