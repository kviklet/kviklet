package dev.kviklet.kviklet.service.dto

import java.io.Serializable

enum class PolicyEffect {
    ALLOW,
    DENY,
}

data class Policy(val id: String? = null, val action: String, val effect: PolicyEffect, val resource: String) :
    Serializable {
    companion object {
        fun create(id: String?, action: String, effect: PolicyEffect, resource: String): Policy = Policy(
            id = id,
            action = action,
            effect = effect,
            resource = resource,
        )
    }
}
