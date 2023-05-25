package com.example.executiongate.db

import com.example.executiongate.db.util.BaseEntity
import com.example.executiongate.service.EntityNotFound
import com.example.executiongate.service.dto.DatasourceConnectionId
import com.example.executiongate.service.dto.Event
import com.example.executiongate.service.dto.ExecutionRequest
import com.example.executiongate.service.dto.ExecutionRequestDetails
import com.example.executiongate.service.dto.ExecutionRequestId
import com.example.executiongate.service.dto.ReviewStatus
import com.querydsl.jpa.impl.JPAQuery
import org.apache.commons.lang3.builder.ToStringBuilder
import org.apache.commons.lang3.builder.ToStringStyle.SHORT_PREFIX_STYLE
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import javax.persistence.CascadeType
import javax.persistence.Entity
import javax.persistence.EntityManager
import javax.persistence.FetchType
import javax.persistence.JoinColumn
import javax.persistence.ManyToOne
import javax.persistence.OneToMany

@Entity(name = "execution_request")
class ExecutionRequestEntity(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "datasource_id")
    val connection: DatasourceConnectionEntity,

    private val title: String,
    private val description: String?,
    private val statement: String,
    private val readOnly: Boolean,

    var reviewStatus: ReviewStatus,
    var executionStatus: String,

    private val createdAt: LocalDateTime = LocalDateTime.now(),

    @OneToMany(cascade = [CascadeType.ALL])
    @JoinColumn(name = "execution_request_id")
    val events: MutableSet<EventEntity>
): BaseEntity() {

    override fun toString(): String {
        return ToStringBuilder(this, SHORT_PREFIX_STYLE)
            .append("id", id)
            .toString()
    }

    fun toDto(): ExecutionRequest = ExecutionRequest(
        id = ExecutionRequestId(id),
        connection = connection.toDto(),
        title = title,
        description = description,
        statement = statement,
        readOnly = readOnly,
        reviewStatus = reviewStatus,
        executionStatus = executionStatus,
        createdAt = createdAt,
    )

    fun toDetailDto() = ExecutionRequestDetails(
        request = toDto(),
        events = events.map { it.toDto() }.toSet()
    )

}

interface ExecutionRequestRepository : JpaRepository<ExecutionRequestEntity, String>, CustomExecutionRequestRepository

interface CustomExecutionRequestRepository {
    fun findByIdWithDetails(id: ExecutionRequestId): ExecutionRequestEntity?
}

class CustomExecutionRequestRepositoryImpl(
    private val entityManager: EntityManager,
): CustomExecutionRequestRepository {

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
    val connectionRepository: DatasourceConnectionRepository

) {

    fun addEvent(id: ExecutionRequestId, payload: Payload): Pair<ExecutionRequestDetails, Event> {
        val executionRequestEntity = getExecutionRequestDetailsEntity(id)
        val eventEntity = EventEntity(
                executionRequest = executionRequestEntity,
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
        description: String?,
        statement: String,
        readOnly: Boolean,
        reviewStatus: ReviewStatus,
        executionStatus: String
    ): ExecutionRequest {
        val connection = connectionRepository.findByIdOrNull(connectionId.toString()) ?: throw EntityNotFound("Connection Not Found", "Connection with id $connectionId does not exist.")

        return executionRequestRepository.save(
            ExecutionRequestEntity(
                connection = connection,
                title = title,
                description = description,
                statement = statement,
                readOnly = readOnly,
                reviewStatus = reviewStatus,
                executionStatus = executionStatus,
                events = mutableSetOf(),
            )
        ).toDto()
    }

    fun listExecutionRequests(): List<ExecutionRequest> =
        executionRequestRepository.findAll().map { it.toDto() }

    fun updateReviewStatus(id: ExecutionRequestId, reviewStatus: ReviewStatus) {
        val executionRequestEntity = getExecutionRequestDetailsEntity(id)
        executionRequestEntity.reviewStatus = reviewStatus
        executionRequestRepository.save(executionRequestEntity)
    }

    private fun getExecutionRequestDetailsEntity(id: ExecutionRequestId): ExecutionRequestEntity =
        executionRequestRepository.findByIdWithDetails(id)
            ?: throw EntityNotFound("Execution Request Not Found", "Execution Request with id $id does not exist.")

    fun getExecutionRequestDetails(id: ExecutionRequestId): ExecutionRequestDetails = getExecutionRequestDetailsEntity(id).toDetailDto()
}
