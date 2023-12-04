package dev.kviklet.kviklet.service

import dev.kviklet.kviklet.controller.CreateCommentRequest
import dev.kviklet.kviklet.controller.CreateExecutionRequestRequest
import dev.kviklet.kviklet.controller.CreateReviewRequest
import dev.kviklet.kviklet.controller.UpdateExecutionRequestRequest
import dev.kviklet.kviklet.db.CommentPayload
import dev.kviklet.kviklet.db.DatasourceConnectionAdapter
import dev.kviklet.kviklet.db.EditPayload
import dev.kviklet.kviklet.db.ExecutionRequestAdapter
import dev.kviklet.kviklet.db.Payload
import dev.kviklet.kviklet.db.ReviewConfig
import dev.kviklet.kviklet.db.ReviewPayload
import dev.kviklet.kviklet.security.Permission
import dev.kviklet.kviklet.security.Policy
import dev.kviklet.kviklet.service.dto.DatasourceConnectionId
import dev.kviklet.kviklet.service.dto.Event
import dev.kviklet.kviklet.service.dto.EventType
import dev.kviklet.kviklet.service.dto.ExecutionRequestDetails
import dev.kviklet.kviklet.service.dto.ExecutionRequestId
import dev.kviklet.kviklet.service.dto.RequestType
import dev.kviklet.kviklet.service.dto.ReviewAction
import dev.kviklet.kviklet.service.dto.ReviewEvent
import dev.kviklet.kviklet.service.dto.ReviewStatus
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service
import java.lang.RuntimeException

@Service
class ExecutionRequestService(
    val executionRequestAdapter: ExecutionRequestAdapter,
    val datasourceConnectionAdapter: DatasourceConnectionAdapter,
    val executorService: ExecutorService,
) {

    @Transactional
    @Policy(Permission.EXECUTION_REQUEST_GET)
    fun create(
        connectionId: DatasourceConnectionId,
        request: CreateExecutionRequestRequest,
        userId: String,
    ): ExecutionRequestDetails {
        return executionRequestAdapter.createExecutionRequest(
            connectionId = request.datasourceConnectionId,
            title = request.title,
            type = request.type,
            description = request.description,
            statement = request.statement,
            readOnly = request.readOnly,
            executionStatus = "PENDING",
            authorId = userId,
        )
    }

    @Transactional
    @Policy(Permission.EXECUTION_REQUEST_EDIT)
    fun update(
        id: ExecutionRequestId,
        request: UpdateExecutionRequestRequest,
        userId: String,
    ): ExecutionRequestDetails {
        val executionRequestDetails = executionRequestAdapter.getExecutionRequestDetails(id)

        if (request.statement != executionRequestDetails.request.statement) {
            saveEvent(
                id,
                userId,
                EditPayload(previousQuery = executionRequestDetails.request.statement ?: ""),
            )
        }

        return executionRequestAdapter.updateExecutionRequest(
            id = executionRequestDetails.request.id!!,
            title = request.title ?: executionRequestDetails.request.title,
            description = request.description ?: executionRequestDetails.request.description,
            statement = request.statement ?: executionRequestDetails.request.statement,
            readOnly = request.readOnly ?: executionRequestDetails.request.readOnly,
            executionStatus = executionRequestDetails.request.executionStatus,
        )
    }

    @Transactional
    @Policy(Permission.EXECUTION_REQUEST_GET)
    fun list(): List<ExecutionRequestDetails> = executionRequestAdapter.listExecutionRequests()

    @Transactional
    @Policy(Permission.EXECUTION_REQUEST_GET)
    fun get(id: ExecutionRequestId): ExecutionRequestDetails = executionRequestAdapter.getExecutionRequestDetails(id)

    @Transactional
    @Policy(Permission.EXECUTION_REQUEST_GET)
    fun createReview(id: ExecutionRequestId, request: CreateReviewRequest, authorId: String): Event {
        val executionRequest = executionRequestAdapter.getExecutionRequestDetails(id)
        if (executionRequest.request.author.id == authorId && request.action == ReviewAction.APPROVE) {
            throw InvalidReviewException("A user can't approve their own request!")
        }
        return saveEvent(
            id,
            authorId,
            ReviewPayload(comment = request.comment, action = request.action),
        )
    }

    @Transactional
    @Policy(Permission.EXECUTION_REQUEST_GET)
    fun createComment(id: ExecutionRequestId, request: CreateCommentRequest, authorId: String) = saveEvent(
        id,
        authorId,
        CommentPayload(comment = request.comment),
    )

    private fun saveEvent(id: ExecutionRequestId, authorId: String, payload: Payload): Event {
        val (executionRequest, event) = executionRequestAdapter.addEvent(id, authorId, payload)
        return event
    }

    fun resolveReviewStatus(events: Set<Event>, reviewConfig: ReviewConfig): ReviewStatus {
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

    @Policy(Permission.EXECUTION_REQUEST_EXECUTE)
    fun execute(id: ExecutionRequestId, query: String?): List<QueryResult> {
        val executionRequest = executionRequestAdapter.getExecutionRequestDetails(id)
        val connection = executionRequest.request.connection

        if (executionRequest.request.executionStatus == "EXECUTED") {
            throw AlreadyExecutedException("This request has already been executed, can only execute once!")
        }

        val result = executorService.execute(
            executionRequestId = id,
            connectionString = connection.getConnectionString(),
            username = connection.username,
            password = connection.password,
            query = when (executionRequest.request.type) {
                RequestType.SingleQuery -> executionRequest.request.statement!!
                RequestType.TemporaryAccess -> query ?: throw MissingQueryException(
                    "For temporary access requests the query param is required",
                )
            },
        )
        if (executionRequest.request.type == RequestType.SingleQuery) {
            executionRequestAdapter.updateExecutionRequest(
                id,
                title = executionRequest.request.title,
                description = executionRequest.request.description,
                statement = executionRequest.request.statement,
                readOnly = executionRequest.request.readOnly,
                executionStatus = "EXECUTED",
            )
        }

        return result
    }
}

class InvalidReviewException(message: String) : RuntimeException(message)

class MissingQueryException(message: String) : RuntimeException(message)

class AlreadyExecutedException(message: String) : RuntimeException(message)
