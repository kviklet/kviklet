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
class RoleSyncConfigService(private val roleSyncConfigAdapter: RoleSyncConfigAdapter) {
    @Policy(Permission.CONFIGURATION_GET, checkIsPresentOnly = true)
    @Transactional(readOnly = true)
    fun getConfig(): RoleSyncConfig = roleSyncConfigAdapter.getConfig()

    @Policy(Permission.CONFIGURATION_EDIT, checkIsPresentOnly = true)
    @Transactional
    fun updateConfig(
        enabled: Boolean? = null,
        syncMode: SyncMode? = null,
        groupsAttribute: String? = null,
    ): RoleSyncConfig = roleSyncConfigAdapter.updateConfig(enabled, syncMode, groupsAttribute)

    @Policy(Permission.CONFIGURATION_EDIT, checkIsPresentOnly = true)
    @Transactional
    fun addMapping(idpGroupName: String, roleId: String): RoleSyncMapping =
        roleSyncConfigAdapter.addMapping(idpGroupName, roleId)

    @Policy(Permission.CONFIGURATION_EDIT, checkIsPresentOnly = true)
    @Transactional
    fun deleteMapping(id: String) = roleSyncConfigAdapter.deleteMapping(id)
}
