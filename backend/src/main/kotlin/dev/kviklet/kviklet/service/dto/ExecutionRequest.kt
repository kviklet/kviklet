package dev.kviklet.kviklet.service.dto

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue
import dev.kviklet.kviklet.db.User
import dev.kviklet.kviklet.security.Permission
import dev.kviklet.kviklet.security.PolicyGrantedAuthority
import dev.kviklet.kviklet.security.Resource
import dev.kviklet.kviklet.security.SecuredCollectionWrapper
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

data class RoleRequirement(val roleId: String, val numRequired: Int)

data class ReviewConfig(val numTotalRequired: Int, val roleRequirements: List<RoleRequirement>? = null)

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
    open val reviewStatus: String,
    open val createdAt: LocalDateTime = utcTimeNow(),
    open val author: User,
    open val temporaryAccessDuration: Duration? = null,
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

data class DBExecutionResult(
    override val executionRequest: ExecutionRequestDetails,
    val results: List<QueryResult>,
    val event: Event? = null,
) : ExecutionResult(executionRequest)

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
    override val reviewStatus: String,
    override val createdAt: LocalDateTime = utcTimeNow(),
    override val author: User,
    override val temporaryAccessDuration: Duration?,
) : ExecutionRequest(
    id, connection, title, type, description, executionStatus, reviewStatus, createdAt,
    author,
    temporaryAccessDuration,
)

data class KubernetesExecutionRequest(
    override val id: ExecutionRequestId?,
    override val connection: KubernetesConnection,
    override val title: String,
    override val type: RequestType,
    override val description: String?,
    override val executionStatus: String,
    override val reviewStatus: String,
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
    reviewStatus,
    createdAt,
    author,
    temporaryAccessDuration,
)

data class ExecutionRequestDetails(val request: ExecutionRequest, val events: MutableSet<Event>) :
    SecuredDomainObject {
    fun addEvent(event: Event): ExecutionRequestDetails {
        events.add(event)
        return this
    }

    fun resolveReviewStatus(): ReviewStatus {
        if (isRejected()) {
            return ReviewStatus.REJECTED
        }

        val progress = getApprovalProgress()

        if (progress.changeRequestedBy.isNotEmpty()) {
            return ReviewStatus.CHANGE_REQUESTED
        }
        if (progress.totalCurrent < progress.totalRequired) {
            return ReviewStatus.AWAITING_APPROVAL
        }
        if (progress.roleProgress.any { it.numCurrent < it.numRequired }) {
            return ReviewStatus.AWAITING_APPROVAL
        }

        return ReviewStatus.APPROVED
    }

    fun isRejected(): Boolean {
        val rejectedReview = events.filter { it.type == EventType.REVIEW }
            .mapNotNull { it as? ReviewEvent }
            .find { it.action == ReviewAction.REJECT }

        return rejectedReview != null
    }

    fun getApproversAfterReset(): List<User> {
        val resetTimestamp = latestResetTimestamp()
        val reviewEventsAfterReset = events.filter {
            it.type == EventType.REVIEW && it is ReviewEvent && it.createdAt > resetTimestamp
        }.mapNotNull { it as? ReviewEvent }

        return reviewEventsAfterReset
            .groupBy { it.author.getId() }
            .filter { (_, userEvents) -> userEvents.maxByOrNull { it.createdAt }?.action == ReviewAction.APPROVE }
            .map { (_, userEvents) -> userEvents.first().author }
    }

    fun getApprovalCount(): Int = getApproversAfterReset().size

    fun getActiveChangeRequesters(): List<User> {
        val resetTimestamp = latestResetTimestamp()
        val reviewEventsAfterReset = events.filter {
            it.type == EventType.REVIEW && it is ReviewEvent && it.createdAt > resetTimestamp
        }.mapNotNull { it as? ReviewEvent }

        return reviewEventsAfterReset
            .groupBy { it.author.getId() }
            .filter { (_, userEvents) ->
                userEvents.maxByOrNull { it.createdAt }?.action == ReviewAction.REQUEST_CHANGE
            }
            .map { (_, userEvents) -> userEvents.first().author }
    }

    fun getApprovalProgress(): ApprovalProgress {
        val approvers = getApproversAfterReset()
        val changeRequesters = getActiveChangeRequesters()
        val reviewConfig = request.connection.reviewConfig

        val totalRequired = reviewConfig.numTotalRequired
        val totalCurrent = approvers.size

        val roleProgress = reviewConfig.roleRequirements?.map { roleReq ->
            val approversForRole = approvers.filter { user ->
                user.roles.any { role -> role.getId() == roleReq.roleId }
            }
            val numCurrent = approversForRole.size
            val approverNames = approversForRole.map { "${it.fullName} (${it.email})" }

            RoleApprovalProgress(
                roleId = roleReq.roleId,
                numRequired = roleReq.numRequired,
                numCurrent = numCurrent,
                approverNames = approverNames,
            )
        } ?: emptyList()

        return ApprovalProgress(
            totalRequired = totalRequired,
            totalCurrent = totalCurrent,
            roleProgress = roleProgress,
            changeRequestedBy = changeRequesters.map { it.fullName ?: it.email },
        )
    }

    fun latestResetTimestamp(): LocalDateTime {
        val latestEdit = events.filter { it.type == EventType.EDIT }.sortedBy { it.createdAt }.lastOrNull()
        val latestEditTimeStamp = latestEdit?.createdAt ?: LocalDateTime.MIN
        if (request.type == RequestType.TemporaryAccess) {
            return latestEditTimeStamp
        }
        // Find the latest execution with error
        val latestExecutionError = events.filter { it.type == EventType.EXECUTE }
            .mapNotNull { it as? ExecuteEvent }
            .filter { executeEvent -> executeEvent.results.any { it is ErrorResultLog } }
            .maxByOrNull { it.createdAt }

        // Use the latest of either edit or execution error timestamp
        val latestErrorTimeStamp = latestExecutionError?.createdAt ?: LocalDateTime.MIN
        val latestResetTimeStamp = if (latestEditTimeStamp.isAfter(
                latestErrorTimeStamp,
            )
        ) {
            latestEditTimeStamp
        } else {
            latestErrorTimeStamp
        }
        return latestResetTimeStamp
    }

    fun resolveExecutionStatus(): ExecutionStatus {
        when (request.type) {
            RequestType.SingleExecution, RequestType.Dump -> {
                val executions = events.filter { it.type == EventType.EXECUTE }
                    .filterIsInstance<ExecuteEvent>()
                    .filter { !it.isDryRun } // Exclude dry runs
                // Filter out executions that resulted in errors
                val successfulExecutions = executions.filter { executeEvent ->
                    executeEvent.results.none { it is ErrorResultLog }
                }

                request.connection.maxExecutions?.let { maxExecutions ->
                    if (maxExecutions == 0) { // magic number for unlimited executions
                        return ExecutionStatus.EXECUTABLE
                    }
                    if (successfulExecutions.size >= maxExecutions) {
                        return ExecutionStatus.EXECUTED
                    }
                }
                return ExecutionStatus.EXECUTABLE
            }

            RequestType.TemporaryAccess -> {
                val executions = events.filter { it.type == EventType.EXECUTE }
                    .filterIsInstance<ExecuteEvent>()
                    .filter { !it.isDryRun } // Temporary Access Requests cannot be dry-run but just in case
                if (executions.isEmpty()) {
                    return ExecutionStatus.EXECUTABLE
                }
                val firstExecution = executions.minBy { it.createdAt }
                // Default to 1 hour if not set, can be for old temporary access requests all new ones should have this set
                // If temporaryAccessDuration is null, it means infinite access
                if (request.temporaryAccessDuration == null) {
                    return ExecutionStatus.ACTIVE
                }

                val duration = request.temporaryAccessDuration
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
        Permission.EXECUTION_REQUEST_EXECUTE -> request.author.getId() == userDetails.id && isExecutable()
        else -> true
    }

    private fun isExecutable(): Boolean {
        // If approved, always executable
        if (resolveReviewStatus() == ReviewStatus.APPROVED) {
            return true
        }
        // If not approved, allow through if dry run is enabled on the connection
        // The service layer will enforce the actual dry run vs normal execution check
        // and provide specific error messages for approval requirements
        val connection = request.connection
        if (connection is DatasourceConnection && connection.dryRunEnabled) {
            return true
        }
        return false
    }

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
            val statementCount = CCJSqlParserUtil.parseStatements(queryToExecute)?.size ?: 0
            if (statementCount > 1) {
                return Pair(false, "This request contains more than one statement!")
            }
            if (CCJSqlParserUtil.parseStatements(
                    queryToExecute,
                )?.first() !is net.sf.jsqlparser.statement.select.Select
            ) {
                return Pair(false, "Can only download results for select queries!")
            }
            return Pair(true, "")
        } catch (e: JSQLParserException) {
            return Pair(false, "Error parsing query: ${e.message}")
        }
    }
}

data class ExecutionRequestDetailsWithRoles(
    val details: ExecutionRequestDetails,
    val resolvedRoles: Map<String, Role>,
) : SecuredDomainObject by details {
    val request get() = details.request
    val events get() = details.events
    fun resolveReviewStatus() = details.resolveReviewStatus()
    fun resolveExecutionStatus() = details.resolveExecutionStatus()
    fun csvDownloadAllowed(query: String? = null) = details.csvDownloadAllowed(query)
    fun getApprovalProgress() = details.getApprovalProgress()
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

data class RoleApprovalProgress(
    val roleId: String,
    val numRequired: Int,
    val numCurrent: Int,
    val approverNames: List<String>,
)

data class ApprovalProgress(
    val totalRequired: Int,
    val totalCurrent: Int,
    val roleProgress: List<RoleApprovalProgress>,
    val changeRequestedBy: List<String> = emptyList(),
)

data class ExecutionRequestList(
    val requests: List<ExecutionRequestDetails>,
    val hasMore: Boolean,
    val cursor: LocalDateTime?,
) : SecuredCollectionWrapper<ExecutionRequestDetails> {
    override fun getCollection(): Collection<ExecutionRequestDetails> = requests

    override fun withFilteredCollection(
        filtered: Collection<ExecutionRequestDetails>,
    ): SecuredCollectionWrapper<ExecutionRequestDetails> = copy(requests = filtered.toList())
}
