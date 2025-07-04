package dev.kviklet.kviklet.security

import dev.kviklet.kviklet.controller.ServerUrlInterceptor
import dev.kviklet.kviklet.db.RoleAdapter
import dev.kviklet.kviklet.db.User
import dev.kviklet.kviklet.db.UserAdapter
import dev.kviklet.kviklet.service.LicenseRestrictionException
import dev.kviklet.kviklet.service.LicenseService
import dev.kviklet.kviklet.service.dto.Role
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.ldap.core.DirContextAdapter
import org.springframework.ldap.core.DirContextOperations
import org.springframework.ldap.core.support.LdapContextSource
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.AuthenticationProvider
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.Authentication
import org.springframework.security.core.AuthenticationException
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.ldap.authentication.BindAuthenticator
import org.springframework.security.ldap.authentication.LdapAuthenticationProvider
import org.springframework.security.ldap.search.FilterBasedLdapUserSearch
import org.springframework.security.ldap.userdetails.UserDetailsContextMapper
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService
import org.springframework.security.oauth2.core.oidc.OidcIdToken
import org.springframework.security.oauth2.core.oidc.OidcUserInfo
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.security.saml2.provider.service.authentication.Saml2AuthenticatedPrincipal
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler
import org.springframework.security.web.context.request.async.WebAsyncManagerIntegrationFilter
import org.springframework.security.web.util.matcher.AntPathRequestMatcher
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import org.springframework.web.filter.ForwardedHeaderFilter
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import java.io.Serializable

@Configuration
class PasswordEncoderConfig {
    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()
}

@ConfigurationProperties(prefix = "cors")
@Configuration
class CorsSettings {
    var allowedOrigins: List<String> = emptyList()
}

@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val oauth2LoginSuccessHandler: OAuth2LoginSuccessHandler,
    private val customAuthenticationProvider: CustomAuthenticationProvider,
    private val customOidcUserService: CustomOidcUserService,
    private val idpProperties: IdentityProviderProperties,
    private val ldapProperties: LdapProperties,
    private val samlProperties: SamlProperties,
    private val contextSource: LdapContextSource,
    private val userDetailsService: UserDetailsServiceImpl,
    private val corsSettings: CorsSettings,
) {

    @Autowired(required = false)
    private var samlLoginSuccessHandler: SamlLoginSuccessHandler? = null

    @Bean
    fun ldapAuthenticationProvider(): LdapAuthenticationProvider {
        val searchBase = ldapProperties.searchBase?.takeIf { it.isNotBlank() } ?: "ou=${ldapProperties.userOu}"
        val userSearch = FilterBasedLdapUserSearch(
            searchBase,
            "(${ldapProperties.uniqueIdentifierAttribute}={0})",
            contextSource,
        )

        val authenticator = BindAuthenticator(contextSource).apply {
            setUserSearch(userSearch)
        }

        return LdapAuthenticationProvider(authenticator).apply {
            setUserDetailsContextMapper(object : UserDetailsContextMapper {
                override fun mapUserFromContext(
                    ctx: DirContextOperations,
                    username: String,
                    authorities: MutableCollection<out GrantedAuthority>,
                ): UserDetails = userDetailsService.loadUserByLdapIdentifier(username)

                override fun mapUserToContext(user: UserDetails, ctx: DirContextAdapter) {
                    // This method is typically used when writing back to LDAP.
                    // We don't need to implement it for our use case.
                }
            })
        }
    }

    @Bean
    fun authManager(http: HttpSecurity): AuthenticationManager {
        val authenticationManagerBuilder = http.getSharedObject(AuthenticationManagerBuilder::class.java)
        val builder = authenticationManagerBuilder
            .authenticationProvider(customAuthenticationProvider)
        if (ldapProperties.enabled) builder.authenticationProvider(ldapAuthenticationProvider())
        return authenticationManagerBuilder.build()
    }

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http.invoke {
            addFilterBefore<WebAsyncManagerIntegrationFilter>(ForwardedHeaderFilter())
            if (corsSettings.allowedOrigins.isNotEmpty()) {
                cors { }
            }
            authenticationManager = authManager(http)

            if (idpProperties.isOauth2Enabled()) {
                oauth2Login {
                    authenticationSuccessHandler = oauth2LoginSuccessHandler
                    userInfoEndpoint {
                        oidcUserService = customOidcUserService
                    }
                }
            }

            if (samlProperties.isSamlEnabled()) {
                saml2Login {
                    samlLoginSuccessHandler?.let { authenticationSuccessHandler = it }
                }

                saml2Metadata { }
            }

            exceptionHandling {
                authenticationEntryPoint = CustomAuthenticationEntryPoint()
                accessDeniedHandler = CustomAccessDeniedHandler()
            }

            authorizeHttpRequests {
                authorize("/login", permitAll)
                authorize("/login**", permitAll)
                authorize("/health", permitAll)
                authorize("/oauth2**", permitAll)
                authorize("/saml2/**", permitAll)
                authorize("/v3/api-docs/**", permitAll)
                authorize("/swagger-ui/**", permitAll)
                authorize("/swagger-resources/**", permitAll)
                authorize("/webjars**", permitAll)
                authorize("/docs/redoc.html", permitAll)
                authorize("/h2-console", permitAll)
                authorize("/h2-console/**", permitAll)
                authorize("/error", permitAll)
                authorize("/config/", permitAll)

                authorize(anyRequest, authenticated)
            }

            securityContext {
                requireExplicitSave = true
            }
            sessionManagement {
                sessionCreationPolicy = SessionCreationPolicy.IF_REQUIRED
            }

            logout {
                logoutRequestMatcher = AntPathRequestMatcher("/logout", "POST")
                invalidateHttpSession = true
                deleteCookies("JSESSIONID")
                logoutSuccessHandler = HttpStatusReturningLogoutSuccessHandler()
            }
            csrf {
                disable()
            }
            headers {
                frameOptions {
                    sameOrigin = true // for h2 console
                    deny = false
                }
            }
        }

        return http.build()
    }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration = CorsConfiguration()
        configuration.allowedOriginPatterns = corsSettings.allowedOrigins
        configuration.allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
        configuration.allowCredentials = true
        configuration.allowedHeaders = listOf("*")

        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", configuration)

        return source
    }
}

@Configuration
class MvcConfig : WebMvcConfigurer {
    override fun addArgumentResolvers(resolvers: MutableList<HandlerMethodArgumentResolver>) {
        resolvers.add(CurrentUserArgumentResolver())
    }

    @Autowired
    private lateinit var serverUrlInterceptor: ServerUrlInterceptor

    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(serverUrlInterceptor)
    }
}

class CustomAccessDeniedHandler : AccessDeniedHandler {
    override fun handle(
        request: HttpServletRequest?,
        response: HttpServletResponse?,
        accessDeniedException: AccessDeniedException?,
    ) {
        response?.sendError(HttpServletResponse.SC_FORBIDDEN, "Unauthorized")
    }
}

class CustomAuthenticationEntryPoint : AuthenticationEntryPoint {

    private val logger = LoggerFactory.getLogger(CustomAuthenticationEntryPoint::class.java)
    override fun commence(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authException: AuthenticationException,
    ) {
        val requestURI = request.requestURI

        // For other API requests, return 401
        logger.info("Unauthorized error on {} : {}", requestURI, authException.message)
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED, authException.message)
    }
}

@Service
class CustomAuthenticationProvider(val userAdapter: UserAdapter, val passwordEncoder: PasswordEncoder) :
    AuthenticationProvider {

    override fun authenticate(authentication: Authentication?): Authentication? {
        val email = authentication?.name!!
        val password = authentication.credentials.toString()

        val user = userAdapter.findByEmail(email)

        if (user == null || user.subject != null || !passwordEncoder.matches(password, user.password)) {
            throw BadCredentialsException("Invalid username or password, or user is an OAuth user.")
        }

        // Create a CustomUserDetails object
        val userDetails = UserDetailsWithId(user.getId()!!, email, user.password, emptyList())

        val policies = user.roles.flatMap { it.policies }.map { PolicyGrantedAuthority(it) }

        return UsernamePasswordAuthenticationToken(
            userDetails,
            password,
            policies,
        )
    }

    override fun supports(authentication: Class<*>): Boolean =
        authentication == UsernamePasswordAuthenticationToken::class.java
}

class CustomOidcUser(
    private val oidcUser: OidcUser,
    private val userDetails: UserDetailsWithId,
    private val authorities: Collection<GrantedAuthority>,
) : OidcUser,
    Serializable {

    override fun getClaims(): Map<String, Any> = oidcUser.claims

    override fun getIdToken(): OidcIdToken = oidcUser.idToken

    override fun getUserInfo(): OidcUserInfo = oidcUser.userInfo

    override fun getName(): String = oidcUser.name

    override fun getAuthorities(): Collection<GrantedAuthority> = authorities

    override fun getAttributes(): Map<String, Any> = oidcUser.attributes

    // Additional methods to expose userDetails
    fun getUserDetails(): UserDetailsWithId = userDetails

    companion object {
        private const val serialVersionUID = 1L // Serializable version UID
    }
}

@Service
class CustomOidcUserService(
    private val userAdapter: UserAdapter,
    private val roleAdapter: RoleAdapter,
    private val licenseService: LicenseService,
) : OidcUserService() {

    @Transactional
    override fun loadUser(userRequest: OidcUserRequest): OidcUser {
        val oidcUser = super.loadUser(userRequest)

        val subject = oidcUser.getAttribute<String>("sub")!!
        val email = oidcUser.getAttribute<String>("email")!!
        val name = oidcUser.getAttribute<String>("name")

        var user = userAdapter.findBySubject(subject)

        if (user == null) {
            // Check if a user with the same email exists
            userAdapter.findByEmail(email)?.let {
                // Update existing user to use SSO
                user = it.copy(
                    subject = subject, // Set the SSO subject
                    email = email,
                    fullName = name ?: it.fullName,
                    password = null,
                    ldapIdentifier = null,
                )
            } ?: run {
                // No existing user found, create a new one
                val license = licenseService.getActiveLicense()
                if (license != null) {
                    val maxUsers = license.allowedUsers
                    if (maxUsers <= userAdapter.listUsers().size.toUInt()) {
                        throw LicenseRestrictionException("License does not allow more users")
                    }
                }
                val defaultRole = roleAdapter.findById(Role.DEFAULT_ROLE_ID)
                user = User(
                    subject = subject,
                    email = email,
                    fullName = name,
                    roles = setOf(defaultRole),
                )
            }
        } else {
            // If the user has already signed in before, update the user's information
            user = user!!.copy(
                subject = subject,
                email = email,
                fullName = name,
            )
            // Update other user properties here
        }

        val savedUser = userAdapter.createOrUpdateUser(user!!)
        // Handle your custom logic (e.g., saving the user to the database)
        // Extract policies

        val policies = savedUser.roles.flatMap { it.policies }.map { PolicyGrantedAuthority(it) }
        val userDetails = UserDetailsWithId(savedUser.getId()!!, email, "", policies)

        // Return a CustomOidcUser with the original OidcUser, custom user details, and authorities
        return CustomOidcUser(oidcUser, userDetails, policies)
    }
}

@Component
class OAuth2LoginSuccessHandler : SimpleUrlAuthenticationSuccessHandler() {

    @Transactional
    override fun onAuthenticationSuccess(
        request: HttpServletRequest?,
        response: HttpServletResponse?,
        authentication: Authentication?,
    ) {
        val baseUrl = request?.let { getBaseUrl(it) }
        val redirectUrl = "$baseUrl/requests"
        redirectStrategy.sendRedirect(request, response, redirectUrl)
    }

    private fun getBaseUrl(request: HttpServletRequest): String {
        val scheme = request.scheme
        val serverName = request.serverName
        val serverPort = request.serverPort

        return "$scheme://$serverName${if (serverPort != 80 && serverPort != 443) ":5173" else ""}"
    }
}

// The following class is not MIT licensed
@Component
@ConditionalOnProperty(prefix = "saml", name = ["enabled"], havingValue = "true")
class SamlLoginSuccessHandler(private val customSaml2UserService: CustomSaml2UserService) :
    SimpleUrlAuthenticationSuccessHandler() {

    @Transactional
    override fun onAuthenticationSuccess(
        request: HttpServletRequest?,
        response: HttpServletResponse?,
        authentication: Authentication?,
    ) {
        // Convert SAML authentication to use UserDetailsWithId
        if (authentication?.principal is Saml2AuthenticatedPrincipal) {
            val samlPrincipal = authentication.principal as Saml2AuthenticatedPrincipal
            val user = customSaml2UserService.loadUser(samlPrincipal)

            val authorities = user.roles.flatMap { it.policies }.map { PolicyGrantedAuthority(it) }
            val userDetails = UserDetailsWithId(user.getId()!!, user.email, "", authorities)

            // Create a new authentication token with UserDetailsWithId as principal
            val newAuth = UsernamePasswordAuthenticationToken(userDetails, authentication.credentials, authorities)
            SecurityContextHolder.getContext().authentication = newAuth
        }

        val baseUrl = request?.let { getBaseUrl(it) }
        val redirectUrl = "$baseUrl/requests"
        redirectStrategy.sendRedirect(request, response, redirectUrl)
    }

    private fun getBaseUrl(request: HttpServletRequest): String {
        val scheme = request.scheme
        val serverName = request.serverName
        val serverPort = request.serverPort

        return "$scheme://$serverName${if (serverPort != 80 && serverPort != 443) ":5173" else ""}"
    }
}
