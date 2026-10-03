package dev.kviklet.kviklet.service

import dev.kviklet.kviklet.db.DumpResultLogPayload
import dev.kviklet.kviklet.db.ErrorResultLogPayload
import dev.kviklet.kviklet.db.EventAdapter
import dev.kviklet.kviklet.db.ExecutePayload
import dev.kviklet.kviklet.db.ExecutionRequestAdapter
import dev.kviklet.kviklet.db.KubernetesOutputResultLogPayload
import dev.kviklet.kviklet.db.Payload
import dev.kviklet.kviklet.db.QueryResultLogPayload
import dev.kviklet.kviklet.db.ReviewPayload
import dev.kviklet.kviklet.db.UpdateResultLogPayload
import dev.kviklet.kviklet.security.Permission
import dev.kviklet.kviklet.security.Policy
import dev.kviklet.kviklet.service.dto.DatasourceConnection
import dev.kviklet.kviklet.service.dto.DumpResultLog
import dev.kviklet.kviklet.service.dto.ErrorResultLog
import dev.kviklet.kviklet.service.dto.Event
import dev.kviklet.kviklet.service.dto.EventId
import dev.kviklet.kviklet.service.dto.ExecuteEvent
import dev.kviklet.kviklet.service.dto.ExecutionRequestId
import dev.kviklet.kviklet.service.dto.KubernetesOutputResultLog
import dev.kviklet.kviklet.service.dto.QueryResultLog
import dev.kviklet.kviklet.service.dto.ResultLog
import dev.kviklet.kviklet.service.dto.ReviewStatus
import dev.kviklet.kviklet.service.dto.UpdateResultLog
import dev.kviklet.kviklet.service.eventstream.accessFields
import dev.kviklet.kviklet.service.eventstream.executionOutcome
import dev.kviklet.kviklet.service.eventstream.executionResultFields
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class EventService(
    private val executionRequestAdapter: ExecutionRequestAdapter,
    private val eventAdapter: EventAdapter,
    private val eventStreamingService: EventStreamingService? = null,
) {
    private val proxyOrigin = ThreadLocal<Map<String, String>>()

    // The protocol path is authoritative; clients cannot choose their execution channel.
    @Policy(Permission.EXECUTION_REQUEST_GET)
    @Transactional
    fun saveProxyEvent(
        id: ExecutionRequestId,
        authorId: String,
        payload: Payload,
        sessionId: String,
        protocol: String,
    ): Event {
        proxyOrigin.set(mapOf("session_id" to sessionId, "protocol" to protocol))
        try {
            return saveEvent(id, authorId, payload)
        } finally {
            proxyOrigin.remove()
        }
    }

    @Policy(Permission.EXECUTION_REQUEST_GET)
    @Transactional
    fun saveEvent(id: ExecutionRequestId, authorId: String, payload: Payload): Event {
        val details = executionRequestAdapter.getExecutionRequestDetailsForUpdate(id)
        when (payload) {
            is ExecutePayload -> {
                val connection = details.request.connection
                if (details.isRejected()) throw RequestNotExecutableException("This request has been rejected!")
                if (!payload.isDryRun) {
                    details.raiseIfNotExecutable()
                } else if (connection is DatasourceConnection && connection.dryRunRequiresApproval &&
                    details.resolveReviewStatus() != ReviewStatus.APPROVED
                ) {
                    throw RequestNotExecutableException("This request has not been approved yet!")
                }
            }

            is ReviewPayload -> if (details.isRejected()) {
                throw InvalidReviewException("Can't review an already rejected request!")
            }

            else -> Unit
        }
        val (updatedDetails, event) = executionRequestAdapter.addEvent(id, authorId, payload)
        // Dry runs stream their outcome only; keep the stored attempt for existing audit semantics.
        if (event is ExecuteEvent && event.isDryRun) return event
        // The update service streams edits after saving the new request contents.
        if (event is dev.kviklet.kviklet.service.dto.EditEvent) return event
        val proxy = proxyOrigin.get()
        eventStreamingService?.emit(
            action = when (event) {
                is ExecuteEvent -> "execution.attempted"
                is dev.kviklet.kviklet.service.dto.ReviewEvent -> "review.${event.action.name.lowercase()}"
                else -> "comment.added"
            },
            category = if (event is ExecuteEvent && event.command == null) "database" else "configuration",
            outcome = if (event is ExecuteEvent) "unknown" else "success",
            fields = { level ->
                accessFields(
                    event,
                    if (proxy != null) "database_proxy" else EventStreamingService.channel(),
                    level,
                    updatedDetails,
                ) +
                    if (proxy != null) mapOf("proxy" to proxy) else emptyMap()
            },
            actorId = authorId,
        )
        return event
    }

    // Fetching another batch of an audited Postgres portal does not write a new execute event.
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
        val saved = eventAdapter.updateEvent(id, updatedEvent.toPayload())
        if (event.command == null) {
            eventStreamingService?.emit(
                "execution.completed",
                "database",
                executionOutcome(updatedEvent),
                { level ->
                    accessFields(
                        updatedEvent,
                        EventStreamingService.channel(),
                        level,
                        executionRequestAdapter.getExecutionRequestDetails(updatedEvent.request.id!!),
                    ) +
                        executionResultFields(updatedEvent)
                },
                actorId = event.author.getId(),
            )
        }
        return saved
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
