package dev.kviklet.kviklet.service

import dev.kviklet.kviklet.db.DatasourceAdapter
import dev.kviklet.kviklet.db.DatasourceConnectionAdapter
import dev.kviklet.kviklet.db.ExecutionRequestAdapter
import dev.kviklet.kviklet.security.SecuredDomainId
import dev.kviklet.kviklet.service.dto.DatasourceConnectionId
import dev.kviklet.kviklet.service.dto.DatasourceId
import dev.kviklet.kviklet.service.dto.ExecutionRequestId
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
