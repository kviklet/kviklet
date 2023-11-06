package com.example.executiongate.db

import com.example.executiongate.db.util.BaseEntity
import com.example.executiongate.service.EntityNotFound
import com.example.executiongate.service.dto.DatasourceConnectionId
import com.example.executiongate.service.dto.Event
import com.example.executiongate.service.dto.ExecutionRequest
import com.example.executiongate.service.dto.ExecutionRequestDetails
import com.example.executiongate.service.dto.ExecutionRequestId
import com.example.executiongate.service.dto.RequestType
import com.querydsl.jpa.impl.JPAQuery
import jakarta.persistence.CascadeType
import jakarta.persistence.Entity
import jakarta.persistence.EntityManager
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import org.apache.commons.lang3.builder.ToStringBuilder
import org.apache.commons.lang3.builder.ToStringStyle.SHORT_PREFIX_STYLE
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

@Entity(name = "execution_request")
class ExecutionRequestEntity(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "datasource_id", nullable = false)
    val connection: DatasourceConnectionEntity,

    var title: String,

    @Enumerated(EnumType.STRING)
    var type: RequestType,
    var description: String?,
    var statement: String?,
    var readOnly: Boolean,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    private val author: UserEntity,

    var executionStatus: String,

    private val createdAt: LocalDateTime = Instant.now().atZone(ZoneOffset.UTC).toLocalDateTime(),

    @OneToMany(cascade = [CascadeType.ALL])
    @JoinColumn(name = "execution_request_id")
    val events: MutableSet<EventEntity>,
) : BaseEntity() {

    override fun toString(): String {
        return ToStringBuilder(this, SHORT_PREFIX_STYLE)
            .append("id", id)
            .toString()
    }

    fun toDto(): ExecutionRequest = ExecutionRequest(
        id = ExecutionRequestId(id),
        connection = connection.toDto(),
        title = title,
        type = type,
        description = description,
        statement = statement,
        readOnly = readOnly,
        executionStatus = executionStatus,
        createdAt = createdAt,
        author = author.toDto(),
    )

    fun toDetailDto() = ExecutionRequestDetails(
        request = toDto(),
        events = events.map { it.toDto() }.toSet(),
    )
}

interface ExecutionRequestRepository : JpaRepository<ExecutionRequestEntity, String>, CustomExecutionRequestRepository

interface CustomExecutionRequestRepository {
    fun findByIdWithDetails(id: ExecutionRequestId): ExecutionRequestEntity?
}

class CustomExecutionRequestRepositoryImpl(
    private val entityManager: EntityManager,
) : CustomExecutionRequestRepository {

    private val qExecutionRequestEntity: QExecutionRequestEntity = QExecutionRequestEntity.executionRequestEntity

    override fun findByIdWithDetails(id: ExecutionRequestId): ExecutionRequestEntity? {
        return JPAQuery<ExecutionRequestEntity>(entityManager).from(qExecutionRequestEntity)
            .where(qExecutionRequestEntity.id.eq(id.toString()))
            .leftJoin(qExecutionRequestEntity.events)
            .fetchJoin()
            .fetch()
            .toSet()
            .firstOrNull()
    }
}

@Service
class ExecutionRequestAdapter(
    val executionRequestRepository: ExecutionRequestRepository,
    val connectionRepository: DatasourceConnectionRepository,
    val userRepository: UserRepository,

) {

    fun addEvent(id: ExecutionRequestId, authorId: String, payload: Payload): Pair<ExecutionRequestDetails, Event> {
        val executionRequestEntity = getExecutionRequestDetailsEntity(id)
        val userEntity = getUserEntity(authorId)
        val eventEntity = EventEntity(
            executionRequest = executionRequestEntity,
            author = userEntity,
            type = payload.type,
            payload = payload,
        )

        executionRequestEntity.events.add(eventEntity)
        executionRequestRepository.saveAndFlush(executionRequestEntity)

        return Pair(executionRequestEntity.toDetailDto(), eventEntity.toDto())
    }

    fun createExecutionRequest(
        connectionId: DatasourceConnectionId,
        title: String,
        type: RequestType,
        description: String?,
        statement: String?,
        readOnly: Boolean,
        executionStatus: String,
        authorId: String,
    ): ExecutionRequestDetails {
        val connection = connectionRepository.findByIdOrNull(connectionId.toString())
            ?: throw EntityNotFound("Connection Not Found", "Connection with id $connectionId does not exist.")
        val authorEntity = userRepository.findByIdOrNull(authorId)
            ?: throw EntityNotFound("User Not Found", "User with id $authorId does not exist.")

        return executionRequestRepository.save(
            ExecutionRequestEntity(
                connection = connection,
                title = title,
                type = type,
                description = description,
                statement = statement,
                readOnly = readOnly,
                executionStatus = executionStatus,
                events = mutableSetOf(),
                author = authorEntity,
            ),
        ).toDetailDto()
    }

    fun updateExecutionRequest(
        id: ExecutionRequestId,
        title: String,
        description: String?,
        statement: String?,
        readOnly: Boolean,
    ): ExecutionRequestDetails {
        val executionRequestEntity = getExecutionRequestDetailsEntity(id)
        executionRequestEntity.title = title
        executionRequestEntity.description = description
        executionRequestEntity.statement = statement
        executionRequestEntity.readOnly = readOnly

        executionRequestRepository.save(executionRequestEntity)
        return executionRequestEntity.toDetailDto()
    }

    fun listExecutionRequests(): List<ExecutionRequestDetails> =
        executionRequestRepository.findAll().map { it.toDetailDto() }

    private fun getExecutionRequestDetailsEntity(id: ExecutionRequestId): ExecutionRequestEntity =
        executionRequestRepository.findByIdWithDetails(id)
            ?: throw EntityNotFound(
                "Execution Request Not Found",
                "Execution Request with id $id does not exist.",
            )

    private fun getUserEntity(id: String): UserEntity = userRepository.findByIdOrNull(id)
        ?: throw EntityNotFound("User Not Found", "User with id $id does not exist.")

    fun getExecutionRequestDetails(id: ExecutionRequestId): ExecutionRequestDetails = getExecutionRequestDetailsEntity(
        id,
    ).toDetailDto()
}
