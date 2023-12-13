package dev.kviklet.kviklet.service

import dev.kviklet.kviklet.controller.UpdateDataSourceConnectionRequest
import dev.kviklet.kviklet.db.DatasourceConnectionAdapter
import dev.kviklet.kviklet.db.ReviewConfig
import dev.kviklet.kviklet.security.Permission
import dev.kviklet.kviklet.security.Policy
import dev.kviklet.kviklet.service.dto.AuthenticationType
import dev.kviklet.kviklet.service.dto.DatasourceConnection
import dev.kviklet.kviklet.service.dto.DatasourceConnectionId
import dev.kviklet.kviklet.service.dto.DatasourceId
import dev.kviklet.kviklet.service.dto.DatasourceType
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service

class EntityNotFound(override val message: String, val detail: String) : Exception(message)

@Service
class DatasourceConnectionService(
    val datasourceConnectionAdapter: DatasourceConnectionAdapter,
) {

    @Transactional
    @Policy(Permission.DATASOURCE_CONNECTION_GET)
    fun listDatasourceConnections(): List<DatasourceConnection> {
        return datasourceConnectionAdapter.listDatasourceConnections()
    }

    @Transactional
    @Policy(Permission.DATASOURCE_CONNECTION_EDIT)
    fun updateDatasourceConnection(
        connectionId: DatasourceConnectionId,
        request: UpdateDataSourceConnectionRequest,
    ): DatasourceConnection {
        val datasourceConnection = datasourceConnectionAdapter.getDatasourceConnection(
            connectionId,
        )

        return datasourceConnectionAdapter.updateDatasourceConnection(
            connectionId,
            request.displayName ?: datasourceConnection.displayName,
            request.databaseName ?: datasourceConnection.databaseName,
            request.username ?: datasourceConnection.username,
            request.password ?: datasourceConnection.password,
            request.description ?: datasourceConnection.description,
            request.reviewConfig?.let { ReviewConfig(it.numTotalRequired) } ?: datasourceConnection.reviewConfig,
        )
    }

    @Transactional
    @Policy(Permission.DATASOURCE_CONNECTION_CREATE)
    fun createDatasourceConnection(
        datasourceConnectionId: DatasourceConnectionId,
        displayName: String,
        databaseName: String?,
        username: String,
        password: String,
        description: String,
        reviewsRequired: Int,
        port: Int,
        hostname: String,
        type: DatasourceType,
    ): DatasourceConnection {
        return datasourceConnectionAdapter.createDatasourceConnection(
            datasourceConnectionId,
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
    @Policy(Permission.DATASOURCE_CONNECTION_EDIT)
    fun deleteDatasourceConnection(connectionId: DatasourceConnectionId) {
        datasourceConnectionAdapter.deleteDatasourceConnection(connectionId)
    }

    @Transactional
    @Policy(Permission.DATASOURCE_CONNECTION_GET)
    fun getDatasourceConnection(
        datasourceId: DatasourceId,
        datasourceConnectionId: DatasourceConnectionId,
    ): DatasourceConnection {
        return datasourceConnectionAdapter.getDatasourceConnection(
            datasourceConnectionId = datasourceConnectionId,
        )
    }
}
