package dev.kviklet.kviklet.service.websocket

import dev.kviklet.kviklet.service.ExecutionRequestService
import dev.kviklet.kviklet.service.dto.ExecutionRequestId
import org.springframework.stereotype.Service

enum class UserRole {
    EDITOR,
    OBSERVER,
}

@Service
class UserRoleService(private val executionRequestService: ExecutionRequestService) {

    fun determineUserRole(userId: String, requestId: ExecutionRequestId): UserRole {
        val executionRequest = executionRequestService.get(requestId)
        return if (executionRequest.request.author.getId() == userId) {
            UserRole.EDITOR
        } else {
            UserRole.OBSERVER
        }
    }
}
