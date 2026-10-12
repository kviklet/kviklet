// This file is not MIT licensed
package dev.kviklet.kviklet.mcp

import dev.kviklet.kviklet.service.dto.Policy
import org.springframework.ai.mcp.annotation.McpToolParam

data class UserPermissions(
    val read: Boolean = false,
    val create: Boolean = false,
    // user:edit on every user, but a user may only edit themselves (see User.auth), as in the UI's editSelf.
    @field:McpToolParam(
        required = false,
        description = "Edit their own profile, needed to change their password. Usually wanted on every role.",
    )
    val editSelf: Boolean = false,
)

data class RoleReadPermission(val read: Boolean = false)

data class ConnectionPermissions(
    @field:McpToolParam(description = "A connection id, or a pattern like prod-* or *")
    val selector: String,
    @field:McpToolParam(required = false, description = "See the requests on the connections")
    val read: Boolean = false,
    @field:McpToolParam(required = false, description = "Create, edit and execute requests")
    val write: Boolean = false,
    @field:McpToolParam(required = false, description = "Review requests")
    val review: Boolean = false,
)

/**
 * A role's policies as the flags of the role page in the UI. The mapping is the UI's own
 * (`hooks/roles.ts`), so a role edited here and in the UI ends up with the same policies.
 */
data class RolePermissions(
    @field:McpToolParam(required = false, description = "Allows everything, the other flags are ignored")
    val isAdmin: Boolean = false,
    val users: UserPermissions = UserPermissions(),
    val roles: RoleReadPermission = RoleReadPermission(),
    val connections: List<ConnectionPermissions> = emptyList(),
) {
    fun toPolicies(): Set<Policy> {
        if (isAdmin) return setOf(policy("*:*", "*"))
        val policies = mutableSetOf<Policy>()
        if (users.read) policies += policy("user:get", "*")
        if (users.create) policies += policy("user:create", "*")
        if (users.editSelf) policies += policy("user:edit", "*")
        if (roles.read) policies += policy("role:get", "*")
        connections.forEach {
            policies += policy("datasource_connection:get", it.selector)
            if (it.read || it.write || it.review) policies += policy("execution_request:get", it.selector)
            if (it.review) policies += policy("execution_request:review", it.selector)
            if (it.write) {
                policies += policy("execution_request:edit", it.selector)
                policies += policy("execution_request:execute", it.selector)
            }
        }
        return policies
    }

    companion object {
        /** The flags for these policies, or null if flags can't express them exactly. */
        fun fromPolicies(policies: Set<Policy>): RolePermissions? {
            var isAdmin = false
            var users = UserPermissions()
            var roles = RoleReadPermission()
            val connections = linkedMapOf<String, ConnectionPermissions>()
            policies.forEach { policy ->
                val (type, action) = policy.action.split(":", limit = 2).let { it[0] to it.getOrNull(1) }
                when (type) {
                    "*" -> if (action == "*" && policy.resource == "*") isAdmin = true

                    "user" -> when (action) {
                        "get" -> users = users.copy(read = true)
                        "create" -> users = users.copy(create = true)
                        "edit" -> users = users.copy(editSelf = true)
                    }

                    "role" -> if (action == "get") roles = RoleReadPermission(read = true)

                    "datasource_connection", "execution_request" -> {
                        val connection = connections.getOrPut(policy.resource) {
                            ConnectionPermissions(policy.resource)
                        }
                        if (type == "execution_request") {
                            connections[policy.resource] = when (action) {
                                "get" -> connection.copy(read = true)
                                "edit" -> connection.copy(write = true)
                                "review" -> connection.copy(review = true)
                                else -> connection
                            }
                        }
                    }
                }
            }
            val permissions = RolePermissions(isAdmin, users, roles, connections.values.toList())
            return permissions.takeIf { it.toPolicies() == policies.map { policy(it.action, it.resource) }.toSet() }
        }

        private fun policy(action: String, resource: String) = Policy(action = action, resource = resource)
    }
}
