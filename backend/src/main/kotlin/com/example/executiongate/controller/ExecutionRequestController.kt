package com.example.executiongate.controller

import com.example.executiongate.service.ExecutionRequestService
import com.example.executiongate.service.QueryResult
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.CrossOrigin
import java.time.LocalDateTime
import javax.validation.Valid

data class CreateExecutionRequest(
    val statement: String,
    val datasourceId: String,
    val createdAt: LocalDateTime
)
data class CreateExecutionResponse(
    val id: String
)

@RestController()
@Validated
@CrossOrigin(origins = ["http://localhost:3000"])
@RequestMapping("/execution-request")
class ExecutionRequestController(
    val executionRequestService: ExecutionRequestService
) {

    @PostMapping("/")
    fun create(
        @Valid @RequestBody
        request: CreateExecutionRequest
    ): CreateExecutionResponse {
        val id = executionRequestService.create(request.datasourceId, request.statement)
        return CreateExecutionResponse(id = id)
    }

    @PostMapping("/{requestId}")
    fun execute(@PathVariable requestId: String): QueryResult {
        return executionRequestService.execute(requestId)
    }
}
