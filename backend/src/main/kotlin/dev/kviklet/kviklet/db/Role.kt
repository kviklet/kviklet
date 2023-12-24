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

    @OneToMany(cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id")
    var policies: Set<PolicyEntity> = HashSet()

    constructor(
        id: String? = null,
        name: String,
        description: String,
        policies: Set<PolicyEntity> = emptySet<PolicyEntity>().toMutableSet(),
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
class RoleAdapter(
    private val roleRepository: RoleRepository,
) {
    fun findById(id: RoleId): Role {
        return roleRepository.findByIdOrNull(id.toString())?.toDto() ?: throw EntityNotFound(
            "Role not found",
            "Role with id $id does not exist",
        )
    }

    fun findByIds(ids: List<String>): List<Role> {
        return roleRepository.findAllById(ids).map { it.toDto() }
    }

    fun findAll(): List<Role> {
        return roleRepository.findAll().map { it.toDto() }
    }

    fun create(role: Role): Role {
        return roleRepository.save(
            RoleEntity(
                name = role.name,
                description = role.description,
            ),
        ).toDto()
    }

    fun delete(id: RoleId) {
        roleRepository.deleteById(id.toString())
    }

    fun update(role: Role): Role {
        val savedRole = roleRepository.save(
            RoleEntity(
                id = role.getId(),
                name = role.name,
                description = role.description,
                policies = role.policies.map {
                    PolicyEntity(
                        id = it.id,
                        action = it.action,
                        effect = it.effect,
                        resource = it.resource,
                    )
                }.toMutableSet(),
            ),
        )
        return savedRole.toDto()
    }

    fun deleteAll() {
        roleRepository.deleteAll()
    }
}
