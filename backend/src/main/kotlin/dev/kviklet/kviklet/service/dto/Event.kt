package dev.kviklet.kviklet.service.dto

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue
import dev.kviklet.kviklet.db.CommentPayload
import dev.kviklet.kviklet.db.DumpResultLogPayload
import dev.kviklet.kviklet.db.EditPayload
import dev.kviklet.kviklet.db.ErrorResultLogPayload
import dev.kviklet.kviklet.db.ExecutePayload
import dev.kviklet.kviklet.db.Payload
import dev.kviklet.kviklet.db.QueryResultLogPayload
import dev.kviklet.kviklet.db.ReviewPayload
import dev.kviklet.kviklet.db.UpdateResultLogPayload
import dev.kviklet.kviklet.db.User
import dev.kviklet.kviklet.security.Resource
import dev.kviklet.kviklet.security.SecuredDomainId
import dev.kviklet.kviklet.security.SecuredDomainObject
import java.io.Serializable
import java.time.Duration
import java.time.LocalDateTime
import java.util.*

data class EventId
@JsonCreator constructor(private val id: String) :
    Serializable,
    SecuredDomainId {
    @JsonValue
    override fun toString() = id
}

enum class EventType {
    REVIEW,
    COMMENT,
    EDIT,
    EXECUTE,
}

enum class ReviewAction {
    APPROVE,
    REQUEST_CHANGE,
    REJECT,
}

/**
 * A DTO for the {@link dev.kviklet.kviklet.db.EventEntity} entity
 */
abstract class Event(
    val type: EventType,
    open val createdAt: LocalDateTime = utcTimeNow(),
    open val request: ExecutionRequest,
) : SecuredDomainObject {
    abstract val eventId: EventId?
    abstract val author: User

    fun getId(): String? = eventId?.toString()
    override fun getSecuredObjectId(): String? = request.getSecuredObjectId()

    override fun getDomainObjectType(): Resource = request.getDomainObjectType()

    override fun getRelated(resource: Resource) = request.getRelated(resource)
    override fun hashCode() = Objects.hash(eventId)

    companion object {
        fun create(
            id: EventId?,
            request: ExecutionRequest,
            author: User,
            createdAt: LocalDateTime,
            payload: Payload,
        ): Event = when (payload) {
            is CommentPayload -> CommentEvent(id, request, author, createdAt, payload.comment)
            is ReviewPayload -> ReviewEvent(id, request, author, createdAt, payload.comment, payload.action)
            is EditPayload -> EditEvent(
                id, request, author, createdAt, payload.previousQuery,
                payload.previousCommand, payload.previousContainerName, payload.previousPodName,
                payload.previousNamespace, payload.previousAccessDurationInMinutes,
            )
            is ExecutePayload -> {
                val results = payload.results.map {
                    when (it) {
                        is ErrorResultLogPayload -> ErrorResultLog(it.errorCode, it.message)
                        is UpdateResultLogPayload -> UpdateResultLog(it.rowsUpdated)
                        is QueryResultLogPayload -> QueryResultLog(it.columnCount, it.rowCount)
                        is DumpResultLogPayload -> DumpResultLog(it.size)
                    }
                }
                ExecuteEvent(
                    id, request, author, createdAt, payload.query, results, payload.command,
                    payload.containerName, payload.podName, payload.namespace, payload.isDownload,
                    payload.isDump,
                )
            }
        }
    }
}

data class CommentEvent(
    override val eventId: EventId?,
    override val request: ExecutionRequest,
    override val author: User,
    override val createdAt: LocalDateTime = utcTimeNow(),
    val comment: String,
) : Event(EventType.COMMENT, createdAt, request) {
    override fun hashCode() = Objects.hash(eventId)
}

data class ReviewEvent(
    override val eventId: EventId?,
    override val request: ExecutionRequest,
    override val author: User,
    override val createdAt: LocalDateTime = utcTimeNow(),
    val comment: String,
    val action: ReviewAction,
) : Event(EventType.REVIEW, createdAt, request) {
    override fun hashCode() = Objects.hash(eventId)
}

data class EditEvent(
    override val eventId: EventId?,
    override val request: ExecutionRequest,
    override val author: User,
    override val createdAt: LocalDateTime = utcTimeNow(),
    val previousQuery: String? = null,
    val previousCommand: String? = null,
    val previousContainerName: String? = null,
    val previousPodName: String? = null,
    val previousNamespace: String? = null,
    val previousAccessDuration: Duration? = null,
) : Event(EventType.EDIT, createdAt, request) {
    override fun hashCode() = Objects.hash(eventId)
}

data class ExecuteEvent(
    override val eventId: EventId?,
    override val request: ExecutionRequest,
    override val author: User,
    override val createdAt: LocalDateTime = utcTimeNow(),
    val query: String? = null,
    val results: List<ResultLog> = emptyList(),
    val command: String? = null,
    val containerName: String? = null,
    val podName: String? = null,
    val namespace: String? = null,
    val isDownload: Boolean = false,
    val isDump: Boolean = false,
) : Event(EventType.EXECUTE, createdAt, request) {
    override fun hashCode() = Objects.hash(eventId)
}

enum class ResultType {
    ERROR,
    UPDATE,
    QUERY,
    DUMP,
}

sealed class ResultLog(val type: ResultType)

data class ErrorResultLog(val errorCode: Int, val message: String) : ResultLog(ResultType.ERROR)

data class UpdateResultLog(val rowsUpdated: Int) : ResultLog(ResultType.UPDATE)

data class QueryResultLog(val columnCount: Int, val rowCount: Int) : ResultLog(ResultType.QUERY)

data class DumpResultLog(val size: Long) : ResultLog(ResultType.DUMP)
