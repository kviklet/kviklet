package dev.kviklet.kviklet.proxy.mocks

import dev.kviklet.kviklet.db.User
import dev.kviklet.kviklet.service.dto.*

data class MockEvent(override val request: ExecutionRequest): Event(EventType.COMMENT, utcTimeNow(), request) {
    override val eventId: EventId?
        get() = TODO("Not yet implemented")
    override val author: User
        get() = TODO("Not yet implemented")
}