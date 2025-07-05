package dev.kviklet.kviklet.proxy.mocks

import dev.kviklet.kviklet.db.User
import dev.kviklet.kviklet.service.dto.Event
import dev.kviklet.kviklet.service.dto.EventId
import dev.kviklet.kviklet.service.dto.EventType
import dev.kviklet.kviklet.service.dto.ExecutionRequest
import dev.kviklet.kviklet.service.dto.utcTimeNow

data class MockEvent(override val request: ExecutionRequest) : Event(EventType.COMMENT, utcTimeNow(), request) {
    override val eventId: EventId?
        get() = throw UnsupportedOperationException("Not implemented")
    override val author: User
        get() = throw UnsupportedOperationException("Not implemented")
}
