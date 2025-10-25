package dev.kviklet.kviklet.service

import dev.kviklet.kviklet.TLSCerts
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
import dev.kviklet.kviklet.db.ReviewPayload
import dev.kviklet.kviklet.db.UserAdapter
import dev.kviklet.kviklet.proxy.postgres.PostgresProxy
import dev.kviklet.kviklet.proxy.postgres.tlsCertificateFactory
import dev.kviklet.kviklet.security.Permission
import dev.kviklet.kviklet.security.Policy
import dev.kviklet.kviklet.security.UserDetailsWithId
import dev.kviklet.kviklet.service.dto.AuthenticationDetails
import dev.kviklet.kviklet.service.dto.ConnectionId
import dev.kviklet.kviklet.service.dto.DBExecutionResult
import dev.kviklet.kviklet.service.dto.DatasourceConnection
import dev.kviklet.kviklet.service.dto.DatasourceExecutionRequest
import dev.kviklet.kviklet.service.dto.DatasourceType
import dev.kviklet.kviklet.service.dto.DumpResultLog
import dev.kviklet.kviklet.service.dto.ErrorResultLog
import dev.kviklet.kviklet.service.dto.Event
import dev.kviklet.kviklet.service.dto.EventType
import dev.kviklet.kviklet.service.dto.ExecuteEvent
import dev.kviklet.kviklet.service.dto.ExecutionProxy
import dev.kviklet.kviklet.service.dto.ExecutionRequest
import dev.kviklet.kviklet.service.dto.ExecutionRequestDetails
import dev.kviklet.kviklet.service.dto.ExecutionRequestId
import dev.kviklet.kviklet.service.dto.ExecutionRequestList
import dev.kviklet.kviklet.service.dto.ExecutionResult
import dev.kviklet.kviklet.service.dto.ExecutionStatus
import dev.kviklet.kviklet.service.dto.KubernetesConnection
import dev.kviklet.kviklet.service.dto.KubernetesExecutionRequest
import dev.kviklet.kviklet.service.dto.KubernetesExecutionResult
import dev.kviklet.kviklet.service.dto.RequestType
import dev.kviklet.kviklet.service.dto.ReviewStatus
import dev.kviklet.kviklet.service.dto.utcTimeNow
import dev.kviklet.kviklet.shell.KubernetesApi
import jakarta.servlet.ServletOutputStream
import jakarta.transaction.Transactional
import net.sf.jsqlparser.parser.CCJSqlParserUtil
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import java.io.BufferedInputStream
import java.io.IOException
import java.io.OutputStream
import java.security.SecureRandom
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.CompletableFuture

@Service
class ExecutionRequestService(
    private val executionRequestAdapter: ExecutionRequestAdapter,
    private val JDBCExecutor: JDBCExecutor,
    private val eventService: EventService,
    private val kubernetesApi: KubernetesApi,
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val mongoDBExecutor: MongoDBExecutor,
    private val connectionService: ConnectionService,
    private val userAdapter: UserAdapter,
    private val proxyTLSCerts: TLSCerts,
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val proxies = mutableListOf<ExecutionProxy>()

    private fun validateTemporaryAccessDuration(connection: DatasourceConnection, requestedDurationMinutes: Long?) {
        if (requestedDurationMinutes != null && requestedDurationMinutes == 0L) {
            throw IllegalArgumentException(
                "Duration cannot be 0. Use null for infinite access or a positive value for limited access",
            )
        }

        connection.maxTemporaryAccessDuration?.let { maxDuration ->
            if (requestedDurationMinutes == null) {
                throw IllegalArgumentException(
                    "Infinite access not allowed for this connection. Maximum duration is $maxDuration minutes",
                )
            }
            if (requestedDurationMinutes > maxDuration) {
                throw IllegalArgumentException("Duration exceeds maximum allowed: $maxDuration minutes")
            }
        }
    }

    @Transactional
    @Policy(Permission.EXECUTION_REQUEST_EDIT)
    fun create(
        connectionId: ConnectionId,
        request: CreateExecutionRequestRequest,
        userId: String,
    ): ExecutionRequestDetails {
        val executionRequestDetails = when (request) {
            is CreateDatasourceExecutionRequestRequest -> {
                createDatasourceRequest(connectionId, request, userId)
            }
            is CreateKubernetesExecutionRequestRequest -> {
                createKubernetesRequest(connectionId, request, userId)
            }
        }
        RequestCreatedEvent.fromRequest(executionRequestDetails).let {
            applicationEventPublisher.publishEvent(it)
        }
        return executionRequestDetails
    }

    private fun createDatasourceRequest(
        connectionId: ConnectionId,
        request: CreateDatasourceExecutionRequestRequest,
        userId: String,
    ): ExecutionRequestDetails {
        val connection = connectionService.getDatasourceConnection(connectionId)
        if (connection !is DatasourceConnection) {
            throw RuntimeException("Unexpected connection type for connectionId: $connectionId")
        }
        if (request.type == RequestType.Dump) {
            if (!connection.dumpsEnabled) {
                throw IllegalStateException("Dumps are not enabled for this connection")
            }
        }
        if (request.type == RequestType.TemporaryAccess) {
            if (!connection.temporaryAccessEnabled) {
                throw IllegalStateException("Temporary access is not enabled for this connection")
            }
            validateTemporaryAccessDuration(connection, request.temporaryAccessDuration)
        }
        return executionRequestAdapter.createExecutionRequest(
            connectionId = connectionId,
            title = request.title,
            type = request.type,
            description = request.description,
            statement = request.statement,
            executionStatus = ExecutionStatus.EXECUTABLE,
            reviewStatus = ReviewStatus.AWAITING_APPROVAL,
            authorId = userId,
            temporaryAccessDuration = request.temporaryAccessDuration?.let { Duration.ofMinutes(it) },
        )
    }

    private fun createKubernetesRequest(
        connectionId: ConnectionId,
        request: CreateKubernetesExecutionRequestRequest,
        userId: String,
    ): ExecutionRequestDetails = executionRequestAdapter.createExecutionRequest(
        connectionId = connectionId,
        title = request.title,
        type = request.type,
        description = request.description,
        executionStatus = ExecutionStatus.EXECUTABLE,
        reviewStatus = ReviewStatus.AWAITING_APPROVAL,
        authorId = userId,
        namespace = request.namespace,
        podName = request.podName,
        containerName = request.containerName,
        command = request.command,
        temporaryAccessDuration = request.temporaryAccessDuration?.let { Duration.ofMinutes(it) },
    )

    private fun constructSQLDumpCommand(connection: DatasourceConnection, outputFile: String? = null): List<String> {
        if (connection.auth !is AuthenticationDetails.UserPassword) {
            throw RuntimeException("Only UserPassword authentication is supported for SQL dumps")
        }
        return when (connection.type) {
            DatasourceType.MYSQL -> {
                val database = if (connection.databaseName.isNullOrBlank()) {
                    listOf("--all-databases")
                } else {
                    listOf("--databases", connection.databaseName)
                }
                val sqlDumpCommand = listOfNotNull(
                    "mysqldump",
                    "-u${connection.auth.username}",
                    "-p${connection.auth.password}",
                    "-h${connection.hostname}",
                    "-P${connection.port}",
                    outputFile?.let { "--result-file=$it" },
                )
                sqlDumpCommand + database
            }
            else -> {
                throw IllegalArgumentException("Unsupported database type: ${connection.type}")
            }
        }
    }

    @Policy(Permission.EXECUTION_REQUEST_EXECUTE)
    fun streamSQLDump(executionRequestId: ExecutionRequestId, outputStream: OutputStream, userId: String) {
        val executionRequest = executionRequestAdapter.getExecutionRequestDetails(executionRequestId)

        val connection = connectionService.getDatasourceConnection(executionRequest.request.connection.id)

        val reviewStatus = executionRequest.resolveReviewStatus()
        if (reviewStatus != ReviewStatus.APPROVED) {
            throw InvalidReviewException("This request has not been approved yet!")
        }

        executionRequest.raiseIfAlreadyExecuted()

        if (connection !is DatasourceConnection) {
            throw IllegalArgumentException("Only Datasource connections can be dumped")
        }

        val event = eventService.saveEvent(
            executionRequestId,
            userId,
            ExecutePayload(
                isDump = true,
            ),
        )

        val command = constructSQLDumpCommand(connection)
        val process = ProcessBuilder(command).start()
        val inputStream = BufferedInputStream(process.inputStream)
        var totalBytesRead = 0L
        try {
            val buffer = ByteArray(8192)
            var bytesRead: Int

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                totalBytesRead += bytesRead
                outputStream.flush()
                logger.info("Read and sent $bytesRead bytes")
            }

            val exitCode = process.waitFor()
            if (exitCode != 0) {
                val errorStream = process.errorStream.bufferedReader().use { it.readText() }
                throw RuntimeException("SQL dump command failed: $errorStream")
            }
        } catch (e: Exception) {
            eventService.addResultLogs(
                event.eventId!!,
                listOf(
                    ErrorResultLog(
                        errorCode = 0,
                        message = "An error occurred while dumping the database: ${e.message}",
                    ),
                ),
            )
            throw RuntimeException("Unexpected error occurred during dump: ${e.message}", e)
        } finally {
            try {
                inputStream.close()
            } catch (e: IOException) {
                logger.error("Error closing inputStream: ${e.message}")
            }
            process.destroy()
        }

        val resultLogs = listOf(DumpResultLog(totalBytesRead))
        eventService.addResultLogs(event.eventId!!, resultLogs)
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
                val newDuration = request.temporaryAccessDuration?.let { Duration.ofMinutes(it) }
                if (newDuration != executionRequestDetails.request.temporaryAccessDuration) {
                    // Validate the new duration if it's for a temporary access request
                    if (executionRequestDetails.request.type == RequestType.TemporaryAccess) {
                        val connection = connectionService.getDatasourceConnection(
                            executionRequestDetails.request.connection.id,
                        )
                        if (connection is DatasourceConnection) {
                            validateTemporaryAccessDuration(connection, request.temporaryAccessDuration)
                        }
                    }
                    eventService.saveEvent(
                        id,
                        userId,
                        EditPayload(
                            previousAccessDurationInMinutes = executionRequestDetails.request.temporaryAccessDuration,
                        ),
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

        // Recalculate statuses after edit event
        val updatedDetails = executionRequestAdapter.getExecutionRequestDetails(id)
        return executionRequestAdapter.updateExecutionRequest(
            id = executionRequestDetails.request.id!!,
            title = request.title,
            description = request.description,
            statement = request.statement,
            executionStatus = updatedDetails.resolveExecutionStatus(),
            reviewStatus = updatedDetails.resolveReviewStatus(),
            namespace = request.namespace,
            podName = request.podName,
            containerName = request.containerName,
            command = request.command,
            temporaryAccessDuration = request.temporaryAccessDuration?.let { Duration.ofMinutes(it) },
        )
    }

    @Transactional
    @Policy(Permission.EXECUTION_REQUEST_GET)
    fun list(): List<ExecutionRequestDetails> = executionRequestAdapter.listExecutionRequests()

    @Transactional
    @Policy(Permission.EXECUTION_REQUEST_GET)
    fun list(
        reviewStatuses: Set<ReviewStatus>?,
        executionStatuses: Set<ExecutionStatus>?,
        connectionId: ConnectionId?,
        after: LocalDateTime?,
        limit: Int = 20,
    ): ExecutionRequestList {
        // Fetch limit + 1 to determine if there are more results, but avoid Int overflow
        val fetchLimit = if (limit == Int.MAX_VALUE) {
            limit // Don't add 1 if already at max
        } else {
            limit + 1
        }

        val requests = executionRequestAdapter.listExecutionRequestsFiltered(
            reviewStatuses = reviewStatuses,
            executionStatuses = executionStatuses,
            connectionId = connectionId,
            after = after,
            limit = fetchLimit,
        )

        val hasMore = requests.size > limit
        val requestsToReturn = requests.take(limit)

        // The cursor is the createdAt timestamp of the last item
        val cursor = requestsToReturn.lastOrNull()?.request?.createdAt

        return ExecutionRequestList(
            requests = requestsToReturn,
            hasMore = hasMore,
            cursor = cursor,
        )
    }

    @Transactional
    @Policy(Permission.EXECUTION_REQUEST_GET)
    fun get(id: ExecutionRequestId): ExecutionRequestDetails = executionRequestAdapter.getExecutionRequestDetails(id)

    @Transactional
    @Policy(Permission.EXECUTION_REQUEST_REVIEW)
    fun createReview(id: ExecutionRequestId, request: CreateReviewRequest, authorId: String): Event {
        val executionRequest = executionRequestAdapter.getExecutionRequestDetails(id)
        if (executionRequest.request.author.getId() == authorId) {
            throw InvalidReviewException("A user can't review their own request!")
        }
        if (executionRequest.resolveReviewStatus() == ReviewStatus.REJECTED) {
            throw InvalidReviewException("Can't review an already rejected request!")
        }
        val reviewEvent = eventService.saveEvent(
            id,
            authorId,
            ReviewPayload(comment = request.comment, action = request.action),
        )
        val updatedExecutionRequestDetails = executionRequestAdapter.getExecutionRequestDetails(id)
        ReviewStatusUpdatedEvent.from(updatedExecutionRequestDetails, reviewEvent).let {
            applicationEventPublisher.publishEvent(it)
        }
        return reviewEvent
    }

    @Transactional
    @Policy(Permission.EXECUTION_REQUEST_GET)
    fun createComment(id: ExecutionRequestId, request: CreateCommentRequest, authorId: String): Event {
        val executionRequest = executionRequestAdapter.getExecutionRequestDetails(id)
        if (executionRequest.resolveReviewStatus() == ReviewStatus.REJECTED) {
            throw InvalidReviewException("Can't comment on a rejected request!")
        }
        return eventService.saveEvent(
            id,
            authorId,
            CommentPayload(comment = request.comment),
        )
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
            RequestType.Dump -> throw RuntimeException("Dump requests can't be executed via the /execute endpoint")
        }
        val event = eventService.saveEvent(
            id,
            userId,
            ExecutePayload(
                query = queryToExecute,
            ),
        )

        val result = when (connection.type) {
            DatasourceType.MONGODB -> {
                mongoDBExecutor.execute(
                    connectionString = connection.getConnectionString(),
                    databaseName = connection.databaseName ?: "db",
                    query = queryToExecute,
                )
            }
            else -> {
                JDBCExecutor.execute(
                    executionRequestId = id,
                    connectionString = connection.getConnectionString(),
                    authenticationDetails = connection.auth,
                    query = queryToExecute,
                )
            }
        }

        // Status will be recalculated automatically after adding result logs via event
        val resultLogs = result.map {
            it.toResultLog()
        }
        val savedEvent = eventService.addResultLogs(event.eventId!!, resultLogs)

        return DBExecutionResult(
            executionRequest = executionRequest,
            results = result,
            event = savedEvent,
        )
    }

    private fun executeKubernetesRequest(
        id: ExecutionRequestId,
        executionRequest: ExecutionRequestDetails,
        userId: String,
        statement: String?,
    ): KubernetesExecutionResult {
        if (executionRequest.request !is KubernetesExecutionRequest) {
            throw RuntimeException("This should never happen! Probably there is a way to refactor this code")
        }

        eventService.saveEvent(
            id,
            userId,
            ExecutePayload(
                command = statement ?: executionRequest.request.command!!,
                containerName = executionRequest.request.containerName,
                podName = executionRequest.request.podName,
                namespace = executionRequest.request.namespace,
            ),
        )

        // only pass container name if it's not empty
        val containerName = executionRequest.request.containerName?.takeIf { it.isNotBlank() }

        val result = kubernetesApi.executeCommandOnPod(
            namespace = executionRequest.request.namespace!!,
            podName = executionRequest.request.podName!!,
            command = statement ?: executionRequest.request.command!!,
            containerName = containerName,
            timeout = 60,
        )
        return KubernetesExecutionResult(
            executionRequest = executionRequest,
            errors = result.errors,
            messages = result.messages,
            finished = result.finished,
            exitCode = result.exitCode,
        )
    }

    @Policy(Permission.EXECUTION_REQUEST_EXECUTE)
    fun cancel(id: ExecutionRequestId) {
        val executionRequest = executionRequestAdapter.getExecutionRequestDetails(id)
        val connection = executionRequest.request.connection
        if (connection !is DatasourceConnection) {
            throw RuntimeException("Only Datasource requests can be cancelled")
        }
        if (connection.type == DatasourceType.MONGODB) {
            throw RuntimeException("MongoDB requests can't be cancelled")
        }
        JDBCExecutor.cancelQuery(id)
    }

    @Policy(Permission.EXECUTION_REQUEST_EXECUTE)
    fun execute(id: ExecutionRequestId, query: String?, userId: String): ExecutionResult {
        val executionRequest = executionRequestAdapter.getExecutionRequestDetails(id)
        val connection = executionRequest.request.connection

        val reviewStatus = executionRequest.resolveReviewStatus()
        if (reviewStatus != ReviewStatus.APPROVED) {
            throw InvalidReviewException("This request has not been approved yet!")
        }

        executionRequest.raiseIfAlreadyExecuted()
        return when (connection) {
            is DatasourceConnection -> {
                executeDatasourceRequest(id, executionRequest, connection, query, userId)
            }
            is KubernetesConnection -> {
                executeKubernetesRequest(id, executionRequest, userId, query)
            }
        }
    }

    @Policy(Permission.EXECUTION_REQUEST_GET, checkIsPresentOnly = true)
    fun getCSVFileName(id: ExecutionRequestId): String {
        val executionRequest = executionRequestAdapter.getExecutionRequestDetails(id)
        val csvName = executionRequest.request.title.replace(" ", "_")
        return "$csvName.csv"
    }

    @Policy(Permission.EXECUTION_REQUEST_EXECUTE)
    fun streamResultsAsCsv(
        id: ExecutionRequestId,
        userId: String,
        outputStream: ServletOutputStream,
        query: String? = null,
    ) {
        val executionRequest = executionRequestAdapter.getExecutionRequestDetails(id)
        val connection = executionRequest.request.connection
        if (connection !is DatasourceConnection ||
            executionRequest.request !is DatasourceExecutionRequest
        ) {
            throw RuntimeException("Only Datasource requests can be downloaded as CSV")
        }
        if (connection.type == DatasourceType.MONGODB) {
            throw RuntimeException("MongoDB requests can't be downloaded as CSV")
        }
        val downloadAllowedAndReason = executionRequest.csvDownloadAllowed(query)
        val queryToExecute = when (executionRequest.request.type) {
            RequestType.SingleExecution ->
                executionRequest.request.statement!!
                    .trim()
                    .removeSuffix(";")
            RequestType.TemporaryAccess ->
                query?.trim()?.removeSuffix(";")
                    ?: throw MissingQueryException("For temporary access requests a query to execute is required")
            RequestType.Dump -> throw RuntimeException("Dump requests can't be downloaded as CSV")
        }
        if (!downloadAllowedAndReason.first) {
            throw RuntimeException(downloadAllowedAndReason.second)
        }

        val eventId = eventService.saveEvent(
            id,
            userId,
            ExecutePayload(
                query = queryToExecute,
                isDownload = true,
            ),
        ).eventId!!

        try {
            val writer = outputStream.writer(Charsets.UTF_8)
            JDBCExecutor.executeAndStreamDbResponse(
                connectionString = connection.getConnectionString(),
                authenticationDetails = connection.auth,
                query = queryToExecute,
            ) { row ->
                writer.write(
                    row.joinToString(",") { field ->
                        escapeCsvField(field)
                    } + "\n",
                )
                writer.flush()
            }
        } catch (e: Exception) {
            eventService.addResultLogs(
                eventId,
                listOf(
                    ErrorResultLog(
                        errorCode = 0,
                        message = e.message ?: "An error occurred while streaming the database response",
                    ),
                ),
            )
            throw e
        }
    }

    private fun escapeCsvField(field: String): String {
        if (field.contains("\"") || field.contains(",") || field.contains("\n")) {
            return "\"" + field.replace("\"", "\"\"") + "\""
        }
        return field
    }

    @Policy(Permission.EXECUTION_REQUEST_GET)
    fun explain(id: ExecutionRequestId, query: String?, userId: String): DBExecutionResult {
        val executionRequest = executionRequestAdapter.getExecutionRequestDetails(id)
        val connection = executionRequest.request.connection
        if (connection !is DatasourceConnection || executionRequest.request !is DatasourceExecutionRequest) {
            throw RuntimeException("Only Datasource connections can be explained")
        }

        if (!connection.explainEnabled) {
            throw IllegalArgumentException("Explain is not enabled for this connection")
        }

        val requestType = executionRequest.request.type
        if (requestType != RequestType.SingleExecution) {
            throw InvalidReviewException("Can only explain single queries!")
        }
        val parsedStatements = CCJSqlParserUtil.parseStatements(executionRequest.request.statement)
        val selectStatements = parsedStatements.filter { it is net.sf.jsqlparser.statement.select.Select }

        if (selectStatements.isEmpty()) {
            throw IllegalArgumentException("Can only explain SELECT queries!")
        }

        val explainStatements = if (connection.type == DatasourceType.MSSQL) {
            selectStatements.joinToString(";")
        } else {
            selectStatements.joinToString(";") { "EXPLAIN $it" }
        }

        val result = JDBCExecutor.execute(
            executionRequestId = id,
            connectionString = connection.getConnectionString(),
            authenticationDetails = connection.auth,
            query = explainStatements,
            MSSQLexplain = connection.type == DatasourceType.MSSQL,
        )

        return DBExecutionResult(results = result, executionRequest = executionRequest)
    }

    @Transactional
    @Policy(Permission.EXECUTION_REQUEST_GET)
    fun getExecutions(): List<ExecuteEvent> = eventService.getAllExecutions()

    // The following function is not MIT licensed
    @Transactional
    @Policy(Permission.EXECUTION_REQUEST_GET, checkIsPresentOnly = true)
    fun exportExecutionsAsText(): String {
        val executions = eventService.getAllExecutions()
        val dateFormatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

        return buildString {
            appendLine("=== KVIKLET AUDIT LOG EXPORT ===")
            appendLine("Generated: ${utcTimeNow().format(dateFormatter)}")
            appendLine("Total Executions: ${executions.size}")
            appendLine("=".repeat(80))
            appendLine()

            executions.sortedByDescending { it.createdAt }.forEach { execution ->
                appendLine("Timestamp: ${execution.createdAt.format(dateFormatter)}")
                appendLine("User: ${execution.author.fullName ?: execution.author.email}")
                appendLine("Connection: ${execution.request.connection.getId()}")
                appendLine("Request ID: ${execution.request.getId()}")

                val statement = execution.query ?: execution.command ?: ""
                appendLine("Statement: $statement")

                appendLine("Result:")
                if (execution.results.isEmpty()) {
                    appendLine("  No results recorded")
                } else {
                    execution.results.forEach { result ->
                        when (result) {
                            is ErrorResultLog -> {
                                appendLine("  ERROR (Code ${result.errorCode}): ${result.message}")
                            }
                            is dev.kviklet.kviklet.service.dto.UpdateResultLog -> {
                                appendLine("  SUCCESS: ${result.rowsUpdated} rows updated")
                            }
                            is dev.kviklet.kviklet.service.dto.QueryResultLog -> {
                                appendLine(
                                    "  SUCCESS: ${result.rowCount} rows returned (${result.columnCount} columns)",
                                )
                            }
                            is DumpResultLog -> {
                                appendLine("  DUMP: ${result.size} bytes")
                            }
                        }
                    }
                }
                appendLine("-".repeat(80))
                appendLine()
            }
        }
    }

    private fun cleanUpProxies() {
        val now = utcTimeNow()
        val expiredProxies = proxies.filter { it.startTime.plusMinutes(60) < now }
        expiredProxies.forEach {
            proxies.remove(it)
        }
    }

    @Transactional
    @Policy(Permission.EXECUTION_REQUEST_EXECUTE)
    fun proxy(executionRequestId: ExecutionRequestId, userDetails: UserDetailsWithId): ExecutionProxy {
        cleanUpProxies()
        val executionRequest = executionRequestAdapter.getExecutionRequestDetails(executionRequestId)
        val connection = executionRequest.request.connection
        if (connection !is DatasourceConnection) {
            throw RuntimeException("Only Datasource connections be proxied")
        }
        val reviewStatus = executionRequest.resolveReviewStatus()
        if (executionRequest.request.author.getId() != userDetails.id) {
            throw RuntimeException("Only the author of the request can proxy it!")
        }
        if (reviewStatus != ReviewStatus.APPROVED) {
            throw InvalidReviewException("This request has not been approved yet!")
        }
        if (connection.type != DatasourceType.POSTGRESQL) {
            throw RuntimeException("Only Postgres is supported for proxying!")
        }
        if (connection.auth !is AuthenticationDetails.UserPassword) {
            throw RuntimeException("Only UserPassword authentication is supported for proxying!")
        }

        executionRequest.raiseIfAlreadyExecuted()

        val executedEvents = executionRequest.events.filter { it.type == EventType.EXECUTE }
        val firstEventTime = executedEvents.map { it.createdAt }
            .ifEmpty { listOf(utcTimeNow()) }
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
            connection.auth,
            // using this instead of the users email because @ for database usernames is not allowed
            connection.auth.username,
            password,
            executionRequest = executionRequest.request,
            userId = userDetails.id,
            firstEventTime,
            maxTimeMinutes =
            executionRequest.request.temporaryAccessDuration?.toMinutes()
                ?: dev.kviklet.kviklet.proxy.postgres.INFINITE_ACCESS,
        )
        logger.info("Started proxy for user ${connection.auth.username} on port 5438")
        val proxy = ExecutionProxy(
            request = executionRequest.request,
            port = availablePort,
            username = connection.auth.username,
            password = password,
            startTime = firstEventTime,
        )
        proxies.add(proxy)

        return proxy
    }

    private fun generateRandomPassword(length: Int): String {
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
        authenticationDetails: AuthenticationDetails.UserPassword,
        email: String,
        tempPassword: String,
        executionRequest: ExecutionRequest,
        userId: String,
        startTime: LocalDateTime,
        maxTimeMinutes: Long,
    ): CompletableFuture<Void>? = CompletableFuture.runAsync {
        try {
            PostgresProxy(
                hostname,
                port,
                databaseName,
                authenticationDetails,
                eventService,
                executionRequest,
                userId,
                tlsCertificateFactory(proxyTLSCerts.proxyCertificates()),
            ).startServer(
                mappedPort,
                email,
                tempPassword,
                startTime,
                maxTimeMinutes,
            )
        } catch (e: Exception) {
            // At least print the exception, otherwise it will fail silently in the background
            logger.error("Error starting proxy for user $userId on port $port", e)
            throw e
        }
    }
}

fun ExecutionRequestDetails.raiseIfAlreadyExecuted() {
    if (resolveExecutionStatus() == ExecutionStatus.EXECUTED) {
        when (request.type) {
            RequestType.SingleExecution, RequestType.Dump ->
                throw AlreadyExecutedException(
                    "This request has already been executed, can only execute a configured amount of times!",
                )
            RequestType.TemporaryAccess ->
                throw AlreadyExecutedException(
                    "This request has timed out, temporary access is only valid for 60 minutes!",
                )
        }
    }
}

class InvalidReviewException(message: String) : RuntimeException(message)

class MissingQueryException(message: String) : RuntimeException(message)

class AlreadyExecutedException(message: String) : RuntimeException(message)
