package dev.kviklet.kviklet.db

import dev.kviklet.kviklet.db.util.BaseEntity
import dev.kviklet.kviklet.security.Permission
import dev.kviklet.kviklet.security.PolicyGrantedAuthority
import dev.kviklet.kviklet.security.Resource
import dev.kviklet.kviklet.security.SecuredDomainId
import dev.kviklet.kviklet.security.SecuredDomainObject
import dev.kviklet.kviklet.security.UserDetailsWithId
import dev.kviklet.kviklet.security.isAllowed
import dev.kviklet.kviklet.security.vote
import dev.kviklet.kviklet.service.EntityNotFound
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.io.Serializable
import java.time.ZonedDateTime

@Entity
@Table(name = "api_keys")
class ApiKeyEntity : BaseEntity() {
    @Column(name = "name", nullable = false)
    var name: String = ""

    @Column(name = "key_hash", nullable = false)
    var keyHash: String = ""

    @Column(name = "created_at", nullable = false)
    var createdAt: ZonedDateTime = ZonedDateTime.now()

    @Column(name = "expires_at", nullable = true)
    var expiresAt: ZonedDateTime? = null

    @Column(name = "last_used_at", nullable = true)
    var lastUsedAt: ZonedDateTime? = null

    @Column(name = "user_id", nullable = false)
    lateinit var userId: String

    fun toDto() = ApiKey(
        id = id?.let { ApiKeyId(it) },
        name = name,
        createdAt = createdAt,
        expiresAt = expiresAt,
        lastUsedAt = lastUsedAt,
        userId = UserId(userId),
    )
}

@Repository
interface ApiKeyRepository : JpaRepository<ApiKeyEntity, String> {
    fun findAllByUserId(userId: String): List<ApiKeyEntity>
}

data class ApiKeyId(private val id: String) :
    SecuredDomainId,
    Serializable {
    override fun toString(): String = id
}

data class ApiKey(
    val id: ApiKeyId? = null,
    val name: String,
    val createdAt: ZonedDateTime,
    val expiresAt: ZonedDateTime? = null,
    val lastUsedAt: ZonedDateTime? = null,
    val userId: UserId,
    val keyHash: String? = null,
) : SecuredDomainObject {
    override fun getSecuredObjectId(): String = id.toString()
    override fun getDomainObjectType(): Resource = Resource.API_KEY

    override fun getRelated(resource: Resource): SecuredDomainObject? = when (resource) {
        Resource.USER -> this
        else -> null
    }

    override fun auth(
        permission: Permission,
        userDetails: UserDetailsWithId,
        policies: List<PolicyGrantedAuthority>,
    ): Boolean {
        if (permission.resource != Resource.API_KEY) {
            return false
        }

        if (permission == Permission.API_KEY_CREATE) {
            return policies.vote(permission, this).isAllowed()
        }

        return userId.toString() == userDetails.id && policies.vote(permission, this).isAllowed()
    }
}

@Service
class ApiKeyAdapter(private val apiKeyRepository: ApiKeyRepository) {
    @Transactional(readOnly = true)
    fun findById(id: ApiKeyId): ApiKey = apiKeyRepository.findByIdOrNull(id.toString())?.toDto()
        ?: throw EntityNotFound("API Key not found", "API Key with id $id does not exist")

    @Transactional(readOnly = true)
    fun findAllByUserId(userId: String): List<ApiKey> = apiKeyRepository.findAllByUserId(userId).map { it.toDto() }

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
    fun updateLastUsed(id: String, lastUsedAt: ZonedDateTime) {
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
}
