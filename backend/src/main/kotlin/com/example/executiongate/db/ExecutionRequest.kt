package com.example.executiongate.db

import com.example.executiongate.db.util.BaseEntity
import com.example.executiongate.service.dto.ExecutionRequest
import com.example.executiongate.service.dto.ExecutionRequestDetails
import com.example.executiongate.service.dto.ExecutionRequestId
import com.querydsl.jpa.impl.JPAQuery
import org.apache.commons.lang3.builder.ToStringBuilder
import org.apache.commons.lang3.builder.ToStringStyle.SHORT_PREFIX_STYLE
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDateTime
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

    private val reviewStatus: String,
    private val executionStatus: String,

    private val createdAt: LocalDateTime = LocalDateTime.now(),

    @OneToMany(mappedBy = "executionRequest")
    private val events: Set<EventEntity>
): BaseEntity() {

    override fun toString(): String {
        return ToStringBuilder(this, SHORT_PREFIX_STYLE)
            .append("id", id)
            .toString()
    }

    fun toDto(): ExecutionRequest = ExecutionRequest(
        id = ExecutionRequestId(id),
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
        events = events.map { it.toDto() }
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
