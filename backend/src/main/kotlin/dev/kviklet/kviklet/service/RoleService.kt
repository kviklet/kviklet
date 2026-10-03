package dev.kviklet.kviklet.service

import dev.kviklet.kviklet.db.RoleAdapter
import dev.kviklet.kviklet.security.Permission
import dev.kviklet.kviklet.service.dto.Policy
import dev.kviklet.kviklet.service.dto.Role
import dev.kviklet.kviklet.service.dto.RoleId
import dev.kviklet.kviklet.telemetry.RoleCreated
import dev.kviklet.kviklet.telemetry.Telemetry
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class RoleService(
    private val roleAdapter: RoleAdapter,
    private val telemetry: Telemetry,
    private val eventStreamingService: EventStreamingService? = null,
) {

    @dev.kviklet.kviklet.security.Policy(Permission.ROLE_EDIT)
    @Transactional
    fun updateRole(roleToUpdate: Role): Role {
        val before = roleAdapter.findById(RoleId(roleToUpdate.getId()!!))
        val savedRole = roleAdapter.update(roleToUpdate)
        val oldPermissions = before.policies.map { it.action to it.resource }.toSet()
        val newPermissions = savedRole.policies.map { it.action to it.resource }.toSet()
        val added = newPermissions - oldPermissions
        val removed = oldPermissions - newPermissions
        if (added.isNotEmpty() || removed.isNotEmpty() || before.name != savedRole.name) {
            eventStreamingService?.emit(
                "role.changed",
                "iam",
                fields = mapOf(
                    "role_id" to savedRole.getId(),
                    "before" to mapOf("name" to before.name),
                    "after" to mapOf("name" to savedRole.name),
                    "name_changed" to (before.name != savedRole.name),
                    "permissions_added" to added.map { mapOf("action" to it.first, "resource" to it.second) },
                    "permissions_removed" to removed.map { mapOf("action" to it.first, "resource" to it.second) },
                ),
            )
        }
        return savedRole
    }

    @dev.kviklet.kviklet.security.Policy(Permission.ROLE_EDIT)
    @Transactional
    fun updateRole(
        id: RoleId,
        name: String? = null,
        description: String? = null,
        policies: Set<Policy>? = emptySet(),
    ): Role {
        val oldRole = roleAdapter.findById(id)
        val newRole = Role.create(
            id = id,
            name = name ?: oldRole.name,
            description = description ?: oldRole.description,
            policies = policies ?: oldRole.policies,
        )
        return updateRole(newRole)
    }

    @dev.kviklet.kviklet.security.Policy(Permission.ROLE_EDIT)
    @Transactional
    fun createRole(name: String, description: String, policies: Set<Policy>? = emptySet()): Role {
        val role = Role(
            name = name,
            description = description,
            policies = policies ?: emptySet(),
        )
        return roleAdapter.create(role).also {
            telemetry.track(RoleCreated)
            eventStreamingService?.emit(
                "role.created",
                "iam",
                fields = mapOf(
                    "role_id" to it.getId(),
                    "before" to mapOf("name" to null),
                    "after" to mapOf("name" to it.name),
                    "permissions" to
                        it.policies.map { policy -> mapOf("action" to policy.action, "resource" to policy.resource) },
                ),
            )
        }
    }

    @dev.kviklet.kviklet.security.Policy(Permission.ROLE_GET)
    @Transactional(readOnly = true)
    fun getAllRoles(): List<Role> = roleAdapter.findAll()

    @dev.kviklet.kviklet.security.Policy(Permission.ROLE_GET)
    @Transactional(readOnly = true)
    fun getRole(id: RoleId): Role = roleAdapter.findById(id)

    @dev.kviklet.kviklet.security.Policy(Permission.ROLE_EDIT)
    @Transactional
    fun deleteRole(id: RoleId) {
        val role = roleAdapter.findById(id)
        if (role.isDefault) {
            throw IllegalArgumentException("Cannot delete default role")
        }
        roleAdapter.delete(id)
        eventStreamingService?.emit(
            "role.deleted",
            "iam",
            fields = mapOf(
                "role_id" to id.toString(),
                "before" to mapOf("name" to role.name),
                "after" to mapOf("name" to null),
            ),
        )
    }
}
