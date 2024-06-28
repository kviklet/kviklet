package dev.kviklet.kviklet.db.init

import dev.kviklet.kviklet.db.PolicyEntity
import dev.kviklet.kviklet.db.RoleEntity
import dev.kviklet.kviklet.db.RoleRepository
import dev.kviklet.kviklet.db.UserAdapter
import dev.kviklet.kviklet.service.dto.PolicyEffect
import dev.kviklet.kviklet.service.dto.Role
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.transaction.annotation.Transactional

@Configuration
class DefaultRoleInitializer(private val roleRepository: RoleRepository, private val userAdapter: UserAdapter) {

    fun createDefaultRole(): Role {
        val role = RoleEntity(
            id = Role.DEFAULT_ROLE_ID.toString(),
            name = "Default Role",
            description = "This is the default role and gives permission to read connections and requests",
            policies = Role.DEFAULT_ROLE_POLICIES.map {
                PolicyEntity(
                    action = it.action,
                    effect = PolicyEffect.ALLOW,
                    resource = it.resource,
                )
            }.toMutableSet(),
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
    @Transactional
    fun initDefaultRole(): ApplicationRunner {
        return ApplicationRunner { _ ->
            if (roleRepository.findById(Role.DEFAULT_ROLE_ID.toString()).isEmpty) {
                val role = createDefaultRole()
                addDefaultRoleToAllUsers(role)
            }
            return@ApplicationRunner
        }
    }
}
