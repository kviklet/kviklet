package dev.kviklet.kviklet.db

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import dev.kviklet.kviklet.db.util.BaseEntity
import dev.kviklet.kviklet.db.util.EventPayloadConverter
import dev.kviklet.kviklet.service.dto.Connection
import dev.kviklet.kviklet.service.dto.Event
import dev.kviklet.kviklet.service.dto.EventId
import dev.kviklet.kviklet.service.dto.EventType
import dev.kviklet.kviklet.service.dto.ExecuteEvent
import dev.kviklet.kviklet.service.dto.ExecutionRequest
import dev.kviklet.kviklet.service.dto.ResultType
import dev.kviklet.kviklet.service.dto.ReviewAction
import dev.kviklet.kviklet.service.dto.utcTimeNow
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
import java.time.Duration
import java.time.LocalDateTime

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "foobar", visible = false)
sealed class Payload(val type: EventType)

@JsonTypeName("COMMENT")
data class CommentPayload(val comment: String) : Payload(EventType.COMMENT)

@JsonTypeName("REVIEW")
data class ReviewPayload(val comment: String, val action: ReviewAction) : Payload(EventType.REVIEW)

@JsonTypeName("EDIT")
data class EditPayload(
    val previousQuery: String? = null,
    val previousAccessDurationInMinutes: Duration? = null,
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
    val results: List<ResultLogPayload> = emptyList(),
    val isDownload: Boolean = false,
    val isDump: Boolean = false,
) : Payload(EventType.EXECUTE)

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "type",
    visible = true,
)
@JsonSubTypes(
    JsonSubTypes.Type(value = ErrorResultLogPayload::class, name = "ERROR"),
    JsonSubTypes.Type(value = UpdateResultLogPayload::class, name = "UPDATE"),
    JsonSubTypes.Type(value = QueryResultLogPayload::class, name = "QUERY"),
)
sealed class ResultLogPayload(val type: ResultType)

data class ErrorResultLogPayload(val errorCode: Int, val message: String) : ResultLogPayload(ResultType.ERROR)

data class UpdateResultLogPayload(val rowsUpdated: Int) : ResultLogPayload(ResultType.UPDATE)

data class QueryResultLogPayload(val columnCount: Int, val rowCount: Int) : ResultLogPayload(ResultType.QUERY)

data class DumpResultLogPayload(val size: Long) : ResultLogPayload(ResultType.DUMP)

@Entity(name = "event")
class EventEntity(

    @ManyToOne
    @JoinColumn(name = "execution_request_id")
    val executionRequest: ExecutionRequestEntity,

    @ManyToOne
    @JoinColumn(name = "author_id")
    val author: UserEntity,

    @Enumerated(EnumType.STRING)
    var type: EventType,

    @Convert(converter = EventPayloadConverter::class)
    @Column(columnDefinition = "json")
    @ColumnTransformer(write = "?::json")
    var payload: Payload,
    var createdAt: LocalDateTime = utcTimeNow(),
) : BaseEntity() {

    override fun toString(): String = ToStringBuilder(this, SHORT_PREFIX_STYLE)
        .append("id", id)
        .toString()

    fun toDto(request: ExecutionRequest? = null, connection: Connection): Event {
        if (request == null) {
            val executionDetails = executionRequest.toDetailDto(connection)
            return executionDetails.events.find { it.eventId.toString() == id }!!
        }
        return Event.create(
            id = EventId(id!!),
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
class EventAdapter(private val eventRepository: EventRepository, private val connectionAdapter: ConnectionAdapter) {
    fun getExecutions(): List<ExecuteEvent> {
        val events = eventRepository.findByType(EventType.EXECUTE)
        return events.map {
            it.toDto(
                connection = connectionAdapter.toDto(it.executionRequest.connection),
            )
        }.filterIsInstance<ExecuteEvent>()
    }

    fun updateEvent(id: EventId, payload: Payload): Event {
        val event = eventRepository.findById(id.toString()).orElseThrow { IllegalArgumentException("Event not found") }
        event.payload = payload
        eventRepository.save(event)
        return event.toDto(
            connection = connectionAdapter.toDto(event.executionRequest.connection),
        )
    }

    fun getEvent(id: EventId): Event {
        val event = eventRepository.findById(id.toString()).orElseThrow { IllegalArgumentException("Event not found") }
        return event.toDto(
            connection = connectionAdapter.toDto(event.executionRequest.connection),
        )
    }
}
