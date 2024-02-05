package dev.kviklet.kviklet.service.dto

import dev.kviklet.kviklet.db.User
import dev.kviklet.kviklet.security.Permission
import dev.kviklet.kviklet.security.PolicyGrantedAuthority
import dev.kviklet.kviklet.security.Resource
import dev.kviklet.kviklet.security.SecuredDomainId
import dev.kviklet.kviklet.security.SecuredDomainObject
import dev.kviklet.kviklet.security.UserDetailsWithId
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

enum class RequestType {
    SingleQuery,
    TemporaryAccess,
}

/**
 * A DTO for the {@link dev.kviklet.kviklet.db.ExecutionRequestEntity} entity
 */
data class ExecutionRequest(
    val id: ExecutionRequestId?,
    val connection: DatasourceConnection,
    val title: String,
    val type: RequestType,
    val description: String?,
    val statement: String?,
    val readOnly: Boolean,
    val executionStatus: String,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val author: User,
)

data class ExecutionRequestDetails(
    val request: ExecutionRequest,
    val events: MutableSet<Event>,
) : SecuredDomainObject {
    fun addEvent(event: Event): ExecutionRequestDetails {
        events.add(event)
        return this
    }

    fun resolveReviewStatus(): ReviewStatus {
        val reviewConfig = request.connection.reviewConfig
        val latestEdit = events.filter { it.type == EventType.EDIT }.sortedBy { it.createdAt }.lastOrNull()
        val latestEditTimeStamp = latestEdit?.createdAt ?: LocalDateTime.MIN
        val numReviews = events.filter {
            it.type == EventType.REVIEW && it is ReviewEvent && it.action == ReviewAction.APPROVE &&
                it.createdAt > latestEditTimeStamp
        }.groupBy { it.author.getId() }.count()
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
        else -> null
    }

    override fun auth(
        permission: Permission,
        userDetails: UserDetailsWithId,
        policies: List<PolicyGrantedAuthority>,
    ): Boolean {
        return when (permission) {
            Permission.EXECUTION_REQUEST_EDIT -> request.author.getId() == userDetails.id
            Permission.EXECUTION_REQUEST_EXECUTE -> request.author.getId() == userDetails.id && isExecutable()
            else -> true
        }
    }

    private fun isExecutable(): Boolean {
        return resolveReviewStatus() == ReviewStatus.APPROVED
    }
}

data class ExecutionProxy(
    val request: ExecutionRequest,
    val port: Int,
    val username: String,
    val password: String,
    val startTime: LocalDateTime,
) : SecuredDomainObject {
    override fun getId() = request.id.toString()

    override fun getDomainObjectType() = Resource.EXECUTION_REQUEST

    override fun getRelated(resource: Resource): SecuredDomainObject? = null
}
