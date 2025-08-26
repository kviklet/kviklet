package dev.kviklet.kviklet.db

import dev.kviklet.kviklet.db.util.BaseEntity
import dev.kviklet.kviklet.service.EntityNotFound
import dev.kviklet.kviklet.service.dto.ApiKey
import dev.kviklet.kviklet.service.dto.ApiKeyId
import dev.kviklet.kviklet.service.dto.utcTimeNow
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Entity
@Table(name = "api_keys")
class ApiKeyEntity : BaseEntity() {
    @Column(name = "name", nullable = false)
    var name: String = ""

    @Column(name = "key_hash", nullable = false)
    var keyHash: String = ""

    @Column(name = "created_at", nullable = false)
    var createdAt: LocalDateTime = utcTimeNow()

    @Column(name = "expires_at", nullable = true)
    var expiresAt: LocalDateTime? = null

    @Column(name = "last_used_at", nullable = true)
    var lastUsedAt: LocalDateTime? = null

    @Column(name = "user_id", nullable = false)
    var userId: String? = null

    fun toDto() = ApiKey(
        id = id?.let { ApiKeyId(it) },
        name = name,
        createdAt = createdAt,
        expiresAt = expiresAt,
        lastUsedAt = lastUsedAt,
        userId = UserId(userId!!),
    )
}

@Repository
interface ApiKeyRepository : JpaRepository<ApiKeyEntity, String> {
    fun findByKeyHash(keyHash: String): ApiKeyEntity?
}

@Service
class ApiKeyAdapter(private val apiKeyRepository: ApiKeyRepository) {
    @Transactional(readOnly = true)
    fun findById(id: ApiKeyId): ApiKey = apiKeyRepository.findByIdOrNull(id.toString())?.toDto()
        ?: throw EntityNotFound("API Key not found", "API Key with id $id does not exist")

    @Transactional(readOnly = true)
    fun findAll(): List<ApiKey> = apiKeyRepository.findAll().map { it.toDto() }

    @Transactional
    fun create(apiKey: ApiKey): ApiKey = apiKeyRepository.save(
        ApiKeyEntity().apply {
            this.name = apiKey.name
            this.createdAt = apiKey.createdAt
            this.expiresAt = apiKey.expiresAt
            this.lastUsedAt = apiKey.lastUsedAt
            this.keyHash = apiKey.keyHash ?: throw EntityNotFound(
                "API key hash not found",
                "API key ${apiKey.id} has no hash",
            )
            this.userId = apiKey.userId.toString()
        },
    ).toDto()

    @Transactional
    fun updateApiKey(id: String, lastUsedAt: LocalDateTime) {
        val apiKey = apiKeyRepository.findByIdOrNull(id) ?: return
        apiKeyRepository.save(
            apiKey.apply {
                this.lastUsedAt = lastUsedAt
            },
        )
    }

    @Transactional
    fun deleteApiKey(id: ApiKeyId) {
        apiKeyRepository.deleteById(id.toString())
    }

    @Transactional
    fun findByHash(keyHash: String): ApiKey? = apiKeyRepository.findByKeyHash(keyHash)?.toDto()
}
