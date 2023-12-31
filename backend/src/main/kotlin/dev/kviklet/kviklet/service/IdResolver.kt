package dev.kviklet.kviklet.service

import dev.kviklet.kviklet.db.DatasourceConnectionAdapter
import dev.kviklet.kviklet.db.ExecutionRequestAdapter
import dev.kviklet.kviklet.db.UserAdapter
import dev.kviklet.kviklet.db.UserId
import dev.kviklet.kviklet.security.SecuredDomainId
import dev.kviklet.kviklet.security.SecuredDomainObject
import dev.kviklet.kviklet.service.dto.DatasourceConnectionId
import dev.kviklet.kviklet.service.dto.ExecutionRequestId
import org.springframework.stereotype.Service

@Service
class IdResolver(
    private val datasourceConnectionAdapter: DatasourceConnectionAdapter,
    private val executionRequestAdapter: ExecutionRequestAdapter,
    private val userAdapter: UserAdapter,
) {
    fun resolve(id: SecuredDomainId): SecuredDomainObject? {
        return try {
            when (id) {
                is DatasourceConnectionId -> datasourceConnectionAdapter.getDatasourceConnection(id)
                is ExecutionRequestId -> executionRequestAdapter.getExecutionRequestDetails(id)
                is UserId -> userAdapter.findById(id.toString())
                else -> throw IllegalArgumentException("Unknown id type: ${id::class}")
            }
        } catch (e: EntityNotFound) {
            null
        }
    }
}
