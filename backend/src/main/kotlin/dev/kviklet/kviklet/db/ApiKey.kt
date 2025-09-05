package dev.kviklet.kviklet.db

import dev.kviklet.kviklet.db.util.BaseEntity
import dev.kviklet.kviklet.service.EntityNotFound
import dev.kviklet.kviklet.service.dto.ApiKey
import dev.kviklet.kviklet.service.dto.ApiKeyId
import dev.kviklet.kviklet.service.dto.utcTimeNow
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Entity
@Table(name = "api_keys")
class ApiKeyEntity(
    @Column(name = "name", nullable = false)
    var name: String = "",

    @Column(name = "key_hash", nullable = false)
    var keyHash: String = "",

    @Column(name = "created_at", nullable = false)
    var createdAt: LocalDateTime = utcTimeNow(),

    @Column(name = "expires_at", nullable = true)
    var expiresAt: LocalDateTime? = null,

    @Column(name = "last_used_at", nullable = true)
    var lastUsedAt: LocalDateTime? = null,

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id")
    var user: UserEntity? = null,
) : BaseEntity() {

    fun toDto() = ApiKey(
        id = id?.let { ApiKeyId(it) },
        name = name,
        createdAt = createdAt,
        expiresAt = expiresAt,
        lastUsedAt = lastUsedAt,
        user = user!!.toDto(),
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
    fun create(apiKey: ApiKey): ApiKey {
        val savedEntity = apiKeyRepository.save(
            ApiKeyEntity().apply {
                this.name = apiKey.name
                this.createdAt = apiKey.createdAt
                this.expiresAt = apiKey.expiresAt
                this.lastUsedAt = apiKey.lastUsedAt
                this.keyHash = apiKey.keyHash ?: throw EntityNotFound(
                    "API key hash not found",
                    "API key ${apiKey.id} has no hash",
                )
                this.user = UserEntity().apply {
                    this.id = apiKey.user.getId()
                }
            },
        )
        // Reload the entity to fetch the user relationship
        return apiKeyRepository.findByIdOrNull(savedEntity.id!!)?.toDto()
            ?: throw EntityNotFound("API Key not found", "Failed to reload API key after creation")
    }

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
