package dev.kviklet.kviklet.controller

import dev.kviklet.kviklet.service.ExecutionRequestService
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime

data class ExecutionLogResponse(
    val requestId: String,
    val name: String,
    val Statement: String,
    val connectionId: String,
    val executionTime: LocalDateTime,
)

data class ExecutionsResponse(val executions: List<ExecutionLogResponse>)

@RestController()
@Validated
@RequestMapping("/executions")
@Tag(
    name = "Executions",
    description = "List all exections that have been run",
)
class ExecutionsController(private val executionRequestService: ExecutionRequestService) {
    @GetMapping("/")
    fun getExecutions(): ExecutionsResponse {
        val executions = executionRequestService.getExecutions()
        return ExecutionsResponse(
            executions = executions.sortedByDescending { it.createdAt }.map {
                ExecutionLogResponse(
                    requestId = it.request.getId(),
                    name = it.author.fullName ?: "",
                    Statement = it.query ?: it.command ?: "",
                    connectionId = it.request.connection.getId(),
                    executionTime = it.createdAt,
                )
            },
        )
    }
}
