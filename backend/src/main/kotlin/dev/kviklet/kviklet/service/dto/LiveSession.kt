package dev.kviklet.kviklet.service.dto

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue
import dev.kviklet.kviklet.security.Permission
import dev.kviklet.kviklet.security.PolicyGrantedAuthority
import dev.kviklet.kviklet.security.Resource
import dev.kviklet.kviklet.security.SecuredDomainId
import dev.kviklet.kviklet.security.SecuredDomainObject
import dev.kviklet.kviklet.security.UserDetailsWithId
import java.io.Serializable

data class LiveSession(
    val id: LiveSessionId? = null,
    val executionRequest: ExecutionRequestDetails,
    val consoleContent: String,
) : SecuredDomainObject {

    fun getId() = executionRequest.getId()

    override fun getSecuredObjectId() = executionRequest.getSecuredObjectId()

    override fun getDomainObjectType() = executionRequest.getDomainObjectType()

    override fun getRelated(resource: Resource) = executionRequest.getRelated(resource)

    override fun auth(
        permission: Permission,
        userDetails: UserDetailsWithId,
        policies: List<PolicyGrantedAuthority>,
    ): Boolean = executionRequest.auth(permission, userDetails, policies)
}

data class LiveSessionId
@JsonCreator constructor(private val id: String) :
    Serializable,
    SecuredDomainId {
    @JsonValue
    override fun toString() = id
}
