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
import org.springframework.security.config.ObjectPostProcessor
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization.OAuth2AuthorizationServerConfigurer
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.Authentication
import org.springframework.security.core.AuthenticationException
import org.springframework.security.oauth2.jwt.JwtClaimNames
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationConsentService
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationProvider
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationToken
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationGrantAuthenticationToken
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientRegistrationAuthenticationProvider
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository
import org.springframework.security.oauth2.server.authorization.converter.OAuth2ClientRegistrationRegisteredClientConverter
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer
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
import java.util.UUID

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
) {

    @Bean
    fun registeredClientRepository(jdbcOperations: JdbcOperations): RegisteredClientRepository =
        JdbcRegisteredClientRepository(jdbcOperations)

    @Bean
    fun authorizationService(
        jdbcOperations: JdbcOperations,
        registeredClientRepository: RegisteredClientRepository,
    ): OAuth2AuthorizationService = JdbcOAuth2AuthorizationService(jdbcOperations, registeredClientRepository)

    @Bean
    fun authorizationConsentService(
        jdbcOperations: JdbcOperations,
        registeredClientRepository: RegisteredClientRepository,
    ): OAuth2AuthorizationConsentService =
        JdbcOAuth2AuthorizationConsentService(jdbcOperations, registeredClientRepository)

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
     * Tokens are audience-restricted to the MCP endpoint. Clients name it in the `resource`
     * parameter, which the MCP authorization server turns into the audience; this covers clients
     * that leave it out, which would otherwise get a token `/mcp` rejects.
     */
    @Bean
    fun mcpAudienceCustomizer(): OAuth2TokenCustomizer<JwtEncodingContext> = OAuth2TokenCustomizer { context ->
        val grant = context.getAuthorizationGrant<Authentication>() as? OAuth2AuthorizationGrantAuthenticationToken
        if (context.tokenType == OAuth2TokenType.ACCESS_TOKEN && grant?.additionalParameters?.get("resource") == null) {
            context.claims.claim(JwtClaimNames.AUD, listOf(currentIssuer() + MCP_ENDPOINT))
        }
    }

    @Bean
    @Order(-2)
    fun mcpAuthorizationServerFilterChain(http: HttpSecurity): SecurityFilterChain {
        // The authorization server's endpoints are only known once the MCP configurer has set it up
        // while the chain is built, so they are looked up when the first request is matched.
        val endpointsMatcher by lazy {
            http.getConfigurer(OAuth2AuthorizationServerConfigurer::class.java).endpointsMatcher
        }
        http.securityMatcher(RequestMatcher { endpointsMatcher.matches(it) })
        http.with(McpAuthorizationServerConfigurer.mcpAuthorizationServer()) { mcp ->
            mcp.authorizationServer { authServer ->
                authServer.authorizationEndpoint { endpoint ->
                    endpoint.consentPage(MCP_CONSENT_ENDPOINT)
                    endpoint.authorizationRequestConverters { converters ->
                        converters.replaceAll(::DefaultScopeAuthorizationRequestConverter)
                    }
                }
                // Added after the MCP configurer's own post-processors, so these win.
                authServer.addObjectPostProcessor(alwaysRequireConsent)
                authServer.addObjectPostProcessor(grantMcpScopeOnRegistration)
            }
        }

        addPublicUrlAndLicenseFilters(http)
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
            }
            mcp.oauth2ResourceServer { resourceServer ->
                resourceServer.jwt { it.jwtAuthenticationConverter(McpJwtAuthenticationConverter(userAdapter)) }
            }
        }
        addPublicUrlAndLicenseFilters(http)
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

    private fun addPublicUrlAndLicenseFilters(http: HttpSecurity) {
        val pathPrefix = if (applicationProperties.inDocker) "/api" else ""
        http.addFilterBefore(ForwardedHeaderFilter(), WebAsyncManagerIntegrationFilter::class.java)
        http.addFilterBefore(McpPublicUrlFilter(pathPrefix), WebAsyncManagerIntegrationFilter::class.java)
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
 * Every registered client may request [MCP_SCOPE], whatever it asked for at registration, and must
 * use PKCE.
 */
private val grantMcpScopeOnRegistration = object : ObjectPostProcessor<OAuth2ClientRegistrationAuthenticationProvider> {
    override fun <O : OAuth2ClientRegistrationAuthenticationProvider> postProcess(provider: O): O {
        val defaultConverter = OAuth2ClientRegistrationRegisteredClientConverter()
        provider.setRegisteredClientConverter { registration ->
            val client = defaultConverter.convert(registration)
            RegisteredClient.from(client)
                .scope(MCP_SCOPE)
                .clientSettings(
                    ClientSettings.withSettings(client.clientSettings.settings).requireProofKey(true).build(),
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
