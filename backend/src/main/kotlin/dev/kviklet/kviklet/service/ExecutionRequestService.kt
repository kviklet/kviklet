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
import dev.kviklet.kviklet.db.ReviewPayload
import dev.kviklet.kviklet.proxy.PostgresProxy
import dev.kviklet.kviklet.security.Permission
import dev.kviklet.kviklet.security.Policy
import dev.kviklet.kviklet.security.Resource
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
import dev.kviklet.kviklet.service.dto.ExecutionStatus
import dev.kviklet.kviklet.service.dto.KubernetesConnection
import dev.kviklet.kviklet.service.dto.KubernetesExecutionRequest
import dev.kviklet.kviklet.service.dto.KubernetesExecutionResult
import dev.kviklet.kviklet.service.dto.RequestType
import dev.kviklet.kviklet.service.dto.ReviewStatus
import dev.kviklet.kviklet.service.dto.SQLDumpResponse
import dev.kviklet.kviklet.service.dto.StreamedSQLDump
import dev.kviklet.kviklet.service.dto.utcTimeNow
import dev.kviklet.kviklet.shell.KubernetesApi
import jakarta.servlet.ServletOutputStream
import jakarta.transaction.Transactional
import net.sf.jsqlparser.parser.CCJSqlParserUtil
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.core.io.InputStreamResource
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import java.io.BufferedInputStream
import java.io.File
import java.io.IOException
import java.security.SecureRandom
import java.time.LocalDateTime
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

@Service
class ExecutionRequestService(
    private val executionRequestAdapter: ExecutionRequestAdapter,
    private val JDBCExecutor: JDBCExecutor,
    private val eventService: EventService,
    private val kubernetesApi: KubernetesApi,
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val mongoDBExecutor: MongoDBExecutor,
    private val connectionService: ConnectionService,
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val proxies = mutableListOf<ExecutionProxy>()

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
    ): ExecutionRequestDetails = executionRequestAdapter.createExecutionRequest(
        connectionId = connectionId,
        title = request.title,
        type = request.type,
        description = request.description,
        statement = request.statement,
        executionStatus = "PENDING",
        authorId = userId,
    )

    private fun createKubernetesRequest(
        connectionId: ConnectionId,
        request: CreateKubernetesExecutionRequestRequest,
        userId: String,
    ): ExecutionRequestDetails = executionRequestAdapter.createExecutionRequest(
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

    /**
     * Constructs a command list for dumping a SQL database based on the connection type.
     *
     * @param connection The DatasourceConnection object containing connection details.
     * @param outputFile An optional file path where the SQL dump will be saved.
     * @return A list of strings representing the command to execute.
     * @throws IllegalArgumentException If the database type is unsupported.
     */
    private fun constructSQLDumpCommand(connection: DatasourceConnection, outputFile: String? = null): List<String> =
        when (connection.type) {
            DatasourceType.MYSQL -> {
                val sqlDumpCommand = listOfNotNull(
                    "mysqldump",
                    "-u${connection.username}",
                    "-p${connection.password}",
                    "-h${connection.hostname}",
                    "-P${connection.port}",
                    "--databases",
                    connection.databaseName,
                    outputFile?.let { "--result-file=$it" },
                )
                sqlDumpCommand
            }

            // TODO: Here we'd get pg_dump version mismatch error
            DatasourceType.POSTGRESQL -> {
                val sqlDumpCommand = listOfNotNull(
                    "pg_dump",
                    "-U${connection.username}",
                    "-h${connection.hostname}",
                    "-p${connection.port}",
                    connection.databaseName,
                    outputFile?.let { "--file=$it" },
                )
                sqlDumpCommand
            }
            else -> {
                throw IllegalArgumentException("Unsupported database type: ${connection.type}")
            }
        }

    /**
     * Streams SQL dump data in real-time for a given datasource connection without having to save any temp files in memory.
     *
     * @param connectionId The ID of the datasource connection.
     * @return A Flux emitting chunks of SQL dump data as byte arrays.
     */
    @Transactional
    @Policy(Permission.EXECUTION_REQUEST_EXECUTE)
    fun streamSQLDump(connectionId: String): StreamedSQLDump {
        val flux = Flux.create<ByteArray> { sink ->
            var inputStream: BufferedInputStream? = null
            var process: Process? = null
            try {
                val connection = connectionService.getDatasourceConnection(ConnectionId(connectionId))

                if (connection is DatasourceConnection) {
                    // Construct SQL dump command
                    val command = constructSQLDumpCommand(connection)

                    // Set the PGPASSWORD environment variable if PostgreSQL
                    val env = System.getenv().toMutableMap()
                    if (connection.type == DatasourceType.POSTGRESQL) {
                        env["PGPASSWORD"] = connection.password
                    }

                    // Execute the SQL dump
                    process = ProcessBuilder(command).apply {
                        environment().putAll(env)
                    }.start()

                    inputStream = BufferedInputStream(process.inputStream)

                    // Buffer to read chunks of data from the process input stream
                    val buffer = ByteArray(8192)
                    var bytesRead: Int

                    // Read from the input stream in chunks and send each chunk to the Flux sink
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        sink.next(buffer.copyOf(bytesRead))
                        logger.info("Read and sent $bytesRead bytes")
                    }

                    // Wait for the process to complete and check the exit code
                    val exitCode = process.waitFor()
                    if (exitCode != 0) {
                        // If the process failed, send an error to the Flux sink
                        val errorStream = process.errorStream.bufferedReader().use { it.readText() }
                        sink.error(Exception("SQL dump command failed: $errorStream"))
                    } else {
                        sink.complete()
                    }
                } else {
                    // If the connection type is invalid, send an error to the Flux sink
                    sink.error(Exception("Invalid connection type for connectionId: $connectionId"))
                }
            } catch (e: Exception) {
                sink.error(Exception("Unexpected error occurred: ${e.message}"))
            } finally {
                // Ensure the input stream is closed properly
                try {
                    inputStream?.close()
                } catch (e: IOException) {
                    throw Exception("Error closing inputStream: ${e.message}")
                }
                process?.destroy()
            }
        }.onBackpressureBuffer()

        return StreamedSQLDump(flux, connectionId, Resource.EXECUTION_REQUEST)
    }

    /**
     * Generates an SQL dump file for a given datasource connection.
     *
     * @param connectionId The ID of the datasource connection.
     * @return An SQLDumpResponse containing the SQL dump file.
     */
    @Transactional
    @Policy(Permission.EXECUTION_REQUEST_EXECUTE)
    fun generateSQLDump(connectionId: String): SQLDumpResponse {
        try {
            // Get the db connection information
            val connection = connectionService.getDatasourceConnection(ConnectionId(connectionId))

            if (connection is DatasourceConnection) {
                // Create a temporary file for SQL dump output
                val tempFile = File.createTempFile(connectionId, ".sql")

                // Construct SQL dump command
                val command = constructSQLDumpCommand(connection, tempFile.absolutePath)

                // Execute SQL dump
                val processBuilder = ProcessBuilder(command)
                processBuilder.redirectErrorStream(true)
                val process = processBuilder.start()

                // Wait for the process to finish with a timeout of 60 seconds
                val success = process.waitFor(60, TimeUnit.SECONDS)

                // Check if the process completed successfully within the timeout
                if (success && process.exitValue() == 0) {
                    // If successful, prepare response entity with the SQL dump as input stream
                    val resource = InputStreamResource(tempFile.inputStream())
                    val fileName = "$connectionId.sql"
                    return SQLDumpResponse(resource, fileName, connectionId)
                } else {
                    // If the process did not complete successfully, destroy the process
                    process.destroy()
                    val errorStream = process.inputStream.bufferedReader().use { it.readText() }
                    throw Exception("SQL dump command failed or timed out: $errorStream")
                }
            } else {
                throw Exception("Invalid connection type for connectionId: $connectionId")
            }
        } catch (e: Exception) {
            throw Exception("Other unexpected error occurred: ${e.message}")
        }
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
            RequestType.SingleExecution, RequestType.SQLDump -> executionRequest.request.statement!!
            RequestType.TemporaryAccess -> query ?: throw MissingQueryException(
                "For temporary access requests the query param is required",
            )
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
                    username = connection.username,
                    password = connection.password,
                    query = queryToExecute,
                )
            }
        }

        if (executionRequest.request.type == RequestType.SingleExecution) {
            executionRequestAdapter.updateExecutionRequest(
                id,
                title = executionRequest.request.title,
                description = executionRequest.request.description,
                statement = executionRequest.request.statement,
                executionStatus = "EXECUTED",
            )
        }
        val resultLogs = result.map {
            it.toResultLog()
        }
        eventService.addResultLogs(event.eventId!!, resultLogs)

        return DBExecutionResult(
            executionRequest = executionRequest,
            results = result,
        )
    }

    private fun executeKubernetesRequest(
        id: ExecutionRequestId,
        executionRequest: ExecutionRequestDetails,
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
            namespace = executionRequest.request.namespace!!,
            podName = executionRequest.request.podName!!,
            command = executionRequest.request.command,
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
                executeKubernetesRequest(id, executionRequest, userId)
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
            RequestType.SingleExecution, RequestType.SQLDump ->
                executionRequest.request.statement!!
                    .trim()
                    .removeSuffix(";")
            RequestType.TemporaryAccess ->
                query?.trim()?.removeSuffix(";")
                    ?: throw MissingQueryException("For temporary access requests a query to execute is required")
        }
        if (!downloadAllowedAndReason.first) {
            throw RuntimeException(downloadAllowedAndReason.second)
        }

        eventService.saveEvent(
            id,
            userId,
            ExecutePayload(
                query = queryToExecute,
                isDownload = true,
            ),
        )

        JDBCExecutor.executeAndStreamDbResponse(
            connectionString = connection.getConnectionString(),
            username = connection.username,
            password = connection.password,
            query = queryToExecute,
        ) { row ->
            outputStream.println(
                row.joinToString(",") { field ->
                    escapeCsvField(field)
                },
            )
        }
    }

    private fun escapeCsvField(field: String): String {
        if (field.contains("\"") || field.contains(",") || field.contains("\n")) {
            return "\"" + field.replace("\"", "\"\"") + "\""
        }
        return field
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

        val explainStatements = if (connection.type == DatasourceType.MSSQL) {
            parsedStatements.joinToString(";")
        } else {
            parsedStatements.joinToString(";") { "EXPLAIN $it" }
        }

        val result = JDBCExecutor.execute(
            executionRequestId = id,
            connectionString = connection.getConnectionString(),
            username = connection.username,
            password = connection.password,
            query = explainStatements,
            MSSQLexplain = connection.type == DatasourceType.MSSQL,
        )

        return DBExecutionResult(results = result, executionRequest = executionRequest)
    }

    @Transactional
    @Policy(Permission.EXECUTION_REQUEST_GET)
    fun getExecutions(): List<ExecuteEvent> = eventService.getAllExecutions()

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
        username: String,
        password: String,
        email: String,
        tempPassword: String,
        executionRequest: ExecutionRequest,
        userId: String,
        startTime: LocalDateTime,
    ): CompletableFuture<Void>? = CompletableFuture.runAsync {
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

fun ExecutionRequestDetails.raiseIfAlreadyExecuted() {
    if (resolveExecutionStatus() == ExecutionStatus.EXECUTED) {
        when (request.type) {
            RequestType.SingleExecution, RequestType.SQLDump ->
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
