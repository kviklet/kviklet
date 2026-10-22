// This file is not MIT licensed
package dev.kviklet.kviklet.mcp

import dev.kviklet.kviklet.security.Permission
import dev.kviklet.kviklet.service.dto.ApprovalProgress
import dev.kviklet.kviklet.service.dto.CommentEvent
import dev.kviklet.kviklet.service.dto.Connection
import dev.kviklet.kviklet.service.dto.ConnectionWithPermissions
import dev.kviklet.kviklet.service.dto.DBExecutionResult
import dev.kviklet.kviklet.service.dto.DatasourceConnection
import dev.kviklet.kviklet.service.dto.DatasourceExecutionRequest
import dev.kviklet.kviklet.service.dto.DatasourceType
import dev.kviklet.kviklet.service.dto.EditEvent
import dev.kviklet.kviklet.service.dto.ErrorQueryResult
import dev.kviklet.kviklet.service.dto.Event
import dev.kviklet.kviklet.service.dto.EventType
import dev.kviklet.kviklet.service.dto.ExecuteEvent
import dev.kviklet.kviklet.service.dto.ExecutionProxy
import dev.kviklet.kviklet.service.dto.ExecutionRequestDetails
import dev.kviklet.kviklet.service.dto.ExecutionRequestDetailsWithRoles
import dev.kviklet.kviklet.service.dto.ExecutionResult
import dev.kviklet.kviklet.service.dto.ExecutionStatus
import dev.kviklet.kviklet.service.dto.KubernetesConnection
import dev.kviklet.kviklet.service.dto.KubernetesExecutionRequest
import dev.kviklet.kviklet.service.dto.KubernetesExecutionResult
import dev.kviklet.kviklet.service.dto.MongoRecordsQueryResult
import dev.kviklet.kviklet.service.dto.QueryResult
import dev.kviklet.kviklet.service.dto.RecordsQueryResult
import dev.kviklet.kviklet.service.dto.RequestSource
import dev.kviklet.kviklet.service.dto.RequestType
import dev.kviklet.kviklet.service.dto.ReviewAction
import dev.kviklet.kviklet.service.dto.ReviewConfig
import dev.kviklet.kviklet.service.dto.ReviewEvent
import dev.kviklet.kviklet.service.dto.ReviewStatus
import dev.kviklet.kviklet.service.dto.Role
import dev.kviklet.kviklet.service.dto.UpdateQueryResult
import io.kubernetes.client.openapi.models.V1Pod
import java.time.LocalDateTime

// Results are kept small on purpose: everything here ends up in the agent's context window.
const val MAX_RESULT_ROWS = 200
const val MAX_CELL_CHARS = 1_000
const val MAX_POD_OUTPUT_CHARS = 20_000
const val MAX_HISTORY_ENTRIES = 50

enum class ConnectionKind { DATABASE, KUBERNETES }

data class ConnectionResult(
    val id: String,
    val displayName: String,
    val description: String,
    val kind: ConnectionKind,
    val engine: String?,
    val reviewConfig: ReviewConfig,
    val temporaryAccessEnabled: Boolean,
    val maxTemporaryAccessMinutes: Long?,
    val canCreateRequest: Boolean,
    val canEditConnection: Boolean,
) {
    companion object {
        fun from(dto: ConnectionWithPermissions): ConnectionResult {
            val connection = dto.connection
            return ConnectionResult(
                id = connection.getId(),
                displayName = connection.displayName,
                description = connection.description,
                kind = connection.kind(),
                engine = (connection as? DatasourceConnection)?.type?.name,
                reviewConfig = connection.reviewConfig,
                temporaryAccessEnabled = when (connection) {
                    is DatasourceConnection -> connection.temporaryAccessEnabled
                    is KubernetesConnection -> connection.temporaryAccessEnabled
                },
                maxTemporaryAccessMinutes = (connection as? DatasourceConnection)?.maxTemporaryAccessDuration,
                canCreateRequest = Permission.EXECUTION_REQUEST_EDIT in dto.permissions,
                canEditConnection = Permission.DATASOURCE_CONNECTION_EDIT in dto.permissions,
            )
        }
    }
}

fun Connection.kind() = when (this) {
    is DatasourceConnection -> ConnectionKind.DATABASE
    is KubernetesConnection -> ConnectionKind.KUBERNETES
}

data class RequestSummary(
    val id: String,
    val title: String,
    val type: RequestType,
    val connectionId: String,
    val connectionKind: ConnectionKind,
    val author: String,
    val reviewStatus: ReviewStatus,
    val executionStatus: ExecutionStatus,
    val source: RequestSource,
    val createdAt: LocalDateTime,
    val canReview: Boolean,
    val canExecute: Boolean,
    val url: String?,
)

data class RequestList(val requests: List<RequestSummary>, val nextCursor: String?)

data class RoleApprovals(val role: String, val required: Int, val current: Int)

data class Approvals(
    val required: Int,
    val current: Int,
    val roles: List<RoleApprovals>,
    val changeRequestedBy: List<String>,
) {
    companion object {
        fun from(progress: ApprovalProgress, roles: Map<String, Role>) = Approvals(
            required = progress.totalRequired,
            current = progress.totalCurrent,
            roles = progress.roleProgress.map {
                RoleApprovals(roles[it.roleId]?.name ?: it.roleId, it.numRequired, it.numCurrent)
            },
            changeRequestedBy = progress.changeRequestedBy,
        )
    }
}

data class Pod(val namespace: String, val name: String, val containerName: String?)

data class HistoryEntry(
    val type: EventType,
    val author: String,
    val at: LocalDateTime,
    val action: ReviewAction? = null,
    val comment: String? = null,
    /** For executions what ran, for edits the statement or command before the edit. */
    val statement: String? = null,
)

data class RequestDetail(
    val id: String,
    val title: String,
    val type: RequestType,
    val connectionId: String,
    val connectionKind: ConnectionKind,
    val author: String,
    val reviewStatus: ReviewStatus,
    val executionStatus: ExecutionStatus,
    val source: RequestSource,
    val createdAt: LocalDateTime,
    val canReview: Boolean,
    val canExecute: Boolean,
    val url: String?,
    val description: String?,
    val statement: String?,
    val pod: Pod?,
    val command: String?,
    val durationMinutes: Long?,
    val approvals: Approvals,
    val history: List<HistoryEntry>,
)

data class ReviewResult(val id: String, val reviewStatus: ReviewStatus, val approvals: Approvals)

data class CloseResult(val id: String, val reviewStatus: ReviewStatus)

data class PodResult(val namespace: String, val name: String, val status: String, val containers: List<String>) {
    companion object {
        fun from(pod: V1Pod) = PodResult(
            namespace = pod.metadata?.namespace ?: "",
            name = pod.metadata?.name ?: "",
            status = pod.status?.phase ?: "",
            containers = pod.spec?.containers?.map { it.name } ?: emptyList(),
        )
    }
}

data class ProxyResult(
    val host: String?,
    val port: Int,
    val username: String,
    val password: String,
    val databaseName: String,
    val engine: String,
    val expiresAt: LocalDateTime?,
    val connectionUrl: String?,
) {
    companion object {
        fun from(proxy: ExecutionProxy) = ProxyResult(
            host = proxy.host,
            port = proxy.port,
            username = proxy.username,
            password = proxy.password,
            databaseName = proxy.databaseName,
            engine = proxy.type.name,
            expiresAt = proxy.expiresAt,
            // MySQL and MariaDB share one wire protocol, and the proxy only serves those and Postgres.
            connectionUrl = proxy.host?.let { host ->
                val scheme = if (proxy.type == DatasourceType.POSTGRESQL) "postgresql" else "mysql"
                "$scheme://${proxy.username}:${proxy.password}@$host:${proxy.port}/${proxy.databaseName}"
            },
        )
    }
}

sealed class ToolExecutionResult(val kind: ConnectionKind)

data class DatabaseExecutionResult(val results: List<StatementResult>, val warnings: List<String>) :
    ToolExecutionResult(ConnectionKind.DATABASE)

data class KubernetesToolExecutionResult(
    val stdout: String,
    val stderr: String,
    val exitCode: Int?,
    val finished: Boolean,
    val truncated: Boolean,
) : ToolExecutionResult(ConnectionKind.KUBERNETES)

/** One statement's outcome. Exactly one of the fields is set. */
data class StatementResult(
    val rows: Rows? = null,
    val documents: Documents? = null,
    val updateCount: Int? = null,
    val error: StatementError? = null,
)

data class Rows(val columns: List<String>, val rows: List<List<String?>>, val rowCount: Int, val truncated: Boolean)

data class Documents(val documents: List<String>, val documentCount: Int, val truncated: Boolean)

data class StatementError(val errorCode: Int, val message: String?)

fun ExecutionResult.toToolResult(): ToolExecutionResult = when (this) {
    is DBExecutionResult -> DatabaseExecutionResult(
        results.map { it.toStatementResult() },
        warnings.take(MAX_RESULT_ROWS).map { it.take(MAX_CELL_CHARS) },
    )

    is KubernetesExecutionResult -> {
        val stdout = messages.joinToString("\n")
        val stderr = errors.joinToString("\n")
        KubernetesToolExecutionResult(
            stdout = stdout.take(MAX_POD_OUTPUT_CHARS),
            stderr = stderr.take(MAX_POD_OUTPUT_CHARS),
            exitCode = exitCode,
            finished = finished,
            truncated = stdout.length > MAX_POD_OUTPUT_CHARS || stderr.length > MAX_POD_OUTPUT_CHARS,
        )
    }
}

private fun QueryResult.toStatementResult(): StatementResult = when (this) {
    is RecordsQueryResult -> {
        val labels = columns.map { it.label }
        var truncated = data.size > MAX_RESULT_ROWS
        val rows = data.take(MAX_RESULT_ROWS).map { row ->
            labels.map { label ->
                row[label]?.let { value ->
                    if (value.length > MAX_CELL_CHARS) truncated = true
                    value.take(MAX_CELL_CHARS)
                }
            }
        }
        StatementResult(rows = Rows(labels, rows, data.size, truncated))
    }

    is MongoRecordsQueryResult -> {
        var truncated = documents.size > MAX_RESULT_ROWS
        val json = documents.take(MAX_RESULT_ROWS).map { document ->
            document.toJson().also { if (it.length > MAX_CELL_CHARS) truncated = true }.take(MAX_CELL_CHARS)
        }
        StatementResult(documents = Documents(json, documents.size, truncated))
    }

    is UpdateQueryResult -> StatementResult(updateCount = rowsUpdated)

    is ErrorQueryResult -> StatementResult(error = StatementError(errorCode, message))
}

/** Builds the agent-facing views of a request, given what the user may do to it. */
class RequestViews(private val userId: String, private val baseUrl: String?) {

    fun summary(details: ExecutionRequestDetails, permissions: Set<Permission>): RequestSummary {
        val request = details.request
        val reviewStatus = details.resolveReviewStatus()
        val executionStatus = details.resolveExecutionStatus()
        return RequestSummary(
            id = request.getId(),
            title = request.title,
            type = request.type,
            connectionId = request.connection.getId(),
            connectionKind = request.connection.kind(),
            author = request.author.email,
            reviewStatus = reviewStatus,
            executionStatus = executionStatus,
            source = request.source,
            createdAt = request.createdAt,
            // As in the UI: the permission, plus the rules the review endpoint applies itself.
            canReview = Permission.EXECUTION_REQUEST_REVIEW in permissions &&
                request.author.getId() != userId &&
                reviewStatus in setOf(ReviewStatus.AWAITING_APPROVAL, ReviewStatus.CHANGE_REQUESTED),
            // The permission alone also holds for unapproved requests on connections with dry runs, which
            // agents can't do, so it is narrowed to requests that can actually run.
            canExecute = Permission.EXECUTION_REQUEST_EXECUTE in permissions &&
                reviewStatus == ReviewStatus.APPROVED &&
                executionStatus != ExecutionStatus.EXECUTED,
            url = baseUrl?.let { "$it/requests/${request.getId()}" },
        )
    }

    fun detail(details: ExecutionRequestDetailsWithRoles): RequestDetail {
        val summary = summary(details.details, details.permissions)
        val request = details.request
        return RequestDetail(
            id = summary.id,
            title = summary.title,
            type = summary.type,
            connectionId = summary.connectionId,
            connectionKind = summary.connectionKind,
            author = summary.author,
            reviewStatus = summary.reviewStatus,
            executionStatus = summary.executionStatus,
            source = summary.source,
            createdAt = summary.createdAt,
            canReview = summary.canReview,
            canExecute = summary.canExecute,
            url = summary.url,
            description = request.description,
            statement = (request as? DatasourceExecutionRequest)?.statement,
            pod = (request as? KubernetesExecutionRequest)?.let {
                Pod(it.namespace ?: "", it.podName ?: "", it.containerName?.takeIf(String::isNotBlank))
            },
            command = (request as? KubernetesExecutionRequest)?.command,
            durationMinutes = request.temporaryAccessDuration?.toMinutes(),
            approvals = Approvals.from(details.getApprovalProgress(), details.resolvedRoles),
            history = details.events.sortedBy {
                it.createdAt
            }.takeLast(MAX_HISTORY_ENTRIES).map { it.toHistoryEntry() },
        )
    }
}

private fun Event.toHistoryEntry(): HistoryEntry {
    val base = HistoryEntry(type = type, author = author.email, at = createdAt)
    return when (this) {
        is CommentEvent -> base.copy(comment = comment)
        is ReviewEvent -> base.copy(action = action, comment = comment)
        is EditEvent -> base.copy(statement = previousQuery ?: previousCommand)
        is ExecuteEvent -> base.copy(statement = query ?: command)
        else -> base
    }
}
