package dev.kviklet.kviklet.service.dto

import dev.kviklet.kviklet.db.User
import dev.kviklet.kviklet.security.Permission
import dev.kviklet.kviklet.security.PolicyGrantedAuthority
import dev.kviklet.kviklet.security.Resource
import dev.kviklet.kviklet.security.SecuredDomainId
import dev.kviklet.kviklet.security.SecuredDomainObject
import dev.kviklet.kviklet.security.UserDetailsWithId
import dev.kviklet.kviklet.service.QueryResult
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
    SingleExecution,
    TemporaryAccess,
}

/**
 * A DTO for the {@link dev.kviklet.kviklet.db.ExecutionRequestEntity} entity
 */
sealed class ExecutionRequest(
    open val id: ExecutionRequestId?,
    open val connection: Connection,
    open val title: String,
    open val type: RequestType,
    open val description: String?,
    open val executionStatus: String,
    open val createdAt: LocalDateTime = LocalDateTime.now(),
    open val author: User,
)

sealed class ExecutionResult(open val executionRequestId: ExecutionRequestId) : SecuredDomainObject {
    override fun getId() = executionRequestId.toString()
    override fun getDomainObjectType() = Resource.EXECUTION_REQUEST
    override fun getRelated(resource: Resource) = null
}

data class DBExecutionResult(
    override val executionRequestId: ExecutionRequestId,
    val results: List<QueryResult>,
) : ExecutionResult(executionRequestId)

data class KubernetesExecutionResult(
    override val executionRequestId: ExecutionRequestId,
    val errors: List<String>,
    val messages: List<String>,
    val finished: Boolean = true,
    val exitCode: Int? = 0,
) : ExecutionResult(executionRequestId)

data class DatasourceExecutionRequest(
    override val id: ExecutionRequestId?,
    override val connection: DatasourceConnection,
    override val title: String,
    override val type: RequestType,
    override val description: String?,
    val statement: String?,
    val readOnly: Boolean,
    override val executionStatus: String,
    override val createdAt: LocalDateTime = LocalDateTime.now(),
    override val author: User,
) : ExecutionRequest(id, connection, title, type, description, executionStatus, createdAt, author)

data class KubernetesExecutionRequest(
    override val id: ExecutionRequestId?,
    override val connection: KubernetesConnection,
    override val title: String,
    override val type: RequestType,
    override val description: String?,
    override val executionStatus: String,
    override val createdAt: LocalDateTime = LocalDateTime.now(),
    override val author: User,
    val namespace: String?,
    val podName: String?,
    val containerName: String?,
    val command: String?,
) : ExecutionRequest(
    id,
    connection,
    title,
    type,
    description,
    executionStatus,
    createdAt,
    author,
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
