// This file is not MIT licensed
package dev.kviklet.kviklet.service

import dev.kviklet.kviklet.db.RoleSyncConfigAdapter
import dev.kviklet.kviklet.security.Permission
import dev.kviklet.kviklet.security.Policy
import dev.kviklet.kviklet.service.dto.RoleSyncConfig
import dev.kviklet.kviklet.service.dto.RoleSyncMapping
import dev.kviklet.kviklet.service.dto.SyncMode
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class RoleSyncConfigService(
    private val roleSyncConfigAdapter: RoleSyncConfigAdapter,
    private val eventStreamingService: EventStreamingService? = null,
) {
    @Policy(Permission.CONFIGURATION_GET, checkIsPresentOnly = true)
    @Transactional(readOnly = true)
    fun getConfig(): RoleSyncConfig = roleSyncConfigAdapter.getConfig()

    @Policy(Permission.CONFIGURATION_EDIT, checkIsPresentOnly = true)
    @Transactional
    fun updateConfig(
        enabled: Boolean? = null,
        syncMode: SyncMode? = null,
        groupsAttribute: String? = null,
    ): RoleSyncConfig {
        val before = roleSyncConfigAdapter.getConfig()
        val after = roleSyncConfigAdapter.updateConfig(enabled, syncMode, groupsAttribute)
        if (before !=
            after
        ) {
            eventStreamingService?.emit(
                "role_sync.configuration_changed",
                "configuration",
                fields = mapOf(
                    "before" to mapOf("enabled" to before.enabled, "sync_mode" to before.syncMode.name),
                    "after" to mapOf("enabled" to after.enabled, "sync_mode" to after.syncMode.name),
                    "groups_attribute_changed" to (before.groupsAttribute != after.groupsAttribute),
                ),
            )
        }
        return after
    }

    @Policy(Permission.CONFIGURATION_EDIT, checkIsPresentOnly = true)
    @Transactional
    fun addMapping(idpGroupName: String, roleId: String): RoleSyncMapping =
        roleSyncConfigAdapter.addMapping(idpGroupName, roleId).also {
            eventStreamingService?.emit(
                "role_sync.mapping_created",
                "configuration",
                fields = mapOf("mapping_id" to it.id, "role_id" to roleId),
            )
        }

    @Policy(Permission.CONFIGURATION_EDIT, checkIsPresentOnly = true)
    @Transactional
    fun deleteMapping(id: String) {
        roleSyncConfigAdapter.deleteMapping(id)
        eventStreamingService?.emit("role_sync.mapping_deleted", "configuration", fields = mapOf("mapping_id" to id))
    }
}
