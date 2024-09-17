package dev.kviklet.kviklet.service.dto

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue
import dev.kviklet.kviklet.security.SecuredDomainId
import java.io.Serializable

data class LiveSession(
    val id: LiveSessionId? = null,
    val executionRequest: ExecutionRequestDetails,
    val consoleContent: String,
)

data class LiveSessionId
@JsonCreator constructor(private val id: String) :
    Serializable,
    SecuredDomainId {
    @JsonValue
    override fun toString() = id
}
