package dev.kviklet.kviklet.service

import dev.kviklet.kviklet.db.ExecutionRequestAdapter
import dev.kviklet.kviklet.security.Permission
import dev.kviklet.kviklet.security.Policy
import dev.kviklet.kviklet.service.dto.ConnectionId
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ExecutionRequestStatusService(private val executionRequestAdapter: ExecutionRequestAdapter) {
    @Transactional
    @Policy(Permission.DATASOURCE_CONNECTION_EDIT, checkIsPresentOnly = true)
    fun recalculateStatusForRequests(connectionId: ConnectionId) {
        val requests = executionRequestAdapter.listExecutionRequestsFiltered(connectionId = connectionId)

        requests.forEach { executionRequestDetails ->
            executionRequestAdapter.updateExecutionRequest(
                id = executionRequestDetails.request.id!!,
                executionStatus = executionRequestDetails.resolveExecutionStatus(),
                reviewStatus = executionRequestDetails.resolveReviewStatus(),
            )
        }
    }
}
