package dev.kviklet.kviklet.service.dto

import dev.kviklet.kviklet.security.Resource
import dev.kviklet.kviklet.security.SecuredDomainObject

data class Configuration(
    val teamsUrl: String?,
    val slackUrl: String?,
    val host: String?,
) : SecuredDomainObject {
    override fun getId(): String? {
        return null
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
