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
class JDBCExecutor(private val rdsIamTokenProvider: RdsIamTokenProvider = AwsRdsIamTokenProvider()) {

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
        maxRowsToStore: Int? = null,
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
                            queryResults.add(createRecordsQueryResult(resultSet, maxRowsToStore))
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

    fun executeDryRun(
        executionRequestId: ExecutionRequestId,
        connectionString: String,
        authenticationDetails: AuthenticationDetails,
        query: String,
        isMSSQL: Boolean = false,
        maxRowsToStore: Int = 0,
    ): List<QueryResult> {
        createConnection(connectionString, authenticationDetails).use { dataSource: HikariDataSource ->
            try {
                dataSource.connection.use { connection ->
                    // Disable auto-commit to start a transaction
                    connection.autoCommit = false

                    connection.createStatement().use { statement ->
                        val previousValues = activeStatements.putIfAbsent(executionRequestId.toString(), statement)
                        if (previousValues != null) {
                            throw IllegalStateException("Request $executionRequestId already is executing a query")
                        }

                        try {
                            // For SQL Server, enable implicit transactions
                            if (isMSSQL) {
                                statement.execute("SET IMPLICIT_TRANSACTIONS ON")
                            }

                            var hasResults = statement.execute(query)
                            val queryResults = mutableListOf<QueryResult>()

                            while (hasResults || statement.updateCount != -1) {
                                statement.resultSet?.use { resultSet ->
                                    queryResults.add(createRecordsQueryResult(resultSet, maxRowsToStore))
                                } ?: queryResults.add(UpdateQueryResult(statement.updateCount))

                                hasResults = statement.moreResults
                            }

                            return queryResults
                        } finally {
                            // Always rollback the transaction to undo any changes
                            try {
                                connection.rollback()
                            } catch (e: SQLException) {
                                // Log rollback failure but don't throw - we still want to return results
                                val logger = LoggerFactory.getLogger(javaClass)
                                logger.warn("Failed to rollback dry run transaction", e)
                            }
                        }
                    }
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

    private fun createRecordsQueryResult(resultSet: ResultSet, maxRowsToStore: Int? = null): RecordsQueryResult {
        val results: MutableList<Map<String, String>> = mutableListOf()

        val metadata = resultSet.metaData
        // Labels are de-duplicated ("id", "id (2)") because rows are keyed by label — duplicate
        // labels (e.g. SELECT a.id, b.id) would otherwise collapse into one column.
        val usedLabels = mutableSetOf<String>()
        val columns = (1..metadata.columnCount).map { i ->
            var label = metadata.getColumnLabel(i)
            var suffix = 2
            while (!usedLabels.add(label)) {
                label = "${metadata.getColumnLabel(i)} ($suffix)"
                suffix++
            }
            ColumnInfo(
                label = label,
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

        val storedRows = if (maxRowsToStore != null) {
            results.take(maxRowsToStore)
        } else {
            null
        }

        val storedRowCount = storedRows?.size

        return RecordsQueryResult(
            columns = columns,
            data = results,
            storedRows = storedRows,
            storedRowCount = storedRowCount,
        )
    }

    private fun iterateResultSet(
        resultSet: ResultSet,
        columns: List<ColumnInfo>,
        forEachRow: (Map<String, String>) -> Unit,
    ) {
        while (resultSet.next()) {
            forEachRow.invoke(
                columns.withIndex().associate { (index, column) ->
                    // Read by position: with duplicate labels in the query, label-based access
                    // returns the first matching column's value for all of them.
                    val value = if (column.typeClass == "[B") {
                        resultSet.getBytes(index + 1)?.let { "0x" + HexFormat.of().formatHex(it) } ?: ""
                    } else {
                        resultSet.getString(index + 1)
                    }
                    Pair(column.label, value)
                },
            )
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
        AwsIamDataSource(rdsIamTokenProvider, auth.username, auth.roleArn).apply {
            jdbcUrl = url
            this.username = auth.username
            maximumPoolSize = 1
        }
}
