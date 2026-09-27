// This file is not MIT licensed
package dev.kviklet.kviklet.controller

import dev.kviklet.kviklet.security.CurrentUser
import dev.kviklet.kviklet.security.EnterpriseOnly
import dev.kviklet.kviklet.security.UserDetailsWithId
import dev.kviklet.kviklet.security.mcp.MCP_CONSENT_ENDPOINT
import dev.kviklet.kviklet.service.BaseUrlResolver
import dev.kviklet.kviklet.service.McpConsentRequest
import dev.kviklet.kviklet.service.McpConsentService
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.net.URI

data class McpConsentResponse(
    val clientId: String,
    val clientName: String,
    val redirectUri: String,
    val scopes: Set<String>,
) {
    companion object {
        fun fromDto(dto: McpConsentRequest) = McpConsentResponse(
            clientId = dto.clientId,
            clientName = dto.clientName,
            redirectUri = dto.redirectUri,
            scopes = dto.scopes,
        )
    }
}

@RestController
@Tag(name = "MCP OAuth")
class McpOAuthController(
    private val baseUrlResolver: BaseUrlResolver,
    private val mcpConsentService: McpConsentService,
) {
    /** Where the authorization server asks for consent; the consent screen is part of the frontend. */
    @GetMapping(MCP_CONSENT_ENDPOINT)
    @EnterpriseOnly("MCP server")
    fun consentPage(request: HttpServletRequest): ResponseEntity<Void> = ResponseEntity.status(HttpStatus.FOUND)
        .location(URI("${baseUrlResolver.resolve(request)}/oauth/consent?${request.queryString ?: ""}"))
        .build()

    @GetMapping("$MCP_CONSENT_ENDPOINT/details")
    @EnterpriseOnly("MCP server")
    fun consentDetails(@RequestParam state: String, @CurrentUser user: UserDetailsWithId): McpConsentResponse =
        McpConsentResponse.fromDto(mcpConsentService.getPendingConsent(state, user.id))
}
