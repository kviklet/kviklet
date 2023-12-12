package dev.kviklet.kviklet.db

import dev.kviklet.kviklet.db.util.BaseEntity
import dev.kviklet.kviklet.service.EntityNotFound
import dev.kviklet.kviklet.service.dto.Policy
import dev.kviklet.kviklet.service.dto.Role
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.JoinTable
import jakarta.persistence.ManyToMany
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Entity
@Table(name = "user")
data class UserEntity(
    @Column(nullable = true)
    var fullName: String? = null,

    @Column(nullable = true)
    var password: String? = null,

    @Column(unique = true)
    var googleId: String? = null,

    @Column(unique = true)
    var email: String = "",

    @ManyToMany(cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    @JoinTable(
        name = "user_role",
        joinColumns = [JoinColumn(name = "user_id")],
        inverseJoinColumns = [JoinColumn(name = "role_id")],
    )
    var roles: Set<RoleEntity> = HashSet(),
) : BaseEntity() {
    fun toDto() = User(
        id = id,
        fullName = fullName,
        password = password,
        googleId = googleId,
        email = email,
        roles = roles.map { it.toDto() }.toSet(),
    )
}

// A dto for the UserEntity
data class User(
    val id: String? = "",
    val fullName: String? = null,
    val password: String? = null,
    val googleId: String? = null,
    val email: String = "",
    val policies: Set<Policy> = HashSet(),
    val roles: Set<Role> = HashSet(),
) {
    fun getAllPolicies(): Set<Policy> {
        val allPolicies = HashSet<Policy>()
        allPolicies.addAll(policies)
        roles.forEach { allPolicies.addAll(it.policies) }
        return allPolicies
    }
}

interface UserRepository : JpaRepository<UserEntity, String> {
    fun findByEmail(email: String): UserEntity?
    fun findByGoogleId(googleId: String): UserEntity?
}

@Service
class UserAdapter(
    private val userRepository: UserRepository,
    private val roleRepository: RoleRepository,
) {
    fun findByEmail(email: String): User? {
        val userEntity = userRepository.findByEmail(email) ?: return null
        return userEntity.toDto()
    }

    fun findByGoogleId(googleId: String): User? {
        val userEntity = userRepository.findByGoogleId(googleId) ?: return null
        return userEntity.toDto()
    }

    fun findById(id: String): User {
        val userEntity = userRepository.findByIdOrNull(id) ?: throw EntityNotFound(
            "User not found",
            "User with id $id does not exist",
        )
        return userEntity.toDto()
    }

    fun createUser(user: User): User {
        val userEntity = UserEntity(
            fullName = user.fullName,
            password = user.password,
            googleId = user.googleId,
            email = user.email,
        )
        val savedUserEntity = userRepository.save(userEntity)
        return savedUserEntity.toDto()
    }

    fun createOrUpdateUser(user: User): User {
        val userEntity = userRepository.findByIdOrNull(user.id)

        if (userEntity == null) {
            return createUser(user)
        } else {
            userEntity.fullName = user.fullName
            userEntity.password = user.password
            userEntity.googleId = user.googleId
            userEntity.email = user.email
            val savedUserEntity = userRepository.save(userEntity)
            return savedUserEntity.toDto()
        }
    }

    @Transactional
    fun updateUser(user: User): User {
        val userEntity = userRepository.findByIdOrNull(user.id) ?: throw EntityNotFound(
            "User not found",
            "User with id ${user.id} does not exist",
        )
        userEntity.fullName = user.fullName
        userEntity.password = user.password
        userEntity.googleId = user.googleId
        userEntity.email = user.email
        // update Roles
        user.roles.let { newRoleIds ->
            val newRoles = roleRepository.findAllById(newRoleIds.map { it.getId() }.toSet())
            userEntity.roles = newRoles.toMutableSet()
        }
        val savedUserEntity = userRepository.save(userEntity)

        return savedUserEntity.toDto()
    }

    fun deleteUser(id: String) {
        userRepository.deleteById(id)
    }

    fun deleteAll() {
        userRepository.deleteAll()
    }

    fun listUsers(): List<User> {
        val userEntities = userRepository.findAll()
        return userEntities.map { it.toDto() }
    }
}
