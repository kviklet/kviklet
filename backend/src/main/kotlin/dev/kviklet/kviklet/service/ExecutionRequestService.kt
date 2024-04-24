package dev.kviklet.kviklet.service

import dev.kviklet.kviklet.controller.CreateCommentRequest
import dev.kviklet.kviklet.controller.CreateDatasourceExecutionRequestRequest
import dev.kviklet.kviklet.controller.CreateExecutionRequestRequest
import dev.kviklet.kviklet.controller.CreateKubernetesExecutionRequestRequest
import dev.kviklet.kviklet.controller.CreateReviewRequest
import dev.kviklet.kviklet.controller.UpdateExecutionRequestRequest
import dev.kviklet.kviklet.db.CommentPayload
import dev.kviklet.kviklet.db.EditPayload
import dev.kviklet.kviklet.db.ExecutePayload
import dev.kviklet.kviklet.db.ExecutionRequestAdapter
import dev.kviklet.kviklet.db.ReviewConfig
import dev.kviklet.kviklet.db.ReviewPayload
import dev.kviklet.kviklet.proxy.PostgresProxy
import dev.kviklet.kviklet.security.Permission
import dev.kviklet.kviklet.security.Policy
import dev.kviklet.kviklet.security.UserDetailsWithId
import dev.kviklet.kviklet.service.dto.ConnectionId
import dev.kviklet.kviklet.service.dto.DBExecutionResult
import dev.kviklet.kviklet.service.dto.DatasourceConnection
import dev.kviklet.kviklet.service.dto.DatasourceExecutionRequest
import dev.kviklet.kviklet.service.dto.DatasourceType
import dev.kviklet.kviklet.service.dto.Event
import dev.kviklet.kviklet.service.dto.EventType
import dev.kviklet.kviklet.service.dto.ExecuteEvent
import dev.kviklet.kviklet.service.dto.ExecutionProxy
import dev.kviklet.kviklet.service.dto.ExecutionRequest
import dev.kviklet.kviklet.service.dto.ExecutionRequestDetails
import dev.kviklet.kviklet.service.dto.ExecutionRequestId
import dev.kviklet.kviklet.service.dto.ExecutionResult
import dev.kviklet.kviklet.service.dto.KubernetesConnection
import dev.kviklet.kviklet.service.dto.KubernetesExecutionRequest
import dev.kviklet.kviklet.service.dto.KubernetesExecutionResult
import dev.kviklet.kviklet.service.dto.RequestType
import dev.kviklet.kviklet.service.dto.ReviewAction
import dev.kviklet.kviklet.service.dto.ReviewEvent
import dev.kviklet.kviklet.service.dto.ReviewStatus
import dev.kviklet.kviklet.shell.KubernetesApi
import jakarta.transaction.Transactional
import net.sf.jsqlparser.parser.CCJSqlParserUtil
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import java.security.SecureRandom
import java.time.LocalDateTime
import java.util.concurrent.CompletableFuture

@Service
class ExecutionRequestService(
    val executionRequestAdapter: ExecutionRequestAdapter,
    val executorService: ExecutorService,
    val eventService: EventService,
    val kubernetesApi: KubernetesApi,
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val proxies = mutableListOf<ExecutionProxy>()

    @Transactional
    @Policy(Permission.EXECUTION_REQUEST_GET)
    fun create(
        connectionId: ConnectionId,
        request: CreateExecutionRequestRequest,
        userId: String,
    ): ExecutionRequestDetails {
        return when (request) {
            is CreateDatasourceExecutionRequestRequest -> {
                createDatasourceRequest(connectionId, request, userId)
            }
            is CreateKubernetesExecutionRequestRequest -> {
                createKubernetesRequest(connectionId, request, userId)
            }
        }
    }

    private fun createDatasourceRequest(
        connectionId: ConnectionId,
        request: CreateDatasourceExecutionRequestRequest,
        userId: String,
    ): ExecutionRequestDetails {
        return executionRequestAdapter.createExecutionRequest(
            connectionId = connectionId,
            title = request.title,
            type = request.type,
            description = request.description,
            statement = request.statement,
            readOnly = request.readOnly,
            executionStatus = "PENDING",
            authorId = userId,
        )
    }

    private fun createKubernetesRequest(
        connectionId: ConnectionId,
        request: CreateKubernetesExecutionRequestRequest,
        userId: String,
    ): ExecutionRequestDetails {
        return executionRequestAdapter.createExecutionRequest(
            connectionId = connectionId,
            title = request.title,
            type = request.type,
            description = request.description,
            executionStatus = "PENDING",
            authorId = userId,
            namespace = request.namespace,
            podName = request.podName,
            containerName = request.containerName,
            command = request.command,
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

        when (executionRequestDetails.request) {
            is DatasourceExecutionRequest -> {
                if (request.statement != executionRequestDetails.request.statement) {
                    eventService.saveEvent(
                        id,
                        userId,
                        EditPayload(previousQuery = executionRequestDetails.request.statement ?: ""),
                    )
                }
            }
            is KubernetesExecutionRequest -> {
                if (request.command != executionRequestDetails.request.command) {
                    eventService.saveEvent(
                        id,
                        userId,
                        EditPayload(
                            previousCommand = request.command?.let { executionRequestDetails.request.command },
                            previousContainerName = request.containerName
                                ?.let { executionRequestDetails.request.containerName ?: "" },
                            previousPodName = request.podName?.let { executionRequestDetails.request.podName },
                            previousNamespace = request.namespace?.let { executionRequestDetails.request.namespace },
                        ),
                    )
                }
            }
        }

        return executionRequestAdapter.updateExecutionRequest(
            id = executionRequestDetails.request.id!!,
            title = request.title,
            description = request.description,
            statement = request.statement,
            readOnly = request.readOnly,
            executionStatus = executionRequestDetails.request.executionStatus,
            namespace = request.namespace,
            podName = request.podName,
            containerName = request.containerName,
            command = request.command,
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

    private fun executeDatasourceRequest(
        id: ExecutionRequestId,
        executionRequest: ExecutionRequestDetails,
        connection: DatasourceConnection,
        query: String?,
        userId: String,
    ): DBExecutionResult {
        if (executionRequest.request !is DatasourceExecutionRequest) {
            throw RuntimeException("This should never happen! Probably there is a way to refactor this code")
        }

        val queryToExecute = when (executionRequest.request.type) {
            RequestType.SingleExecution -> executionRequest.request.statement!!
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

        if (executionRequest.request.type == RequestType.SingleExecution) {
            executionRequestAdapter.updateExecutionRequest(
                id,
                title = executionRequest.request.title,
                description = executionRequest.request.description,
                statement = executionRequest.request.statement,
                readOnly = executionRequest.request.readOnly,
                executionStatus = "EXECUTED",
            )
        }

        return DBExecutionResult(
            executionRequestId = id,
            results = result,
        )
    }

    private fun executeKubernetesRequest(
        id: ExecutionRequestId,
        executionRequest: ExecutionRequestDetails,
        connection: KubernetesConnection,
        userId: String,
    ): KubernetesExecutionResult {
        if (executionRequest.request !is KubernetesExecutionRequest) {
            throw RuntimeException("This should never happen! Probably there is a way to refactor this code")
        }

        eventService.saveEvent(
            id,
            userId,
            ExecutePayload(
                command = executionRequest.request.command!!,
                containerName = executionRequest.request.containerName,
                podName = executionRequest.request.podName,
                namespace = executionRequest.request.namespace,
            ),
        )

        // only pass container name if it's not empty
        val containerName = executionRequest.request.containerName?.takeIf { it.isNotBlank() }

        val result = kubernetesApi.executeCommandOnPod(
            executionRequestId = id,
            namespace = executionRequest.request.namespace!!,
            podName = executionRequest.request.podName!!,
            command = executionRequest.request.command,
            containerName = containerName,
            timeout = 60,
        )
        return result
    }

    @Policy(Permission.EXECUTION_REQUEST_EXECUTE)
    fun execute(id: ExecutionRequestId, query: String?, userId: String): ExecutionResult {
        val executionRequest = executionRequestAdapter.getExecutionRequestDetails(id)
        val connection = executionRequest.request.connection

        val reviewStatus = resolveReviewStatus(executionRequest.events, connection.reviewConfig)
        if (reviewStatus != ReviewStatus.APPROVED) {
            throw InvalidReviewException("This request has not been approved yet!")
        }

        executionRequest.events.raiseIfAlreadyExecuted(executionRequest.request.type)
        return when (connection) {
            is DatasourceConnection -> {
                executeDatasourceRequest(id, executionRequest, connection, query, userId)
            }
            is KubernetesConnection -> {
                executeKubernetesRequest(id, executionRequest, connection, userId)
            }
        }
    }

    @Policy(Permission.EXECUTION_REQUEST_EXECUTE)
    fun explain(id: ExecutionRequestId, query: String?, userId: String): DBExecutionResult {
        val executionRequest = executionRequestAdapter.getExecutionRequestDetails(id)
        val connection = executionRequest.request.connection
        if (connection !is DatasourceConnection || executionRequest.request !is DatasourceExecutionRequest) {
            throw RuntimeException("Only Datasource connections can be explained")
        }

        val requestType = executionRequest.request.type
        if (requestType != RequestType.SingleExecution) {
            throw InvalidReviewException("Can only explain single queries!")
        }
        val parsedStatements = CCJSqlParserUtil.parseStatements(executionRequest.request.statement)
        val explainStatements = parsedStatements.joinToString(";") { "EXPLAIN $it" }

        val result = executorService.execute(
            executionRequestId = id,
            connectionString = connection.getConnectionString(),
            username = connection.username,
            password = connection.password,
            query = explainStatements,
        )

        return DBExecutionResult(results = result, executionRequestId = id)
    }

    @Transactional
    @Policy(Permission.EXECUTION_REQUEST_GET)
    fun getExecutions(): List<ExecuteEvent> {
        return eventService.getAllExecutions()
    }

    private fun cleanUpProxies() {
        val now = LocalDateTime.now()
        val expiredProxies = proxies.filter { it.startTime.plusMinutes(60) < now }
        expiredProxies.forEach {
            proxies.remove(it)
        }
    }

    // @Policy(Permission.EXECUTION_REQUEST_GET)
    @Transactional
    fun proxy(executionRequestId: ExecutionRequestId, userDetails: UserDetailsWithId): ExecutionProxy {
        cleanUpProxies()
        val executionRequest = executionRequestAdapter.getExecutionRequestDetails(executionRequestId)
        val connection = executionRequest.request.connection
        if (connection !is DatasourceConnection) {
            throw RuntimeException("Only Datasource connections be proxied")
        }
        val reviewStatus = resolveReviewStatus(executionRequest.events, connection.reviewConfig)
        if (executionRequest.request.author.getId() != userDetails.id) {
            throw RuntimeException("Only the author of the request can proxy it!")
        }
        if (reviewStatus != ReviewStatus.APPROVED) {
            throw InvalidReviewException("This request has not been approved yet!")
        }
        if (connection.type != DatasourceType.POSTGRESQL) {
            throw RuntimeException("Only Postgres is supported for proxying!")
        }
        executionRequest.events.raiseIfAlreadyExecuted(executionRequest.request.type)

        val executedEvents = executionRequest.events.filter { it.type == EventType.EXECUTE }
        val firstEventTime = executedEvents.map { it.createdAt }
            .ifEmpty { listOf(LocalDateTime.now()) }
            .minOf { it }

        // Randomly generate a temp password for the proxy
        val password = generateRandomPassword(16)

        val usedPorts = proxies.map { it.port }
        val availablePort = (5438..6000).first { it !in usedPorts }

        startServerAsync(
            connection.hostname,
            connection.port,
            connection.databaseName ?: "",
            availablePort,
            connection.username,
            connection.password,
            connection.username,
            password,
            executionRequest = executionRequest.request,
            userId = userDetails.id,
            firstEventTime,
        )
        logger.info("Started proxy for user ${connection.username} on port 5438")
        val proxy = ExecutionProxy(
            request = executionRequest.request,
            port = availablePort,
            username = connection.username,
            password = password,
            startTime = firstEventTime,
        )
        proxies.add(proxy)

        return proxy
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
        databaseName: String,
        mappedPort: Int,
        username: String,
        password: String,
        email: String,
        tempPassword: String,
        executionRequest: ExecutionRequest,
        userId: String,
        startTime: LocalDateTime,
    ): CompletableFuture<Void>? {
        return CompletableFuture.runAsync {
            PostgresProxy(
                hostname,
                port,
                databaseName,
                username,
                password,
                eventService,
                executionRequest,
                userId,
            ).startServer(
                mappedPort,
                email,
                tempPassword,
                startTime,
            )
        }
    }
}

fun Set<Event>.raiseIfAlreadyExecuted(requestType: RequestType) {
    val executedEvents = filter { it.type == EventType.EXECUTE }
    if (executedEvents.isEmpty()) return

    when (requestType) {
        RequestType.SingleExecution -> {
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
