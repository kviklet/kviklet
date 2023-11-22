package dev.kviklet.kviklet.service

import dev.kviklet.kviklet.controller.CreateDatasourceConnectionRequest
import dev.kviklet.kviklet.controller.UpdateDataSourceConnectionRequest
import dev.kviklet.kviklet.db.DatasourceConnectionAdapter
import dev.kviklet.kviklet.db.ReviewConfig
import dev.kviklet.kviklet.security.Permission
import dev.kviklet.kviklet.security.Policy
import dev.kviklet.kviklet.service.dto.AuthenticationType
import dev.kviklet.kviklet.service.dto.DatasourceConnection
import dev.kviklet.kviklet.service.dto.DatasourceConnectionId
import dev.kviklet.kviklet.service.dto.DatasourceId
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service

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
        datasourceId: DatasourceId,
        connectionId: DatasourceConnectionId,
        request: dev.kviklet.kviklet.controller.UpdateDataSourceConnectionRequest,
    ): DatasourceConnection {
        val datasourceConnection = datasourceConnectionAdapter.getDatasourceConnection(datasourceId, connectionId)

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
        datasourceId: DatasourceId,
        request: dev.kviklet.kviklet.controller.CreateDatasourceConnectionRequest,
    ): DatasourceConnection {
        return datasourceConnectionAdapter.createDatasourceConnection(
            datasourceId,
            DatasourceConnectionId(request.id),
            request.displayName,
            AuthenticationType.USER_PASSWORD,
            request.databaseName,
            request.username,
            request.password,
            request.description,
            ReviewConfig(
                numTotalRequired = request.reviewConfig.numTotalRequired,
            ),
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
            datasourceId = datasourceId,
            datasourceConnectionId = datasourceConnectionId,
        )
    }
}
