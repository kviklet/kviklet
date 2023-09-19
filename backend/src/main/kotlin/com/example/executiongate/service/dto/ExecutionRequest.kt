package com.example.executiongate.service.dto

import com.example.executiongate.db.User
import com.example.executiongate.security.Permission
import com.example.executiongate.security.Resource
import com.example.executiongate.security.SecuredDomainId
import com.example.executiongate.security.SecuredDomainObject
import com.example.executiongate.security.UserDetailsWithId
import java.io.Serializable
import java.time.LocalDateTime

@JvmInline
value class ExecutionRequestId(private val id: String) : Serializable, SecuredDomainId {
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
) : SecuredDomainObject {
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

    override fun getId() = request.id.toString()

    override fun getDomainObjectType() = Resource.EXECUTION_REQUEST

    override fun getRelated(resource: Resource) = when (resource) {
        Resource.DATASOURCE_CONNECTION -> request.connection
        Resource.DATASOURCE -> request.connection.datasource
        else -> null
    }

    override fun auth(permission: Permission, userDetails: UserDetailsWithId): Boolean {
        return when (permission) {
            Permission.EXECUTION_REQUEST_EDIT -> request.author.id == userDetails.id
            Permission.EXECUTION_REQUEST_EXECUTE -> request.author.id == userDetails.id && isExecutable()
            else -> true
        }
    }

    private fun isExecutable(): Boolean {
        return resolveReviewStatus() == ReviewStatus.APPROVED
    }
}
