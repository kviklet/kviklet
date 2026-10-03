// This file is not MIT licensed
package dev.kviklet.kviklet.service.eventstream

import dev.kviklet.kviklet.service.EventStreamingService
import dev.kviklet.kviklet.service.dto.DatasourceConnection
import dev.kviklet.kviklet.service.dto.DatasourceExecutionRequest
import dev.kviklet.kviklet.service.dto.DumpResultLog
import dev.kviklet.kviklet.service.dto.ErrorResultLog
import dev.kviklet.kviklet.service.dto.Event
import dev.kviklet.kviklet.service.dto.EventLoggingLevel
import dev.kviklet.kviklet.service.dto.ExecuteEvent
import dev.kviklet.kviklet.service.dto.ExecutionRequest
import dev.kviklet.kviklet.service.dto.KubernetesOutputResultLog
import dev.kviklet.kviklet.service.dto.QueryResultLog
import dev.kviklet.kviklet.service.dto.RequestType
import dev.kviklet.kviklet.service.dto.ReviewEvent
import dev.kviklet.kviklet.service.dto.UpdateResultLog
import java.time.ZoneOffset

/** Do not serialize Event/Connection/User directly: they contain credentials and stored result contents. */
fun requestFields(request: ExecutionRequest): Map<String, Any?> = buildMap {
    put("request_id", request.getId())
    val connection = request.connection
    put(
        "connection",
        buildMap {
            put("id", connection.getId())
            put("type", connection.connectionType.name)
            put("name", connection.displayName)
            if (connection is DatasourceConnection) {
                put("database_type", connection.type.name)
                put("hostname", connection.hostname)
                put("port", connection.port)
                put("database_name", connection.databaseName)
            }
        },
    )
    val details = buildMap {
        if (request.title.isNotBlank()) put("title", request.title)
        request.description?.let { putAll(EventStreamingService.reason(it)) }
    }
    if (details.isNotEmpty()) put("request", details)
}

fun requestStatementFields(request: ExecutionRequest, level: EventLoggingLevel): Map<String, Any?> {
    if (level != EventLoggingLevel.FULL || request !is DatasourceExecutionRequest ||
        request.type != RequestType.SingleExecution || request.statement == null
    ) {
        return emptyMap()
    }
    return mapOf("execution" to EventStreamingService.statement(request.statement))
}

fun accessFields(event: Event, channel: String, level: EventLoggingLevel = EventLoggingLevel.FULL): Map<String, Any?> =
    buildMap {
        putAll(requestFields(event.request))
        put("audit_event_id", event.getId())
        if (event is ExecuteEvent) {
            put(
                "execution",
                mapOf(
                    "channel" to channel,
                    "mode" to when {
                        event.isDump -> "dump"
                        event.isDownload -> "download"
                        event.isDryRun -> "dry_run"
                        event.command != null -> "kubernetes"
                        else -> "query"
                    },
                ) + if (level == EventLoggingLevel.FULL) {
                    EventStreamingService.statement(event.query ?: event.command)
                } else {
                    emptyMap()
                },
            )
            event.namespace?.let { put("namespace", it) }
            event.podName?.let { put("pod_name", it) }
            event.containerName?.let { put("container_name", it) }
        }
        if (event is ReviewEvent) put("review_action", event.action.name.lowercase())
    }

fun executionResultFields(event: ExecuteEvent): Map<String, Any?> = mapOf(
    "duration_ms" to
        java.time.Duration.between(
            event.createdAt.toInstant(ZoneOffset.UTC),
            java.time.Instant.now(),
        ).toMillis().coerceAtLeast(0),
    "results" to event.results.map { result ->
        when (result) {
            is QueryResultLog -> mapOf(
                "type" to "query",
                "rows_returned" to result.rowCount,
                "column_count" to result.columnCount,
            )

            is UpdateResultLog -> mapOf("type" to "update", "rows_affected" to result.rowsUpdated)

            is DumpResultLog -> mapOf("type" to "dump", "bytes_exported" to result.size)

            is ErrorResultLog -> mapOf("type" to "error", "error_code" to result.errorCode)

            is KubernetesOutputResultLog -> mapOf("type" to "kubernetes", "exit_code" to result.exitCode)
        }
    },
)

fun executionOutcome(event: ExecuteEvent): String = when {
    event.results.isEmpty() -> "unknown"

    event.results.any {
        it is ErrorResultLog ||
            (it is KubernetesOutputResultLog && it.exitCode != null && it.exitCode != 0)
    } -> "failure"

    event.results.any { it is KubernetesOutputResultLog && it.exitCode == null } -> "unknown"

    else -> "success"
}
