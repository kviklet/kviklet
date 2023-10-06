package com.example.executiongate.service

import com.example.executiongate.db.DatasourceAdapter
import com.example.executiongate.db.DatasourceConnectionAdapter
import com.example.executiongate.db.ExecutionRequestAdapter
import com.example.executiongate.security.SecuredDomainId
import com.example.executiongate.service.dto.DatasourceConnectionId
import com.example.executiongate.service.dto.DatasourceId
import com.example.executiongate.service.dto.ExecutionRequestId
import org.springframework.stereotype.Service

@Service
class IdResolver(
    private val datasourceConnectionAdapter: DatasourceConnectionAdapter,
    private val datasourceAdapter: DatasourceAdapter,
    private val executionRequestAdapter: ExecutionRequestAdapter,
) {
    fun resolve(id: SecuredDomainId) = when (id) {
        is DatasourceConnectionId -> datasourceConnectionAdapter.getDatasourceConnection(null, id)
        is DatasourceId -> datasourceAdapter.getDatasource(id)
        is ExecutionRequestId -> executionRequestAdapter.getExecutionRequestDetails(id)

        else -> throw IllegalArgumentException("Unknown id type: ${id::class}")
    }
}
