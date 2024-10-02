package dev.kviklet.kviklet.service.websocket

import dev.kviklet.kviklet.db.LiveSessionAdapter
import dev.kviklet.kviklet.service.ExecutionRequestService
import dev.kviklet.kviklet.service.dto.ExecutionRequestId
import dev.kviklet.kviklet.service.dto.ExecutionResult
import dev.kviklet.kviklet.service.dto.LiveSession
import dev.kviklet.kviklet.service.dto.LiveSessionId
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class SessionService(
    private val sessionAdapter: LiveSessionAdapter,
    private val executionRequestService: ExecutionRequestService,
) {

    @Transactional
    fun createOrConnectToSession(executionRequestId: ExecutionRequestId): LiveSession {
        val existingSession = sessionAdapter.findByExecutionRequestId(executionRequestId)

        if (existingSession != null) {
            return existingSession
        }
        return sessionAdapter.createLiveSession(executionRequestId, "")
    }

    fun executeStatement(sessionId: LiveSessionId, statement: String): ExecutionResult {
        val session = sessionAdapter.findById(sessionId)
        return executionRequestService.execute(
            session.executionRequest.request.id!!,
            statement,
            session.executionRequest.request.author.getId()!!,
        )
    }

    fun updateContent(sessionId: LiveSessionId, consoleContent: String): LiveSession =
        sessionAdapter.updateLiveSession(sessionId, consoleContent)

    fun getSession(sessionId: LiveSessionId): LiveSession = sessionAdapter.findById(sessionId)
}
