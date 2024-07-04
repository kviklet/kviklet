package dev.kviklet.kviklet.security

import dev.kviklet.kviklet.db.RoleAdapter
import dev.kviklet.kviklet.db.User
import dev.kviklet.kviklet.db.UserAdapter
import dev.kviklet.kviklet.service.EmailAlreadyExistsException
import dev.kviklet.kviklet.service.dto.Role
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.ldap.core.LdapTemplate
import org.springframework.ldap.query.LdapQueryBuilder
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.ldap.userdetails.LdapUserDetails
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken
import org.springframework.security.web.context.HttpSessionSecurityContextRepository
import org.springframework.security.web.context.SecurityContextRepository
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import javax.naming.AuthenticationException
import javax.naming.directory.Attributes
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@RestController
class LoginController(
    private val authenticationManager: AuthenticationManager,
    private val customAuthenticationProvider: CustomAuthenticationProvider,
    private val userAdapter: UserAdapter,
    private val ldapTemplate: LdapTemplate,
    private val ldapProperties: LdapProperties,
    private val roleAdapter: RoleAdapter,
) {

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
                // Check if it's an LDAP authentication
                if (authentication.principal is LdapUserDetails) {
                    val ldapUser = authentication.principal as LdapUserDetails
                    createOrUpdateLdapUser(ldapUser)
                }
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

    private fun createOrUpdateLdapUser(ldapUser: LdapUserDetails) {
        val loginFieldInput = ldapUser.username
        var user = userAdapter.findByLdapIdentifier(loginFieldInput)

        // Fetch additional attributes from LDAP
        val searchResults = ldapTemplate.search(
            LdapQueryBuilder.query().where(ldapProperties.uniqueIdentifierAttribute).`is`(loginFieldInput),
        ) { attrs: Attributes ->
            mapOf(
                "uid" to attrs.get(ldapProperties.uniqueIdentifierAttribute)?.get()?.toString(),
                "email" to attrs.get(ldapProperties.emailAttribute)?.get()?.toString(),
                "fullName" to attrs.get(ldapProperties.fullNameAttribute)?.get()?.toString(),
            )
        }.firstOrNull()
        if (searchResults == null) {
            throw IllegalStateException("LDAP user not found")
        }

        val email = searchResults["email"] ?: throw IllegalStateException("mail attribute in LDAP user not found")
        val fullName = searchResults["fullName"]
            ?: throw IllegalStateException("Full Name attribute in LDAP user not found")
        val uniqueId = searchResults["uid"] ?: throw IllegalStateException("uid attribute in LDAP user not found")

        if (user == null) {
            userAdapter.findByEmail(email)?.let {
                // This means a password or sso user with the same email already exists
                throw EmailAlreadyExistsException(email)
            }
            val defaultRole = roleAdapter.findById(Role.DEFAULT_ROLE_ID)

            user = User(
                ldapIdentifier = uniqueId,
                email = email,
                fullName = fullName,
                roles = setOf(defaultRole),
            )
        } else {
            user = user.copy(
                email = email,
                fullName = fullName,
            )
        }
        userAdapter.createOrUpdateUser(user)
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
