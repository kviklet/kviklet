// This file is not MIT licensed
package dev.kviklet.kviklet.security.mcp

import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.security.authentication.AuthenticationProvider
import org.springframework.security.core.Authentication
import org.springframework.security.crypto.keygen.Base64StringKeyGenerator
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.ClientAuthenticationMethod
import org.springframework.security.oauth2.core.OAuth2AuthenticationException
import org.springframework.security.oauth2.core.OAuth2ErrorCodes
import org.springframework.security.oauth2.core.OAuth2RefreshToken
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenContext
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator
import org.springframework.security.web.authentication.AuthenticationConverter
import java.time.Instant
import java.util.Base64

// MCP clients are public clients: they run on the user's machine and have no secret. Spring
// Authorization Server gives public clients no refresh tokens, so they would have to send the user
// through the browser login every time the short-lived access token expires. OAuth 2.1 allows
// refresh tokens for public clients as long as they are rotated on every use, which the registered
// clients are configured for (see grantMcpScopeOnRegistration). The two classes below lift the two
// restrictions: issuing the token, and letting the client authenticate when it redeems it.

/** Issues refresh tokens to public clients too, unlike Spring's OAuth2RefreshTokenGenerator. */
class PublicClientRefreshTokenGenerator : OAuth2TokenGenerator<OAuth2RefreshToken> {
    private val keyGenerator = Base64StringKeyGenerator(Base64.getUrlEncoder().withoutPadding(), 96)

    override fun generate(context: OAuth2TokenContext): OAuth2RefreshToken? {
        if (context.tokenType != OAuth2TokenType.REFRESH_TOKEN) {
            return null
        }
        val issuedAt = Instant.now()
        val expiresAt = issuedAt.plus(context.registeredClient.tokenSettings.refreshTokenTimeToLive)
        return OAuth2RefreshToken(keyGenerator.generateKey(), issuedAt, expiresAt)
    }
}

/**
 * Recognizes a refresh token request from a public client: only a `client_id`, no secret and no
 * `Authorization` header. The refresh token itself is checked by the refresh token grant, which also
 * makes sure it was issued to this client.
 */
class PublicClientRefreshTokenAuthenticationConverter : AuthenticationConverter {
    override fun convert(request: HttpServletRequest): Authentication? {
        if (request.method != HttpMethod.POST.name() ||
            request.getParameter(OAuth2ParameterNames.GRANT_TYPE) != AuthorizationGrantType.REFRESH_TOKEN.value ||
            request.getHeader(HttpHeaders.AUTHORIZATION) != null ||
            request.getParameter(OAuth2ParameterNames.CLIENT_SECRET) != null
        ) {
            return null
        }
        val clientIds = request.getParameterValues(OAuth2ParameterNames.CLIENT_ID)
        if (clientIds == null || clientIds.size != 1 || clientIds[0].isBlank()) {
            return null
        }
        return OAuth2ClientAuthenticationToken(
            clientIds[0],
            ClientAuthenticationMethod.NONE,
            null,
            mapOf(OAuth2ParameterNames.GRANT_TYPE to AuthorizationGrantType.REFRESH_TOKEN.value),
        )
    }
}

/** Authenticates the public client of a refresh token request recognized by the converter above. */
class PublicClientRefreshTokenAuthenticationProvider(
    private val registeredClientRepository: RegisteredClientRepository,
) : AuthenticationProvider {
    override fun authenticate(authentication: Authentication): Authentication? {
        val clientAuthentication = authentication as OAuth2ClientAuthenticationToken
        if (clientAuthentication.clientAuthenticationMethod != ClientAuthenticationMethod.NONE ||
            clientAuthentication.additionalParameters[OAuth2ParameterNames.GRANT_TYPE] !=
            AuthorizationGrantType.REFRESH_TOKEN.value
        ) {
            return null
        }
        val registeredClient = registeredClientRepository.findByClientId(clientAuthentication.principal.toString())
        if (registeredClient == null ||
            ClientAuthenticationMethod.NONE !in registeredClient.clientAuthenticationMethods ||
            AuthorizationGrantType.REFRESH_TOKEN !in registeredClient.authorizationGrantTypes
        ) {
            throw OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_CLIENT)
        }
        return OAuth2ClientAuthenticationToken(registeredClient, ClientAuthenticationMethod.NONE, null)
    }

    override fun supports(authentication: Class<*>): Boolean =
        OAuth2ClientAuthenticationToken::class.java.isAssignableFrom(authentication)
}
