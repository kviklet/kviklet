package com.example.executiongate.db

import com.example.executiongate.db.util.BaseEntity
import com.example.executiongate.db.util.EventPayloadConverter
import com.example.executiongate.service.dto.Event
import com.example.executiongate.service.dto.EventId
import com.example.executiongate.service.dto.EventType
import com.example.executiongate.service.dto.ReviewAction
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import org.apache.commons.lang3.builder.ToStringBuilder
import org.apache.commons.lang3.builder.ToStringStyle.SHORT_PREFIX_STYLE
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDateTime
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import org.hibernate.annotations.ColumnTransformer

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "foobar", visible = false)
sealed class Payload(
    val type: EventType,
)

@JsonTypeName("COMMENT")
data class CommentPayload(
    val comment: String,
): Payload(EventType.COMMENT)

@JsonTypeName("REVIEW")
data class ReviewPayload(
    val comment: String,
    val action: ReviewAction,
): Payload(EventType.REVIEW)


@Entity(name = "event")
class EventEntity(

    @ManyToOne
    @JoinColumn(name = "execution_request_id")
    val executionRequest: ExecutionRequestEntity,

    @ManyToOne
    @JoinColumn(name = "author_id")
    val author: UserEntity,

    private val type: EventType,

    @Convert(converter = EventPayloadConverter::class)
    @Column(columnDefinition = "json")
    @ColumnTransformer(write = "?::json")
    private val payload: Payload,
    private val createdAt: LocalDateTime = LocalDateTime.now(),
): BaseEntity() {

    override fun toString(): String {
        return ToStringBuilder(this, SHORT_PREFIX_STYLE)
            .append("id", id)
            .toString()
    }

    fun toDto(): Event {
        return Event.create(
            id = id,
            createdAt = createdAt,
            payload = payload,
            author = author.toDto(),
        )
    }

}

interface EventRepository : JpaRepository<EventEntity, String>
