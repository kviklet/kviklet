package dev.kviklet.kviklet.service

import dev.kviklet.kviklet.controller.UpdateConnectionRequest
import dev.kviklet.kviklet.controller.UpdateDatasourceConnectionRequest
import dev.kviklet.kviklet.controller.UpdateKubernetesConnectionRequest
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

    private fun updateDatasourceConnection(
        connectionId: ConnectionId,
        request: UpdateDatasourceConnectionRequest,
    ): Connection {
        val connection = connectionAdapter.getConnection(
            connectionId,
        ) as DatasourceConnection

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
            request.additionalJDBCOptions ?: connection.additionalJDBCOptions,
        )
    }

    private fun updateKubernetesConnection(
        connectionId: ConnectionId,
        request: UpdateKubernetesConnectionRequest,
    ): Connection {
        val connection = connectionAdapter.getConnection(
            connectionId,
        ) as KubernetesConnection

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

    @Transactional
    @Policy(Permission.DATASOURCE_CONNECTION_EDIT)
    fun updateConnection(connectionId: ConnectionId, request: UpdateConnectionRequest): Connection {
        val connection = connectionAdapter.getConnection(
            connectionId,
        )

        if (request is UpdateDatasourceConnectionRequest && connection is DatasourceConnection) {
            return updateDatasourceConnection(connectionId, request)
        } else if (request is UpdateKubernetesConnectionRequest && connection is KubernetesConnection) {
            return updateKubernetesConnection(connectionId, request)
        } else {
            throw EntityNotFound("Connection not found", "Connection with id $connectionId not found")
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
        additionalJDBCOptions: String,
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
            additionalJDBCOptions,
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
