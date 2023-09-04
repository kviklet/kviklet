package com.example.executiongate.service.dto

import com.example.executiongate.db.User
import java.io.Serializable
import java.time.LocalDateTime

@JvmInline
value class ExecutionRequestId(private val id: String) : Serializable {
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
    val executionStatus: String,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val author: User,
)

data class ExecutionRequestDetails(
    val request: ExecutionRequest,
    val events: Set<Event>,
) {
    fun addEvent(event: Event): ExecutionRequestDetails {
        val allEvents = events + event

        return copy(events = allEvents)
    }

    fun resolveReviewStatus(): ReviewStatus {
        val reviewConfig = request.connection.reviewConfig
        val numReviews = events.filter {
            it.type == EventType.REVIEW && it is ReviewEvent && it.action == ReviewAction.APPROVE
        }.groupBy { it.author.id }.count()
        val reviewStatus = if (numReviews >= reviewConfig.numTotalRequired) {
            ReviewStatus.APPROVED
        } else {
            ReviewStatus.AWAITING_APPROVAL
        }

        return reviewStatus
    }
}
