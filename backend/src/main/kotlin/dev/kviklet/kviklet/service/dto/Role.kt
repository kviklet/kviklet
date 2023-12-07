package dev.kviklet.kviklet.service.dto

import dev.kviklet.kviklet.security.Resource
import dev.kviklet.kviklet.security.SecuredDomainId
import dev.kviklet.kviklet.security.SecuredDomainObject
import java.io.Serializable

data class Role(
    private val id: RoleId? = null,
    val name: String,
    val description: String,
    val policies: Set<Policy> = HashSet(),
) : SecuredDomainObject {
    companion object {
        fun create(id: RoleId, name: String, description: String, policies: Set<Policy>): Role {
            return Role(
                id = id,
                name = name,
                description = description,
                policies = policies,
            )
        }
    }

    override fun getId() = id?.toString()

    override fun getDomainObjectType(): Resource {
        return Resource.ROLE
    }

    override fun getRelated(resource: Resource): SecuredDomainObject? {
        return null
    }
}

@JvmInline
value class RoleId(private val id: String) : Serializable, SecuredDomainId {
    override fun toString() = id
}
