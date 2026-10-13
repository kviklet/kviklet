package dev.kviklet.kviklet.security

import dev.kviklet.kviklet.security.mcp.McpAuthentication
import dev.kviklet.kviklet.service.dto.RequestSource
import org.springframework.security.core.context.SecurityContextHolder

/**
 * How the current caller reached Kviklet, taken from their authentication: an API key, an MCP client,
 * or otherwise a login session in the web UI. The services call this when the caller doesn't name a
 * source, and pass the result to the database layer.
 *
 * Calls without an authentication come from within the application, which the policy checks let
 * through too (see MyAuthorizationManager). They count as the web UI, so work that has a source of its
 * own, like statements run through the proxy, must pass it explicitly.
 */
fun currentRequestSource(): RequestSource = when (SecurityContextHolder.getContext().authentication) {
    is ApiKeyAuthentication -> RequestSource.API_KEY
    is McpAuthentication -> RequestSource.MCP
    else -> RequestSource.WEB
}
