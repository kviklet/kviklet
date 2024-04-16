package dev.kviklet.kviklet.service

import dev.kviklet.kviklet.controller.UpdateDataSourceConnectionRequest
import dev.kviklet.kviklet.db.ConnectionAdapter
import dev.kviklet.kviklet.db.ReviewConfig
import dev.kviklet.kviklet.security.Permission
import dev.kviklet.kviklet.security.Policy
import dev.kviklet.kviklet.service.dto.AuthenticationType
import dev.kviklet.kviklet.service.dto.Connection
import dev.kviklet.kviklet.service.dto.ConnectionId
import dev.kviklet.kviklet.service.dto.DatasourceConnection
import dev.kviklet.kviklet.service.dto.DatasourceType
import dev.kviklet.kviklet.service.dto.KubernetesConnection
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service

class EntityNotFound(override val message: String, val detail: String) : Exception(message)

@Service
class ConnectionService(
    val connectionAdapter: ConnectionAdapter,
) {

    @Transactional
    @Policy(Permission.DATASOURCE_CONNECTION_GET)
    fun listConnections(): List<Connection> {
        return connectionAdapter.listConnections()
    }

    @Transactional
    @Policy(Permission.DATASOURCE_CONNECTION_EDIT)
    fun updateConnection(connectionId: ConnectionId, request: UpdateDataSourceConnectionRequest): Connection {
        val connection = connectionAdapter.getConnection(
            connectionId,
        )

        when (connection) {
            is DatasourceConnection -> {
                return connectionAdapter.updateDatasourceConnection(
                    connectionId,
                    request.displayName ?: connection.displayName,
                    request.databaseName ?: connection.databaseName,
                    request.username ?: connection.username,
                    request.password ?: connection.password,
                    request.description ?: connection.description,
                    request.reviewConfig?.let {
                        ReviewConfig(
                            it.numTotalRequired,
                        )
                    } ?: connection.reviewConfig,
                )
            }
            is KubernetesConnection -> {
                return connectionAdapter.updateKubernetesConnection(
                    connectionId,
                    request.displayName ?: connection.displayName,
                    request.description ?: connection.description,
                    request.reviewConfig?.let {
                        ReviewConfig(
                            it.numTotalRequired,
                        )
                    } ?: connection.reviewConfig,
                )
            }
            else -> throw EntityNotFound("Connection not found", "Connection with id $connectionId not found")
        }
    }

    @Transactional
    @Policy(Permission.DATASOURCE_CONNECTION_CREATE)
    fun createDatasourceConnection(
        connectionId: ConnectionId,
        displayName: String,
        databaseName: String?,
        username: String,
        password: String,
        description: String,
        reviewsRequired: Int,
        port: Int,
        hostname: String,
        type: DatasourceType,
    ): Connection {
        return connectionAdapter.createDatasourceConnection(
            connectionId,
            displayName,
            AuthenticationType.USER_PASSWORD,
            databaseName,
            username,
            password,
            description,
            ReviewConfig(
                numTotalRequired = reviewsRequired,
            ),
            port,
            hostname,
            type,
        )
    }

    @Transactional
    @Policy(Permission.DATASOURCE_CONNECTION_CREATE)
    fun createKubernetesConnection(
        connectionId: ConnectionId,
        displayName: String,
        description: String,
        reviewsRequired: Int,
    ): Connection {
        return connectionAdapter.createKubernetesConnection(
            connectionId,
            displayName,
            description,
            ReviewConfig(
                numTotalRequired = reviewsRequired,
            ),

        )
    }

    @Transactional
    @Policy(Permission.DATASOURCE_CONNECTION_EDIT)
    fun deleteDatasourceConnection(connectionId: ConnectionId) {
        connectionAdapter.deleteConnection(connectionId)
    }

    @Transactional
    @Policy(Permission.DATASOURCE_CONNECTION_GET)
    fun getDatasourceConnection(connectionId: ConnectionId): Connection {
        return connectionAdapter.getConnection(
            connectionId = connectionId,
        )
    }
}
