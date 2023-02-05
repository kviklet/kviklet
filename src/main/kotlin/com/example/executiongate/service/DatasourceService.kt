package com.example.executiongate.service

import com.example.executiongate.db.ConnectionEntity
import com.example.executiongate.db.ConnectionRepository
import com.zaxxer.hikari.HikariDataSource
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.jdbc.DataSourceBuilder
import org.springframework.stereotype.Service
import java.sql.SQLException

enum class DbType(val schema: String) {
    POSTGRESQL("postgresql"),
    MYSQL("mysql")
}

@Service
class DatasourceService(
    val connectionRepository: ConnectionRepository,
) {
    var logger: Logger = LoggerFactory.getLogger(DatasourceService::class.java)

    fun createConnection(
        name: String,
        uri: String,
        username: String,
        password: String,
    ): Boolean {
        val entity = ConnectionEntity(name = name, uri = uri, username = username, password = password)
        val savedEntity = connectionRepository.save(entity)
        logger.info("Created $savedEntity")

        return true
    }

    fun testConnection(
        type: DbType,
        hostname: String,
        port: Int,
        path: String,
        username: String,
        password: String
    ): Boolean {
        val dataSource: HikariDataSource = DataSourceBuilder
            .create()
            .url("jdbc:${type.schema}://$hostname:$port/$path")
            .username(username)
            .password(password)
            .type(HikariDataSource::class.java).build()

        dataSource.maximumPoolSize = 1

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

    fun listConnections() = connectionRepository.findAll().mapNotNull { it?.toDto() }
}
