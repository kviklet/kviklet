package dev.kviklet.kviklet.db

import dev.kviklet.kviklet.db.util.BaseEntity
import dev.kviklet.kviklet.service.EntityNotFound
import dev.kviklet.kviklet.service.dto.Role
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
        policies: Set<PolicyEntity> = emptySet(),
    ) : this() {
        this.id = id
        this.name = name
        this.description = description
        this.policies = policies
    }

    constructor()

    fun toDto() = Role(
        id = id,
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
    fun findById(id: String): Role {
        return roleRepository.findByIdOrNull(id)?.toDto() ?: throw EntityNotFound(
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

    fun delete(id: String) {
        roleRepository.deleteById(id)
    }

    fun update(role: Role): Role {
        val savedRole = roleRepository.save(
            RoleEntity(
                id = role.id,
                name = role.name,
                description = role.description,
                policies = role.policies.map {
                    PolicyEntity(
                        id = it.id,
                        action = it.action,
                        effect = it.effect,
                        resource = it.resource,
                    )
                }.toSet(),
            ),
        )
        return savedRole.toDto()
    }
}
