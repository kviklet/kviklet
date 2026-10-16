// This file is not MIT licensed
package dev.kviklet.kviklet.mcp

import dev.kviklet.kviklet.controller.CreateDatasourceExecutionRequestRequest
import dev.kviklet.kviklet.controller.CreateExecutionRequestRequest
import dev.kviklet.kviklet.controller.CreateKubernetesExecutionRequestRequest
import dev.kviklet.kviklet.controller.CreateReviewRequest
import dev.kviklet.kviklet.controller.UpdateExecutionRequestRequest
import dev.kviklet.kviklet.security.PermissionResolver
import dev.kviklet.kviklet.service.BaseUrlResolver
import dev.kviklet.kviklet.service.ConnectionService
import dev.kviklet.kviklet.service.ExecutionRequestService
import dev.kviklet.kviklet.service.dto.ConnectionId
import dev.kviklet.kviklet.service.dto.DatasourceExecutionRequest
import dev.kviklet.kviklet.service.dto.ExecutionRequestId
import dev.kviklet.kviklet.service.dto.ExecutionStatus
import dev.kviklet.kviklet.service.dto.KubernetesExecutionRequest
import dev.kviklet.kviklet.service.dto.RequestType
import dev.kviklet.kviklet.service.dto.ReviewAction
import dev.kviklet.kviklet.service.dto.ReviewStatus
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.ai.mcp.annotation.McpTool.McpAnnotations
import org.springframework.ai.mcp.annotation.McpToolParam
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.time.format.DateTimeParseException

/** Finding, creating, editing, reviewing and closing requests. */
@Component
class RequestTools(
    private val executionRequestService: ExecutionRequestService,
    private val connectionService: ConnectionService,
    private val permissionResolver: PermissionResolver,
    private val baseUrlResolver: BaseUrlResolver,
) {

    @McpTool(
        name = "list_connections",
        description = "Lists the connections the user can see: databases and Kubernetes clusters. " +
            "Use a connection's id to create a request on it.",
        annotations = McpAnnotations(readOnlyHint = true, destructiveHint = false),
    )
    fun listConnections(): List<ConnectionResult> = connectionService.listConnections().map(ConnectionResult::from)

    @McpTool(
        name = "list_requests",
        description = "Lists requests, newest first. Pass nextCursor back as cursor to get the next page.",
        annotations = McpAnnotations(readOnlyHint = true, destructiveHint = false),
    )
    fun listRequests(
        @McpToolParam(required = false, description = "Only requests with one of these review statuses")
        reviewStatuses: List<ReviewStatus>?,
        @McpToolParam(required = false, description = "Only requests with one of these execution statuses")
        executionStatuses: List<ExecutionStatus>?,
        @McpToolParam(required = false, description = "Only requests on this connection")
        connectionId: String?,
        @McpToolParam(required = false, description = "Only the user's own requests")
        mine: Boolean?,
        @McpToolParam(required = false, description = "Page size, 20 by default and at most 100")
        limit: Int?,
        @McpToolParam(required = false, description = "The nextCursor of the previous page")
        cursor: String?,
    ): RequestList {
        val after = cursor?.let {
            try {
                LocalDateTime.parse(it)
            } catch (e: DateTimeParseException) {
                throw IllegalArgumentException("Invalid cursor, pass the nextCursor of the previous page")
            }
        }
        val list = executionRequestService.list(
            reviewStatuses = reviewStatuses?.toSet(),
            executionStatuses = executionStatuses?.toSet(),
            types = null,
            connectionIds = connectionId?.let { setOf(ConnectionId(it)) },
            authorId = if (mine == true) currentUser().id else null,
            createdAfter = null,
            createdBefore = null,
            after = after,
            limit = (limit ?: 20).coerceIn(1, 100),
        )
        val user = currentUser()
        val policies = permissionResolver.policiesFor(user.id)
        val views = views()
        return RequestList(
            requests = list.requests.map { views.summary(it, permissionResolver.resolve(user, policies, it)) },
            nextCursor = list.cursor?.takeIf { list.hasMore }?.toString(),
        )
    }

    @McpTool(
        name = "get_request",
        description = "Gets a request with its approvals and its latest history.",
        annotations = McpAnnotations(readOnlyHint = true, destructiveHint = false),
    )
    fun getRequest(@McpToolParam(description = "The request id") id: String): RequestDetail =
        views().detail(executionRequestService.get(ExecutionRequestId(id)))

    @McpTool(
        name = "create_query_request",
        description = "Asks for review of one or more statements to run once on a database connection. " +
            "After approval, run them with execute_request.",
        annotations = McpAnnotations(destructiveHint = false),
    )
    fun createQueryRequest(
        @McpToolParam(description = "The id of a database connection") connectionId: String,
        @McpToolParam(description = "A short title for reviewers") title: String,
        @McpToolParam(description = "Why the statements need to run") description: String,
        @McpToolParam(description = "The statements to run, e.g. SQL or a MongoDB command") statement: String,
    ): RequestDetail = create(
        connectionId,
        ConnectionKind.DATABASE,
        CreateDatasourceExecutionRequestRequest(
            connectionId = ConnectionId(connectionId),
            title = title,
            type = RequestType.SingleExecution,
            description = description,
            statement = statement,
        ),
    )

    @McpTool(
        name = "create_database_access_request",
        description = "Asks for temporary access to a database connection. After approval, run statements " +
            "with run_with_access or connect a database client with start_proxy.",
        annotations = McpAnnotations(destructiveHint = false),
    )
    fun createDatabaseAccessRequest(
        @McpToolParam(description = "The id of a database connection") connectionId: String,
        @McpToolParam(description = "A short title for reviewers") title: String,
        @McpToolParam(description = "Why the access is needed") description: String,
        @McpToolParam(description = "How long the access lasts once first used, in minutes") durationMinutes: Long,
    ): RequestDetail = create(
        connectionId,
        ConnectionKind.DATABASE,
        CreateDatasourceExecutionRequestRequest(
            connectionId = ConnectionId(connectionId),
            title = title,
            type = RequestType.TemporaryAccess,
            description = description,
            statement = null,
            temporaryAccessDuration = durationMinutes,
        ),
    )

    @McpTool(
        name = "create_pod_command_request",
        description = "Asks for review of a shell command to run once in a Kubernetes pod. Find pods with " +
            "list_pods. After approval, run it with execute_request.",
        annotations = McpAnnotations(destructiveHint = false),
    )
    fun createPodCommandRequest(
        @McpToolParam(description = "The id of a Kubernetes connection") connectionId: String,
        @McpToolParam(description = "A short title for reviewers") title: String,
        @McpToolParam(description = "Why the command needs to run") description: String,
        @McpToolParam(description = "The pod's namespace") namespace: String,
        @McpToolParam(description = "The pod's name") podName: String,
        @McpToolParam(required = false, description = "The container, if not the pod's default one")
        containerName: String?,
        @McpToolParam(description = "The shell command to run") command: String,
    ): RequestDetail = create(
        connectionId,
        ConnectionKind.KUBERNETES,
        CreateKubernetesExecutionRequestRequest(
            connectionId = ConnectionId(connectionId),
            title = title,
            type = RequestType.SingleExecution,
            description = description,
            namespace = namespace,
            podName = podName,
            containerName = containerName,
            command = command,
        ),
    )

    @McpTool(
        name = "create_pod_access_request",
        description = "Asks for temporary access to a Kubernetes pod. Find pods with list_pods. After " +
            "approval, run commands with run_with_access.",
        annotations = McpAnnotations(destructiveHint = false),
    )
    fun createPodAccessRequest(
        @McpToolParam(description = "The id of a Kubernetes connection") connectionId: String,
        @McpToolParam(description = "A short title for reviewers") title: String,
        @McpToolParam(description = "Why the access is needed") description: String,
        @McpToolParam(description = "The pod's namespace") namespace: String,
        @McpToolParam(description = "The pod's name") podName: String,
        @McpToolParam(required = false, description = "The container, if not the pod's default one")
        containerName: String?,
        @McpToolParam(description = "How long the access lasts once first used, in minutes") durationMinutes: Long,
    ): RequestDetail = create(
        connectionId,
        ConnectionKind.KUBERNETES,
        CreateKubernetesExecutionRequestRequest(
            connectionId = ConnectionId(connectionId),
            title = title,
            type = RequestType.TemporaryAccess,
            description = description,
            namespace = namespace,
            podName = podName,
            containerName = containerName,
            command = null,
            temporaryAccessDuration = durationMinutes,
        ),
    )

    private fun create(
        connectionId: String,
        kind: ConnectionKind,
        request: CreateExecutionRequestRequest,
    ): RequestDetail {
        val connection = connectionService.getDatasourceConnection(ConnectionId(connectionId))
        if (connection.kind() != kind) {
            val tools = when (connection.kind()) {
                ConnectionKind.DATABASE -> "create_query_request or create_database_access_request"
                ConnectionKind.KUBERNETES -> "create_pod_command_request or create_pod_access_request"
            }
            throw IllegalArgumentException("$connectionId is a ${connection.kind().label} connection, use $tools")
        }
        val created = executionRequestService.create(ConnectionId(connectionId), request, currentUser().id)
        return getRequest(created.getId())
    }

    @McpTool(
        name = "update_request",
        description = "Edits the user's own request. Pass only the fields to change. Changing what runs " +
            "resets the approvals.",
        annotations = McpAnnotations(destructiveHint = false),
    )
    fun updateRequest(
        @McpToolParam(description = "The request id") id: String,
        @McpToolParam(required = false) title: String?,
        @McpToolParam(required = false) description: String?,
        @McpToolParam(required = false, description = "Database requests only") statement: String?,
        @McpToolParam(required = false, description = "Kubernetes requests only") namespace: String?,
        @McpToolParam(required = false, description = "Kubernetes requests only") podName: String?,
        @McpToolParam(required = false, description = "Kubernetes requests only") containerName: String?,
        @McpToolParam(required = false, description = "Kubernetes requests only") command: String?,
        @McpToolParam(required = false, description = "Temporary access requests only") durationMinutes: Long?,
    ): RequestDetail {
        val requestId = ExecutionRequestId(id)
        // Only the kind is read, to point the agent at the right fields: it never changes, so unlike the
        // field values it can't go stale before the update. The service leaves missing fields unchanged.
        when (executionRequestService.get(requestId).request) {
            is DatasourceExecutionRequest -> if (listOf(namespace, podName, containerName, command).any {
                    it != null
                }
            ) {
                throw IllegalArgumentException("$id is a database request, it has no pod or command")
            }

            is KubernetesExecutionRequest -> if (statement != null) {
                throw IllegalArgumentException("$id is a Kubernetes request, change its command instead")
            }
        }
        val update = UpdateExecutionRequestRequest(
            title = title,
            description = description,
            statement = statement,
            namespace = namespace,
            podName = podName,
            containerName = containerName,
            command = command,
            temporaryAccessDuration = durationMinutes,
        )
        return views().detail(executionRequestService.update(requestId, update, currentUser().id))
    }

    @McpTool(
        name = "review_request",
        description = "Approves a request, requests changes or rejects it, as the user. Users can't review " +
            "their own requests. A rejection is final.",
        annotations = McpAnnotations(destructiveHint = true),
    )
    fun reviewRequest(
        @McpToolParam(description = "The request id") id: String,
        @McpToolParam(description = "APPROVE, REQUEST_CHANGE or REJECT") action: ReviewAction,
        @McpToolParam(description = "The review comment") comment: String,
    ): ReviewResult {
        val requestId = ExecutionRequestId(id)
        executionRequestService.createReview(requestId, CreateReviewRequest(comment, action), currentUser().id)
        val request = executionRequestService.get(requestId)
        return ReviewResult(id, request.resolveReviewStatus(), views().detail(request).approvals)
    }

    @McpTool(
        name = "close_request",
        description = "Withdraws the user's own request. A closed request can't be reopened.",
        annotations = McpAnnotations(destructiveHint = true),
    )
    fun closeRequest(
        @McpToolParam(description = "The request id") id: String,
        @McpToolParam(required = false, description = "Why the request is closed") comment: String?,
    ): CloseResult {
        val requestId = ExecutionRequestId(id)
        executionRequestService.close(requestId, comment ?: "", currentUser().id)
        return CloseResult(id, executionRequestService.get(requestId).resolveReviewStatus())
    }

    private fun views() = RequestViews(currentUser().id, baseUrlResolver.resolve())
}

private val ConnectionKind.label get() = name.lowercase()
