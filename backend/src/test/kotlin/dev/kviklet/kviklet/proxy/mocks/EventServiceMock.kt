// This file is not MIT licensed
package dev.kviklet.kviklet.proxy.mocks

import dev.kviklet.kviklet.db.EventAdapter
import dev.kviklet.kviklet.db.ExecutePayload
import dev.kviklet.kviklet.db.ExecutionRequestAdapter
import dev.kviklet.kviklet.db.Payload
import dev.kviklet.kviklet.service.EventService
import dev.kviklet.kviklet.service.RequestNotExecutableException
import dev.kviklet.kviklet.service.dto.Event
import dev.kviklet.kviklet.service.dto.EventType
import dev.kviklet.kviklet.service.dto.ExecutionRequest
import dev.kviklet.kviklet.service.dto.ExecutionRequestId
import dev.kviklet.kviklet.service.dto.ReviewStatus
import org.junit.jupiter.api.Assertions.assertTrue
import java.util.ArrayList
import java.util.concurrent.ConcurrentHashMap

open class EventServiceMock(
    executionRequestAdapter: ExecutionRequestAdapter,
    eventAdapter: EventAdapter,
    var executionRequest: ExecutionRequest,
) : EventService(executionRequestAdapter, eventAdapter) {
    var queries: ArrayList<String> = ArrayList<String>()
    var rawQueries: ArrayList<String> = ArrayList<String>()

    // Requests the real service would refuse to execute for (rejected or closed), by id. Stands in for
    // the executability guard of recordExecution, which the real implementation resolves from the request's
    // review events.
    private val terminalRequests = ConcurrentHashMap<ExecutionRequestId, ReviewStatus>()

    fun markNotExecutable(requestId: ExecutionRequestId, status: ReviewStatus) {
        terminalRequests[requestId] = status
    }
    fun assertAuditedQueryContains(fragment: String) {
        assertTrue(
            this.rawQueries.any { it.contains(fragment) },
            "No audited query contains \"$fragment\". Audited queries: $rawQueries",
        )
    }
    fun assertQueryIsAudited(query: String) {
        var processedQuery = query.lowercase().replace(" ", "").replace("\n".toRegex(), "")
        if (processedQuery.last() == ';') {
            assertTrue(this.queries.contains(processedQuery.dropLast(1)))
            return
        }
        assertTrue(this.queries.contains(processedQuery))
    }
    override fun recordExecution(id: ExecutionRequestId, authorId: String, payload: ExecutePayload): Event {
        assertExecutable(id)
        return saveEvent(id, authorId, payload)
    }

    override fun assertExecutable(id: ExecutionRequestId) {
        terminalRequests[id]?.let { throw RequestNotExecutableException(it) }
    }

    override fun saveEvent(id: ExecutionRequestId, authorId: String, payload: Payload): Event {
        if (payload.type.compareTo(EventType.EXECUTE) == 0) {
            val executePayload = payload as ExecutePayload
            executePayload.query?.let { rawQueries.add(it) }
            if (executePayload.query?.last() == ';') {
                executePayload.query?.let {
                    queries.add(it.lowercase().replace(" ", "").replace("\n".toRegex(), "").dropLast(1))
                }
            } else {
                executePayload.query?.let { queries.add(it.lowercase().replace(" ", "").replace("\n".toRegex(), "")) }
            }
        }
        return MockEvent(this.executionRequest)
    }
}
