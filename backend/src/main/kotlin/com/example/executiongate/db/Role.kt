package com.example.executiongate.db

import com.example.executiongate.service.EntityNotFound
import com.example.executiongate.service.dto.Role
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import jakarta.transaction.Transactional
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service

@Entity
@Table(name = "role")
data class RoleEntity(
    @Id
    @Column(nullable = false)
    val id: String = "",
    @Column(nullable = false)
    val description: String = "",
    @Column(nullable = false)
    val managedByIdp: Boolean = false,

    @OneToMany(cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id")
    var policies: Set<PolicyEntity> = HashSet(),
) {
    fun toDto() = Role(
        id = id,
        description = description,
        policies = policies.map { it.toDto() }.toSet(),
    )
}

interface RoleRepository : JpaRepository<RoleEntity, String> {

    fun findAllByIdIn(names: Collection<String>): List<RoleEntity>

    fun findAllByIdInAndManagedByIdpIsTrue(names: Collection<String>): List<RoleEntity>
}

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

    @Transactional
    fun findByIds(ids: Collection<String>): List<Role> {
        return roleRepository.findAllById(ids).map { it.toDto() }
    }

    @Transactional
    fun findByNames(names: Collection<String>): List<Role> {
        return roleRepository.findAllByIdIn(names).map { it.toDto() }
    }

    @Transactional
    fun findIdpRolesByNames(names: Collection<String>): List<Role> {
        return roleRepository.findAllByIdInAndManagedByIdpIsTrue(names).map { it.toDto() }
    }

    @Transactional
    fun findAll(): List<Role> {
        return roleRepository.findAll().map { it.toDto() }
    }

    fun create(role: Role): Role {
        return roleRepository.save(
            RoleEntity(
                id = role.id,
                description = role.description,
            ),
        ).toDto()
    }

    fun delete(id: String) {
        roleRepository.deleteById(id)
    }
}
