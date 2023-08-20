package com.example.executiongate.security

import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken
import org.springframework.web.bind.annotation.*
import jakarta.servlet.http.HttpServletRequest
import javax.naming.AuthenticationException

@RestController
class LoginController(private val customAuthenticationProvider: CustomAuthenticationProvider) {

    @PostMapping("/login")
    fun login(@RequestBody credentials: LoginCredentials, request: HttpServletRequest): ResponseEntity<Any> {
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
            return ResponseEntity.ok().build()
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
    val password: String
)

