package dev.kviklet.kviklet.service.dto

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue
import dev.kviklet.kviklet.db.User
import dev.kviklet.kviklet.security.Permission
import dev.kviklet.kviklet.security.PolicyGrantedAuthority
import dev.kviklet.kviklet.security.Resource
import dev.kviklet.kviklet.security.SecuredDomainId
import dev.kviklet.kviklet.security.SecuredDomainObject
import dev.kviklet.kviklet.security.UserDetailsWithId
import net.sf.jsqlparser.JSQLParserException
import net.sf.jsqlparser.parser.CCJSqlParserUtil
import java.io.Serializable
import java.time.Duration
import java.time.LocalDateTime

data class ExecutionRequestId
@JsonCreator constructor(private val id: String) :
    Serializable,
    SecuredDomainId {
    @JsonValue
    override fun toString() = id
}

enum class ReviewStatus {
    AWAITING_APPROVAL,
    APPROVED,
    REJECTED,
    CHANGE_REQUESTED,
}

enum class ExecutionStatus {
    EXECUTABLE,
    ACTIVE,
    EXECUTED,
}

enum class RequestType {
    SingleExecution,
    TemporaryAccess,
    Dump,
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
    open val createdAt: LocalDateTime = utcTimeNow(),
    open val author: User,
    open val temporaryAccessDuration: Duration? = null,
    open val reviewer: User?,
) : SecuredDomainObject {
    fun getId() = id.toString()
    override fun getSecuredObjectId() = connection.getSecuredObjectId()
    override fun getDomainObjectType() = Resource.EXECUTION_REQUEST

    override fun getRelated(resource: Resource) = when (resource) {
        Resource.EXECUTION_REQUEST -> this
        Resource.DATASOURCE_CONNECTION -> connection
        else -> null
    }
}

sealed class ExecutionResult(open val executionRequest: ExecutionRequestDetails) : SecuredDomainObject {
    override fun getSecuredObjectId() = executionRequest.getSecuredObjectId()
    override fun getDomainObjectType() = executionRequest.getDomainObjectType()
    override fun getRelated(resource: Resource) = executionRequest.getRelated(resource)
}

data class DBExecutionResult(override val executionRequest: ExecutionRequestDetails, val results: List<QueryResult>) :
    ExecutionResult(executionRequest)

data class KubernetesExecutionResult(
    override val executionRequest: ExecutionRequestDetails,
    val errors: List<String>,
    val messages: List<String>,
    val finished: Boolean = true,
    val exitCode: Int? = 0,
) : ExecutionResult(executionRequest)

data class DatasourceExecutionRequest(
    override val id: ExecutionRequestId?,
    override val connection: DatasourceConnection,
    override val title: String,
    override val type: RequestType,
    override val description: String?,
    val statement: String?,
    override val executionStatus: String,
    override val createdAt: LocalDateTime = utcTimeNow(),
    override val author: User,
    override val temporaryAccessDuration: Duration?,
    override val reviewer: User?,
) : ExecutionRequest(
    id, connection, title, type, description, executionStatus, createdAt,
    author,
    temporaryAccessDuration,
    reviewer
)

data class KubernetesExecutionRequest(
    override val id: ExecutionRequestId?,
    override val connection: KubernetesConnection,
    override val title: String,
    override val type: RequestType,
    override val description: String?,
    override val executionStatus: String,
    override val createdAt: LocalDateTime = utcTimeNow(),
    override val author: User,
    val namespace: String?,
    val podName: String?,
    val containerName: String?,
    val command: String?,
    override val temporaryAccessDuration: Duration?,
) : ExecutionRequest(
    id,
    connection,
    title,
    type,
    description,
    executionStatus,
    createdAt,
    author,
    temporaryAccessDuration,
    null
)

data class ExecutionRequestDetails(val request: ExecutionRequest, val events: MutableSet<Event>) :
    SecuredDomainObject {
    fun addEvent(event: Event): ExecutionRequestDetails {
        events.add(event)
        return this
    }

    fun resolveReviewStatus(): ReviewStatus {
        val reviewConfig = request.connection.reviewConfig
        val numReviews = getApprovalCount()

        if (isRejected()) {
            return ReviewStatus.REJECTED
        }

        if (activeRequestedChanges() > 0) {
            return ReviewStatus.CHANGE_REQUESTED
        }
        val reviewStatus = if (numReviews >= reviewConfig.numTotalRequired) {
            ReviewStatus.APPROVED
        } else {
            ReviewStatus.AWAITING_APPROVAL
        }

        return reviewStatus
    }

    fun isRejected(): Boolean {
        val rejectedReview = events.filter { it.type == EventType.REVIEW }
            .mapNotNull { it as? ReviewEvent }
            .find { it.action == ReviewAction.REJECT }

        return rejectedReview != null
    }

    fun getApprovalCount(): Int {
        val latestEdit = events.filter { it.type == EventType.EDIT }.sortedBy { it.createdAt }.lastOrNull()
        val latestEditTimeStamp = latestEdit?.createdAt ?: LocalDateTime.MIN
        val numReviews = events.filter {
            it.type == EventType.REVIEW &&
                it is ReviewEvent &&
                it.action == ReviewAction.APPROVE &&
                it.createdAt > latestEditTimeStamp
        }.groupBy { it.author.getId() }.count()
        return numReviews
    }

    fun activeRequestedChanges(): Int {
        val changesRequested = events.filter { it.type == EventType.REVIEW }
            .mapNotNull { it as? ReviewEvent }
            .filter { it.action == ReviewAction.REQUEST_CHANGE }

        val approvals = events.filter { it.type == EventType.REVIEW }
            .mapNotNull { it as? ReviewEvent }
            .filter { it.action == ReviewAction.APPROVE }

        val openChangeRequests = changesRequested.filter { requestChange ->
            approvals.none { it.author == requestChange.author && it.createdAt.isAfter(requestChange.createdAt) }
        }

        return openChangeRequests.size
    }

    fun resolveExecutionStatus(): ExecutionStatus {
        when (request.type) {
            RequestType.SingleExecution, RequestType.Dump -> {
                val executions = events.filter { it.type == EventType.EXECUTE }
                request.connection.maxExecutions?.let { maxExecutions ->
                    if (maxExecutions == 0) { // magic number for unlimited executions
                        return ExecutionStatus.EXECUTABLE
                    }
                    if (executions.size >= maxExecutions) {
                        return ExecutionStatus.EXECUTED
                    }
                }
                return ExecutionStatus.EXECUTABLE
            }

            RequestType.TemporaryAccess -> {
                val executions = events.filter { it.type == EventType.EXECUTE }
                if (executions.isEmpty()) {
                    return ExecutionStatus.EXECUTABLE
                }
                val firstExecution = executions.minBy { it.createdAt }
                // Default to 1 hour if not set, can be for old temporary access requests all new ones should have this set
                val duration = request.temporaryAccessDuration ?: Duration.ofMinutes(60)

                if (duration.isZero) {
                    // If duration is zero, the request is always active
                    return ExecutionStatus.ACTIVE
                }
                return if (firstExecution.createdAt.plus(duration) < utcTimeNow()) {
                    ExecutionStatus.EXECUTED
                } else {
                    ExecutionStatus.ACTIVE
                }
            }
        }
    }

    fun getId() = request.getId()

    override fun getSecuredObjectId() = request.getSecuredObjectId()

    override fun getDomainObjectType() = request.getDomainObjectType()

    override fun getRelated(resource: Resource) = request.getRelated(resource)

    override fun auth(
        permission: Permission,
        userDetails: UserDetailsWithId,
        policies: List<PolicyGrantedAuthority>,
    ): Boolean = when (permission) {
        Permission.EXECUTION_REQUEST_EDIT -> request.author.getId() == userDetails.id
        Permission.EXECUTION_REQUEST_EXECUTE -> {
            val userIdToCheck: String = if (request.reviewer != null) {
                request.reviewer?.getId() ?: ""
            } else {
                request.author.getId() ?: ""
            }
            userIdToCheck == userDetails.id && isExecutable()
        }
        else -> true
    }

    private fun isExecutable(): Boolean = resolveReviewStatus() == ReviewStatus.APPROVED

    fun csvDownloadAllowed(query: String? = null): Pair<Boolean, String> {
        if (request.type === RequestType.Dump) {
            return Pair(false, "CSV download is not available for SQLDump")
        }

        if (request.connection !is DatasourceConnection || request !is DatasourceExecutionRequest) {
            return Pair(false, "Only Datasource Requests can be downloaded as CSV")
        }

        if (request.connection.type == DatasourceType.MONGODB) {
            return Pair(false, "MongoDB requests can't be downloaded as CSV")
        }

        if (resolveReviewStatus() != ReviewStatus.APPROVED) {
            return Pair(false, "This request has not been approved yet!")
        }

        if (resolveExecutionStatus() == ExecutionStatus.EXECUTED) {
            return Pair(false, "This request has already been executed the maximum amount of times!")
        }

        val queryToExecute = when (request.type) {
            RequestType.SingleExecution -> request.statement!!.trim().removeSuffix(";")
            RequestType.TemporaryAccess -> query?.trim()?.removeSuffix(
                ";",
            ) ?: return Pair(false, "Query can't be empty")

            else -> return Pair(false, "Can't download results for this request type")
        }
        try {
            val statementCount = CCJSqlParserUtil.parseStatements(queryToExecute).size
            if (statementCount > 1) {
                return Pair(false, "This request contains more than one statement!")
            }
            if (CCJSqlParserUtil.parseStatements(
                    queryToExecute,
                ).first() !is net.sf.jsqlparser.statement.select.Select
            ) {
                return Pair(false, "Can only download results for select queries!")
            }
            return Pair(true, "")
        } catch (e: JSQLParserException) {
            return Pair(false, "Error parsing query: ${e.message}")
        }
    }
}

data class ExecutionProxy(
    val request: ExecutionRequest,
    val port: Int,
    val username: String,
    val password: String,
    val startTime: LocalDateTime,
) : SecuredDomainObject {
    override fun getSecuredObjectId() = request.connection.id.toString()

    override fun getDomainObjectType() = Resource.EXECUTION_REQUEST

    override fun getRelated(resource: Resource): SecuredDomainObject? = null
}
