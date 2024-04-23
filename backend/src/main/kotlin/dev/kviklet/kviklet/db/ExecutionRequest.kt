package dev.kviklet.kviklet.db

import com.querydsl.jpa.impl.JPAQuery
import dev.kviklet.kviklet.db.util.BaseEntity
import dev.kviklet.kviklet.service.EntityNotFound
import dev.kviklet.kviklet.service.dto.ConnectionId
import dev.kviklet.kviklet.service.dto.DatasourceConnection
import dev.kviklet.kviklet.service.dto.DatasourceExecutionRequest
import dev.kviklet.kviklet.service.dto.Event
import dev.kviklet.kviklet.service.dto.ExecutionRequest
import dev.kviklet.kviklet.service.dto.ExecutionRequestDetails
import dev.kviklet.kviklet.service.dto.ExecutionRequestId
import dev.kviklet.kviklet.service.dto.KubernetesConnection
import dev.kviklet.kviklet.service.dto.KubernetesExecutionRequest
import dev.kviklet.kviklet.service.dto.RequestType
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
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

enum class ExecutionRequestType {
    DATASOURCE,
    KUBERNETES,
}

@Entity(name = "execution_request")
class ExecutionRequestEntity(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "datasource_id", nullable = false)
    val connection: ConnectionEntity,

    var title: String,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    private val author: UserEntity,

    var executionStatus: String,

    private val createdAt: LocalDateTime = Instant.now().atZone(ZoneOffset.UTC).toLocalDateTime(),

    @OneToMany(mappedBy = "executionRequest", cascade = [CascadeType.ALL])
    val events: MutableSet<EventEntity>,

    @Enumerated(EnumType.STRING)
    var executionRequestType: ExecutionRequestType,

    @Enumerated(EnumType.STRING)
    var executionType: RequestType,

    var description: String,

    // Datsource Request specific fields
    var statement: String?,
    var readOnly: Boolean?,

    // Kubernetes Request specific fields
    var namespace: String? = "",
    var podName: String? = "",
    var containerName: String? = "",
    var command: String? = "",

) : BaseEntity() {

    override fun toString(): String {
        return ToStringBuilder(this, SHORT_PREFIX_STYLE)
            .append("id", id)
            .toString()
    }

    fun toDto(): ExecutionRequest {
        return when (executionRequestType) {
            ExecutionRequestType.DATASOURCE -> DatasourceExecutionRequest(
                id = ExecutionRequestId(id!!),
                connection = connection.toDto() as DatasourceConnection,
                title = title,
                type = executionType,
                description = description,
                statement = statement,
                readOnly = readOnly!!,
                executionStatus = executionStatus,
                createdAt = createdAt,
                author = author.toDto(),
            )

            ExecutionRequestType.KUBERNETES -> KubernetesExecutionRequest(
                id = ExecutionRequestId(id!!),
                connection = connection.toDto() as KubernetesConnection,
                title = title,
                type = executionType,
                description = description,
                executionStatus = executionStatus,
                createdAt = createdAt,
                author = author.toDto(),
                namespace = namespace,
                podName = podName,
                containerName = containerName,
                command = command,
            )
        }
    }

    fun toDetailDto(): ExecutionRequestDetails {
        val details = ExecutionRequestDetails(
            request = toDto(),
            events = mutableSetOf<Event>(),
        )
        details.events.addAll(events.map { it.toDto(details) }.toSet())
        return details
    }
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
    val connectionRepository: ConnectionRepository,
    val userRepository: UserRepository,
) {

    @Autowired
    private lateinit var entityManager: EntityManager

    fun addEvent(id: ExecutionRequestId, authorId: String, payload: Payload): Pair<ExecutionRequestDetails, Event> {
        val executionRequestEntity = getExecutionRequestDetailsEntity(id)
        val userEntity = getUserEntity(authorId)
        val eventEntity = EventEntity(
            executionRequest = executionRequestEntity,
            author = userEntity,
            type = payload.type,
            payload = payload,
        )

        val event = entityManager.merge(eventEntity)

        executionRequestEntity.events.add(event)
        executionRequestRepository.saveAndFlush(executionRequestEntity)
        val details = executionRequestEntity.toDetailDto()
        entityManager.refresh(event)

        return Pair(details, event.toDto(details))
    }

    fun deleteAll() {
        executionRequestRepository.deleteAll()
    }

    @Transactional
    fun createExecutionRequest(
        connectionId: ConnectionId,
        title: String,
        type: RequestType,
        description: String,
        statement: String? = null,
        readOnly: Boolean? = null,
        executionStatus: String,
        authorId: String,
        namespace: String? = null,
        podName: String? = null,
        containerName: String? = null,
        command: String? = null,
    ): ExecutionRequestDetails {
        val connection = connectionRepository.findByIdOrNull(connectionId.toString())
            ?: throw EntityNotFound("Connection Not Found", "Connection with id $connectionId does not exist.")
        val authorEntity = userRepository.findByIdOrNull(authorId)
            ?: throw EntityNotFound("User Not Found", "User with id $authorId does not exist.")

        if (connection.connectionType == ConnectionType.DATASOURCE &&
            (namespace != null || podName != null || containerName != null || command != null)
        ) {
            throw IllegalArgumentException(
                "Cannot create Kubernetes specific fields for a Datasource Execution Request",
            )
        }

        if (connection.connectionType == ConnectionType.KUBERNETES &&
            (statement != null || readOnly != null)
        ) {
            throw IllegalArgumentException(
                "Cannot create Datasource specific fields for a Kubernetes Execution Request",
            )
        }

        return executionRequestRepository.save(
            ExecutionRequestEntity(
                connection = connection,
                title = title,
                executionType = type,
                description = description,
                statement = statement,
                readOnly = readOnly,
                executionStatus = executionStatus,
                events = mutableSetOf(),
                author = authorEntity,
                executionRequestType = when (connection.connectionType) {
                    ConnectionType.DATASOURCE -> ExecutionRequestType.DATASOURCE
                    ConnectionType.KUBERNETES -> ExecutionRequestType.KUBERNETES
                },
                namespace = namespace,
                podName = podName,
                containerName = containerName,
                command = command,
            ),
        ).toDetailDto()
    }

    fun updateExecutionRequest(
        id: ExecutionRequestId,
        title: String? = null,
        description: String? = null,
        statement: String? = null,
        readOnly: Boolean? = null,
        executionStatus: String? = null,
        namespace: String? = null,
        podName: String? = null,
        containerName: String? = null,
        command: String? = null,
    ): ExecutionRequestDetails {
        val executionRequestEntity = getExecutionRequestDetailsEntity(id)

        if (executionRequestEntity.executionRequestType == ExecutionRequestType.DATASOURCE &&
            (namespace != null || podName != null || containerName != null || command != null)
        ) {
            throw IllegalArgumentException(
                "Cannot update Kubernetes specific fields for a Datasource Execution Request",
            )
        }

        if (executionRequestEntity.executionRequestType == ExecutionRequestType.KUBERNETES &&
            (statement != null || readOnly != null)
        ) {
            throw IllegalArgumentException(
                "Cannot update Datasource specific fields for a Kubernetes Execution Request",
            )
        }

        title?.let { executionRequestEntity.title = it }
        description?.let { executionRequestEntity.description = it }
        statement?.let { executionRequestEntity.statement = it }
        readOnly?.let { executionRequestEntity.readOnly = it }
        executionStatus?.let { executionRequestEntity.executionStatus = it }

        namespace?.let { executionRequestEntity.namespace = it }
        podName?.let { executionRequestEntity.podName = it }
        containerName?.let { executionRequestEntity.containerName = it }
        command?.let { executionRequestEntity.command = it }

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

    @Transactional
    fun getExecutionRequestDetails(id: ExecutionRequestId): ExecutionRequestDetails = getExecutionRequestDetailsEntity(
        id,
    ).toDetailDto()
}
