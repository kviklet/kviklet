package com.example.executiongate.service

import com.zaxxer.hikari.HikariDataSource
import org.springframework.boot.jdbc.DataSourceBuilder
import org.springframework.stereotype.Service
import java.sql.SQLException
import java.sql.Statement
import java.util.HexFormat

sealed class QueryResult

data class RecordsQueryResult(
    val columns: List<ColumnInfo>,
    val data: List<Map<String, String>>,
) : QueryResult()

data class UpdateQueryResult(
    val rowsUpdated: Int,
) : QueryResult()

data class ErrorQueryResult(
    val errorCode: Int,
    val message: String?,
) : QueryResult()

data class ColumnInfo(
    val label: String,
    // "int4", "text", "timestamptz"
    val typeName: String,
    // "java.lang.Integer", "java.lang.String", "java.sql.Timestamp"
    val typeClass: String,
)

@Service
class ExecutorService {

    fun execute(connectionString: String, username: String, password: String, query: String): QueryResult {
        createConnection(connectionString, username, password).use { dataSource: HikariDataSource ->
            try {
                dataSource.connection.createStatement().use { statement ->
                    val execute = statement.execute(query)
                    if (execute) {
                        // Execution returned a ResultSet
                        return handleResultSet(statement)
                    } else {
                        // Execution returned an update count or empty result
                        return UpdateQueryResult(statement.updateCount)
                    }
                }
            } catch (e: SQLException) {
                return ErrorQueryResult(e.errorCode, e.message)
            }
        }
    }

    private fun handleResultSet(statement: Statement): QueryResult {
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
