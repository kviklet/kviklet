// This file is not MIT licensed
package dev.kviklet.kviklet.mcp

import dev.kviklet.kviklet.security.Permission
import dev.kviklet.kviklet.security.PolicyGrantedAuthority
import dev.kviklet.kviklet.security.vote
import io.modelcontextprotocol.common.McpTransportContext
import io.modelcontextprotocol.server.McpServer
import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification
import io.modelcontextprotocol.server.McpStatelessSyncServer
import io.modelcontextprotocol.server.McpSyncListFilter
import io.modelcontextprotocol.spec.McpSchema
import io.modelcontextprotocol.spec.McpStatelessServerTransport
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerProperties
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.core.context.SecurityContextHolder

/**
 * Tools that only users with a permission beyond working with requests can use, and that permission.
 * Tools not listed here are shown to everyone.
 */
private val TOOL_PERMISSIONS: Map<String, Permission> = mapOf(
    "create_database_connection" to Permission.DATASOURCE_CONNECTION_CREATE,
    "create_kubernetes_connection" to Permission.DATASOURCE_CONNECTION_CREATE,
    "update_database_connection" to Permission.DATASOURCE_CONNECTION_EDIT,
    "update_kubernetes_connection" to Permission.DATASOURCE_CONNECTION_EDIT,
    "list_roles" to Permission.ROLE_GET,
    "get_role" to Permission.ROLE_GET,
    "create_role" to Permission.ROLE_EDIT,
    "update_role" to Permission.ROLE_EDIT,
    "delete_role" to Permission.ROLE_EDIT,
    "list_users" to Permission.USER_GET,
    "create_user" to Permission.USER_CREATE,
    "set_user_roles" to Permission.USER_EDIT_ROLES,
    "set_user_active" to Permission.USER_EDIT_ROLES,
    "get_role_sync" to Permission.CONFIGURATION_GET,
    "update_role_sync" to Permission.CONFIGURATION_EDIT,
    "add_role_sync_mapping" to Permission.CONFIGURATION_EDIT,
    "delete_role_sync_mapping" to Permission.CONFIGURATION_EDIT,
    "list_api_keys" to Permission.API_KEY_GET,
    "create_api_key" to Permission.API_KEY_CREATE,
    "delete_api_key" to Permission.API_KEY_EDIT,
    "get_config" to Permission.CONFIGURATION_GET,
    "update_config" to Permission.CONFIGURATION_EDIT,
)

/**
 * Leaves the configuration tools out of `tools/list` for users who lack the permission they need, so
 * the agents of most users don't carry a few dozen tools that would only ever fail. It only decides
 * what is listed: calling a hidden tool is still refused by the service's `@Policy`, as it was before.
 *
 * The user's policies come from the MCP authentication, which loaded them for this request (see
 * McpJwtAuthenticationConverter). The server runs list calls on the request thread, so the security
 * context is the caller's.
 */
class McpToolVisibilityFilter : McpSyncListFilter<McpSchema.Tool> {
    override fun isVisible(context: McpTransportContext, tool: McpSchema.Tool): Boolean {
        val permission = TOOL_PERMISSIONS[tool.name()] ?: return true
        val policies = SecurityContextHolder.getContext().authentication?.authorities
            ?.filterIsInstance<PolicyGrantedAuthority>()
            ?: return false
        return policies.vote(permission)
    }
}

/**
 * Builds the MCP server in place of Spring AI's McpServerStatelessAutoConfiguration, which is excluded
 * (see KvikletApplication): it has no hook for adding [McpToolVisibilityFilter], and its server bean
 * can't be overridden. Otherwise the server is built the way Spring AI builds a stateless servlet
 * server offering only tools, including immediate execution: tools and the filter rely on running on
 * the request thread, where the caller's security context is.
 */
@Configuration
@EnableConfigurationProperties(McpServerProperties::class)
class McpServerConfig {

    @Bean
    fun mcpStatelessSyncServer(
        transport: McpStatelessServerTransport,
        properties: McpServerProperties,
        tools: ObjectProvider<List<SyncToolSpecification>>,
    ): McpStatelessSyncServer = McpServer.sync(transport)
        .serverInfo(McpSchema.Implementation.builder(properties.name, properties.version).build())
        .capabilities(McpSchema.ServerCapabilities.builder().tools(false).build())
        .tools(tools.stream().flatMap { it.stream() }.toList())
        .addToolFilter(McpToolVisibilityFilter())
        .instructions(properties.instructions)
        .requestTimeout(properties.requestTimeout)
        .immediateExecution(true)
        .build()
}
