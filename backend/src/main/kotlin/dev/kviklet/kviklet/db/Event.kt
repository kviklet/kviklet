package dev.kviklet.kviklet.db

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import dev.kviklet.kviklet.db.util.BaseEntity
import dev.kviklet.kviklet.db.util.EventPayloadConverter
import dev.kviklet.kviklet.service.dto.Event
import dev.kviklet.kviklet.service.dto.EventType
import dev.kviklet.kviklet.service.dto.ExecutionRequestDetails
import dev.kviklet.kviklet.service.dto.ReviewAction
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import org.apache.commons.lang3.builder.ToStringBuilder
import org.apache.commons.lang3.builder.ToStringStyle.SHORT_PREFIX_STYLE
import org.hibernate.annotations.ColumnTransformer
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDateTime

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "foobar", visible = false)
sealed class Payload(
    val type: EventType,
)

@JsonTypeName("COMMENT")
data class CommentPayload(
    val comment: String,
) : Payload(EventType.COMMENT)

@JsonTypeName("REVIEW")
data class ReviewPayload(
    val comment: String,
    val action: ReviewAction,
) : Payload(EventType.REVIEW)

@JsonTypeName("EDIT")
data class EditPayload(
    val previousQuery: String,
) : Payload(EventType.EDIT)

@JsonTypeName("EXECUTE")
data class ExecutePayload(
    val query: String,
) : Payload(EventType.EXECUTE)

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
) : BaseEntity() {

    override fun toString(): String {
        return ToStringBuilder(this, SHORT_PREFIX_STYLE)
            .append("id", id)
            .toString()
    }

    fun toDto(request: ExecutionRequestDetails): Event {
        return Event.create(
            id = id,
            createdAt = createdAt,
            payload = payload,
            author = author.toDto(),
            request = request,
        )
    }
}

interface EventRepository : JpaRepository<EventEntity, String>
