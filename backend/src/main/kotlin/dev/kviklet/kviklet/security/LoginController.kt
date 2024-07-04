package dev.kviklet.kviklet.security

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken
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
class LoginController(private val authenticationManager: AuthenticationManager) {

    private val securityContextHolderStrategy = SecurityContextHolder
        .getContextHolderStrategy()

    private val securityContextRepository: SecurityContextRepository = HttpSessionSecurityContextRepository()

    @OptIn(ExperimentalEncodingApi::class)
    @PostMapping("/login")
    fun login(
        @RequestBody credentials: LoginCredentials,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): ResponseEntity<LoginResponse> {
        // This handles username/password authentication. For Oauth see CustomOidcUserService
        try {
            val authenticationToken = UsernamePasswordAuthenticationToken(credentials.email, credentials.password)
            val authentication = authenticationManager.authenticate(authenticationToken)
            if (authentication.isAuthenticated) {
                val session = request.getSession(true)
                val sessionId = session?.id ?: throw IllegalStateException("Session was not created")
                val context: SecurityContext = this.securityContextHolderStrategy.createEmptyContext()
                context.authentication = authentication
                this.securityContextHolderStrategy.context = context
                this.securityContextRepository.saveContext(context, request, response)
                return ResponseEntity.ok(LoginResponse(Base64.encode(sessionId.toByteArray())))
            } else {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
            }
        } catch (e: AuthenticationException) {
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

data class LoginResponse(val sessionId: String)
