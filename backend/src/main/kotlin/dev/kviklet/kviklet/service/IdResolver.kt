package dev.kviklet.kviklet.service

import dev.kviklet.kviklet.db.ConnectionAdapter
import dev.kviklet.kviklet.db.EventAdapter
import dev.kviklet.kviklet.db.ExecutionRequestAdapter
import dev.kviklet.kviklet.db.LiveSessionAdapter
import dev.kviklet.kviklet.db.RoleAdapter
import dev.kviklet.kviklet.db.UserAdapter
import dev.kviklet.kviklet.db.UserId
import dev.kviklet.kviklet.security.SecuredDomainId
import dev.kviklet.kviklet.security.SecuredDomainObject
import dev.kviklet.kviklet.service.dto.ConnectionId
import dev.kviklet.kviklet.service.dto.EventId
import dev.kviklet.kviklet.service.dto.ExecutionRequestId
import dev.kviklet.kviklet.service.dto.LiveSessionId
import dev.kviklet.kviklet.service.dto.RoleId
import org.springframework.stereotype.Service

@Service
class IdResolver(
    private val connectionAdapter: ConnectionAdapter,
    private val executionRequestAdapter: ExecutionRequestAdapter,
    private val userAdapter: UserAdapter,
    private val roleAdapter: RoleAdapter,
    private val eventAdapter: EventAdapter,
    private val liveSessionAdapter: LiveSessionAdapter,
) {
    fun resolve(id: SecuredDomainId): SecuredDomainObject? = try {
        when (id) {
            is ConnectionId -> connectionAdapter.getConnection(id)
            is ExecutionRequestId -> executionRequestAdapter.getExecutionRequestDetails(id)
            is UserId -> userAdapter.findById(id.toString())
            is RoleId -> roleAdapter.findById(id)
            is EventId -> eventAdapter.getEvent(id)
            is LiveSessionId -> liveSessionAdapter.findById(id)
            else -> throw IllegalArgumentException("Unknown id type: ${id::class}")
        }
    } catch (e: EntityNotFound) {
        null
    }
}
