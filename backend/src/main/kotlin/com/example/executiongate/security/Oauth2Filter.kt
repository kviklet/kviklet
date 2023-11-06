package com.example.executiongate.security

import com.example.executiongate.db.RoleAdapter
import com.example.executiongate.db.UserAdapter
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.client.ClientAuthorizationException
import org.springframework.security.oauth2.client.OAuth2AuthorizationContext
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService
import org.springframework.security.oauth2.client.RefreshTokenOAuth2AuthorizedClientProvider
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService
import org.springframework.security.oauth2.core.OAuth2AccessToken
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal
import org.springframework.security.oauth2.core.OAuth2AuthenticationException
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository
import org.springframework.security.web.context.SecurityContextRepository
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.time.Duration

abstract class Oauth2Filter : OncePerRequestFilter() {

    @Autowired
    private lateinit var clientService: OAuth2AuthorizedClientService

    @Autowired
    private lateinit var userAdapter: UserAdapter

    @Autowired
    private lateinit var roleAdapter: RoleAdapter

    @Autowired
    private lateinit var oauth2UserService: OAuth2UserService<OAuth2UserRequest, OAuth2User>

    @Autowired
    private lateinit var authenticationEntryPoint: AuthenticationEntryPoint

    private val refreshProvider = RefreshTokenOAuth2AuthorizedClientProvider()

    private val securityContextHolderStrategy = SecurityContextHolder
        .getContextHolderStrategy()

    // We only change the authentication for the current request, so we don't need to save the context in the session
    private val securityContextRepository: SecurityContextRepository = RequestAttributeSecurityContextRepository()

    init {
        refreshProvider.setClockSkew(Duration.ZERO)
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val authentication = SecurityContextHolder.getContext().authentication

        if (authentication !is OAuth2AuthenticationToken) {
            filterChain.doFilter(request, response)
            return
        }

        try {
            val client: OAuth2AuthorizedClient = clientService.loadAuthorizedClient(
                authentication.authorizedClientRegistrationId,
                authentication.name,
            )

            val authorizedClient = refreshIfExpired(client, authentication)

            val oauth2UserRequest = buildUserRequest(authorizedClient)

            val principal: OAuth2AuthenticatedPrincipal = oauth2UserService.loadUser(oauth2UserRequest)

            val userDetails = loadAuthorities(principal)

            val authenticationResult = MyToken(
                userDetails,
                oauth2UserRequest.accessToken,
                principal,
                userDetails.authorities,
            )

            val context: SecurityContext = this.securityContextHolderStrategy.createEmptyContext()
            context.authentication = authenticationResult
            this.securityContextHolderStrategy.setContext(context)
            this.securityContextRepository.saveContext(context, request, response)

            filterChain.doFilter(request, response)
        } catch (e: OAuth2AuthenticationException) {
            handleError(e, request, response)
            return
        } catch (e: ClientAuthorizationException) {
            handleError(OAuth2AuthenticationException(e.error, e), request, response)
            return
        }
    }

    abstract fun buildUserRequest(authorizedClient: OAuth2AuthorizedClient): OAuth2UserRequest

    private fun handleError(
        e: OAuth2AuthenticationException,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) {
        securityContextHolderStrategy.clearContext()
        logger.trace("Failed to process authentication request", e)
        this.authenticationEntryPoint.commence(request, response, e)
    }

    private fun refreshIfExpired(
        client: OAuth2AuthorizedClient,
        authentication: Authentication,
    ): OAuth2AuthorizedClient {
        val authorizationContext = OAuth2AuthorizationContext.withAuthorizedClient(
            client,
        ).principal(authentication).build()

        if (client.refreshToken != null) {
            val refreshedToken = refreshProvider.authorize(authorizationContext)

            if (refreshedToken == null) {
                logger.trace("Access token not expired, no refresh needed")
                return client
            }
            clientService.saveAuthorizedClient(refreshedToken, authentication)
            logger.trace("Access token successfully refreshed")
            return refreshedToken
        }

        logger.trace("No refresh token available")
        return client
    }

    private fun loadAuthorities(principal: OAuth2AuthenticatedPrincipal): UserDetailsWithId {
        val email = principal.name
        val user = userAdapter.findByEmail(email)!!

        val manuallyGrantedPolicies: List<PolicyGrantedAuthority> = user.roles
            .flatMap { it.policies.map { policy -> PolicyGrantedAuthority(it.id, policy) } }

        val roles = parseRoles(principal.attributes)
        val idpGrantedPolicies = roleAdapter.findIdpRolesByNames(roles)
            .flatMap { it.policies.map { policy -> PolicyGrantedAuthority(it.id, policy) } }

        return UserDetailsWithId(user.id, user.email, idpGrantedPolicies + manuallyGrantedPolicies)
    }

    abstract fun parseRoles(userAttributes: MutableMap<String, Any>): Collection<String>
}

@Component
@ConditionalOnProperty(prefix = "opsgate.identity-provider", name = ["type"], havingValue = "keycloak")
class KeycloakOauth2Filter(
    private val nimbusJwtDecoder: JwtDecoder,
) : Oauth2Filter() {

    override fun buildUserRequest(authorizedClient: OAuth2AuthorizedClient): OAuth2UserRequest {
        val jwt: Jwt = nimbusJwtDecoder.decode(authorizedClient.accessToken.tokenValue)
        val accessToken = OAuth2AccessToken(
            OAuth2AccessToken.TokenType.BEARER,
            authorizedClient.accessToken.tokenValue,
            jwt.issuedAt,
            jwt.expiresAt,
        )
        return OAuth2UserRequest(authorizedClient.clientRegistration, accessToken)
    }

    override fun parseRoles(userAttributes: MutableMap<String, Any>): Collection<String> {
        val realmAccess = userAttributes["realm_access"]
        if (realmAccess != null && realmAccess is Map<*, *>) {
            val roles = realmAccess["roles"]

            if (roles is Collection<*>) {
                return roles.filterIsInstance<String>()
            }
        }
        return emptyList()
    }
}

@Component
@ConditionalOnProperty(prefix = "opsgate.identity-provider", name = ["type"], havingValue = "google")
class GoogleOauth2Filter : Oauth2Filter() {

    override fun buildUserRequest(authorizedClient: OAuth2AuthorizedClient): OAuth2UserRequest {
        val accessToken = authorizedClient.accessToken
        return OAuth2UserRequest(authorizedClient.clientRegistration, accessToken)
    }

    override fun parseRoles(userAttributes: MutableMap<String, Any>): Collection<String> {
        return emptyList()
    }
}
