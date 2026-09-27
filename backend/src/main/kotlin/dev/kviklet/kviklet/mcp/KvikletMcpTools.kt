// This file is not MIT licensed
package dev.kviklet.kviklet.mcp

import dev.kviklet.kviklet.security.UserDetailsWithId
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component

data class WhoAmIResult(val id: String, val email: String)

/**
 * The tools Kviklet's MCP server offers. They run on the request thread of the MCP call, with the
 * calling user authenticated exactly like a logged-in browser session.
 */
@Component
class KvikletMcpTools {

    @McpTool(name = "whoami", description = "Returns the Kviklet user this MCP client acts on behalf of.")
    fun whoami(): WhoAmIResult {
        val user = currentUser()
        return WhoAmIResult(id = user.id, email = user.username)
    }

    // @Policy lets calls without any authentication through, since those normally come from within
    // the application. Tools are never such calls, so they must refuse to run without a user.
    private fun currentUser(): UserDetailsWithId =
        SecurityContextHolder.getContext().authentication?.principal as? UserDetailsWithId
            ?: throw IllegalStateException("MCP tool called without an authenticated Kviklet user")
}
