package com.example.executiongate

import com.example.executiongate.db.PolicyEntity
import com.example.executiongate.db.PolicyRepository
import com.example.executiongate.db.RoleEntity
import com.example.executiongate.db.RoleRepository
import com.example.executiongate.db.User
import com.example.executiongate.db.UserEntity
import com.example.executiongate.db.UserRepository
import com.example.executiongate.security.UserDetailsWithId
import com.example.executiongate.service.dto.PolicyEffect
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.crypto.password.PasswordEncoder

@Suppress("SpringJavaInjectionPointsAutowiringInspection")
open class TestBase {

    @Autowired lateinit var passwordEncoder: PasswordEncoder

    @Autowired lateinit var userRepository: UserRepository

    @Autowired lateinit var roleRepository: RoleRepository

    @Autowired lateinit var policyRepository: PolicyRepository

    lateinit var testUser: User
    lateinit var testUserDetails: UserDetailsWithId

    @BeforeEach
    fun baseSetUp() {
        val userEntity = UserEntity(
            email = "testUser@example.com",
            fullName = "Admin User",
            password = passwordEncoder.encode("testPassword"),
        )

        val savedUser = userRepository.saveAndFlush(userEntity)
        val role = RoleEntity(
            name = "Test Role",
            description = "This is a test role",
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

        testUser = savedUser.toDto()
        testUserDetails = UserDetailsWithId(
            id = testUser.id,
            email = testUser.email,
            password = testUser.password,
            authorities = emptyList(),
        )
    }
}
