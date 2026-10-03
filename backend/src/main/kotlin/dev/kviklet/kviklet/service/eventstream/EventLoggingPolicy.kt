// This file is not MIT licensed
package dev.kviklet.kviklet.service.eventstream

import dev.kviklet.kviklet.service.dto.EventLoggingLevel

/** Explicit actions keep request activity separate from security configuration changes. */
internal object EventLoggingPolicy {
    private val securityActions = setOf(
        "authentication.login",
        "authentication.logout",
        "authentication.api_key",
        "authorization.denied",
        "user.created",
        "user.activated",
        "user.deactivated",
        "user.password_changed",
        "user.roles_changed",
        "role.created",
        "role.changed",
        "role.deleted",
        "api_key.created",
        "api_key.revoked",
        "role_sync.configuration_changed",
        "role_sync.mapping_created",
        "role_sync.mapping_deleted",
        "connection.created",
        "connection.deleted",
        "connection.security_changed",
        "configuration.changed",
    )

    fun includes(action: String, level: EventLoggingLevel): Boolean =
        level != EventLoggingLevel.SECURITY_ONLY || action in securityActions

    /** Strip dedicated query text even when callers supply fields without the standard helpers. */
    fun apply(event: Map<String, Any?>, level: EventLoggingLevel): Map<String, Any?> {
        if (level == EventLoggingLevel.FULL) return event
        val fields = event["kviklet"] as? Map<*, *> ?: return event
        val execution = fields["execution"] as? Map<*, *> ?: return event
        if (!execution.containsKey("statement")) return event
        return event + ("kviklet" to (fields + ("execution" to execution.filterKeys { it != "statement" })))
    }
}
