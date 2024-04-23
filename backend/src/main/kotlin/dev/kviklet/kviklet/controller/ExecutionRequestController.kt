package dev.kviklet.kviklet.controller

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import dev.kviklet.kviklet.db.User
import dev.kviklet.kviklet.security.CurrentUser
import dev.kviklet.kviklet.security.UserDetailsWithId
import dev.kviklet.kviklet.service.ColumnInfo
import dev.kviklet.kviklet.service.ErrorQueryResult
import dev.kviklet.kviklet.service.ExecutionRequestService
import dev.kviklet.kviklet.service.QueryResult
import dev.kviklet.kviklet.service.RecordsQueryResult
import dev.kviklet.kviklet.service.UpdateQueryResult
import dev.kviklet.kviklet.service.dto.CommentEvent
import dev.kviklet.kviklet.service.dto.ConnectionId
import dev.kviklet.kviklet.service.dto.DBExecutionResult
import dev.kviklet.kviklet.service.dto.DatasourceExecutionRequest
import dev.kviklet.kviklet.service.dto.EditEvent
import dev.kviklet.kviklet.service.dto.Event
import dev.kviklet.kviklet.service.dto.EventType
import dev.kviklet.kviklet.service.dto.ExecuteEvent
import dev.kviklet.kviklet.service.dto.ExecutionRequestDetails
import dev.kviklet.kviklet.service.dto.ExecutionRequestId
import dev.kviklet.kviklet.service.dto.ExecutionResult
import dev.kviklet.kviklet.service.dto.KubernetesExecutionRequest
import dev.kviklet.kviklet.service.dto.KubernetesExecutionResult
import dev.kviklet.kviklet.service.dto.RequestType
import dev.kviklet.kviklet.service.dto.ReviewAction
import dev.kviklet.kviklet.service.dto.ReviewEvent
import dev.kviklet.kviklet.service.dto.ReviewStatus
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.DiscriminatorMapping
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "connectionType")
@JsonSubTypes(
    JsonSubTypes.Type(value = CreateDatasourceExecutionRequestRequest::class, name = "DATASOURCE"),
    JsonSubTypes.Type(value = CreateKubernetesExecutionRequestRequest::class, name = "KUBERNETES"),
)
sealed class CreateExecutionRequestRequest(
    open val connectionId: ConnectionId,
    open val title: String,
    open val type: RequestType,
    open val description: String,
)

data class CreateDatasourceExecutionRequestRequest(
    override val connectionId: ConnectionId,
    override val title: String,
    override val type: RequestType,
    override val description: String,
    val statement: String?,
    val readOnly: Boolean,
) : CreateExecutionRequestRequest(connectionId, title, type, description)

data class CreateKubernetesExecutionRequestRequest(
    override val connectionId: ConnectionId,
    override val title: String,
    override val type: RequestType,
    override val description: String,
    val namespace: String,
    val podName: String,
    val containerName: String?,
    val command: String,
) : CreateExecutionRequestRequest(connectionId, title, type, description)

data class UpdateExecutionRequestRequest(
    val title: String?,
    val description: String?,
    val statement: String?,
    val readOnly: Boolean?,
    val namespace: String?,
    val podName: String?,
    val containerName: String?,
    val command: String?,
)

data class ExecuteExecutionRequestRequest(
    val query: String?,
    val explain: Boolean = false,
)

data class CreateReviewRequest(
    val comment: String,
    val action: ReviewAction,
)

data class CreateCommentRequest(
    val comment: String,
)

sealed class ExecutionRequestResponse() {
    companion object {
        fun fromDto(dto: ExecutionRequestDetails): ExecutionRequestResponse {
            val userResponse = UserResponse(dto.request.author)

            return when (dto.request) {
                is DatasourceExecutionRequest -> DatasourceExecutionRequestResponse(
                    id = dto.request.id!!,
                    author = userResponse,
                    type = dto.request.type,
                    title = dto.request.title,
                    description = dto.request.description,
                    statement = dto.request.statement,
                    readOnly = dto.request.readOnly,
                    reviewStatus = dto.resolveReviewStatus(),
                    executionStatus = dto.request.executionStatus,
                    createdAt = dto.request.createdAt,
                    connection = ConnectionResponse.fromDto(
                        dto.request.connection,
                    ),
                )
                is KubernetesExecutionRequest -> KubernetesExecutionRequestResponse(
                    id = dto.request.id!!,
                    author = userResponse,
                    type = dto.request.type,
                    title = dto.request.title,
                    description = dto.request.description,
                    reviewStatus = dto.resolveReviewStatus(),
                    executionStatus = dto.request.executionStatus,
                    createdAt = dto.request.createdAt,
                    connection = ConnectionResponse.fromDto(
                        dto.request.connection,
                    ),
                    namespace = dto.request.namespace,
                    podName = dto.request.podName,
                    containerName = dto.request.containerName,
                    command = dto.request.command,
                )
            }
        }
    }

    data class DatasourceExecutionRequestResponse(
        val id: ExecutionRequestId,
        val title: String,
        val type: RequestType,
        val author: UserResponse,
        val connection: ConnectionResponse,
        val description: String?,
        val statement: String?,
        val readOnly: Boolean,
        val reviewStatus: ReviewStatus,
        val executionStatus: String,
        val createdAt: LocalDateTime = LocalDateTime.now(),
    ) : ExecutionRequestResponse()

    data class KubernetesExecutionRequestResponse(
        val id: ExecutionRequestId,
        val title: String,
        val type: RequestType,
        val author: UserResponse,
        val connection: ConnectionResponse,
        val description: String?,
        val reviewStatus: ReviewStatus,
        val executionStatus: String,
        val createdAt: LocalDateTime = LocalDateTime.now(),
        val namespace: String?,
        val podName: String?,
        val containerName: String?,
        val command: String?,
    ) : ExecutionRequestResponse()
}

/**
 * A DTO for the {@link dev.kviklet.kviklet.db.ExecutionRequestEntity} entity
 */
sealed class ExecutionRequestDetailResponse {

    companion object {
        fun fromDto(dto: ExecutionRequestDetails): ExecutionRequestDetailResponse {
            return when (dto.request) {
                is DatasourceExecutionRequest -> DatasourceExecutionRequestDetailResponse(
                    id = dto.request.id!!,
                    author = UserResponse(dto.request.author),
                    type = dto.request.type,
                    title = dto.request.title,
                    description = dto.request.description,
                    statement = dto.request.statement,
                    readOnly = dto.request.readOnly,
                    reviewStatus = dto.resolveReviewStatus(),
                    executionStatus = dto.request.executionStatus,
                    createdAt = dto.request.createdAt,
                    events = dto.events.sortedBy { it.createdAt }.map { EventResponse.fromEvent(it) },
                    connection = ConnectionResponse.fromDto(dto.request.connection),
                )
                is KubernetesExecutionRequest -> KubernetesExecutionRequestDetailResponse(
                    id = dto.request.id!!,
                    author = UserResponse(dto.request.author),
                    type = dto.request.type,
                    title = dto.request.title,
                    description = dto.request.description,
                    reviewStatus = dto.resolveReviewStatus(),
                    executionStatus = dto.request.executionStatus,
                    createdAt = dto.request.createdAt,
                    namespace = dto.request.namespace,
                    podName = dto.request.podName,
                    containerName = dto.request.containerName,
                    command = dto.request.command,
                    events = dto.events.sortedBy { it.createdAt }.map { EventResponse.fromEvent(it) },
                    connection = ConnectionResponse.fromDto(dto.request.connection),
                )
            }
        }
    }
}

data class DatasourceExecutionRequestDetailResponse(
    val id: ExecutionRequestId,
    val title: String,
    val type: RequestType,
    val author: UserResponse,
    val connection: ConnectionResponse,
    val description: String?,
    val statement: String?,
    val readOnly: Boolean,
    val reviewStatus: ReviewStatus,
    val executionStatus: String,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val events: List<EventResponse>,
) : ExecutionRequestDetailResponse()

data class KubernetesExecutionRequestDetailResponse(
    val id: ExecutionRequestId,
    val title: String,
    val type: RequestType,
    val author: UserResponse,
    val connection: ConnectionResponse,
    val description: String?,
    val reviewStatus: ReviewStatus,
    val executionStatus: String,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val namespace: String?,
    val podName: String?,
    val containerName: String?,
    val command: String?,
    val events: List<EventResponse>,
) : ExecutionRequestDetailResponse()

@Schema(
    name = "ExecutionResultResponse",
    discriminatorProperty = "type",
    subTypes = [
        RecordsQueryResultResponse::class,
        UpdateQueryResultResponse::class,
    ],
    discriminatorMapping = [
        DiscriminatorMapping(value = "RECORDS", schema = RecordsQueryResultResponse::class),
        DiscriminatorMapping(value = "UPDATE_COUNT", schema = UpdateQueryResultResponse::class),
        DiscriminatorMapping(value = "ERROR", schema = ErrorQueryResultResponse::class),
    ],
)
sealed class ExecutionResultResponse(
    val type: ExecutionResultType,
) {

    companion object {
        fun fromDto(dto: QueryResult): ExecutionResultResponse {
            return when (dto) {
                is RecordsQueryResult -> RecordsQueryResultResponse.fromDto(dto)
                is UpdateQueryResult -> UpdateQueryResultResponse.fromDto(dto)
                is ErrorQueryResult -> ErrorQueryResultResponse.fromDto(dto)
            }
        }
    }
}

sealed class ExecutionResponse() {
    companion object {
        fun fromDto(results: ExecutionResult): ExecutionResponse {
            return when (results) {
                is DBExecutionResult -> DatasourceExecutionResponse(
                    results.results.map { it -> ExecutionResultResponse.fromDto(it) },
                )

                is KubernetesExecutionResult -> KubernetesExecutionResponse(
                    errors = results.errors,
                    messages = results.messages,
                    finished = results.finished,
                    exitCode = results.exitCode,
                )
            }
        }
    }

    data class DatasourceExecutionResponse(
        val results: List<ExecutionResultResponse>,
    ) : ExecutionResponse()

    data class KubernetesExecutionResponse(
        val errors: List<String>,
        val messages: List<String>,
        val finished: Boolean = true,
        val exitCode: Int? = 0,
    ) : ExecutionResponse()
}

enum class ExecutionResultType {
    RECORDS,
    UPDATE_COUNT,
    ERROR,
}

data class RecordsQueryResultResponse(
    val columns: List<ColumnInfo>,
    val data: List<Map<String, String>>,
) : ExecutionResultResponse(
    type = ExecutionResultType.RECORDS,
) {
    companion object {
        fun fromDto(dto: RecordsQueryResult) = RecordsQueryResultResponse(
            columns = dto.columns,
            data = dto.data,
        )
    }
}

data class UpdateQueryResultResponse(
    val rowsUpdated: Int,
) : ExecutionResultResponse(
    type = ExecutionResultType.UPDATE_COUNT,
) {
    companion object {
        fun fromDto(dto: UpdateQueryResult) = UpdateQueryResultResponse(
            rowsUpdated = dto.rowsUpdated,
        )
    }
}

data class ErrorQueryResultResponse(
    val errorCode: Int,
    val message: String?,
) : ExecutionResultResponse(
    type = ExecutionResultType.ERROR,
) {

    companion object {
        fun fromDto(dto: ErrorQueryResult) = ErrorQueryResultResponse(
            errorCode = dto.errorCode,
            message = dto.message,
        )
    }
}

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = CommentEventResponse::class, name = "COMMENT"),
    JsonSubTypes.Type(value = ReviewEventResponse::class, name = "REVIEW"),
    JsonSubTypes.Type(value = EditEventResponse::class, name = "EDIT"),
)
abstract class EventResponse(
    val type: EventType,
    open val createdAt: LocalDateTime = LocalDateTime.now(),
) {
    abstract val id: String
    abstract val author: User

    companion object {
        fun fromEvent(event: Event): EventResponse = when (event) {
            is CommentEvent -> CommentEventResponse(event.eventId!!, event.author, event.createdAt, event.comment)
            is ReviewEvent -> ReviewEventResponse(
                event.eventId!!,
                event.author,
                event.createdAt,
                event.comment,
                event.action,
            )
            is EditEvent -> EditEventResponse(
                event.eventId!!,
                event.author,
                event.createdAt,
                event.previousQuery,
                event.previousCommand,
                event.previousContainerName,
                event.previousPodName,
                event.previousNamespace,
            )
            is ExecuteEvent -> ExecuteEventResponse(
                event.eventId!!,
                event.author,
                event.createdAt,
                event.query,
                event.command,
                event.containerName,
                event.podName,
                event.namespace,
            )
            else -> {
                throw IllegalStateException("Somehow found event of type ${event.type}")
            }
        }
    }
}

@JsonTypeName("COMMENT")
data class CommentEventResponse(
    override val id: String,
    override val author: User,
    override val createdAt: LocalDateTime = LocalDateTime.now(),
    val comment: String,
) : EventResponse(EventType.COMMENT, createdAt)

@JsonTypeName("REVIEW")
data class ReviewEventResponse(
    override val id: String,
    override val author: User,
    override val createdAt: LocalDateTime = LocalDateTime.now(),
    val comment: String,
    val action: ReviewAction,
) : EventResponse(EventType.REVIEW, createdAt)

@JsonTypeName("EDIT")
data class EditEventResponse(
    override val id: String,
    override val author: User,
    override val createdAt: LocalDateTime = LocalDateTime.now(),
    val previousQuery: String? = null,
    val previousCommand: String? = null,
    val previousContainerName: String? = null,
    val previousPodName: String? = null,
    val previousNamespace: String? = null,
) : EventResponse(EventType.EDIT, createdAt)

@JsonTypeName("EXECUTE")
data class ExecuteEventResponse(
    override val id: String,
    override val author: User,
    override val createdAt: LocalDateTime = LocalDateTime.now(),
    val query: String? = null,
    val command: String? = null,
    val containerName: String? = null,
    val podName: String? = null,
    val namespace: String? = null,
) : EventResponse(EventType.EXECUTE, createdAt)

data class ProxyResponse(
    val port: Int,
    val username: String,
    val password: String,
)

@RestController()
@Validated
@RequestMapping("/execution-requests")
@Tag(
    name = "Execution Requests",
    description = "Run queries against a datasource by interacting with Execution Requests",
)
class ExecutionRequestController(
    val executionRequestService: ExecutionRequestService,
) {

    @Operation(summary = "Create Execution Request")
    @PostMapping("/")
    fun create(
        @Valid @RequestBody
        request: CreateExecutionRequestRequest,
        @CurrentUser userDetails: UserDetailsWithId,
    ): ExecutionRequestResponse {
        val executionRequest = executionRequestService.create(request.connectionId, request, userDetails.id)
        return ExecutionRequestResponse.fromDto(executionRequest)
    }

    @Operation(summary = "Get Execution Request")
    @GetMapping("/{executionRequestId}")
    fun get(@PathVariable executionRequestId: ExecutionRequestId): ExecutionRequestDetailResponse {
        return executionRequestService.get(executionRequestId).let { ExecutionRequestDetailResponse.fromDto(it) }
    }

    @Operation(summary = "List Execution Requests")
    @GetMapping("/")
    fun list(): List<ExecutionRequestResponse> {
        return executionRequestService.list().map { ExecutionRequestResponse.fromDto(it) }
    }

    @Operation(summary = "Review Execution Request", description = "Approve or disapprove an execution request.")
    @PostMapping("/{executionRequestId}/reviews")
    fun createReview(
        @PathVariable executionRequestId: ExecutionRequestId,
        @Valid @RequestBody
        request: CreateReviewRequest,
        @CurrentUser userDetails: UserDetailsWithId,
    ): EventResponse {
        return EventResponse.fromEvent(
            executionRequestService.createReview(executionRequestId, request, userDetails.id),
        )
    }

    @PatchMapping("/{id}")
    fun update(
        @PathVariable id: ExecutionRequestId,
        @Valid @RequestBody
        request: UpdateExecutionRequestRequest,
        @CurrentUser userDetails: UserDetailsWithId,
    ): ExecutionRequestDetailResponse {
        val newRequest = executionRequestService.update(id, request, userDetails.id)
        return ExecutionRequestDetailResponse.fromDto(newRequest)
    }

    @Operation(summary = "Comment", description = "Leave a comment on an execution request.")
    @PostMapping("/{executionRequestId}/comments")
    fun createComment(
        @PathVariable executionRequestId: ExecutionRequestId,
        @Valid @RequestBody
        request: CreateCommentRequest,
        @CurrentUser userDetails: UserDetailsWithId,
    ): EventResponse {
        return EventResponse
            .fromEvent(executionRequestService.createComment(executionRequestId, request, userDetails.id))
    }

    @Operation(
        summary = "Execute Execution Request",
        description = "Run the query after the Execution Request has been approved.",
    )
    @PostMapping("/{executionRequestId}/execute")
    fun execute(
        @PathVariable executionRequestId: ExecutionRequestId,
        @RequestBody(required = false) request: ExecuteExecutionRequestRequest?,
        @CurrentUser userDetails: UserDetailsWithId,
    ): ExecutionResponse {
        return ExecutionResponse.fromDto(
            when (request?.explain) {
                true -> executionRequestService.explain(executionRequestId, request.query, userDetails.id)
                else -> executionRequestService.execute(executionRequestId, request?.query, userDetails.id)
            },
        )
    }

    @Operation(
        summary = "Start Proxy",
        description = "Start the Kviklet proxy for a temp access request. Only works for postgresql.",
    )
    @PostMapping("/{executionRequestId}/proxy")
    fun proxy(
        @PathVariable executionRequestId: ExecutionRequestId,
        @CurrentUser userDetails: UserDetailsWithId,
    ): ProxyResponse {
        val proxy = executionRequestService.proxy(executionRequestId, userDetails)
        return ProxyResponse(
            port = proxy.port,
            username = proxy.username,
            password = proxy.password,
        )
    }
}
