package dev.kviklet.kviklet.helper

import dev.kviklet.kviklet.db.User
import dev.kviklet.kviklet.db.UserId
import dev.kviklet.kviklet.security.UserService
import dev.kviklet.kviklet.service.RoleService
import dev.kviklet.kviklet.service.dto.Policy
import dev.kviklet.kviklet.service.dto.PolicyEffect
import dev.kviklet.kviklet.service.dto.Role
import dev.kviklet.kviklet.service.dto.RoleId
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class UserHelper(private val userService: UserService, private val roleHelper: RoleHelper) {
    @Transactional
    fun createUser(
        permissions: List<String>,
        resources: List<String>? = null,
        email: String = "user@example.com",
        password: String = "123456",
        fullName: String = "Some User",
    ): User {
        val user = userService.createUser(
            email = email,
            password = password,
            fullName = fullName,
        )
        val role = roleHelper.createRole(permissions, resources, "$fullName Role", "$fullName users role")
        val updatedUser = userService.updateUserWithRoles(UserId(user.getId()!!), roles = listOf(role.getId()!!))
        return updatedUser
    }
}

@Component
class RoleHelper(private val roleService: RoleService) {
    @Transactional
    fun createRole(
        permissions: List<String>,
        resources: List<String>? = null,
        name: String = "Test Role",
        description: String = "This is a test role",
    ): Role {
        val role = roleService.createRole(name, description)
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
        return role
    }
}
