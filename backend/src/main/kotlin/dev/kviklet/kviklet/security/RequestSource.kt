package dev.kviklet.kviklet.security

import dev.kviklet.kviklet.security.mcp.McpAuthentication
import dev.kviklet.kviklet.service.dto.RequestSource
import org.springframework.security.core.context.SecurityContextHolder

/**
 * How the current caller reached Kviklet. Anything that isn't an API key or an MCP client, including
 * work done without an authentication, counts as the web UI.
 */
fun currentRequestSource(): RequestSource = when (SecurityContextHolder.getContext().authentication) {
    is ApiKeyAuthentication -> RequestSource.API_KEY
    is McpAuthentication -> RequestSource.MCP
    else -> RequestSource.WEB
}
