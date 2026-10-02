// This file is not MIT licensed
package dev.kviklet.kviklet.service.eventstream

import dev.kviklet.kviklet.service.EventStreamingService
import dev.kviklet.kviklet.service.dto.DatasourceConnection
import dev.kviklet.kviklet.service.dto.DumpResultLog
import dev.kviklet.kviklet.service.dto.ErrorResultLog
import dev.kviklet.kviklet.service.dto.Event
import dev.kviklet.kviklet.service.dto.ExecuteEvent
import dev.kviklet.kviklet.service.dto.KubernetesOutputResultLog
import dev.kviklet.kviklet.service.dto.QueryResultLog
import dev.kviklet.kviklet.service.dto.ReviewEvent
import dev.kviklet.kviklet.service.dto.UpdateResultLog
import java.time.ZoneOffset

/** Do not serialize Event/Connection/User directly: they contain credentials and stored result contents. */
fun accessFields(event: Event, channel: String): Map<String, Any?> = buildMap {
    put("request_id", event.request.getId())
    put("audit_event_id", event.getId())
    put(
        "connection",
        mapOf(
            "id" to event.request.connection.getId(),
            "type" to event.request.connection.connectionType.name,
            "name" to event.request.connection.displayName,
            "database_type" to (event.request.connection as? DatasourceConnection)?.type?.name,
        ),
    )
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
            ) + EventStreamingService.statement(event.query ?: event.command),
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
