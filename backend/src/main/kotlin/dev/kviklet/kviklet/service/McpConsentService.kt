// This file is not MIT licensed
package dev.kviklet.kviklet.service

import dev.kviklet.kviklet.security.NoPolicy
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository
import org.springframework.stereotype.Service

data class McpConsentRequest(
    val clientId: String,
    val clientName: String,
    val redirectUri: String,
    val scopes: Set<String>,
)

@Service
class McpConsentService(
    private val authorizationService: OAuth2AuthorizationService,
    private val registeredClientRepository: RegisteredClientRepository,
) {
    private val stateTokenType = OAuth2TokenType(OAuth2ParameterNames.STATE)

    /**
     * What an MCP client waiting for the user's consent asked for, identified by the consent `state`
     * the authorization server handed the consent page. Only the user the authorization belongs to
     * gets to see it, which is also why this needs no permission.
     */
    @NoPolicy
    fun getPendingConsent(state: String, userId: String): McpConsentRequest {
        val authorization = authorizationService.findByToken(state, stateTokenType)
        if (authorization == null || authorization.principalName != userId) {
            throw EntityNotFound("Authorization request not found", "No pending authorization request for this state")
        }
        val client = registeredClientRepository.findById(authorization.registeredClientId)
            ?: throw EntityNotFound("Client not found", "The client of this authorization request no longer exists")
        val request: OAuth2AuthorizationRequest =
            authorization.getAttribute(OAuth2AuthorizationRequest::class.java.name)
                ?: throw EntityNotFound("Authorization request not found", "The authorization request is incomplete")
        return McpConsentRequest(
            clientId = client.clientId,
            clientName = client.clientName,
            redirectUri = request.redirectUri ?: client.redirectUris.first(),
            scopes = request.scopes,
        )
    }
}
