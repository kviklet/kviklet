package dev.kviklet.kviklet.service

import dev.kviklet.kviklet.controller.CreateCommentRequest
import dev.kviklet.kviklet.controller.CreateExecutionRequestRequest
import dev.kviklet.kviklet.controller.CreateReviewRequest
import dev.kviklet.kviklet.controller.UpdateExecutionRequestRequest
import dev.kviklet.kviklet.db.CommentPayload
import dev.kviklet.kviklet.db.DatasourceConnectionAdapter
import dev.kviklet.kviklet.db.EditPayload
import dev.kviklet.kviklet.db.ExecutePayload
import dev.kviklet.kviklet.db.ExecutionRequestAdapter
import dev.kviklet.kviklet.db.ReviewConfig
import dev.kviklet.kviklet.db.ReviewPayload
import dev.kviklet.kviklet.db.UserAdapter
import dev.kviklet.kviklet.proxy.PostgresProxy
import dev.kviklet.kviklet.security.Permission
import dev.kviklet.kviklet.security.Policy
import dev.kviklet.kviklet.security.UserDetailsWithId
import dev.kviklet.kviklet.service.dto.DatasourceConnectionId
import dev.kviklet.kviklet.service.dto.DatasourceType
import dev.kviklet.kviklet.service.dto.Event
import dev.kviklet.kviklet.service.dto.EventType
import dev.kviklet.kviklet.service.dto.ExecuteEvent
import dev.kviklet.kviklet.service.dto.ExecutionRequest
import dev.kviklet.kviklet.service.dto.ExecutionRequestDetails
import dev.kviklet.kviklet.service.dto.ExecutionRequestId
import dev.kviklet.kviklet.service.dto.RequestType
import dev.kviklet.kviklet.service.dto.ReviewAction
import dev.kviklet.kviklet.service.dto.ReviewEvent
import dev.kviklet.kviklet.service.dto.ReviewStatus
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import java.lang.RuntimeException
import java.security.SecureRandom
import java.time.LocalDateTime
import java.util.concurrent.CompletableFuture

@Service
class ExecutionRequestService(
    val executionRequestAdapter: ExecutionRequestAdapter,
    val datasourceConnectionAdapter: DatasourceConnectionAdapter,
    val executorService: ExecutorService,
    val eventService: EventService,
    val userAdapter: UserAdapter,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

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
            eventService.saveEvent(
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
        if (executionRequest.request.author.getId() == authorId && request.action == ReviewAction.APPROVE) {
            throw InvalidReviewException("A user can't approve their own request!")
        }
        return eventService.saveEvent(
            id,
            authorId,
            ReviewPayload(comment = request.comment, action = request.action),
        )
    }

    @Transactional
    @Policy(Permission.EXECUTION_REQUEST_GET)
    fun createComment(id: ExecutionRequestId, request: CreateCommentRequest, authorId: String) = eventService.saveEvent(
        id,
        authorId,
        CommentPayload(comment = request.comment),
    )

    fun resolveReviewStatus(events: Set<Event>, reviewConfig: ReviewConfig): ReviewStatus {
        val numReviews = events.filter {
            it.type == EventType.REVIEW && it is ReviewEvent && it.action == ReviewAction.APPROVE
        }.groupBy { it.author.getId() }.count()
        val reviewStatus = if (numReviews >= reviewConfig.numTotalRequired) {
            ReviewStatus.APPROVED
        } else {
            ReviewStatus.AWAITING_APPROVAL
        }
        return reviewStatus
    }

    @Policy(Permission.EXECUTION_REQUEST_EXECUTE)
    fun execute(id: ExecutionRequestId, query: String?, userId: String): List<QueryResult> {
        val executionRequest = executionRequestAdapter.getExecutionRequestDetails(id)
        val connection = executionRequest.request.connection

        val reviewStatus = resolveReviewStatus(executionRequest.events, connection.reviewConfig)
        if (reviewStatus != ReviewStatus.APPROVED) {
            throw InvalidReviewException("This request has not been approved yet!")
        }

        executionRequest.events.raiseIfAlreadyExecuted(executionRequest.request.type)

        val queryToExecute = when (executionRequest.request.type) {
            RequestType.SingleQuery -> executionRequest.request.statement!!
            RequestType.TemporaryAccess -> query ?: throw MissingQueryException(
                "For temporary access requests the query param is required",
            )
        }
        eventService.saveEvent(
            id,
            userId,
            ExecutePayload(
                query = queryToExecute,
            ),
        )

        val result = executorService.execute(
            executionRequestId = id,
            connectionString = connection.getConnectionString(),
            username = connection.username,
            password = connection.password,
            query = queryToExecute,
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

    @Transactional
    @Policy(Permission.EXECUTION_REQUEST_GET)
    fun getExecutions(): List<ExecuteEvent> {
        return eventService.getAllExecutions()
    }

    // @Policy(Permission.EXECUTION_REQUEST_GET)
    @Transactional
    fun proxy(executionRequestId: ExecutionRequestId, userDetails: UserDetailsWithId): ProxyResponse {
        val executionRequest = executionRequestAdapter.getExecutionRequestDetails(executionRequestId)
        val connection = executionRequest.request.connection
        val reviewStatus = resolveReviewStatus(executionRequest.events, connection.reviewConfig)
        if (reviewStatus != ReviewStatus.APPROVED) {
            throw InvalidReviewException("This request has not been approved yet!")
        }
        if (connection.type != DatasourceType.POSTGRESQL) {
            throw RuntimeException("Only Postgres is supported for proxying!")
        }
        // Randomly generate a temp password for the proxy
        val password = generateRandomPassword(16)

        startServerAsync(
            connection.hostname,
            connection.port,
            connection.username,
            connection.password,
            connection.username,
            password,
            executionRequest = executionRequest.request,
            userId = userDetails.id,
        )
        logger.info("Started proxy for user ${connection.username} on port 5438")

        return ProxyResponse(
            port = 5438,
            username = connection.username,
            password = password,
        )
    }

    fun generateRandomPassword(length: Int): String {
        val characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        val secureRandom = SecureRandom()
        val password = StringBuilder(length)

        for (i in 0 until length) {
            val randomIndex = secureRandom.nextInt(characters.length)
            password.append(characters[randomIndex])
        }

        return password.toString()
    }

    @Async
    fun startServerAsync(
        hostname: String,
        port: Int,
        username: String,
        password: String,
        email: String,
        tempPassword: String,
        executionRequest: ExecutionRequest,
        userId: String,
    ): CompletableFuture<Void>? {
        return CompletableFuture.runAsync {
            PostgresProxy(hostname, port, username, password, eventService, executionRequest, userId).startServer(
                5438,
                email,
                tempPassword,
            )
        }
    }
}

data class ProxyResponse(
    val port: Int,
    val username: String,
    val password: String,
)

fun Set<Event>.raiseIfAlreadyExecuted(requestType: RequestType) {
    val executedEvents = filter { it.type == EventType.EXECUTE }
    if (executedEvents.isEmpty()) return

    when (requestType) {
        RequestType.SingleQuery -> {
            if (this.isNotEmpty()) {
                throw AlreadyExecutedException("This request has already been executed, can only execute once!")
            }
        }
        RequestType.TemporaryAccess -> {
            val firstEventTime = executedEvents.minOf { it.createdAt }
            if (firstEventTime.plusMinutes(60) < LocalDateTime.now()) {
                throw AlreadyExecutedException(
                    "This request has timed out, temporary access is only valid for 60 minutes!",
                )
            }
        }
    }
}

class InvalidReviewException(message: String) : RuntimeException(message)

class MissingQueryException(message: String) : RuntimeException(message)

class AlreadyExecutedException(message: String) : RuntimeException(message)
