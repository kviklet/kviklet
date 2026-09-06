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
        val (_, event) = executionRequestAdapter.addEvent(id, authorId, payload)
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
