package dev.kviklet.kviklet.service.dto

enum class SyncMode {
    FULL_SYNC,
    ADDITIVE,
    FIRST_LOGIN_ONLY,
}

data class RoleSyncConfig(
    val enabled: Boolean,
    val syncMode: SyncMode,
    val groupsAttribute: String,
    val mappings: List<RoleSyncMapping>,
)

data class RoleSyncMapping(val id: String?, val idpGroupName: String, val roleId: String, val roleName: String)
