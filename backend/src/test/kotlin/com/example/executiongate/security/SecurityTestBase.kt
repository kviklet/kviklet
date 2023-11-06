package com.example.executiongate.security

import com.example.executiongate.db.User
import com.example.executiongate.db.UserEntity
import com.example.executiongate.db.UserRepository
import com.example.executiongate.service.dto.Policy
import com.example.executiongate.service.dto.PolicyEffect
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.test.context.support.TestExecutionEvent
import org.springframework.security.test.context.support.WithSecurityContext
import org.springframework.security.test.context.support.WithSecurityContextFactory
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder

fun allow(action: String, resource: String) = Policy("id", action, PolicyEffect.ALLOW, resource)
fun deny(action: String, resource: String) = Policy("id", action, PolicyEffect.DENY, resource)

@Retention(AnnotationRetention.RUNTIME)
@WithSecurityContext(
    factory = WithMockCustomUserSecurityContextFactory::class,
    setupBefore = TestExecutionEvent.TEST_EXECUTION,
)
annotation class WithAdminUser()

class WithMockCustomUserSecurityContextFactory : WithSecurityContextFactory<WithAdminUser> {
    override fun createSecurityContext(customUser: WithAdminUser): SecurityContext {
        val context = SecurityContextHolder.createEmptyContext()
        context.authentication = getAuth(listOf(allow("*", "*")))
        return context
    }
}

@SpringBootTest
@AutoConfigureMockMvc
class SecurityTestBase {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Autowired lateinit var passwordEncoder: PasswordEncoder

    @Autowired lateinit var userRepository: UserRepository

    lateinit var testUser: User
    lateinit var testUserDetails: UserDetailsWithId

    @BeforeEach
    fun securitySetUp() {
        val userEntity = UserEntity(
            email = "testUser@example.com",
            fullName = "Admin User",
            password = passwordEncoder.encode("testPassword"),
        )

        val savedUser = userRepository.saveAndFlush(userEntity)

        testUser = savedUser.toDto()
        testUserDetails = UserDetailsWithId(
            id = testUser.id,
            email = testUser.email,
            password = testUser.password,
            authorities = emptyList(),
        )
    }

    @AfterEach
    fun securityTearDown() {
        SecurityContextHolder.clearContext()
        userRepository.deleteAllInBatch()
    }

    protected fun setAuthentication(policies: List<Policy>) {
        SecurityContextHolder.getContext().authentication = getAuth(policies)
    }

    protected fun getContext(policies: List<Policy>, userDetails: UserDetailsWithId? = null): SecurityContext {
        return SecurityContextHolder.getContext()
            .also { it.authentication = getAuth(policies, userDetails ?: testUserDetails) }
    }

    protected fun MockHttpServletRequestBuilder.withContext(
        policies: List<Policy>,
        userDetails: UserDetailsWithId? = null,
    ): MockHttpServletRequestBuilder {
        return this.with(SecurityMockMvcRequestPostProcessors.securityContext(getContext(policies, userDetails)))
    }

    final inline fun <reified T> MvcResult.parse(): T {
        return objectMapper.readValue(response.contentAsString, T::class.java)
    }

    final inline fun <reified T> MockHttpServletRequestBuilder.content(obj: T) =
        this.contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(obj))

    protected fun asAdmin(function: () -> Unit) {
        setAuthentication(listOf(allow("*", "*")))
        try {
            function()
        } finally {
            SecurityContextHolder.clearContext()
        }
    }
}

private fun getAuth(
    policies: List<Policy>,
    userDetails: UserDetailsWithId? = null,
): UsernamePasswordAuthenticationToken {
    val authorities = policies.map { PolicyGrantedAuthority("role", it) }

    val userDetailsWithPolicies = UserDetailsWithId(
        id = userDetails?.id ?: "id",
        authorities = authorities,
        email = userDetails?.username ?: "username@example.com",
        password = userDetails?.password ?: "password",
    )
    return UsernamePasswordAuthenticationToken(userDetailsWithPolicies, "password", authorities)
}
