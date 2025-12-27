package dev.kviklet.kviklet.db

import dev.kviklet.kviklet.db.util.BaseEntity
import dev.kviklet.kviklet.service.dto.RoleSyncConfig
import dev.kviklet.kviklet.service.dto.RoleSyncMapping
import dev.kviklet.kviklet.service.dto.SyncMode
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Service

@Entity
@Table(name = "role_sync_config")
class RoleSyncConfigEntity : BaseEntity {

    @Column(nullable = false)
    var enabled: Boolean = false

    @Column(name = "sync_mode", nullable = false)
    @Enumerated(EnumType.STRING)
    var syncMode: SyncMode = SyncMode.FULL_SYNC

    @Column(name = "groups_attribute", nullable = false)
    var groupsAttribute: String = "groups"

    @OneToMany(mappedBy = "config", cascade = [CascadeType.ALL], fetch = FetchType.LAZY, orphanRemoval = true)
    var mappings: MutableSet<RoleSyncMappingEntity> = mutableSetOf()

    constructor(
        id: String? = null,
        enabled: Boolean = false,
        syncMode: SyncMode = SyncMode.FULL_SYNC,
        groupsAttribute: String = "groups",
    ) : this() {
        this.id = id
        this.enabled = enabled
        this.syncMode = syncMode
        this.groupsAttribute = groupsAttribute
    }

    constructor()

    fun toDto() = RoleSyncConfig(
        enabled = enabled,
        syncMode = syncMode,
        groupsAttribute = groupsAttribute,
        mappings = mappings.map { it.toDto() },
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "config_id")
    var config: RoleSyncConfigEntity? = null

    constructor(
        id: String? = null,
        idpGroupName: String,
        role: RoleEntity,
        config: RoleSyncConfigEntity? = null,
    ) : this() {
        this.id = id
        this.idpGroupName = idpGroupName
        this.role = role
        this.config = config
    }

    constructor()

    fun toDto() = RoleSyncMapping(
        id = id,
        idpGroupName = idpGroupName,
        roleId = role.id!!,
        roleName = role.name,
    )
}

interface RoleSyncConfigRepository : JpaRepository<RoleSyncConfigEntity, String>

interface RoleSyncMappingRepository : JpaRepository<RoleSyncMappingEntity, String>

@Service
class RoleSyncConfigAdapter(
    private val configRepository: RoleSyncConfigRepository,
    private val mappingRepository: RoleSyncMappingRepository,
    private val roleRepository: RoleRepository,
) {
    companion object {
        const val DEFAULT_CONFIG_ID = "default"
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
        return config.toDto()
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

        return configRepository.save(config).toDto()
    }

    fun addMapping(idpGroupName: String, roleId: String): RoleSyncMapping {
        val config = configRepository.findById(DEFAULT_CONFIG_ID).orElseThrow {
            IllegalStateException("Role sync config not found")
        }

        val role = roleRepository.findById(roleId).orElseThrow {
            IllegalArgumentException("Role with id $roleId not found")
        }

        val mapping = RoleSyncMappingEntity(
            idpGroupName = idpGroupName,
            role = role,
            config = config,
        )

        return mappingRepository.save(mapping).toDto()
    }

    fun deleteMapping(id: String) {
        mappingRepository.deleteById(id)
    }

    fun deleteAllMappings() {
        mappingRepository.deleteAll()
    }

    fun getMappings(): List<RoleSyncMapping> {
        val config = configRepository.findById(DEFAULT_CONFIG_ID).orElse(null) ?: return emptyList()
        return config.mappings.map { it.toDto() }
    }

    fun getMappingsByGroupNames(groupNames: List<String>): List<RoleSyncMapping> {
        val config = configRepository.findById(DEFAULT_CONFIG_ID).orElse(null) ?: return emptyList()
        return config.mappings
            .filter { groupNames.contains(it.idpGroupName) }
            .map { it.toDto() }
    }
}
