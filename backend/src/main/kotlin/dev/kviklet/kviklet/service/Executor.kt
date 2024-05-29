package dev.kviklet.kviklet.service

import com.zaxxer.hikari.HikariDataSource
import dev.kviklet.kviklet.security.Resource
import dev.kviklet.kviklet.security.SecuredDomainObject
import dev.kviklet.kviklet.service.dto.ErrorResultLog
import dev.kviklet.kviklet.service.dto.ExecutionRequestId
import dev.kviklet.kviklet.service.dto.QueryResultLog
import dev.kviklet.kviklet.service.dto.ResultLog
import dev.kviklet.kviklet.service.dto.UpdateResultLog
import org.springframework.boot.jdbc.DataSourceBuilder
import org.springframework.stereotype.Service
import java.sql.ResultSet
import java.sql.SQLException
import java.util.*

sealed class QueryResult(open val executionRequestId: ExecutionRequestId) : SecuredDomainObject {
    override fun getId() = executionRequestId.toString()
    override fun getDomainObjectType() = Resource.EXECUTION_REQUEST
    override fun getRelated(resource: Resource) = null

    abstract fun toResultLog(): ResultLog
}

data class RecordsQueryResult(
    val columns: List<ColumnInfo>,
    val data: List<Map<String, String>>,
    override val executionRequestId: ExecutionRequestId,
) : QueryResult(executionRequestId) {
    override fun toResultLog(): QueryResultLog {
        return QueryResultLog(
            columnCount = columns.size,
            rowCount = data.size,
        )
    }
}

data class UpdateQueryResult(
    val rowsUpdated: Int,
    override val executionRequestId: ExecutionRequestId,
) : QueryResult(executionRequestId) {

    override fun toResultLog(): UpdateResultLog {
        return UpdateResultLog(
            rowsUpdated = rowsUpdated,
        )
    }
}

data class ErrorQueryResult(
    val errorCode: Int,
    val message: String?,
    override val executionRequestId: ExecutionRequestId,
) : QueryResult(executionRequestId) {
    override fun toResultLog(): ResultLog {
        return ErrorResultLog(
            errorCode = errorCode,
            message = message ?: "",
        )
    }
}

data class ColumnInfo(
    val label: String,
    // "int4", "text", "timestamptz"
    val typeName: String,
    // "java.lang.Integer", "java.lang.String", "java.sql.Timestamp"
    val typeClass: String,
)

@Service
class Executor() {

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
                    if (MSSQLexplain) {
                        statement.execute("SET SHOWPLAN_TEXT ON")
                    }
                    var hasResults = statement.execute(query)
                    val queryResults = mutableListOf<QueryResult>()

                    while (hasResults || statement.updateCount != -1) {
                        statement.resultSet?.use { resultSet ->
                            queryResults.add(createRecordsQueryResult(executionRequestId, resultSet))
                        } ?: queryResults.add(UpdateQueryResult(statement.updateCount, executionRequestId))

                        hasResults = statement.moreResults
                    }
                    return queryResults
                }
            } catch (e: SQLException) {
                var message = e.message
                // adding all the cause messages to the original message as well
                var cause = e.cause
                while (cause != null) {
                    message += "--> ${cause.javaClass}: ${cause.message} "
                    cause = cause.cause
                }
                return listOf(ErrorQueryResult(e.errorCode, message, executionRequestId))
            }
        }
    }

    fun executeAndStreamDbResponse(
        executionRequestId: ExecutionRequestId,
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

    private fun createRecordsQueryResult(
        executionRequestId: ExecutionRequestId,
        resultSet: ResultSet,
    ): RecordsQueryResult {
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
            executionRequestId,
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
