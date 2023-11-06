package com.example.executiongate.security

import com.example.executiongate.db.User
import com.example.executiongate.db.UserAdapter
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.oauth2.client.registration.ClientRegistrations
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtDecoders
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler
import org.springframework.security.web.util.matcher.AntPathRequestMatcher
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Component
@ConfigurationProperties(prefix = "opsgate.identity-provider")
@EnableWebSecurity
data class IdentityProviderConfig(
    var type: String? = null,
    var clientId: String? = null,
    var clientSecret: String? = null,
    var issuerUri: String? = null,
) {

    @Bean
    fun oauth2Enabled(): Boolean {
        return type != null && clientId != null && clientSecret != null && issuerUri != null
    }

    @Bean
    @ConditionalOnExpression(value = "\${opsgate.identity-provider.oauth2-enabled:true}")
    fun clientRegistrationRepository(): ClientRegistrationRepository {
        val clientRegistration = ClientRegistrations
            .fromIssuerLocation(issuerUri)
            .registrationId(type)
            .clientId(clientId)
            .clientSecret(clientSecret)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .scope("openid", "email")
            .userNameAttributeName("email")
            .build()

        return InMemoryClientRegistrationRepository(clientRegistration)
    }

    @Bean
    @ConditionalOnExpression(value = "\${opsgate.identity-provider.oauth2-enabled:true}")
    fun jwtDecoder(): JwtDecoder {
        return JwtDecoders.fromIssuerLocation(this.issuerUri)
    }

    @Bean
    @ConditionalOnExpression(value = "\${opsgate.identity-provider.oauth2-enabled:true}")
    fun oauth2UserService(): OAuth2UserService<OAuth2UserRequest, OAuth2User> {
        return DefaultOAuth2UserService()
    }
}

@Configuration
class PasswordEncoderConfig {
    @Bean
    fun passwordEncoder(): PasswordEncoder {
        return BCryptPasswordEncoder()
    }
}

@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val customAuthenticationProvider: CustomAuthenticationProvider,
    private val oauth2Enabled: Boolean,
    private val filter: Oauth2Filter,
    private val oauth2LoginSuccessHandler: OAuth2LoginSuccessHandler,
) {

    @Bean
    fun authManager(http: HttpSecurity): AuthenticationManager {
        val authenticationManagerBuilder = http.getSharedObject(AuthenticationManagerBuilder::class.java)
        authenticationManagerBuilder.authenticationProvider(customAuthenticationProvider)
        return authenticationManagerBuilder.build()
    }

    @Bean
    @Order(2)
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http.invoke {
            cors { }

            authenticationManager = authManager(http)

            if (oauth2Enabled) {
                addFilterBefore<BearerTokenAuthenticationFilter>(filter)

                oauth2Login {
                    authenticationSuccessHandler = oauth2LoginSuccessHandler
                }
            }

            exceptionHandling {
                authenticationEntryPoint = CustomAuthenticationEntryPoint()
                accessDeniedHandler = CustomAccessDeniedHandler()
            }

            authorizeHttpRequests {
                authorize("/login", permitAll)
                authorize("/login**", permitAll)
                authorize("/oauth2**", permitAll)
                authorize("/v3/api-docs/**", permitAll)
                authorize("/swagger-ui/**", permitAll)
                authorize("/swagger-resources/**", permitAll)
                authorize("/webjars**", permitAll)
                authorize("/docs/redoc.html", permitAll)
                authorize("/h2-console", permitAll)
                authorize("/h2-console/**", permitAll)
                authorize("/error", permitAll)

                authorize(anyRequest, authenticated)
            }

            securityContext {
                requireExplicitSave = true
            }
            sessionManagement {
                sessionCreationPolicy = SessionCreationPolicy.ALWAYS
            }

            logout {
                logoutRequestMatcher = AntPathRequestMatcher("/logout", "POST")
                invalidateHttpSession = true
                deleteCookies("JSESSIONID")
                addLogoutHandler { _, response, _ ->
                    response.status = HttpStatus.OK.value()
                }
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
        configuration.allowedOrigins = listOf("http://localhost:5173", "http://localhost:80", "http://localhost")
        configuration.allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
        configuration.allowCredentials = true
        configuration.allowedHeaders = listOf("*")

        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", configuration)

        return source
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

@Component
class CustomAuthenticationEntryPoint : AuthenticationEntryPoint {
    override fun commence(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authException: AuthenticationException,
    ) {
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED, authException.message)
    }
}

@Service
class CustomAuthenticationProvider(
    val userAdapter: UserAdapter,
    val passwordEncoder: PasswordEncoder,
) : AuthenticationProvider {

    override fun authenticate(authentication: Authentication?): Authentication? {
        val email = authentication?.name!!
        val password = authentication.credentials.toString()

        val user = userAdapter.findByEmail(email)

        if (user == null || user.googleId != null || !passwordEncoder.matches(password, user.password)) {
            throw BadCredentialsException("Invalid username or password, or user is an OAuth user.")
        }

        // Create a CustomUserDetails object
        val userDetails = UserDetailsWithId(user.id, email, emptyList())

        val policies = user.roles.flatMap { it.policies.map { policy -> PolicyGrantedAuthority(it.id, policy) } }

        return UsernamePasswordAuthenticationToken(
            userDetails,
            password,
            policies,
        )
    }

    override fun supports(authentication: Class<*>): Boolean {
        return authentication == UsernamePasswordAuthenticationToken::class.java
    }
}

@Component
class OAuth2LoginSuccessHandler(
    private val userAdapter: UserAdapter,
) : SimpleUrlAuthenticationSuccessHandler() {

    override fun onAuthenticationSuccess(
        request: HttpServletRequest?,
        response: HttpServletResponse?,
        authentication: Authentication?,
    ) {
        if (authentication is OAuth2AuthenticationToken) {
            val oauth2User = authentication.principal
            val googleId = oauth2User.getAttribute<String>("sub")!!
            val email = oauth2User.getAttribute<String>("email")!!
            val name = oauth2User.getAttribute<String>("name")

            var user = userAdapter.findByEmail(email)

            if (user == null) {
                // If the user is signing in for the first time, create a new user
                user = User(
                    googleId = googleId,
                    email = email,
                    fullName = name,
                    // Set default roles and other user properties here
                )
            } else {
                // If the user has already signed in before, update the user's information
                user = user.copy(
                    id = user.id,
                    email = email,
                    fullName = name,
                    // Set default roles and other user properties here
                )
                // Update other user properties here
            }

            userAdapter.createOrUpdateUser(user)
        }

        redirectStrategy.sendRedirect(request, response, "http://localhost:3000")
    }
}
