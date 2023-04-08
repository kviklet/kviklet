package com.example.executiongate.db

import com.example.executiongate.db.util.BaseEntity
import com.example.executiongate.db.util.EventPayloadConverter
import com.example.executiongate.service.dto.Event
import com.example.executiongate.service.dto.EventId
import com.example.executiongate.service.dto.EventType
import com.example.executiongate.service.dto.ReviewAction
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import org.apache.commons.lang3.builder.ToStringBuilder
import org.apache.commons.lang3.builder.ToStringStyle.SHORT_PREFIX_STYLE
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDateTime
import javax.persistence.Convert
import javax.persistence.Entity
import javax.persistence.JoinColumn
import javax.persistence.ManyToOne

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type", visible = true)
@JsonSubTypes(
    JsonSubTypes.Type(value = CommentPayload::class, name = "COMMENT"),
    JsonSubTypes.Type(value = ReviewPayload::class, name = "REVIEW"),
)
abstract class Payload(
    val type: EventType,
)

data class CommentPayload(
    val comment: String,
): Payload(EventType.COMMENT)

data class ReviewPayload(
    val comment: String,
    val action: ReviewAction,
): Payload(EventType.REVIEW)


@Entity(name = "event")
class EventEntity(
    @ManyToOne
    @JoinColumn(name = "execution_request_id")
    val executionRequest: ExecutionRequestEntity,

    private val type: EventType,

    @Convert(converter = EventPayloadConverter::class)
    private val payload: Payload,

    // createdBy
    private val createdAt: LocalDateTime = LocalDateTime.now(),
): BaseEntity() {

    override fun toString(): String {
        return ToStringBuilder(this, SHORT_PREFIX_STYLE)
            .append("id", id)
            .toString()
    }

    fun toDto(): Event {
        return Event.create(
            id = EventId(id),
            createdAt = createdAt,
            payload = payload,
        )
    }

}

interface EventRepository : JpaRepository<EventEntity, EventId>
