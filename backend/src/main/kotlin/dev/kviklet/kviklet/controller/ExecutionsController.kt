package dev.kviklet.kviklet.controller

import dev.kviklet.kviklet.security.EnterpriseOnly
import dev.kviklet.kviklet.service.ExecutionRequestService
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

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

    // The following function is not MIT licensed
    @GetMapping("/export")
    @EnterpriseOnly("Audit Log Export")
    fun exportExecutions(): ResponseEntity<String> {
        val exportContent = executionRequestService.exportExecutionsAsText()
        val fileNameDate = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss"))

        val headers = HttpHeaders()
        headers.contentType = MediaType.TEXT_PLAIN
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"auditlog-export-$fileNameDate.txt\"")

        return ResponseEntity(exportContent, headers, HttpStatus.OK)
    }
}
