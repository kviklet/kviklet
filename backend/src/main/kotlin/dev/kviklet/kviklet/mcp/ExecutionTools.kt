// This file is not MIT licensed
package dev.kviklet.kviklet.mcp

import dev.kviklet.kviklet.service.ExecutionRequestService
import dev.kviklet.kviklet.service.KubernetesService
import dev.kviklet.kviklet.service.dto.ExecutionRequestId
import dev.kviklet.kviklet.service.dto.RequestType
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.ai.mcp.annotation.McpTool.McpAnnotations
import org.springframework.ai.mcp.annotation.McpToolParam
import org.springframework.stereotype.Component

private const val TRUNCATION_HINT = "Results are cut to $MAX_RESULT_ROWS rows, $MAX_CELL_CHARS characters per " +
    "value and $MAX_POD_OUTPUT_CHARS characters of pod output, and marked truncated. For more, use " +
    "start_proxy on a database access request."

/** Running approved requests, and what is needed to set them up. */
@Component
class ExecutionTools(
    private val executionRequestService: ExecutionRequestService,
    private val kubernetesService: KubernetesService,
) {

    @McpTool(
        name = "execute_request",
        description = "Runs an approved request's statements or command and returns the result. Only for " +
            "requests that run once; temporary access requests use run_with_access. $TRUNCATION_HINT",
        annotations = McpAnnotations(destructiveHint = true),
    )
    fun executeRequest(@McpToolParam(description = "The request id") id: String): ToolExecutionResult {
        val requestId = ExecutionRequestId(id)
        when (executionRequestService.get(requestId).request.type) {
            RequestType.SingleExecution -> Unit

            RequestType.TemporaryAccess -> throw IllegalArgumentException(
                "$id is a temporary access request, use run_with_access",
            )

            RequestType.Dump -> throw IllegalArgumentException("$id is a dump request, download it in Kviklet")
        }
        return executionRequestService.execute(requestId, null, currentUser().id).toToolResult()
    }

    @McpTool(
        name = "run_with_access",
        description = "Runs a statement, or a shell command for pods, on the user's own approved temporary " +
            "access request. $TRUNCATION_HINT",
        annotations = McpAnnotations(destructiveHint = true),
    )
    fun runWithAccess(
        @McpToolParam(description = "The id of a temporary access request") id: String,
        @McpToolParam(description = "The statement, or the shell command for pods") statement: String,
    ): ToolExecutionResult {
        val requestId = ExecutionRequestId(id)
        if (executionRequestService.get(requestId).request.type != RequestType.TemporaryAccess) {
            throw IllegalArgumentException("$id is not a temporary access request, use execute_request")
        }
        return executionRequestService.execute(requestId, statement, currentUser().id).toToolResult()
    }

    @McpTool(
        name = "start_proxy",
        description = "Starts a database proxy for the user's own approved database access request and " +
            "returns credentials for a database client like psql or mysql, so large results don't go " +
            "through the agent. The host must be reachable from where the client runs. PostgreSQL, MySQL " +
            "and MariaDB only.",
        annotations = McpAnnotations(destructiveHint = true),
    )
    fun startProxy(@McpToolParam(description = "The id of a database access request") id: String): ProxyResult =
        ProxyResult.from(executionRequestService.proxy(ExecutionRequestId(id), currentUser()))

    @McpTool(
        name = "list_pods",
        description = "Lists the running pods of the Kubernetes cluster, for creating pod requests.",
        annotations = McpAnnotations(readOnlyHint = true, destructiveHint = false),
    )
    fun listPods(
        @McpToolParam(required = false, description = "Only pods in this namespace") namespace: String?,
    ): List<PodResult> = kubernetesService.listPods()
        .map(PodResult::from)
        .filter { namespace == null || it.namespace == namespace }
}
