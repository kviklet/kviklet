package dev.kviklet.kviklet.service

import dev.kviklet.kviklet.db.ExecutionRequestAdapter
import dev.kviklet.kviklet.db.Payload
import dev.kviklet.kviklet.service.dto.Event
import dev.kviklet.kviklet.service.dto.ExecutionRequestId
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service

@Service
class EventService(private val executionRequestAdapter: ExecutionRequestAdapter) {

    @Transactional
    fun saveEvent(id: ExecutionRequestId, authorId: String, payload: Payload): Event {
        val (executionRequest, event) = executionRequestAdapter.addEvent(id, authorId, payload)
        return event
    }
}
