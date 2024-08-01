package dev.kviklet.kviklet.service

import com.zaxxer.hikari.HikariDataSource
import dev.kviklet.kviklet.service.dto.ErrorQueryResult
import dev.kviklet.kviklet.service.dto.ExecutionRequestId
import dev.kviklet.kviklet.service.dto.QueryResult
import dev.kviklet.kviklet.service.dto.RecordsQueryResult
import dev.kviklet.kviklet.service.dto.UpdateQueryResult
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
class JDBCExecutor {

    private val activeStatements = ConcurrentHashMap<String, Statement>()

    companion object {
        val DEFAULT_POSTGRES_DATABASES = listOf("template0", "template1")
    }

    fun execute(
        executionRequestId: ExecutionRequestId,
        connectionString: String,
        username: String,
        password: String,
        query: String,
        MSSQLexplain: Boolean = false,
    ): List<QueryResult> {
        createConnection(connectionString, username, password).use { dataSource: HikariDataSource ->
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
                return listOf(sqlExecptionToResult(e))
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

    private fun sqlExecptionToResult(e: SQLException): ErrorQueryResult {
        var message = e.message ?: ""
        // adding all the cause messages to the original message as well
        var cause = e.cause
        while (cause != null) {
            message += "--> ${cause.javaClass}: ${cause.message} "
            cause = cause.cause
        }
        return ErrorQueryResult(e.errorCode, message)
    }

    fun testCredentials(connectionString: String, username: String, password: String): TestCredentialsResult {
        try {
            val datasource = createConnection(connectionString, username, password)
            datasource.connection.use { connection ->
                connection.isValid(5)
            }
            return TestCredentialsResult(success = true, message = "Connection successful")
        } catch (e: SQLException) {
            val result = sqlExecptionToResult(e)
            return TestCredentialsResult(success = false, message = result.message)
        }
    }

    fun getAccessibleDatabasesPostgres(connectionString: String, username: String, password: String): List<String> {
        createConnection(connectionString, username, password).use { dataSource: HikariDataSource ->
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
            } catch (e: SQLException) {
                return emptyList()
            }
        }
    }

    fun executeAndStreamDbResponse(
        connectionString: String,
        username: String,
        password: String,
        query: String,
        callback: (List<String>) -> Unit,
    ) {
        createConnection(connectionString, username, password).use { dataSource: HikariDataSource ->
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
                throw IllegalStateException("Error executing query", e)
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

    private fun createConnection(url: String, username: String, password: String): HikariDataSource {
        val dataSource: HikariDataSource = DataSourceBuilder
            .create()
            .url(url)
            .username(username)
            .password(password)
            .type(HikariDataSource::class.java)
            .build()

        dataSource.maximumPoolSize = 1
        return dataSource
    }
}
