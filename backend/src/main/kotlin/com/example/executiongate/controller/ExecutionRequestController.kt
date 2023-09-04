package com.example.executiongate.controller

import com.example.executiongate.security.UserDetailsWithId
import com.example.executiongate.service.ColumnInfo
import com.example.executiongate.service.ErrorQueryResult
import com.example.executiongate.service.ExecutionRequestService
import com.example.executiongate.service.QueryResult
import com.example.executiongate.service.RecordsQueryResult
import com.example.executiongate.service.UpdateQueryResult
import com.example.executiongate.service.dto.DatasourceConnectionId
import com.example.executiongate.service.dto.Event
import com.example.executiongate.service.dto.ExecutionRequestDetails
import com.example.executiongate.service.dto.ExecutionRequestId
import com.example.executiongate.service.dto.ReviewAction
import com.example.executiongate.service.dto.ReviewStatus
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.DiscriminatorMapping
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime

data class CreateExecutionRequest(
    val datasourceConnectionId: DatasourceConnectionId,
    val title: String,
    val description: String?,
    val statement: String,
    val readOnly: Boolean,
)

data class UpdateExecutionRequest(
    val title: String?,
    val description: String?,
    val statement: String?,
    val readOnly: Boolean?,
)

data class CreateReviewRequest(
    val comment: String,
    val action: ReviewAction,
)

data class CreateCommentRequest(
    val comment: String,
)

/**
 * A DTO for the {@link com.example.executiongate.db.ExecutionRequestEntity} entity
 */
data class ExecutionRequestResponse(
    val id: ExecutionRequestId,
    val title: String,
    val author: UserResponse,
    val connection: DatasourceConnectionResponse,
    val description: String?,
    val statement: String,
    val readOnly: Boolean,
    val reviewStatus: ReviewStatus,
    val executionStatus: String,
    val createdAt: LocalDateTime = LocalDateTime.now(),
) {

    companion object {
        fun fromDto(dto: ExecutionRequestDetails): ExecutionRequestResponse {
            val userResponse = UserResponse(dto.request.author)
            return ExecutionRequestResponse(
                id = dto.request.id,
                author = userResponse,
                title = dto.request.title,
                description = dto.request.description,
                statement = dto.request.statement,
                readOnly = dto.request.readOnly,
                reviewStatus = dto.resolveReviewStatus(),
                executionStatus = dto.request.executionStatus,
                createdAt = dto.request.createdAt,
                connection = DatasourceConnectionResponse.fromDto(dto.request.connection),
            )
        }
    }
}

/**
 * A DTO for the {@link com.example.executiongate.db.ExecutionRequestEntity} entity
 */
data class ExecutionRequestDetailResponse(
    val id: ExecutionRequestId,
    val author: UserResponse,
    val connection: DatasourceConnectionResponse,
    val title: String,
    val description: String?,
    val statement: String,
    val readOnly: Boolean,
    val reviewStatus: ReviewStatus,
    val executionStatus: String,
    val events: List<Event>,
    val createdAt: LocalDateTime = LocalDateTime.now(),
) {

    companion object {
        fun fromDto(dto: ExecutionRequestDetails) = ExecutionRequestDetailResponse(
            id = dto.request.id,
            author = UserResponse(dto.request.author),
            title = dto.request.title,
            description = dto.request.description,
            statement = dto.request.statement,
            readOnly = dto.request.readOnly,
            reviewStatus = dto.resolveReviewStatus(),
            executionStatus = dto.request.executionStatus,
            createdAt = dto.request.createdAt,
            events = dto.events.sortedBy { it.createdAt },
            connection = DatasourceConnectionResponse.fromDto(dto.request.connection),
        )
    }
}

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
        @Valid @RequestBody request: CreateExecutionRequest,
        @AuthenticationPrincipal userDetails: UserDetailsWithId,
    ): ExecutionRequestResponse {
        val executionRequest = executionRequestService.create(request, userDetails.id)
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
        @Valid @RequestBody request: CreateReviewRequest,
        @AuthenticationPrincipal userDetails: UserDetailsWithId,
    ): Event {
        return executionRequestService.createReview(executionRequestId, request, userDetails.id)
    }

    @PatchMapping("/{id}")
    fun update(
        @PathVariable id: ExecutionRequestId,
        @Valid @RequestBody request: UpdateExecutionRequest,
    ): ExecutionRequestDetailResponse {
        val newRequest = executionRequestService.update(id, request)
        return ExecutionRequestDetailResponse.fromDto(newRequest)
    }

    @Operation(summary = "Comment", description = "Leave a comment on an execution request.")
    @PostMapping("/{executionRequestId}/comments")
    fun createComment(
        @PathVariable executionRequestId: ExecutionRequestId,
        @Valid @RequestBody request: CreateCommentRequest,
        @AuthenticationPrincipal userDetails: UserDetailsWithId,
    ): Event {
        return executionRequestService.createComment(executionRequestId, request, userDetails.id)
    }

    @Operation(
        summary = "Execute Execution Request",
        description = "Run the query after the Execution Request has been approved.",
    )
    @PostMapping("/{executionRequestId}/execute")
    fun execute(@PathVariable executionRequestId: ExecutionRequestId): ExecutionResultResponse {
        return ExecutionResultResponse.fromDto(executionRequestService.execute(executionRequestId))
    }
}
