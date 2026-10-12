// This file is not MIT licensed
package dev.kviklet.kviklet.proxy.mocks

import dev.kviklet.kviklet.db.EventAdapter
import dev.kviklet.kviklet.db.ExecutionRequestAdapter
import dev.kviklet.kviklet.db.Payload
import dev.kviklet.kviklet.service.dto.Event
import dev.kviklet.kviklet.service.dto.ExecutionRequest
import dev.kviklet.kviklet.service.dto.ExecutionRequestId
import dev.kviklet.kviklet.service.dto.RequestSource

class FailingEventServiceMock(
    executionRequestAdapter: ExecutionRequestAdapter,
    eventAdapter: EventAdapter,
    executionRequest: ExecutionRequest,
) : EventServiceMock(executionRequestAdapter, eventAdapter, executionRequest) {
    var failing = false

    override fun saveEvent(id: ExecutionRequestId, authorId: String, payload: Payload, source: RequestSource): Event {
        if (failing) {
            throw RuntimeException("Simulated audit failure")
        }
        return super.saveEvent(id, authorId, payload, source)
    }
}
