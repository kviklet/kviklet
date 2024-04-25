package dev.kviklet.kviklet.service

import com.zaxxer.hikari.HikariDataSource
import dev.kviklet.kviklet.security.Resource
import dev.kviklet.kviklet.security.SecuredDomainObject
import dev.kviklet.kviklet.service.dto.ExecutionRequestId
import org.springframework.boot.jdbc.DataSourceBuilder
import org.springframework.stereotype.Service
import java.sql.ResultSet
import java.sql.SQLException
import java.util.*

sealed class QueryResult(open val executionRequestId: ExecutionRequestId) : SecuredDomainObject {
    override fun getId() = executionRequestId.toString()
    override fun getDomainObjectType() = Resource.EXECUTION_REQUEST
    override fun getRelated(resource: Resource) = null
}

data class RecordsQueryResult(
    val columns: List<ColumnInfo>,
    val data: List<Map<String, String>>,
    override val executionRequestId: ExecutionRequestId,
) : QueryResult(executionRequestId)

data class UpdateQueryResult(
    val rowsUpdated: Int,
    override val executionRequestId: ExecutionRequestId,
) : QueryResult(executionRequestId)

data class ErrorQueryResult(
    val errorCode: Int,
    val message: String?,
    override val executionRequestId: ExecutionRequestId,
) : QueryResult(executionRequestId)

data class ColumnInfo(
    val label: String,
    // "int4", "text", "timestamptz"
    val typeName: String,
    // "java.lang.Integer", "java.lang.String", "java.sql.Timestamp"
    val typeClass: String,
)

@Service
class ExecutorService {

    fun execute(
        executionRequestId: ExecutionRequestId,
        connectionString: String,
        username: String,
        password: String,
        query: String,
    ): List<QueryResult> {
        createConnection(connectionString, username, password).use { dataSource: HikariDataSource ->
            try {
                dataSource.connection.createStatement().use { statement ->
                    var hasResults = statement.execute(query)
                    val queryResults = mutableListOf<QueryResult>()

                    while (hasResults || statement.updateCount != -1) {
                        statement.resultSet?.use { resultSet ->
                            queryResults.add(handleResultSet(resultSet, executionRequestId))
                        } ?: queryResults.add(UpdateQueryResult(statement.updateCount, executionRequestId))

                        hasResults = statement.moreResults
                    }
                    return queryResults
                }
            } catch (e: SQLException) {
                return listOf(ErrorQueryResult(e.errorCode, e.message, executionRequestId))
            }
        }
    }

    private fun handleResultSet(resultSet: ResultSet, executionRequestId: ExecutionRequestId): QueryResult {
        val metadata = resultSet.metaData
        val columns = (1..metadata.columnCount).map { i ->
            ColumnInfo(
                label = metadata.getColumnLabel(i),
                typeName = metadata.getColumnTypeName(i),
                typeClass = metadata.getColumnClassName(i),
            )
        }

        val results: MutableList<Map<String, String>> = mutableListOf()
        while (resultSet.next()) {
            results.add(
                columns.associate {
                    if (it.typeClass == "[B") {
                        Pair(it.label, resultSet.getBytes(it.label)?.let { "0x" + HexFormat.of().formatHex(it) } ?: "")
                    } else {
                        Pair(it.label, resultSet.getString(it.label))
                    }
                },
            )
        }
        return RecordsQueryResult(
            columns = columns,
            data = results,
            executionRequestId,
        )
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
