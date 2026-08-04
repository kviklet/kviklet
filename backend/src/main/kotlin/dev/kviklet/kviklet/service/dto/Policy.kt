package dev.kviklet.kviklet.service.dto

import java.io.Serializable

data class Policy(val id: String? = null, val action: String, val resource: String) : Serializable {
    companion object {
        fun create(id: String?, action: String, resource: String): Policy = Policy(
            id = id,
            action = action,
            resource = resource,
        )
    }
}
