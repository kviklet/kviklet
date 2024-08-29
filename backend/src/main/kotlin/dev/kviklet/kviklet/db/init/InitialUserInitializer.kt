package dev.kviklet.kviklet.db.init

import dev.kviklet.kviklet.db.PolicyEntity
import dev.kviklet.kviklet.db.RoleEntity
import dev.kviklet.kviklet.db.RoleRepository
import dev.kviklet.kviklet.db.UserEntity
import dev.kviklet.kviklet.db.UserRepository
import dev.kviklet.kviklet.security.Permission
import dev.kviklet.kviklet.service.dto.PolicyEffect
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.security.crypto.password.PasswordEncoder

@Configuration
@Profile("!local & !e2e & !test")
class InitialUserInitializer(private val roleRepository: RoleRepository) {

    fun createAdminRole(savedUser: UserEntity) {
        val role = RoleEntity(
            name = "Admin role",
            description = "This role gives admin permissions on everything",
            policies = mutableSetOf(
                PolicyEntity(
                    action = "*",
                    effect = PolicyEffect.ALLOW,
                    resource = "*",
                ),
            ),
        )
        val savedRole = roleRepository.saveAndFlush(role)

        savedUser.roles += savedRole
    }

    fun createDevRole() {
        val role = RoleEntity(
            name = "Developer Role",
            description = "This role gives permission to create, review and execute requests",
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
                PolicyEntity(
                    action = Permission.EXECUTION_REQUEST_EDIT.getPermissionString(),
                    effect = PolicyEffect.ALLOW,
                    resource = "*",
                ),
                PolicyEntity(
                    action = Permission.EXECUTION_REQUEST_REVIEW.getPermissionString(),
                    effect = PolicyEffect.ALLOW,
                    resource = "*",
                ),
                PolicyEntity(
                    action = Permission.EXECUTION_REQUEST_EXECUTE.getPermissionString(),
                    effect = PolicyEffect.ALLOW,
                    resource = "*",
                ),
                PolicyEntity(
                    action = Permission.USER_GET.getPermissionString(),
                    effect = PolicyEffect.ALLOW,
                    resource = "*",
                ),
                PolicyEntity(
                    action = Permission.USER_EDIT.getPermissionString(),
                    effect = PolicyEffect.ALLOW,
                    resource = "*",
                ),
                PolicyEntity(
                    action = Permission.ROLE_GET.getPermissionString(),
                    effect = PolicyEffect.ALLOW,
                    resource = "*",
                ),
            ),
        )
        roleRepository.saveAndFlush(role)
    }

    @Bean
    fun initializer(
        userRepository: UserRepository,
        passwordEncoder: PasswordEncoder,
        @Value("\${initial.user.email}") email: String,
        @Value("\${initial.user.password}") password: String,
    ): ApplicationRunner {
        return ApplicationRunner { _ ->
            if (userRepository.findAll().isEmpty()) {
                val user = UserEntity(
                    email = email,
                    fullName = "Admin User",
                    password = passwordEncoder.encode(password),
                )

                val savedUser = userRepository.saveAndFlush(user)

                createAdminRole(savedUser)
                createDevRole()
                userRepository.saveAndFlush(savedUser)
            }
            return@ApplicationRunner
        }
    }
}
