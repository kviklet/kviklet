package com.example.executiongate.service

import com.example.executiongate.db.DatasourceAdapter
import com.example.executiongate.db.DatasourceConnectionAdapter
import com.example.executiongate.security.SecuredDomainId
import com.example.executiongate.service.dto.DatasourceConnectionId
import com.example.executiongate.service.dto.DatasourceId
import org.springframework.stereotype.Service

@Service
class IdResolver(
    private val datasourceConnectionAdapter: DatasourceConnectionAdapter,
    private val datasourceAdapter: DatasourceAdapter,
) {
    fun resolve(id: SecuredDomainId) = when (id) {
        is DatasourceConnectionId -> datasourceConnectionAdapter.getDatasourceConnection(id)
        is DatasourceId -> datasourceAdapter.getDatasource(id)

        else -> throw IllegalArgumentException("Unknown id type: ${id::class}")
    }
}
