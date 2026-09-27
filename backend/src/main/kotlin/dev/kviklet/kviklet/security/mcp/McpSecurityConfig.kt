// This file is not MIT licensed
package dev.kviklet.kviklet.security.mcp

import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jose.jwk.source.ImmutableJWKSet
import com.nimbusds.jose.jwk.source.JWKSource
import com.nimbusds.jose.proc.SecurityContext
import dev.kviklet.kviklet.ApplicationProperties
import dev.kviklet.kviklet.db.OAuth2SigningKeyAdapter
import dev.kviklet.kviklet.db.UserAdapter
import dev.kviklet.kviklet.mcp.KvikletMcpTools
import dev.kviklet.kviklet.security.CorsSettings
import dev.kviklet.kviklet.service.BaseUrlResolver
import dev.kviklet.kviklet.service.LicenseService
import dev.kviklet.kviklet.telemetry.McpClient
import dev.kviklet.kviklet.telemetry.McpClientConnected
import dev.kviklet.kviklet.telemetry.Telemetry
import io.modelcontextprotocol.server.McpStatelessSyncServer
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springaicommunity.mcp.security.authorizationserver.config.McpAuthorizationServerConfigurer
import org.springaicommunity.mcp.security.server.config.McpServerOAuth2Configurer
import org.springframework.boot.LazyInitializationExcludeFilter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.http.HttpMethod
import org.springframework.jdbc.core.JdbcOperations
import org.springframework.security.authentication.AuthenticationEventPublisher
import org.springframework.security.config.ObjectPostProcessor
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization.McpDefaultJwtCustomizer
import org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization.OAuth2AuthorizationServerConfigurer
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.Authentication
import org.springframework.security.core.AuthenticationException
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.ClientAuthenticationMethod
import org.springframework.security.oauth2.core.OAuth2AuthenticationException
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.OAuth2ErrorCodes
import org.springframework.security.oauth2.jwt.JwtClaimNames
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsent
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationProvider
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationToken
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationGrantAuthenticationToken
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientRegistrationAuthenticationContext
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientRegistrationAuthenticationProvider
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientRegistrationAuthenticationToken
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientRegistrationAuthenticationValidator
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository
import org.springframework.security.oauth2.server.authorization.converter.OAuth2ClientRegistrationRegisteredClientConverter
import org.springframework.security.oauth2.server.authorization.mcp.token.ResourceIdentifierAudienceTokenCustomizer
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings
import org.springframework.security.oauth2.server.authorization.token.DelegatingOAuth2TokenGenerator
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext
import org.springframework.security.oauth2.server.authorization.token.JwtGenerator
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.AuthenticationConverter
import org.springframework.security.web.context.SecurityContextHolderFilter
import org.springframework.security.web.context.request.async.WebAsyncManagerIntegrationFilter
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher
import org.springframework.security.web.util.UrlUtils
import org.springframework.security.web.util.matcher.RequestMatcher
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import org.springframework.web.filter.ForwardedHeaderFilter
import org.springframework.web.util.UriComponentsBuilder
import tools.jackson.databind.json.JsonMapper
import java.time.Duration
import java.util.UUID
import java.util.function.Consumer

/** The one scope MCP clients are granted: acting as the user, with the user's own permissions. */
const val MCP_SCOPE = "mcp"

const val MCP_ENDPOINT = "/mcp"

/** Backend endpoint the authorization server sends the user to for consent; it forwards to the frontend. */
const val MCP_CONSENT_ENDPOINT = "/oauth2/consent"

/**
 * Kviklet as an OAuth 2.1 authorization server for MCP clients, and `/mcp` as the resource those
 * clients call with the tokens it issues.
 *
 * - Clients register themselves (Dynamic Client Registration) and use PKCE; there are no client
 *   secrets to hand out.
 * - Users log in with whatever Kviklet login they normally use, then approve the client on Kviklet's
 *   consent screen. Consent is asked for on every authorization: anyone can register a client, so a
 *   silent authorization must never happen.
 * - OAuth is the only way into `/mcp`. Its chain is ordered before the API key chain and only accepts
 *   tokens from this authorization server, so API keys and session cookies are rejected there.
 * - Everything here answers 402 without an enterprise license, like Kviklet's other enterprise features.
 */
@Configuration
class McpSecurityConfig(
    private val licenseService: LicenseService,
    private val applicationProperties: ApplicationProperties,
    private val baseUrlResolver: BaseUrlResolver,
    private val corsSettings: CorsSettings,
    private val userAdapter: UserAdapter,
    private val jsonMapper: JsonMapper,
    private val telemetry: Telemetry,
) {

    @Bean
    fun registeredClientRepository(jdbcOperations: JdbcOperations): RegisteredClientRepository =
        JdbcRegisteredClientRepository(jdbcOperations)

    @Bean
    fun authorizationService(
        jdbcOperations: JdbcOperations,
        registeredClientRepository: RegisteredClientRepository,
    ): OAuth2AuthorizationService = JdbcOAuth2AuthorizationService(jdbcOperations, registeredClientRepository)

    /**
     * Consent is never stored. The authorization server counts scopes the user approved before as
     * approved again, so a stored consent would turn a later "Cancel" into an approval. It asks to
     * store the consent exactly when the user approves, which is reported to telemetry instead.
     */
    @Bean
    fun authorizationConsentService(
        registeredClientRepository: RegisteredClientRepository,
    ): OAuth2AuthorizationConsentService = object : OAuth2AuthorizationConsentService {
        override fun save(authorizationConsent: OAuth2AuthorizationConsent) {
            val clientName = registeredClientRepository.findById(authorizationConsent.registeredClientId)
                ?.clientName ?: ""
            // The principal name is the user id, see McpAuthorizationPrincipalFilter.
            telemetry.track(
                McpClientConnected(McpClient.fromClientName(clientName)),
                userId = authorizationConsent.principalName,
            )
        }

        override fun remove(authorizationConsent: OAuth2AuthorizationConsent) = Unit

        override fun findById(registeredClientId: String, principalName: String): OAuth2AuthorizationConsent? = null
    }

    @Bean
    fun authorizationServerSettings(): AuthorizationServerSettings = AuthorizationServerSettings.builder().build()

    @Bean
    fun jwkSource(signingKeyAdapter: OAuth2SigningKeyAdapter): JWKSource<SecurityContext> {
        val key = signingKeyAdapter.findOldestJwk()?.let { RSAKey.parse(it) }
            ?: RSAKeyGenerator(2048).keyID(UUID.randomUUID().toString()).generate().also {
                signingKeyAdapter.create(it.keyID, it.toJSONString())
            }
        return ImmutableJWKSet(JWKSet(key))
    }

    /**
     * Access tokens are JWTs audience-restricted to the MCP endpoint; refresh tokens are issued to
     * public clients as well (see [PublicClientRefreshTokenGenerator]). The JWT customizers are the
     * ones the MCP configurer would use by default, plus a fallback audience.
     */
    @Bean
    fun tokenGenerator(jwkSource: JWKSource<SecurityContext>): OAuth2TokenGenerator<*> {
        val jwtGenerator = JwtGenerator(NimbusJwtEncoder(jwkSource))
        val resourceAudience = ResourceIdentifierAudienceTokenCustomizer()
        jwtGenerator.setJwtCustomizer { context ->
            McpDefaultJwtCustomizer.DEFAULT_JWT_CUSTOMIZER.customize(context)
            resourceAudience.customize(context)
            defaultToMcpAudience(context)
        }
        return DelegatingOAuth2TokenGenerator(jwtGenerator, PublicClientRefreshTokenGenerator())
    }

    @Bean
    @Order(-2)
    fun mcpAuthorizationServerFilterChain(
        http: HttpSecurity,
        registeredClientRepository: RegisteredClientRepository,
    ): SecurityFilterChain {
        // The authorization server's endpoints are only known once the MCP configurer has set it up
        // while the chain is built, so they are looked up when the first request is matched.
        val endpointsMatcher by lazy {
            http.getConfigurer(OAuth2AuthorizationServerConfigurer::class.java).endpointsMatcher
        }
        http.securityMatcher(RequestMatcher { endpointsMatcher.matches(it) })
        http.with(McpAuthorizationServerConfigurer.mcpAuthorizationServer()) { mcp ->
            mcp.dynamicClientRegistrationValidator(mcpClientRegistrationValidator)
            mcp.authorizationServer { authServer ->
                authServer.authorizationEndpoint { endpoint ->
                    endpoint.consentPage(MCP_CONSENT_ENDPOINT)
                    endpoint.authorizationRequestConverters { converters ->
                        converters.replaceAll(::DefaultScopeAuthorizationRequestConverter)
                    }
                }
                authServer.clientAuthentication { clientAuthentication ->
                    clientAuthentication.authenticationConverters {
                        it.add(0, PublicClientRefreshTokenAuthenticationConverter())
                    }
                    clientAuthentication.authenticationProviders {
                        it.add(0, PublicClientRefreshTokenAuthenticationProvider(registeredClientRepository))
                    }
                }
                // Added after the MCP configurer's own post-processors, so these win.
                authServer.addObjectPostProcessor(alwaysRequireConsent)
                authServer.addObjectPostProcessor(grantMcpScopeOnRegistration)
            }
        }

        addPublicUrlAndLicenseFilters(http)
        publishNoAuthenticationEvents(http)
        http.addFilterAfter(McpAuthorizationPrincipalFilter(), SecurityContextHolderFilter::class.java)
        http.addFilterAfter(McpConsentSubmissionFilter(jsonMapper), SecurityContextHolderFilter::class.java)
        if (corsSettings.allowedOrigins.isNotEmpty()) {
            http.cors { }
        }
        http.authorizeHttpRequests { it.anyRequest().authenticated() }
        http.exceptionHandling {
            it.defaultAuthenticationEntryPointFor(
                FrontendLoginEntryPoint(baseUrlResolver),
                PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, "/oauth2/authorize"),
            )
        }
        // The session only supplies the logged-in user; nothing in this chain may write to it.
        http.requestCache { it.disable() }
        // Replaced by the X-Kviklet-Request header check on consent submissions.
        http.csrf { it.disable() }
        return http.build()
    }

    @Bean
    @Order(-1)
    fun mcpResourceServerFilterChain(http: HttpSecurity, jwkSource: JWKSource<SecurityContext>): SecurityFilterChain {
        val jwtDecoder: JwtDecoder = NimbusJwtDecoder.withJwkSource(jwkSource).build()
        http.securityMatcher(MCP_ENDPOINT, "$MCP_ENDPOINT/**", "/.well-known/oauth-protected-resource/**")
        http.with(McpServerOAuth2Configurer.mcpServerOAuth2()) { mcp ->
            // Required by the configurer, but unused: the metadata customizer below names the
            // authorization server per request, and the decoder is given directly.
            mcp.authorizationServer("")
            mcp.resourcePath(MCP_ENDPOINT)
            mcp.resourceName("Kviklet")
            mcp.jwtDecoder(jwtDecoder)
            mcp.validateAudienceClaim(true)
            mcp.protectedResourceMetadataCustomizer { metadata ->
                metadata.authorizationServer(currentIssuer()).resourceName("Kviklet").scope(MCP_SCOPE)
                    // Spring's default claims certificate-bound tokens, which these are not.
                    .tlsClientCertificateBoundAccessTokens(false)
            }
            mcp.oauth2ResourceServer { resourceServer ->
                resourceServer.jwt { it.jwtAuthenticationConverter(McpJwtAuthenticationConverter(userAdapter)) }
            }
        }
        addPublicUrlAndLicenseFilters(http)
        publishNoAuthenticationEvents(http)
        http.authorizeHttpRequests {
            it.requestMatchers("/.well-known/**").permitAll()
            it.anyRequest().authenticated()
        }
        http.sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
        http.requestCache { it.disable() }
        http.csrf { it.disable() }
        return http.build()
    }

    companion object {
        /**
         * With `spring.main.lazy-initialization=true`, nothing would ever create the MCP server: it
         * attaches itself to the `/mcp` endpoint when it is created, and nothing asks for it. Every
         * call would then fail with "MCP handler not configured". The tool beans (the `mcp` package)
         * must exist before it too, because the server only picks up `@McpTool` beans that are already
         * there. Static because it is read while the bean factory is still being set up.
         */
        @Bean
        @JvmStatic
        fun mcpServerEagerInitialization(): LazyInitializationExcludeFilter =
            LazyInitializationExcludeFilter { _, _, beanType ->
                beanType == McpStatelessSyncServer::class.java ||
                    beanType.packageName == KvikletMcpTools::class.java.packageName
            }
    }

    /**
     * Nobody logs in on these chains: users log in to Kviklet as usual, and what is authenticated here
     * are clients and tokens. Spring would still report each of those as a successful authentication,
     * which the login telemetry counts as a password login.
     */
    private fun publishNoAuthenticationEvents(http: HttpSecurity) {
        http.getSharedObject(AuthenticationManagerBuilder::class.java)
            .authenticationEventPublisher(object : AuthenticationEventPublisher {
                override fun publishAuthenticationSuccess(authentication: Authentication) = Unit

                override fun publishAuthenticationFailure(
                    exception: AuthenticationException,
                    authentication: Authentication,
                ) = Unit
            })
    }

    private fun addPublicUrlAndLicenseFilters(http: HttpSecurity) {
        val publicBackendUrl = if (applicationProperties.inDocker) {
            { request: HttpServletRequest -> baseUrlResolver.resolve(request) + "/api" }
        } else {
            null
        }
        http.addFilterBefore(ForwardedHeaderFilter(), WebAsyncManagerIntegrationFilter::class.java)
        http.addFilterBefore(McpPublicUrlFilter(publicBackendUrl), WebAsyncManagerIntegrationFilter::class.java)
        http.addFilterBefore(McpLicenseFilter(licenseService), WebAsyncManagerIntegrationFilter::class.java)
    }
}

/**
 * The issuer the authorization server reports for the current request. Spring Authorization Server
 * derives it from the request the same way when no issuer is configured.
 */
private fun currentIssuer(): String {
    val request = (RequestContextHolder.currentRequestAttributes() as ServletRequestAttributes).request
    return UriComponentsBuilder.fromUriString(UrlUtils.buildFullRequestUrl(request))
        .replacePath(request.contextPath)
        .replaceQuery(null)
        .fragment(null)
        .build()
        .toUriString()
}

private val alwaysRequireConsent = object : ObjectPostProcessor<OAuth2AuthorizationCodeRequestAuthenticationProvider> {
    override fun <O : OAuth2AuthorizationCodeRequestAuthenticationProvider> postProcess(provider: O): O {
        provider.setAuthorizationConsentRequired { true }
        return provider
    }
}

/**
 * Tokens are audience-restricted to the MCP endpoint. Clients name it in the `resource` parameter,
 * which the MCP authorization server turns into the audience; this covers clients that leave it out
 * (some do on refresh), which would otherwise get a token `/mcp` rejects.
 */
private fun defaultToMcpAudience(context: JwtEncodingContext) {
    val grant = context.getAuthorizationGrant<Authentication>() as? OAuth2AuthorizationGrantAuthenticationToken
    if (context.tokenType == OAuth2TokenType.ACCESS_TOKEN && grant?.additionalParameters?.get("resource") == null) {
        context.claims.claim(JwtClaimNames.AUD, listOf(currentIssuer() + MCP_ENDPOINT))
    }
}

/** The only grants MCP clients get: the browser login and refreshing its tokens. */
private val MCP_GRANT_TYPES = setOf(AuthorizationGrantType.AUTHORIZATION_CODE, AuthorizationGrantType.REFRESH_TOKEN)

/**
 * Spring's default registration checks, except for the scope: MCP clients such as Claude Code
 * register with the scopes the protected resource advertises, i.e. [MCP_SCOPE], which the default
 * rejects outright. Any other scope is still refused.
 *
 * Clients must also be public clients limited to [MCP_GRANT_TYPES]. Registration is open to anyone,
 * and every client is granted [MCP_SCOPE]; a client allowed e.g. token exchange could trade a user's
 * token for fresh ones indefinitely, without the user ever consenting.
 */
private val mcpClientRegistrationValidator: Consumer<OAuth2ClientRegistrationAuthenticationContext> =
    OAuth2ClientRegistrationAuthenticationValidator.DEFAULT_REDIRECT_URI_VALIDATOR
        .andThen(OAuth2ClientRegistrationAuthenticationValidator.DEFAULT_JWK_SET_URI_VALIDATOR)
        .andThen { context ->
            val registration = context.getAuthentication<OAuth2ClientRegistrationAuthenticationToken>()
                .clientRegistration
            if (registration.scopes.orEmpty().any { it != MCP_SCOPE }) {
                throw invalidRegistration(OAuth2ErrorCodes.INVALID_SCOPE, "scope")
            }
            if (registration.grantTypes.orEmpty().any { grant -> MCP_GRANT_TYPES.none { it.value == grant } }) {
                throw invalidRegistration(INVALID_CLIENT_METADATA, "grant_types")
            }
            if (registration.tokenEndpointAuthenticationMethod != ClientAuthenticationMethod.NONE.value) {
                throw invalidRegistration(INVALID_CLIENT_METADATA, "token_endpoint_auth_method")
            }
        }

private const val INVALID_CLIENT_METADATA = "invalid_client_metadata"

private fun invalidRegistration(errorCode: String, field: String) = OAuth2AuthenticationException(
    OAuth2Error(
        errorCode,
        "Invalid Client Registration: $field",
        "https://datatracker.ietf.org/doc/html/rfc7591#section-3.2.2",
    ),
)

/** How long a client stays logged in without being used. Refresh tokens rotate on every use. */
private val REFRESH_TOKEN_TIME_TO_LIVE: Duration = Duration.ofDays(30)

/**
 * Every registered client may request [MCP_SCOPE], whatever it asked for at registration, must use
 * PKCE, and gets rotating refresh tokens. Its grants and client authentication are set again here,
 * on top of what [mcpClientRegistrationValidator] lets through.
 */
private val grantMcpScopeOnRegistration = object : ObjectPostProcessor<OAuth2ClientRegistrationAuthenticationProvider> {
    override fun <O : OAuth2ClientRegistrationAuthenticationProvider> postProcess(provider: O): O {
        val defaultConverter = OAuth2ClientRegistrationRegisteredClientConverter()
        provider.setRegisteredClientConverter { registration ->
            val client = defaultConverter.convert(registration)
            RegisteredClient.from(client)
                .authorizationGrantTypes {
                    it.clear()
                    it.addAll(MCP_GRANT_TYPES)
                }
                .clientAuthenticationMethods {
                    it.clear()
                    it.add(ClientAuthenticationMethod.NONE)
                }
                .scope(MCP_SCOPE)
                .clientSettings(
                    ClientSettings.withSettings(client.clientSettings.settings).requireProofKey(true).build(),
                )
                .tokenSettings(
                    TokenSettings.withSettings(client.tokenSettings.settings)
                        .reuseRefreshTokens(false)
                        .refreshTokenTimeToLive(REFRESH_TOKEN_TIME_TO_LIVE)
                        .build(),
                )
                .build()
        }
        return provider
    }
}

/**
 * Consent is recorded per scope, and a consent without any scope counts as a denial. Authorization
 * requests that name no scope therefore ask for [MCP_SCOPE], the only one there is.
 */
private class DefaultScopeAuthorizationRequestConverter(private val delegate: AuthenticationConverter) :
    AuthenticationConverter {
    override fun convert(request: HttpServletRequest): Authentication? {
        val authentication = delegate.convert(request)
        if (authentication !is OAuth2AuthorizationCodeRequestAuthenticationToken ||
            authentication.scopes.isNotEmpty()
        ) {
            return authentication
        }
        return OAuth2AuthorizationCodeRequestAuthenticationToken(
            authentication.authorizationUri,
            authentication.clientId,
            authentication.principal as Authentication,
            authentication.redirectUri,
            authentication.state,
            setOf(MCP_SCOPE),
            authentication.additionalParameters,
        )
    }
}

/**
 * Sends a browser that starts an authorization without a Kviklet session to the frontend, which
 * logs the user in (any login method) and then resumes the authorization request.
 */
private class FrontendLoginEntryPoint(private val baseUrlResolver: BaseUrlResolver) : AuthenticationEntryPoint {
    override fun commence(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authException: AuthenticationException,
    ) {
        val query = request.queryString?.let { "?$it" } ?: ""
        response.sendRedirect("${baseUrlResolver.resolve(request)}/oauth/authorize$query")
    }
}
