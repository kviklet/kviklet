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
        datasourceId: String,
        connectionId: DatasourceConnectionId,
        request: UpdateDataSourceConnectionRequest,
    ): DatasourceConnection {
        val datasourceConnection = datasourceConnectionAdapter.getDatasourceConnection(
            DatasourceId(datasourceId),
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
        datasourceId: String,
        datasourceConnectionId: DatasourceConnectionId,
        displayName: String,
        databaseName: String?,
        username: String,
        password: String,
        description: String,
        reviewsRequired: Int,
    ): DatasourceConnection {
        return datasourceConnectionAdapter.createDatasourceConnection(
            DatasourceId(datasourceId),
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
