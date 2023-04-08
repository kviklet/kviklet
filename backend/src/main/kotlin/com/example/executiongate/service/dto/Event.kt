package com.example.executiongate.service.dto

import com.example.executiongate.db.CommentPayload
import com.example.executiongate.db.Payload
import com.example.executiongate.db.ReviewPayload
import java.io.Serializable
import java.time.LocalDateTime


@JvmInline
value class EventId(private val id: String): Serializable {
    override fun toString() = id
}

enum class EventType {
    REVIEW,
    COMMENT,
}

enum class ReviewAction {
    APPROVE,
    COMMENT,
    REQUEST_CHANGE,
}

/**
 * A DTO for the {@link com.example.executiongate.db.EventEntity} entity
 */
abstract class Event {
    abstract val id: EventId
    abstract val createdAt: LocalDateTime

    companion object {
        fun create(id: EventId, createdAt: LocalDateTime, payload: Payload): Event = when (payload) {
            is CommentPayload -> CommentEvent(id, createdAt, payload.comment)
            is ReviewPayload -> ReviewEvent(id, createdAt, payload.comment, payload.action)
            else -> throw IllegalArgumentException("Unknown payload type: ${payload::class}")
        }
    }
}

data class CommentEvent(
    override val id: EventId,
    override val createdAt: LocalDateTime,
    val comment: String,
): Event()

data class ReviewEvent(
    override val id: EventId,
    override val createdAt: LocalDateTime,
    val comment: String,
    val action: ReviewAction,
): Event()
