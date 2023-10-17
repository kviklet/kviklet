package com.example.executiongate.service

import com.example.executiongate.security.Permission
import com.example.executiongate.security.Resource
import com.example.executiongate.security.SecuredDomainObject
import com.example.executiongate.security.UserDetailsWithId
import com.example.executiongate.service.dto.ExecutionRequestId
import com.zaxxer.hikari.HikariDataSource
import org.springframework.boot.jdbc.DataSourceBuilder
import org.springframework.stereotype.Service
import java.sql.SQLException
import java.sql.Statement
import java.util.HexFormat

sealed class QueryResult(open val executionRequestId: ExecutionRequestId) : SecuredDomainObject {
    override fun getId() = executionRequestId.toString()
    override fun getDomainObjectType() = Resource.EXECUTION_REQUEST
    override fun getRelated(resource: Resource) = null
    override fun auth(permission: Permission, userDetails: UserDetailsWithId): Boolean = true
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
    ): QueryResult {
        createConnection(connectionString, username, password).use { dataSource: HikariDataSource ->
            try {
                dataSource.connection.createStatement().use { statement ->
                    val execute = statement.execute(query)
                    if (execute) {
                        // Execution returned a ResultSet
                        return handleResultSet(statement, executionRequestId)
                    } else {
                        // Execution returned an update count or empty result
                        return UpdateQueryResult(statement.updateCount, executionRequestId)
                    }
                }
            } catch (e: SQLException) {
                return ErrorQueryResult(e.errorCode, e.message, executionRequestId)
            }
        }
    }

    private fun handleResultSet(statement: Statement, executionRequestId: ExecutionRequestId): QueryResult {
        val resultSet = statement.resultSet
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
                        Pair(it.label, "0x" + HexFormat.of().formatHex(resultSet.getBytes(it.label)))
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
