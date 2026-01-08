package dev.kviklet.kviklet.db

import dev.kviklet.kviklet.db.util.BaseEntity
import dev.kviklet.kviklet.service.dto.RoleSyncConfig
import dev.kviklet.kviklet.service.dto.RoleSyncMapping
import dev.kviklet.kviklet.service.dto.SyncMode
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Service

@Entity
@Table(name = "role_sync_config")
class RoleSyncConfigEntity(
    @Id
    val id: Int = 1,

    @Column(nullable = false)
    var enabled: Boolean = false,

    @Column(name = "sync_mode", nullable = false)
    @Enumerated(EnumType.STRING)
    var syncMode: SyncMode = SyncMode.FULL_SYNC,

    @Column(name = "groups_attribute", nullable = false)
    var groupsAttribute: String = "groups",
) {
    fun toDto(mappings: List<RoleSyncMapping> = emptyList()) = RoleSyncConfig(
        enabled = enabled,
        syncMode = syncMode,
        groupsAttribute = groupsAttribute,
        mappings = mappings,
    )
}

@Entity
@Table(name = "role_sync_mapping")
class RoleSyncMappingEntity : BaseEntity {

    @Column(name = "idp_group_name", nullable = false, unique = true)
    lateinit var idpGroupName: String

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id", nullable = false)
    lateinit var role: RoleEntity

    constructor(
        id: String? = null,
        idpGroupName: String,
        role: RoleEntity,
    ) : this() {
        this.id = id
        this.idpGroupName = idpGroupName
        this.role = role
    }

    constructor()

    fun toDto() = RoleSyncMapping(
        id = id,
        idpGroupName = idpGroupName,
        roleId = role.id!!,
        roleName = role.name,
    )
}

interface RoleSyncConfigRepository : JpaRepository<RoleSyncConfigEntity, Int>

interface RoleSyncMappingRepository : JpaRepository<RoleSyncMappingEntity, String> {
    fun findByIdpGroupNameIn(groupNames: List<String>): List<RoleSyncMappingEntity>
}

@Service
class RoleSyncConfigAdapter(
    private val configRepository: RoleSyncConfigRepository,
    private val mappingRepository: RoleSyncMappingRepository,
    private val roleRepository: RoleRepository,
) {
    companion object {
        const val DEFAULT_CONFIG_ID = 1
    }

    fun getConfig(): RoleSyncConfig {
        val config = configRepository.findById(DEFAULT_CONFIG_ID).orElseGet {
            configRepository.save(
                RoleSyncConfigEntity(
                    id = DEFAULT_CONFIG_ID,
                    enabled = false,
                    syncMode = SyncMode.FULL_SYNC,
                    groupsAttribute = "groups",
                ),
            )
        }
        val mappings = getMappings()
        return config.toDto(mappings)
    }

    fun updateConfig(
        enabled: Boolean? = null,
        syncMode: SyncMode? = null,
        groupsAttribute: String? = null,
    ): RoleSyncConfig {
        val config = configRepository.findById(DEFAULT_CONFIG_ID).orElseGet {
            RoleSyncConfigEntity(id = DEFAULT_CONFIG_ID)
        }

        enabled?.let { config.enabled = it }
        syncMode?.let { config.syncMode = it }
        groupsAttribute?.let { config.groupsAttribute = it }

        val mappings = getMappings()
        return configRepository.save(config).toDto(mappings)
    }

    fun addMapping(idpGroupName: String, roleId: String): RoleSyncMapping {
        val role = roleRepository.findById(roleId).orElseThrow {
            IllegalArgumentException("Role with id $roleId not found")
        }

        val mapping = RoleSyncMappingEntity(
            idpGroupName = idpGroupName,
            role = role,
        )

        return mappingRepository.save(mapping).toDto()
    }

    fun deleteMapping(id: String) {
        mappingRepository.deleteById(id)
    }

    fun deleteAllMappings() {
        mappingRepository.deleteAll()
    }

    fun getMappings(): List<RoleSyncMapping> = mappingRepository.findAll().map { it.toDto() }

    fun getMappingsByGroupNames(groupNames: List<String>): List<RoleSyncMapping> {
        if (groupNames.isEmpty()) return emptyList()
        return mappingRepository.findByIdpGroupNameIn(groupNames).map { it.toDto() }
    }
}
