package dev.kviklet.kviklet

import dev.kviklet.kviklet.db.PolicyEntity
import dev.kviklet.kviklet.db.PolicyRepository
import dev.kviklet.kviklet.db.RoleEntity
import dev.kviklet.kviklet.db.RoleRepository
import dev.kviklet.kviklet.db.User
import dev.kviklet.kviklet.db.UserEntity
import dev.kviklet.kviklet.db.UserRepository
import dev.kviklet.kviklet.security.UserDetailsWithId
import dev.kviklet.kviklet.service.dto.PolicyEffect
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
        val policyEntity = PolicyEntity(
            action = "*",
            effect = PolicyEffect.ALLOW,
            resource = "*",
        )
        val role = RoleEntity(
            name = "Test Role",
            description = "This is a test role",
            policies = setOf(policyEntity),
        )
        roleRepository.saveAndFlush(role)

        policyRepository.saveAndFlush(policyEntity)

        testUser = savedUser.toDto()
        testUserDetails = UserDetailsWithId(
            id = testUser.id!!,
            email = testUser.email,
            password = testUser.password,
            authorities = emptyList(),
        )
    }
}
