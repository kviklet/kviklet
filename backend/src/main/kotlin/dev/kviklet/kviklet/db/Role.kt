package dev.kviklet.kviklet.db

import dev.kviklet.kviklet.db.util.BaseEntity
import dev.kviklet.kviklet.service.EntityNotFound
import dev.kviklet.kviklet.service.dto.Role
import dev.kviklet.kviklet.service.dto.RoleId
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service

@Entity
@Table(name = "role")
class RoleEntity : BaseEntity {

    @Column(nullable = false)
    lateinit var name: String

    @Column(nullable = false)
    lateinit var description: String

    @OneToMany(cascade = [CascadeType.ALL], fetch = FetchType.LAZY, orphanRemoval = true)
    @JoinColumn(name = "role_id")
    var policies: MutableSet<PolicyEntity> = HashSet()

    constructor(
        id: String? = null,
        name: String,
        description: String,
        policies: MutableSet<PolicyEntity> = emptySet<PolicyEntity>().toMutableSet(),
    ) : this() {
        this.id = id
        this.name = name
        this.description = description
        this.policies = policies
    }

    constructor()

    fun toDto() = Role(
        id = id?.let { RoleId(it) },
        name = name,
        description = description,
        policies = policies.map { it.toDto() }.toSet(),
    )
}

interface RoleRepository : JpaRepository<RoleEntity, String>

@Service
class RoleAdapter(private val roleRepository: RoleRepository) {
    fun findById(id: RoleId): Role = roleRepository.findByIdOrNull(id.toString())?.toDto() ?: throw EntityNotFound(
        "Role not found",
        "Role with id $id does not exist",
    )

    fun findByIds(ids: List<String>): List<Role> = roleRepository.findAllById(ids).map { it.toDto() }

    fun findAll(): List<Role> = roleRepository.findAll().map { it.toDto() }

    fun create(role: Role): Role = roleRepository.save(
        RoleEntity(
            name = role.name,
            description = role.description,
            policies = role.policies.map {
                PolicyEntity(
                    action = it.action,
                    effect = it.effect,
                    resource = it.resource,
                )
            }.toMutableSet(),
        ),
    ).toDto()

    fun delete(id: RoleId) {
        roleRepository.deleteById(id.toString())
    }

    fun update(role: Role): Role {
        val existingRoleEntity = roleRepository.findById(role.getId()!!).orElseThrow {
            EntityNotFound(
                "Role not found",
                "Role with id ${role.getId()} does not exist",
            )
        }

        existingRoleEntity.name = role.name
        existingRoleEntity.description = role.description
        // Create a map of existing policies based on their unique fields
        val existingPoliciesMap = existingRoleEntity.policies.associateBy { Triple(it.action, it.effect, it.resource) }
        val newPoliciesMap = role.policies.associateBy { Triple(it.action, it.effect, it.resource) }

        // Identify policies to remove
        val policiesToRemove = existingRoleEntity.policies.filter {
            !newPoliciesMap.containsKey(Triple(it.action, it.effect, it.resource))
        }

        // Identify policies to add
        val policiesToAdd = role.policies.filter {
            !existingPoliciesMap.containsKey(
                Triple(it.action, it.effect, it.resource),
            )
        }
            .map {
                PolicyEntity(
                    id = it.id,
                    action = it.action,
                    effect = it.effect,
                    resource = it.resource,
                )
            }

        // Remove old policies
        existingRoleEntity.policies.removeAll(policiesToRemove)

        // Add new policies
        existingRoleEntity.policies.addAll(policiesToAdd)

        val savedRole = roleRepository.save(existingRoleEntity)
        return savedRole.toDto()
    }

    fun deleteAll() {
        roleRepository.deleteAll()
    }
}
