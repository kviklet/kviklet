package dev.kviklet.kviklet.service

import dev.kviklet.kviklet.db.RoleAdapter
import dev.kviklet.kviklet.security.Permission
import dev.kviklet.kviklet.service.dto.Policy
import dev.kviklet.kviklet.service.dto.Role
import dev.kviklet.kviklet.service.dto.RoleId
import org.springframework.stereotype.Service

@Service
class RoleService(private val roleAdapter: RoleAdapter) {

    @dev.kviklet.kviklet.security.Policy(Permission.ROLE_EDIT)
    fun updateRole(roleToUpdate: Role): Role {
        val savedRole = roleAdapter.update(roleToUpdate)
        return savedRole
    }

    fun updateRole(id: RoleId, name: String?, description: String?, policies: Set<Policy>?): Role {
        val oldRole = roleAdapter.findById(id)
        val newRole = Role.create(
            id = id,
            name = name ?: oldRole.name,
            description = description ?: oldRole.description,
            policies = policies ?: oldRole.policies,
        )
        return updateRole(newRole)
    }
}
