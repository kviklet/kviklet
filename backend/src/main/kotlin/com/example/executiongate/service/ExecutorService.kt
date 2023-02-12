package com.example.executiongate.service

import com.zaxxer.hikari.HikariDataSource
import org.springframework.boot.jdbc.DataSourceBuilder
import java.sql.SQLException

data class QueryResult(
    val columns: List<ColumnInfo>,
    val data: List<List<String>>,
)

data class ColumnInfo(
    val label: String,
    val typeName: String, // "int4", "text", "timestamptz"
    val typeClass: String, // "java.lang.Integer", "java.lang.String", "java.sql.Timestamp"
)

class ExecutorService {

    fun testConnection(
        url: String
    ): Boolean {
        val dataSource: HikariDataSource = createConnection(url)

        return try {
            val isValid = dataSource.connection.isValid(10)
            dataSource.catalog
            isValid
        } catch (e: SQLException) {
            false
        } finally {
            dataSource.close()
        }
    }

    fun execute(url: String, statement: String): QueryResult {
        val dataSource: HikariDataSource = createConnection(url)

        try {
            val connection = dataSource.connection
            val query = connection.createStatement()
            val execution = query.execute(statement)
            val resultSet = query.resultSet

            val metadata = resultSet.metaData
            val columns = (1..metadata.columnCount).map { i ->
                ColumnInfo(
                    label=metadata.getColumnLabel(i),
                    typeName = metadata.getColumnTypeName(i),
                    typeClass = metadata.getColumnClassName(i),
                )
            }

            val results: MutableList<List<String>> = mutableListOf<List<String>>()
            while (resultSet.next()) {
                results.add(
                    (1..columns.size).map { i ->
                        resultSet.getString(i)
                    }
                )
            }
            connection.close()
            query.close()
            resultSet.close()

            return QueryResult(
                columns = columns,
                data = results,
            )
        } finally {
            dataSource.close()
        }

    }

    private fun createConnection(url: String): HikariDataSource {
        val dataSource: HikariDataSource = DataSourceBuilder
            .create()
            .url("jdbc:$url")
            .username("root")
            .password("root")
            .type(HikariDataSource::class.java)
            .build()

        dataSource.maximumPoolSize = 1
        return dataSource
    }

}