package dev.kviklet.kviklet.db

import dev.kviklet.kviklet.db.util.BaseEntity
import dev.kviklet.kviklet.service.EntityNotFound
import dev.kviklet.kviklet.service.dto.Connection
import dev.kviklet.kviklet.service.dto.ExecutionRequestId
import dev.kviklet.kviklet.service.dto.LiveSession
import dev.kviklet.kviklet.service.dto.LiveSessionId
import jakarta.persistence.Entity
import jakarta.persistence.OneToOne
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Entity(name = "live_session")
class LiveSessionEntity(
    @OneToOne
    val executionRequest: ExecutionRequestEntity,

    var consoleContent: String,

) : BaseEntity() {
    fun toDto(connectionDto: Connection): LiveSession = LiveSession(
        id = id?.let { LiveSessionId(it) },
        executionRequest = executionRequest.toDetailDto(connectionDto),
        consoleContent = consoleContent,
    )
}

interface LiveSessionRepository : JpaRepository<LiveSessionEntity, String> {
    fun findByExecutionRequestId(executionRequestId: String): LiveSessionEntity?
}

@Service
class LiveSessionAdapter(
    val liveSessionRepository: LiveSessionRepository,
    val connectionAdapter: ConnectionAdapter,
    private val executionRequestRepository: ExecutionRequestRepository,
    private val executionRequestAdapter: ExecutionRequestAdapter,
) {

    fun findByExecutionRequestId(executionRequestId: ExecutionRequestId): LiveSession? {
        val liveSessionEntity = liveSessionRepository.findByExecutionRequestId(executionRequestId.toString())
        return liveSessionEntity?.toDto(connectionAdapter.toDto(liveSessionEntity.executionRequest.connection))
    }

    @Transactional(readOnly = true)
    fun findById(id: LiveSessionId): LiveSession {
        val liveSessionEntity = liveSessionRepository.findByIdOrNull(id.toString()) ?: throw EntityNotFound(
            "Live session not found",
            "Live session with id $id does not exist",
        )
        return liveSessionEntity.toDto(connectionAdapter.toDto(liveSessionEntity.executionRequest.connection))
    }

    @Transactional
    fun createLiveSession(executionRequestId: ExecutionRequestId, consoleContent: String): LiveSession {
        val executionRequestEntity =
            executionRequestRepository.findByIdOrNull(executionRequestId.toString()) ?: throw EntityNotFound(
                "Execution request not found",
                "Execution request with id $executionRequestId does not exist",
            )

        val liveSessionEntity = LiveSessionEntity(
            executionRequest = executionRequestEntity,
            consoleContent = consoleContent,
        )

        val savedEntity = liveSessionRepository.save(liveSessionEntity)
        return savedEntity.toDto(connectionAdapter.toDto(executionRequestEntity.connection))
    }

    @Transactional
    fun updateLiveSession(id: LiveSessionId, consoleContent: String?): LiveSession {
        val liveSessionEntity = liveSessionRepository.findByIdOrNull(id.toString()) ?: throw EntityNotFound(
            "Live session not found",
            "Live session with id $id does not exist",
        )

        consoleContent?.let { liveSessionEntity.consoleContent = it }

        val updatedEntity = liveSessionRepository.save(liveSessionEntity)
        return updatedEntity.toDto(connectionAdapter.toDto(updatedEntity.executionRequest.connection))
    }

    @Transactional
    fun deleteLiveSession(id: LiveSessionId) {
        liveSessionRepository.deleteById(id.toString())
    }

    @Transactional
    fun deleteAll() {
        liveSessionRepository.deleteAll()
    }
}
