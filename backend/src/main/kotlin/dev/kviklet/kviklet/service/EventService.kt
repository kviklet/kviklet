package dev.kviklet.kviklet.service

import dev.kviklet.kviklet.db.EventAdapter
import dev.kviklet.kviklet.db.ExecutionRequestAdapter
import dev.kviklet.kviklet.db.Payload
import dev.kviklet.kviklet.service.dto.Event
import dev.kviklet.kviklet.service.dto.ExecuteEvent
import dev.kviklet.kviklet.service.dto.ExecutionRequestId
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service

@Service
class EventService(
    private val executionRequestAdapter: ExecutionRequestAdapter,
    private val eventAdapter: EventAdapter,
) {

    @Transactional
    fun saveEvent(id: ExecutionRequestId, authorId: String, payload: Payload): Event {
        val (executionRequest, event) = executionRequestAdapter.addEvent(id, authorId, payload)
        return event
    }

    fun getAllExecutions(): List<ExecuteEvent> {
        return eventAdapter.getExecutions()
    }
}
