package dev.kviklet.kviklet.helper

import dev.kviklet.kviklet.db.User
import dev.kviklet.kviklet.security.UserService
import dev.kviklet.kviklet.service.RoleService
import dev.kviklet.kviklet.service.dto.Policy
import dev.kviklet.kviklet.service.dto.PolicyEffect
import dev.kviklet.kviklet.service.dto.RoleId
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class UserHelper(private val userService: UserService, private val roleService: RoleService) {
    @Transactional
    fun createUser(permissions: List<String>, resources: List<String>? = null): User {
        val user = userService.createUser(
            email = "user@example.com",
            password = "123456",
            fullName = "Some User",
        )
        val role = roleService.createRole("USER", "the users role")
        val policies = permissions.mapIndexed { index, it ->
            Policy(
                action = it,
                effect = PolicyEffect.ALLOW,
                resource = resources?.get(index) ?: "*",
            )
        }.toSet()
        roleService.updateRole(
            id = RoleId(role.getId()!!),
            policies = policies,
        )
        val updatedUser = userService.updateUser(user.id!!, roles = listOf(role.getId()!!))
        return updatedUser
    }
}
