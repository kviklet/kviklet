package dev.kviklet.kviklet.db.init

import dev.kviklet.kviklet.db.PolicyEntity
import dev.kviklet.kviklet.db.PolicyRepository
import dev.kviklet.kviklet.db.RoleEntity
import dev.kviklet.kviklet.db.RoleRepository
import dev.kviklet.kviklet.db.UserEntity
import dev.kviklet.kviklet.db.UserRepository
import dev.kviklet.kviklet.service.dto.PolicyEffect
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.security.crypto.password.PasswordEncoder

@Configuration
@Profile("!local & !e2e & !test")
class InitialUserInitializer(
    private val roleRepository: RoleRepository,
    private val policyRepository: PolicyRepository,
) {

    fun createAdminRole(savedUser: UserEntity) {
        val role = RoleEntity(
            name = "Admin role",
            description = "This role gives admin permissions on everything",
            policies = emptySet(),
        )
        val savedRole = roleRepository.saveAndFlush(role)
        val policyEntity = PolicyEntity(
            role = savedRole,
            action = "*",
            effect = PolicyEffect.ALLOW,
            resource = "*",
        )
        policyRepository.saveAndFlush(policyEntity)

        savedUser.roles += savedRole
    }

    @Bean
    fun initializer(
        userRepository: UserRepository,
        passwordEncoder: PasswordEncoder,
        @Value("\${initial.user.email}") email: String,
        @Value("\${initial.user.password}") password: String,
    ): ApplicationRunner {
        return ApplicationRunner { _ ->
            if (userRepository.findAll().isNotEmpty()) {
                return@ApplicationRunner
            }

            val user = UserEntity(
                email = email,
                fullName = "Admin User",
                password = passwordEncoder.encode(password),
            )

            val savedUser = userRepository.saveAndFlush(user)

            createAdminRole(savedUser)
            userRepository.saveAndFlush(savedUser)
        }
    }
}
