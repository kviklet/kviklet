package dev.kviklet.kviklet.controller

import dev.kviklet.kviklet.security.EnterpriseOnly
import dev.kviklet.kviklet.service.RoleSyncConfigService
import dev.kviklet.kviklet.service.dto.RoleSyncConfig
import dev.kviklet.kviklet.service.dto.RoleSyncMapping
import dev.kviklet.kviklet.service.dto.SyncMode
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class RoleSyncConfigResponse(
    val enabled: Boolean,
    val syncMode: SyncMode,
    val groupsAttribute: String,
    val mappings: List<RoleSyncMappingResponse>,
) {
    companion object {
        fun fromDto(config: RoleSyncConfig): RoleSyncConfigResponse = RoleSyncConfigResponse(
            enabled = config.enabled,
            syncMode = config.syncMode,
            groupsAttribute = config.groupsAttribute,
            mappings = config.mappings.map { RoleSyncMappingResponse.fromDto(it) },
        )
    }
}

data class RoleSyncMappingResponse(
    val id: String,
    val idpGroupName: String,
    val roleId: String,
    val roleName: String,
) {
    companion object {
        fun fromDto(mapping: RoleSyncMapping): RoleSyncMappingResponse = RoleSyncMappingResponse(
            id = mapping.id ?: "",
            idpGroupName = mapping.idpGroupName,
            roleId = mapping.roleId,
            roleName = mapping.roleName,
        )
    }
}

data class UpdateRoleSyncConfigRequest(
    val enabled: Boolean? = null,
    val syncMode: SyncMode? = null,
    val groupsAttribute: String? = null,
)

data class AddRoleSyncMappingRequest(val idpGroupName: String, val roleId: String)

@RestController
@Validated
@RequestMapping("/config/role-sync")
@Tag(
    name = "Role Sync Config",
    description = "Configure role synchronization from identity providers.",
)
class RoleSyncConfigController(private val roleSyncConfigService: RoleSyncConfigService) {
    @GetMapping("/")
    @EnterpriseOnly(feature = "Role Synchronization")
    fun getConfig(): ResponseEntity<RoleSyncConfigResponse> {
        val config = roleSyncConfigService.getConfig()
        return ResponseEntity.ok(RoleSyncConfigResponse.fromDto(config))
    }

    @PutMapping("/")
    @EnterpriseOnly(feature = "Role Synchronization")
    fun updateConfig(@RequestBody request: UpdateRoleSyncConfigRequest): ResponseEntity<RoleSyncConfigResponse> {
        val config = roleSyncConfigService.updateConfig(
            enabled = request.enabled,
            syncMode = request.syncMode,
            groupsAttribute = request.groupsAttribute,
        )
        return ResponseEntity.ok(RoleSyncConfigResponse.fromDto(config))
    }

    @PostMapping("/mappings")
    @EnterpriseOnly(feature = "Role Synchronization")
    fun addMapping(@RequestBody request: AddRoleSyncMappingRequest): ResponseEntity<RoleSyncMappingResponse> {
        val mapping = roleSyncConfigService.addMapping(
            idpGroupName = request.idpGroupName,
            roleId = request.roleId,
        )
        return ResponseEntity.ok(RoleSyncMappingResponse.fromDto(mapping))
    }

    @DeleteMapping("/mappings/{id}")
    @EnterpriseOnly(feature = "Role Synchronization")
    fun deleteMapping(@PathVariable id: String): ResponseEntity<Unit> {
        roleSyncConfigService.deleteMapping(id)
        return ResponseEntity.noContent().build()
    }
}
