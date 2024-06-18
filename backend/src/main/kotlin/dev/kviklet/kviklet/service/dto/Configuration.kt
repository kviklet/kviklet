package dev.kviklet.kviklet.service.dto

import dev.kviklet.kviklet.security.Resource
import dev.kviklet.kviklet.security.SecuredDomainObject

data class Configuration(
    val teamsUrl: String?,
    val slackUrl: String?,
) : SecuredDomainObject {
    override fun getSecuredObjectId(): String? {
        return "configuration"
    }

    override fun getDomainObjectType(): Resource {
        return Resource.CONFIGURATION
    }

    override fun getRelated(resource: Resource): SecuredDomainObject? {
        if (resource == Resource.CONFIGURATION) {
            return this
        } else {
            throw IllegalArgumentException("Cannot get related object for resource $resource")
        }
    }
}
