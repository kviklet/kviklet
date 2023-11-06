package com.example.executiongate.db.init

import com.example.executiongate.db.PolicyEntity
import com.example.executiongate.db.PolicyRepository
import com.example.executiongate.db.RoleEntity
import com.example.executiongate.db.RoleRepository
import com.example.executiongate.db.UserEntity
import com.example.executiongate.db.UserRepository
import com.example.executiongate.service.dto.PolicyEffect
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
            id = "Admin role",
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
