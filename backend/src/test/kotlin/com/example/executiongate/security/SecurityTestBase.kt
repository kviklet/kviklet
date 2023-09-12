package com.example.executiongate.security

import com.example.executiongate.service.dto.Policy
import com.example.executiongate.service.dto.PolicyEffect
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.AfterEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder

fun allow(action: String, resource: String) = Policy("id", action, PolicyEffect.ALLOW, resource)
fun deny(action: String, resource: String) = Policy("id", action, PolicyEffect.DENY, resource)

@SpringBootTest
@AutoConfigureMockMvc
class SecurityTestBase {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @AfterEach
    fun securityTearDown() {
        SecurityContextHolder.clearContext()
    }

    protected fun setAuthentication(policies: List<Policy>) {
        SecurityContextHolder.getContext().authentication = getAuth(policies)
    }

    protected fun getContext(policies: List<Policy>): SecurityContext {
        return SecurityContextHolder.getContext().also { it.authentication = getAuth(policies) }
    }

    private fun getAuth(policies: List<Policy>): UsernamePasswordAuthenticationToken {
        val authorities = policies.map { PolicyGrantedAuthority(it) }
        val userDetails = UserDetailsWithId(
            id = "id",
            authorities = authorities,
            email = "username@example.com",
            password = "password",
        )
        return UsernamePasswordAuthenticationToken(userDetails, "password", authorities)
    }

    protected fun MockHttpServletRequestBuilder.withContext(policies: List<Policy>): MockHttpServletRequestBuilder {
        return this.with(SecurityMockMvcRequestPostProcessors.securityContext(getContext(policies)))
    }

    final inline fun <reified T> MvcResult.parse(): T {
        return objectMapper.readValue(response.contentAsString, T::class.java)
    }

    final inline fun <reified T> MockHttpServletRequestBuilder.content(obj: T) =
        this.contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(obj))
}
