package dev.kviklet.kviklet.db.init

import dev.kviklet.kviklet.db.PolicyEntity
import dev.kviklet.kviklet.db.RoleEntity
import dev.kviklet.kviklet.db.RoleRepository
import dev.kviklet.kviklet.db.UserAdapter
import dev.kviklet.kviklet.security.Permission
import dev.kviklet.kviklet.service.dto.PolicyEffect
import dev.kviklet.kviklet.service.dto.Role
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class DefaultRoleInitializer(
    private val roleRepository: RoleRepository,
    private val userAdapter: UserAdapter,
) {

    fun createDefaultRole(): Role {
        val role = RoleEntity(
            id = Role.DEFAULT_ROLE_ID.toString(),
            name = "Default Role",
            description = "This is the default role and gives permission to read connections and requests",
            policies = mutableSetOf(
                PolicyEntity(
                    action = Permission.DATASOURCE_CONNECTION_GET.getPermissionString(),
                    effect = PolicyEffect.ALLOW,
                    resource = "*",
                ),
                PolicyEntity(
                    action = Permission.EXECUTION_REQUEST_GET.getPermissionString(),
                    effect = PolicyEffect.ALLOW,
                    resource = "*",
                ),
            ),
        )
        return roleRepository.saveAndFlush(role).toDto()
    }

    fun addDefaultRoleToAllUsers(role: Role) {
        val users = userAdapter.listUsers()
        users.forEach { user ->
            val newUser = user.copy(
                roles = user.roles + role,
            )
            userAdapter.updateUser(newUser)
        }
    }

    @Bean
    fun initializer(): ApplicationRunner {
        return ApplicationRunner { _ ->
            if (roleRepository.findById(Role.DEFAULT_ROLE_ID.toString()).isEmpty) {
                val role = createDefaultRole()
                addDefaultRoleToAllUsers(role)
            }
            return@ApplicationRunner
        }
    }
}
