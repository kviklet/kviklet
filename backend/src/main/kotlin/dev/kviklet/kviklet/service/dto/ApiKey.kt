package dev.kviklet.kviklet.service.dto

import dev.kviklet.kviklet.db.User
import dev.kviklet.kviklet.security.Resource
import dev.kviklet.kviklet.security.SecuredDomainId
import dev.kviklet.kviklet.security.SecuredDomainObject
import java.io.Serializable
import java.time.LocalDateTime

data class ApiKeyId(private val id: String) :
    SecuredDomainId,
    Serializable {
    override fun toString(): String = id
}

data class ApiKey(
    val id: ApiKeyId? = null,
    val name: String,
    val createdAt: LocalDateTime,
    val expiresAt: LocalDateTime? = null,
    val lastUsedAt: LocalDateTime? = null,
    val user: User,
    val keyHash: String? = null,
    val key: String? = null,
) : SecuredDomainObject {
    override fun getSecuredObjectId(): String = id.toString()
    override fun getDomainObjectType(): Resource = Resource.API_KEY

    override fun getRelated(resource: Resource): SecuredDomainObject? = when (resource) {
        Resource.USER -> this
        else -> null
    }
}
