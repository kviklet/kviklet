package dev.kviklet.kviklet.service

import dev.kviklet.kviklet.db.RoleAdapter
import dev.kviklet.kviklet.security.Permission
import dev.kviklet.kviklet.service.dto.Policy
import dev.kviklet.kviklet.service.dto.Role
import dev.kviklet.kviklet.service.dto.RoleId
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class RoleService(private val roleAdapter: RoleAdapter) {

    @dev.kviklet.kviklet.security.Policy(Permission.ROLE_EDIT)
    @Transactional
    fun updateRole(roleToUpdate: Role): Role {
        val savedRole = roleAdapter.update(roleToUpdate)
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
        return roleAdapter.create(role)
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
    }
}
