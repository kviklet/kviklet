// This file is not MIT licensed
package dev.kviklet.kviklet.mcp

import dev.kviklet.kviklet.service.ApiKeyService
import dev.kviklet.kviklet.service.BaseUrlResolver
import dev.kviklet.kviklet.service.ConfigService
import dev.kviklet.kviklet.service.LicenseService
import dev.kviklet.kviklet.service.RoleSyncConfigService
import dev.kviklet.kviklet.service.dto.ApiKey
import dev.kviklet.kviklet.service.dto.ApiKeyId
import dev.kviklet.kviklet.service.dto.Configuration
import dev.kviklet.kviklet.service.dto.RoleSyncConfig
import dev.kviklet.kviklet.service.dto.RoleSyncMapping
import dev.kviklet.kviklet.service.dto.SyncMode
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.ai.mcp.annotation.McpTool.McpAnnotations
import org.springframework.ai.mcp.annotation.McpToolParam
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.LocalDateTime

data class RoleSyncResult(
    val enabled: Boolean,
    val syncMode: SyncMode,
    val groupsAttribute: String,
    val mappings: List<RoleSyncMapping>,
    val url: String?,
)

data class ApiKeyResult(
    val id: String,
    val name: String,
    val owner: String,
    val createdAt: LocalDateTime,
    val expiresAt: LocalDateTime?,
    val lastUsedAt: LocalDateTime?,
    /** Only right after creation, it can't be shown again. */
    val key: String? = null,
) {
    companion object {
        fun from(apiKey: ApiKey) = ApiKeyResult(
            id = apiKey.id.toString(),
            name = apiKey.name,
            owner = apiKey.user.email,
            createdAt = apiKey.createdAt,
            expiresAt = apiKey.expiresAt,
            lastUsedAt = apiKey.lastUsedAt,
            key = apiKey.key,
        )
    }
}

data class LicenseStatus(val valid: Boolean, val validUntil: LocalDate?, val seats: UInt?)

data class ConfigResult(
    val slackUrl: String?,
    val teamsUrl: String?,
    val proxyEnabled: Boolean,
    val license: LicenseStatus,
    val url: String?,
)

/** Role sync, API keys, notifications and the proxy. */
@Component
class SettingsTools(
    private val roleSyncConfigService: RoleSyncConfigService,
    private val apiKeyService: ApiKeyService,
    private val configService: ConfigService,
    private val licenseService: LicenseService,
    private val baseUrlResolver: BaseUrlResolver,
) {

    @McpTool(
        name = "get_role_sync",
        description = "Gets how users' roles are synced from the groups the identity provider sends at login.",
        annotations = McpAnnotations(readOnlyHint = true, destructiveHint = false),
    )
    fun getRoleSync(): RoleSyncResult = roleSync(roleSyncConfigService.getConfig())

    @McpTool(
        name = "update_role_sync",
        description = "Changes the role sync. Pass only the fields to change.",
        annotations = McpAnnotations(destructiveHint = true),
    )
    fun updateRoleSync(
        @McpToolParam(required = false) enabled: Boolean?,
        @McpToolParam(
            required = false,
            description = "FULL_SYNC sets the roles to the mapped ones at every login, ADDITIVE only adds " +
                "roles, FIRST_LOGIN_ONLY sets them when the user is created",
        )
        syncMode: SyncMode?,
        @McpToolParam(required = false, description = "The claim or attribute that holds the groups")
        groupsAttribute: String?,
    ): RoleSyncResult = roleSync(roleSyncConfigService.updateConfig(enabled, syncMode, groupsAttribute))

    @McpTool(
        name = "add_role_sync_mapping",
        description = "Gives users of an identity provider group a role at login.",
        annotations = McpAnnotations(destructiveHint = false),
    )
    fun addRoleSyncMapping(
        @McpToolParam(description = "The group's name as the identity provider sends it") idpGroupName: String,
        @McpToolParam(description = "The role id") roleId: String,
    ): RoleSyncMapping = roleSyncConfigService.addMapping(idpGroupName, roleId)

    @McpTool(
        name = "delete_role_sync_mapping",
        description = "Deletes a role sync mapping.",
        annotations = McpAnnotations(destructiveHint = true),
    )
    fun deleteRoleSyncMapping(@McpToolParam(description = "The mapping id") id: String): Deleted {
        roleSyncConfigService.deleteMapping(id)
        return Deleted(id)
    }

    @McpTool(
        name = "list_api_keys",
        description = "Lists the API keys, without the keys themselves.",
        annotations = McpAnnotations(readOnlyHint = true, destructiveHint = false),
    )
    fun listApiKeys(): List<ApiKeyResult> = apiKeyService.listApiKeys().map(ApiKeyResult::from)

    @McpTool(
        name = "create_api_key",
        description = "Creates an API key that acts as the user, and returns it once. The key passes through " +
            "the model provider: tell the user, and suggest creating keys in Kviklet instead or rotating " +
            "this one if that matters.",
        annotations = McpAnnotations(destructiveHint = false),
    )
    fun createApiKey(
        @McpToolParam(description = "What the key is for") name: String,
        @McpToolParam(required = false, description = "Never expires by default") expiresInDays: Int?,
    ): ApiKeyResult = ApiKeyResult.from(apiKeyService.createApiKey(currentUser().id, name, expiresInDays))

    @McpTool(
        name = "delete_api_key",
        description = "Deletes an API key. Whatever uses it loses access.",
        annotations = McpAnnotations(destructiveHint = true),
    )
    fun deleteApiKey(@McpToolParam(description = "The API key id") id: String): Deleted {
        apiKeyService.deleteApiKey(ApiKeyId(id))
        return Deleted(id)
    }

    @McpTool(
        name = "get_config",
        description = "Gets the Slack and Teams webhooks for notifications, whether the database proxy is on, " +
            "and the license.",
        annotations = McpAnnotations(readOnlyHint = true, destructiveHint = false),
    )
    fun getConfig(): ConfigResult = config(configService.getConfiguration())

    @McpTool(
        name = "update_config",
        description = "Changes the notification webhooks and turns the database proxy on or off. Pass only the " +
            "fields to change. Turning the proxy on needs a license.",
        annotations = McpAnnotations(destructiveHint = true),
    )
    fun updateConfig(
        @McpToolParam(required = false, description = "An empty string removes it") slackUrl: String?,
        @McpToolParam(required = false, description = "An empty string removes it") teamsUrl: String?,
        @McpToolParam(required = false) proxyEnabled: Boolean?,
    ): ConfigResult = config(configService.setConfiguration(Configuration(teamsUrl, slackUrl, proxyEnabled)))

    private fun roleSync(config: RoleSyncConfig) = RoleSyncResult(
        enabled = config.enabled,
        syncMode = config.syncMode,
        groupsAttribute = config.groupsAttribute,
        mappings = config.mappings,
        url = baseUrlResolver.resolve()?.let { "$it/settings/role-sync" },
    )

    private fun config(config: Configuration): ConfigResult {
        // The active license is the one seats are enforced against. Without one, the newest shows what expired.
        val active = licenseService.getActiveLicense()
        val license = active ?: licenseService.getLicenses().maxByOrNull { it.file.createdAt }
        return ConfigResult(
            slackUrl = config.slackUrl?.takeIf { it.isNotEmpty() },
            teamsUrl = config.teamsUrl?.takeIf { it.isNotEmpty() },
            proxyEnabled = config.proxyEnabled ?: false,
            license = LicenseStatus(
                valid = active != null,
                validUntil = license?.validUntil,
                seats = license?.allowedUsers,
            ),
            url = baseUrlResolver.resolve()?.let { "$it/settings" },
        )
    }
}
