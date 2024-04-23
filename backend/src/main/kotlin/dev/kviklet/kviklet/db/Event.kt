package dev.kviklet.kviklet.db

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import dev.kviklet.kviklet.db.util.BaseEntity
import dev.kviklet.kviklet.db.util.EventPayloadConverter
import dev.kviklet.kviklet.service.dto.Event
import dev.kviklet.kviklet.service.dto.EventType
import dev.kviklet.kviklet.service.dto.ExecuteEvent
import dev.kviklet.kviklet.service.dto.ExecutionRequestDetails
import dev.kviklet.kviklet.service.dto.ReviewAction
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import org.apache.commons.lang3.builder.ToStringBuilder
import org.apache.commons.lang3.builder.ToStringStyle.SHORT_PREFIX_STYLE
import org.hibernate.annotations.ColumnTransformer
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Service
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
    val previousQuery: String? = null,
    val previousCommand: String? = null,
    val previousContainerName: String? = null,
    val previousPodName: String? = null,
    val previousNamespace: String? = null,
) : Payload(EventType.EDIT)

@JsonTypeName("EXECUTE")
data class ExecutePayload(
    val query: String? = null,
    val command: String? = null,
    val containerName: String? = null,
    val podName: String? = null,
    val namespace: String? = null,
) : Payload(EventType.EXECUTE)

@Entity(name = "event")
class EventEntity(

    @ManyToOne
    @JoinColumn(name = "execution_request_id")
    val executionRequest: ExecutionRequestEntity,

    @ManyToOne
    @JoinColumn(name = "author_id")
    val author: UserEntity,

    @Enumerated(EnumType.STRING)
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

    fun toDto(request: ExecutionRequestDetails? = null): Event {
        if (request == null) {
            val executionDetails = executionRequest.toDetailDto()
            return executionDetails.events.find { it.eventId == id }!!
        }
        return Event.create(
            id = id,
            createdAt = createdAt,
            payload = payload,
            author = author.toDto(),
            request = request,
        )
    }
}

interface EventRepository : JpaRepository<EventEntity, String> {
    fun findByType(type: EventType): List<EventEntity>
}

@Service
class EventAdapter(
    private val eventRepository: EventRepository,
) {
    fun getExecutions(): List<ExecuteEvent> {
        val events = eventRepository.findByType(EventType.EXECUTE)
        return events.map { it.toDto() }.filterIsInstance<ExecuteEvent>()
    }
}
