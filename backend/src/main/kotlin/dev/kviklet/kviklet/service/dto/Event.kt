package dev.kviklet.kviklet.service.dto

import dev.kviklet.kviklet.db.CommentPayload
import dev.kviklet.kviklet.db.EditPayload
import dev.kviklet.kviklet.db.ExecutePayload
import dev.kviklet.kviklet.db.Payload
import dev.kviklet.kviklet.db.ReviewPayload
import dev.kviklet.kviklet.db.User
import dev.kviklet.kviklet.security.Resource
import dev.kviklet.kviklet.security.SecuredDomainObject
import java.io.Serializable
import java.time.LocalDateTime
import java.util.*

@JvmInline
value class EventId(private val id: String) : Serializable {
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
    COMMENT,
    REQUEST_CHANGE,
}

/**
 * A DTO for the {@link dev.kviklet.kviklet.db.EventEntity} entity
 */
abstract class Event(
    val type: EventType,
    open val createdAt: LocalDateTime = LocalDateTime.now(),
    open val request: ExecutionRequestDetails,
) : SecuredDomainObject {
    abstract val eventId: String?
    abstract val author: User

    override fun getId(): String? = eventId

    override fun getDomainObjectType(): Resource = Resource.EVENT

    override fun getRelated(resource: Resource): SecuredDomainObject? = request
    override fun hashCode() = Objects.hash(eventId)

    companion object {
        fun create(
            id: String?,
            request: ExecutionRequestDetails,
            author: User,
            createdAt: LocalDateTime,
            payload: Payload,
        ): Event = when (payload) {
            is CommentPayload -> CommentEvent(id, request, author, createdAt, payload.comment)
            is ReviewPayload -> ReviewEvent(id, request, author, createdAt, payload.comment, payload.action)
            is EditPayload -> EditEvent(id, request, author, createdAt, payload.previousQuery)
            is ExecutePayload -> ExecuteEvent(id, request, author, createdAt, payload.query)
        }
    }
}

data class CommentEvent(
    override val eventId: String?,
    override val request: ExecutionRequestDetails,
    override val author: User,
    override val createdAt: LocalDateTime = LocalDateTime.now(),
    val comment: String,
) : Event(EventType.COMMENT, createdAt, request) {
    override fun hashCode() = Objects.hash(eventId)
}

data class ReviewEvent(
    override val eventId: String?,
    override val request: ExecutionRequestDetails,
    override val author: User,
    override val createdAt: LocalDateTime = LocalDateTime.now(),
    val comment: String,
    val action: ReviewAction,
) : Event(EventType.REVIEW, createdAt, request) {
    override fun hashCode() = Objects.hash(eventId)
}

data class EditEvent(
    override val eventId: String?,
    override val request: ExecutionRequestDetails,
    override val author: User,
    override val createdAt: LocalDateTime = LocalDateTime.now(),
    val previousQuery: String,
) : Event(EventType.EDIT, createdAt, request) {
    override fun hashCode() = Objects.hash(eventId)
}

data class ExecuteEvent(
    override val eventId: String?,
    override val request: ExecutionRequestDetails,
    override val author: User,
    override val createdAt: LocalDateTime = LocalDateTime.now(),
    val query: String,
) : Event(EventType.EXECUTE, createdAt, request) {
    override fun hashCode() = Objects.hash(eventId)
}
