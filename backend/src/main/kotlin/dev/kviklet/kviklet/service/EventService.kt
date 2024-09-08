package dev.kviklet.kviklet.service

import dev.kviklet.kviklet.db.DumpResultLogPayload
import dev.kviklet.kviklet.db.ErrorResultLogPayload
import dev.kviklet.kviklet.db.EventAdapter
import dev.kviklet.kviklet.db.ExecutePayload
import dev.kviklet.kviklet.db.ExecutionRequestAdapter
import dev.kviklet.kviklet.db.Payload
import dev.kviklet.kviklet.db.QueryResultLogPayload
import dev.kviklet.kviklet.db.UpdateResultLogPayload
import dev.kviklet.kviklet.security.Permission
import dev.kviklet.kviklet.security.Policy
import dev.kviklet.kviklet.service.dto.DumpResultLog
import dev.kviklet.kviklet.service.dto.ErrorResultLog
import dev.kviklet.kviklet.service.dto.Event
import dev.kviklet.kviklet.service.dto.EventId
import dev.kviklet.kviklet.service.dto.ExecuteEvent
import dev.kviklet.kviklet.service.dto.ExecutionRequestId
import dev.kviklet.kviklet.service.dto.QueryResultLog
import dev.kviklet.kviklet.service.dto.ResultLog
import dev.kviklet.kviklet.service.dto.UpdateResultLog
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service

@Service
class EventService(
    private val executionRequestAdapter: ExecutionRequestAdapter,
    private val eventAdapter: EventAdapter,
) {

    @Policy(Permission.EXECUTION_REQUEST_GET)
    @Transactional
    fun saveEvent(id: ExecutionRequestId, authorId: String, payload: Payload): Event {
        val (executionRequest, event) = executionRequestAdapter.addEvent(id, authorId, payload)
        return event
    }

    @Policy(Permission.EXECUTION_REQUEST_EXECUTE)
    @Transactional
    fun addResultLogs(id: EventId, resultLogs: List<ResultLog>): Event {
        val event = eventAdapter.getEvent(id)
        if (event !is ExecuteEvent) {
            throw IllegalArgumentException("Event is not an execution event")
        }
        val updatedEvent = event.copy(
            results = resultLogs,
        )
        return eventAdapter.updateEvent(id, updatedEvent.toPayload())
    }

    @Policy(Permission.EXECUTION_REQUEST_GET)
    fun getAllExecutions(): List<ExecuteEvent> = eventAdapter.getExecutions()
}

fun ExecuteEvent.toPayload(): Payload = ExecutePayload(
    query = query,
    command = command,
    containerName = containerName,
    podName = podName,
    namespace = namespace,
    results = results.map {
        when (it) {
            is ErrorResultLog -> ErrorResultLogPayload(it.errorCode, it.message)
            is UpdateResultLog -> UpdateResultLogPayload(it.rowsUpdated)
            is QueryResultLog -> QueryResultLogPayload(it.columnCount, it.rowCount)
            is DumpResultLog -> DumpResultLogPayload(it.size)
        }
    },
    isDownload = isDownload,
    isDump = isDump,
)
