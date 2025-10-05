package dev.kviklet.kviklet.service.websocket

import dev.kviklet.kviklet.db.LiveSessionAdapter
import dev.kviklet.kviklet.security.Permission
import dev.kviklet.kviklet.security.Policy
import dev.kviklet.kviklet.service.ExecutionRequestService
import dev.kviklet.kviklet.service.dto.ExecutionRequestId
import dev.kviklet.kviklet.service.dto.ExecutionResult
import dev.kviklet.kviklet.service.dto.LiveSession
import dev.kviklet.kviklet.service.dto.LiveSessionId
import jakarta.persistence.LockModeType
import jakarta.persistence.QueryHint
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.QueryHints
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class SessionService(
    private val sessionAdapter: LiveSessionAdapter,
    private val executionRequestService: ExecutionRequestService,
) {

    @Transactional
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(QueryHint(name = "javax.persistence.lock.timeout", value = "3000"))
    @Policy(Permission.EXECUTION_REQUEST_GET)
    fun createOrConnectToSession(executionRequestId: ExecutionRequestId): LiveSession {
        val existingSession = sessionAdapter.findByExecutionRequestId(executionRequestId)

        if (existingSession != null) {
            return existingSession
        }
        return sessionAdapter.createLiveSession(executionRequestId, "")
    }

    @Policy(Permission.EXECUTION_REQUEST_EXECUTE)
    fun executeStatement(sessionId: LiveSessionId, statement: String): ExecutionResult {
        // Atomically check and set executing flag - this will throw if already executing
        val session = sessionAdapter.checkAndSetExecuting(sessionId)

        return executionRequestService.execute(
            session.executionRequest.request.id!!,
            statement,
            session.executionRequest.request.author.getId()!!,
        )
    }

    @Policy(Permission.EXECUTION_REQUEST_EXECUTE)
    fun updateContent(sessionId: LiveSessionId, consoleContent: String): LiveSession =
        sessionAdapter.updateLiveSession(sessionId, consoleContent)

    @Policy(Permission.EXECUTION_REQUEST_GET)
    fun getSession(sessionId: LiveSessionId): LiveSession = sessionAdapter.findById(sessionId)

    @Policy(Permission.EXECUTION_REQUEST_EXECUTE)
    fun cancelQuery(sessionId: LiveSessionId) {
        val session = sessionAdapter.findById(sessionId)
        executionRequestService.cancel(session.executionRequest.request.id!!)
    }

    @Policy(Permission.EXECUTION_REQUEST_EXECUTE)
    fun clearExecutingFlag(sessionId: LiveSessionId) {
        sessionAdapter.setExecuting(sessionId, false)
    }
}
