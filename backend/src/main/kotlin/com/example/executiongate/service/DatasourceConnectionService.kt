package com.example.executiongate.service

import com.example.executiongate.controller.CreateDatasourceConnectionRequest
import com.example.executiongate.controller.UpdateDataSourceConnectionRequest
import com.example.executiongate.db.DatasourceAdapter
import com.example.executiongate.db.DatasourceConnectionAdapter
import com.example.executiongate.db.ReviewConfig
import com.example.executiongate.security.Policy
import com.example.executiongate.service.dto.AuthenticationType
import com.example.executiongate.service.dto.DatasourceConnection
import com.example.executiongate.service.dto.DatasourceConnectionId
import com.example.executiongate.service.dto.DatasourceId
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service

@Service
class DatasourceConnectionService(
    val datasourceAdapter: DatasourceAdapter,
    val datasourceConnectionAdapter: DatasourceConnectionAdapter,
) {

    @Transactional
    fun listDatasourceConnections(): List<DatasourceConnection> {
        return datasourceConnectionAdapter.listDatasourceConnections()
    }

    @Transactional
    fun updateDatasourceConnection(
        connectionId: DatasourceConnectionId,
        request: UpdateDataSourceConnectionRequest,
    ): DatasourceConnection {
        val datasourceConnection = datasourceConnectionAdapter.getDatasourceConnection(connectionId)

        return datasourceConnectionAdapter.updateDatasourceConnection(
            connectionId,
            request.displayName ?: datasourceConnection.displayName,
            request.username ?: datasourceConnection.username,
            request.password ?: datasourceConnection.password,
            request.description ?: datasourceConnection.description,
            request.reviewConfig?.let { ReviewConfig(it.numTotalRequired) } ?: datasourceConnection.reviewConfig,
        )
    }

    @Transactional
    fun createDatasourceConnection(
        datasourceId: DatasourceId,
        request: CreateDatasourceConnectionRequest,
    ): DatasourceConnection {
        return datasourceConnectionAdapter.createDatasourceConnection(
            datasourceId,
            request.displayName,
            AuthenticationType.USER_PASSWORD,
            request.username,
            request.password,
            request.description,
            ReviewConfig(
                numTotalRequired = request.reviewConfig.numTotalRequired,
            ),
        )
    }

    @Policy("connection:edit")
    fun deleteDatasourceConnection(connectionId: DatasourceConnectionId) {
        datasourceConnectionAdapter.deleteDatasourceConnection(connectionId)
    }
}
