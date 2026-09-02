package dev.kviklet.kviklet.service

import dev.kviklet.kviklet.db.DumpResultLogPayload
import dev.kviklet.kviklet.db.ErrorResultLogPayload
import dev.kviklet.kviklet.db.EventAdapter
import dev.kviklet.kviklet.db.ExecutePayload
import dev.kviklet.kviklet.db.ExecutionRequestAdapter
import dev.kviklet.kviklet.db.KubernetesOutputResultLogPayload
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
import dev.kviklet.kviklet.service.dto.KubernetesOutputResultLog
import dev.kviklet.kviklet.service.dto.QueryResultLog
import dev.kviklet.kviklet.service.dto.ResultLog
import dev.kviklet.kviklet.service.dto.UpdateResultLog
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class EventService(
    private val executionRequestAdapter: ExecutionRequestAdapter,
    private val eventAdapter: EventAdapter,
) {

    @Policy(Permission.EXECUTION_REQUEST_GET)
    @Transactional
    fun saveEvent(id: ExecutionRequestId, authorId: String, payload: Payload): Event {
        val (_, event) = executionRequestAdapter.addEvent(id, authorId, payload)
        return event
    }

    /**
     * The single write path for execute events (REST execute, live session statements, downloads, dumps,
     * Kubernetes commands, and every statement relayed by a database proxy). Whether the request may run
     * anything is decided here, against the request row locked for this transaction, so no caller can
     * forget the check and no rejection or close can slip in between the check and the write.
     *
     * Throws [RequestNotExecutableException] when the request is rejected, closed, or (unless it is a dry
     * run on a connection that allows those before approval) not approved, and [AlreadyExecutedException]
     * when its executions are used up. Nothing is written in either case.
     */
    @Policy(Permission.EXECUTION_REQUEST_GET)
    @Transactional
    fun recordExecution(id: ExecutionRequestId, authorId: String, payload: ExecutePayload): Event {
        val (_, event) = executionRequestAdapter.addEvent(id, authorId, payload) { details ->
            if (payload.isDryRun) {
                details.raiseIfNotDryRunnable()
            } else {
                details.raiseIfNotExecutable()
            }
        }
        return event
    }

    /**
     * The read-only counterpart of [recordExecution] for a statement that does not produce a new execute
     * event but must still be refused once the request is rejected or closed, e.g. a client paging through
     * the results of an already audited proxy query.
     */
    @Policy(Permission.EXECUTION_REQUEST_GET)
    @Transactional
    fun assertExecutable(id: ExecutionRequestId) {
        executionRequestAdapter.getExecutionRequestDetails(id).raiseIfNotExecutable()
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
    fun getAllExecutions(from: LocalDateTime? = null, to: LocalDateTime? = null): List<ExecuteEvent> =
        eventAdapter.getExecutions(from, to)
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

            is QueryResultLog -> QueryResultLogPayload(
                it.columnCount,
                it.rowCount,
                it.columns,
                it.storedRows,
                it.storedRowCount,
            )

            is DumpResultLog -> DumpResultLogPayload(it.size)

            is KubernetesOutputResultLog -> KubernetesOutputResultLogPayload(
                it.exitCode,
                it.storedOutput,
                it.storedErrors,
                it.outputTruncated,
            )
        }
    },
    isDownload = isDownload,
    isDump = isDump,
    isDryRun = isDryRun,
)
