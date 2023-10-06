package com.example.executiongate.security

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import javax.naming.AuthenticationException
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@RestController
class LoginController(private val customAuthenticationProvider: CustomAuthenticationProvider) {

    @OptIn(ExperimentalEncodingApi::class)
    @PostMapping("/login")
    fun login(
        @RequestBody credentials: LoginCredentials,
        request: HttpServletRequest,
    ): ResponseEntity<LoginFooResponse> {
        try {
            // Create an unauthenticated token
            val authenticationToken = UsernamePasswordAuthenticationToken(credentials.email, credentials.password)

            // Attempt to authenticate the user
            val authentication = customAuthenticationProvider.authenticate(authenticationToken)

            // If successful, store the authentication instance in the SecurityContext
            SecurityContextHolder.getContext().authentication = authentication

            // Create a new session
            request.getSession(true)

            // Respond with OK status
            return ResponseEntity.ok(LoginFooResponse(Base64.encode(request.session.id.toByteArray())))
        } catch (e: AuthenticationException) {
            // Respond with Unauthorized status
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }
    }
}

@RestController
class Oauth2Controller {

    @GetMapping("/oauth2info")
    fun oauth2Info(): Map<String, Any>? {
        val authentication = SecurityContextHolder.getContext().authentication
        if (authentication == null) {
            return mapOf("error" to "User is not authenticated")
        } else if (authentication is OAuth2AuthenticationToken) {
            return authentication.principal.attributes
        }
        return mapOf("error" to "Unexpected authentication type: ${authentication.javaClass}")
    }
}

data class LoginCredentials(
    @Schema(example = "testUser@example.com")
    val email: String,
    @Schema(example = "testPassword")
    val password: String,
)

data class LoginFooResponse(
    val sessionId: String,
)
