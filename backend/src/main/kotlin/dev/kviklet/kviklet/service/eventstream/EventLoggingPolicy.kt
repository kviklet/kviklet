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
        "event_stream.configuration_changed",
        "event_stream.enabled",
    )
    private val queryTextFields = setOf("statement", "statement_truncated", "statement_original_bytes")

    fun includes(action: String, level: EventLoggingLevel): Boolean =
        level != EventLoggingLevel.SECURITY_ONLY || action in securityActions

    /** Reapply the text policy at write time in case settings changed while a transaction was running. */
    fun apply(event: Map<String, Any?>, level: EventLoggingLevel): Map<String, Any?> {
        if (level == EventLoggingLevel.FULL) return event
        val fields = event["kviklet"] as? Map<*, *> ?: return event
        val execution = fields["execution"] as? Map<*, *> ?: return event
        if (execution.keys.none { it in queryTextFields }) return event
        return event + ("kviklet" to (fields + ("execution" to execution.filterKeys { it !in queryTextFields })))
    }
}
