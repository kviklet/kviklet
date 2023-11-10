package com.example.executiongate.security

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal
import org.springframework.security.web.context.HttpSessionSecurityContextRepository
import org.springframework.security.web.context.SecurityContextRepository
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import javax.naming.AuthenticationException
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@RestController
class LoginController(private val customAuthenticationProvider: CustomAuthenticationProvider) {

    private val securityContextHolderStrategy = SecurityContextHolder
        .getContextHolderStrategy()

    private val securityContextRepository: SecurityContextRepository = HttpSessionSecurityContextRepository()

    @OptIn(ExperimentalEncodingApi::class)
    @PostMapping("/login")
    fun login(
        @RequestBody credentials: LoginCredentials,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): ResponseEntity<LoginFooResponse> {
        try {
            val authenticationToken = UsernamePasswordAuthenticationToken(credentials.email, credentials.password)
            val authentication = customAuthenticationProvider.authenticate(authenticationToken)

            request.getSession(true)

            val context: SecurityContext = this.securityContextHolderStrategy.createEmptyContext()
            context.authentication = authentication
            this.securityContextHolderStrategy.context = context
            this.securityContextRepository.saveContext(context, request, response)

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
        } else if (authentication is MyToken) {
            val principal = authentication.principal
            if (principal is OAuth2AuthenticatedPrincipal) {
                return principal.attributes
            }
            return mapOf("error" to "Unexpected principal: ${principal.javaClass}")
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
