package dev.kviklet.kviklet.service.dto

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue
import dev.kviklet.kviklet.security.Permission
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
    val isDefault: Boolean
        get() = id == DEFAULT_ROLE_ID
    companion object {

        val DEFAULT_ROLE_ID = RoleId("7WoJJYKT2hhrLp49YrT2yr")

        val DEFAULT_ROLE_POLICIES = setOf(
            Policy(
                action = Permission.DATASOURCE_CONNECTION_GET.getPermissionString(),
                effect = PolicyEffect.ALLOW,
                resource = "*",
            ),
            Policy(
                action = Permission.EXECUTION_REQUEST_GET.getPermissionString(),
                effect = PolicyEffect.ALLOW,
                resource = "*",
            ),
        )
        fun create(id: RoleId, name: String, description: String, policies: Set<Policy>): Role = Role(
            id = id,
            name = name,
            description = description,
            policies = policies,
        )
    }

    override fun getSecuredObjectId() = id?.toString()

    fun getId(): String? = id?.toString()

    override fun getDomainObjectType(): Resource = Resource.ROLE

    override fun getRelated(resource: Resource): SecuredDomainObject? = null
}

data class RoleId
@JsonCreator constructor(private val id: String) :
    Serializable,
    SecuredDomainId {
    @JsonValue
    override fun toString() = id
}
