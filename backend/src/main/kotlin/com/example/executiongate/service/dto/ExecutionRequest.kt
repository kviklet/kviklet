package com.example.executiongate.service.dto

import java.io.Serializable
import java.time.LocalDateTime

@JvmInline
value class ExecutionRequestId(private val id: String): Serializable {
    override fun toString() = id
}

/**
 * A DTO for the {@link com.example.executiongate.db.ExecutionRequestEntity} entity
 */
data class ExecutionRequest(
    val id: ExecutionRequestId,
    val connection: DatasourceConnectionDto,
    val title: String,
    val description: String?,
    val statement: String,
    val readOnly: Boolean,
    val reviewStatus: String,
    val executionStatus: String,
    val createdAt: LocalDateTime = LocalDateTime.now()
)