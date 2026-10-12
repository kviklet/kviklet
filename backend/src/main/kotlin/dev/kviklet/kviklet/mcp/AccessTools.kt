// This file is not MIT licensed
package dev.kviklet.kviklet.mcp

import dev.kviklet.kviklet.controller.CreateUserRequest
import dev.kviklet.kviklet.db.User
import dev.kviklet.kviklet.db.UserId
import dev.kviklet.kviklet.service.BaseUrlResolver
import dev.kviklet.kviklet.service.RoleService
import dev.kviklet.kviklet.service.UserService
import dev.kviklet.kviklet.service.dto.Role
import dev.kviklet.kviklet.service.dto.RoleId
import jakarta.validation.Validator
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.ai.mcp.annotation.McpTool.McpAnnotations
import org.springframework.ai.mcp.annotation.McpToolParam
import org.springframework.stereotype.Component

data class PolicyResult(val action: String, val resource: String)

data class RoleResult(
    val id: String,
    val name: String,
    val description: String,
    val isDefault: Boolean,
    /** Null if the role's policies can't be expressed as flags, see [policies]. */
    val permissions: RolePermissions?,
    /** Only for roles without [permissions]. */
    val policies: List<PolicyResult>?,
    val url: String?,
) {
    companion object {
        fun from(role: Role, baseUrl: String?): RoleResult {
            val permissions = RolePermissions.fromPolicies(role.policies)
            return RoleResult(
                id = role.getId()!!,
                name = role.name,
                description = role.description,
                isDefault = role.isDefault,
                permissions = permissions,
                policies = if (permissions == null) {
                    role.policies.map { PolicyResult(it.action, it.resource) }.sortedBy { it.action + it.resource }
                } else {
                    null
                },
                url = baseUrl?.let { "$it/settings/roles/${role.getId()}" },
            )
        }
    }
}

data class Deleted(val id: String, val deleted: Boolean = true)

data class RoleRef(val id: String, val name: String)

data class UserResult(
    val id: String,
    val email: String,
    val fullName: String?,
    val active: Boolean,
    val roles: List<RoleRef>,
    val url: String?,
) {
    companion object {
        fun from(user: User, baseUrl: String?) = UserResult(
            id = user.getId()!!,
            email = user.email,
            fullName = user.fullName,
            active = user.active,
            roles = user.roles.map { RoleRef(it.getId()!!, it.name) }.sortedBy { it.name },
            url = baseUrl?.let { "$it/settings/users" },
        )
    }
}

/** Managing roles and users. */
@Component
class AccessTools(
    private val roleService: RoleService,
    private val userService: UserService,
    private val baseUrlResolver: BaseUrlResolver,
    private val validator: Validator,
) {

    @McpTool(
        name = "list_roles",
        description = "Lists the roles with their permissions. Every user has the default role.",
        annotations = McpAnnotations(readOnlyHint = true, destructiveHint = false),
    )
    fun listRoles(): List<RoleResult> = roleService.getAllRoles().map(::role)

    @McpTool(
        name = "get_role",
        description = "Gets a role with its permissions.",
        annotations = McpAnnotations(readOnlyHint = true, destructiveHint = false),
    )
    fun getRole(@McpToolParam(description = "The role id") id: String): RoleResult =
        role(roleService.getRole(RoleId(id)))

    @McpTool(
        name = "create_role",
        description = "Creates a role. Give it to users with set_user_roles.",
        annotations = McpAnnotations(destructiveHint = false),
    )
    fun createRole(
        name: String,
        description: String,
        @McpToolParam(required = false, description = "None by default") permissions: RolePermissions?,
    ): RoleResult = role(roleService.createRole(name, description, (permissions ?: RolePermissions()).toPolicies()))

    @McpTool(
        name = "update_role",
        description = "Changes a role. Pass only the fields to change; permissions replace the role's " +
            "permissions as a whole. Roles whose permissions are shown as policies can only be changed in " +
            "Kviklet.",
        annotations = McpAnnotations(destructiveHint = true),
    )
    fun updateRole(
        @McpToolParam(description = "The role id") id: String,
        @McpToolParam(required = false) name: String?,
        @McpToolParam(required = false) description: String?,
        @McpToolParam(required = false) permissions: RolePermissions?,
    ): RoleResult {
        // Only refused for a permissions change, so such a role can still be renamed.
        if (permissions != null && RolePermissions.fromPolicies(roleService.getRole(RoleId(id)).policies) == null) {
            throw IllegalArgumentException(
                "The permissions of role $id can't be expressed as flags, change them in Kviklet",
            )
        }
        return role(roleService.updateRole(RoleId(id), name, description, permissions?.toPolicies()))
    }

    @McpTool(
        name = "delete_role",
        description = "Deletes a role. Its users lose its permissions. The default role can't be deleted.",
        annotations = McpAnnotations(destructiveHint = true),
    )
    fun deleteRole(@McpToolParam(description = "The role id") id: String): Deleted {
        roleService.deleteRole(RoleId(id))
        return Deleted(id)
    }

    @McpTool(
        name = "list_users",
        description = "Lists the users with their roles.",
        annotations = McpAnnotations(readOnlyHint = true, destructiveHint = false),
    )
    fun listUsers(): List<UserResult> = userService.getUsers().map(::user)

    @McpTool(
        name = "create_user",
        description = "Creates a user who logs in with email and password, with the default role. The password " +
            "passes through the model provider: tell the user, and suggest a temporary password that the new " +
            "user changes on first login. Needs a free seat in the license.",
        annotations = McpAnnotations(destructiveHint = false),
    )
    fun createUser(
        email: String,
        @McpToolParam(description = "At least 6 characters") password: String,
        fullName: String,
    ): UserResult {
        val request = CreateUserRequest(email = email, password = password, fullName = fullName)
        validator.check(request)
        return user(userService.createUser(request.email, request.password, request.fullName))
    }

    @McpTool(
        name = "set_user_roles",
        description = "Replaces a user's roles. The default role must stay.",
        annotations = McpAnnotations(destructiveHint = true),
    )
    fun setUserRoles(
        @McpToolParam(description = "The user id") userId: String,
        @McpToolParam(description = "All roles the user has afterwards") roleIds: List<String>,
    ): UserResult = user(userService.updateUserWithRoles(UserId(userId), roles = roleIds))

    @McpTool(
        name = "set_user_active",
        description = "Deactivates a user, which ends their access and frees their seat, or reactivates " +
            "them, which needs a free seat. Users can't deactivate themselves.",
        annotations = McpAnnotations(destructiveHint = true),
    )
    fun setUserActive(@McpToolParam(description = "The user id") userId: String, active: Boolean): UserResult =
        user(userService.setUserActive(UserId(userId), active, currentUser().id))

    private fun role(role: Role) = RoleResult.from(role, baseUrlResolver.resolve())

    private fun user(user: User) = UserResult.from(user, baseUrlResolver.resolve())
}
