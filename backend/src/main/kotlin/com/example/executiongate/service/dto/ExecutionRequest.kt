package com.example.executiongate.service.dto

import com.example.executiongate.db.User
import java.io.Serializable
import java.time.LocalDateTime

@JvmInline
value class ExecutionRequestId(private val id: String): Serializable {
    override fun toString() = id
}

enum class ReviewStatus {
    AWAITING_APPROVAL,
    APPROVED,
}

/**
 * A DTO for the {@link com.example.executiongate.db.ExecutionRequestEntity} entity
 */
data class ExecutionRequest(
    val id: ExecutionRequestId,
    val connection: DatasourceConnection,
    val title: String,
    val description: String?,
    val statement: String,
    val readOnly: Boolean,
    val reviewStatus: ReviewStatus,
    val executionStatus: String,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val author: User
)


data class ExecutionRequestDetails(
    val request: ExecutionRequest,
    val events: Set<Event>
) {
    fun addEvent(event: Event): ExecutionRequestDetails {
        val allEvents = events + event

        val numReviews = events.count { it.type == EventType.REVIEW }
        val reviewStatus = if (numReviews >= request.connection.reviewConfig.numTotalRequired) {
            ReviewStatus.APPROVED
        } else {
            ReviewStatus.AWAITING_APPROVAL
        }

        return copy(events = allEvents, request = request.copy(reviewStatus = reviewStatus))
    }
}
