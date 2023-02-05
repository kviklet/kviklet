package com.example.executiongate.controller

import com.example.executiongate.service.ExecutionRequestService
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime
import javax.validation.Valid


data class CreateExecutionRequest(
    val statement: String,
    val createdAt: LocalDateTime
)


@RestController()
@Validated
@RequestMapping("/execution-request")
class ExecutionRequestController(
    val executionRequestService: ExecutionRequestService
) {

    @PostMapping("/")
    fun create(@Valid @RequestBody connection: CreateExecutionRequest): ResponseEntity<Any> {
        executionRequestService.create(connection.statement)
        return ResponseEntity.noContent().build()
    }

}

