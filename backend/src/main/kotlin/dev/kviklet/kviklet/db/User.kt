package dev.kviklet.kviklet.db

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue
import dev.kviklet.kviklet.db.util.BaseEntity
import dev.kviklet.kviklet.security.Permission
import dev.kviklet.kviklet.security.PolicyGrantedAuthority
import dev.kviklet.kviklet.security.Resource
import dev.kviklet.kviklet.security.SecuredDomainId
import dev.kviklet.kviklet.security.SecuredDomainObject
import dev.kviklet.kviklet.security.UserDetailsWithId
import dev.kviklet.kviklet.security.isAllowed
import dev.kviklet.kviklet.security.vote
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
import java.io.Serializable

@Entity
@Table(name = "user")
class UserEntity(
    @Column(nullable = true)
    var fullName: String? = null,

    @Column(nullable = true)
    var password: String? = null,

    @Column(unique = true)
    var subject: String? = null,

    @Column(unique = true)
    var email: String = "",

    @ManyToMany(cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    @JoinTable(
        name = "user_role",
        joinColumns = [JoinColumn(name = "user_id")],
        inverseJoinColumns = [JoinColumn(name = "role_id")],
    )
    var roles: Set<RoleEntity> = emptySet<RoleEntity>().toMutableSet(),
) : BaseEntity() {
    fun toDto() = User(
        id = id?.let { UserId(it) },
        fullName = fullName,
        password = password,
        subject = subject,
        email = email,
        roles = roles.map { it.toDto() }.toSet(),
    )
}

data class UserId
@JsonCreator constructor(private val id: String) : Serializable, SecuredDomainId {
    @JsonValue
    override fun toString() = id
}

// A dto for the UserEntity
data class User(
    private val id: UserId? = null,
    val fullName: String? = null,
    val password: String? = null,
    val subject: String? = null,
    val email: String = "",
    val policies: Set<Policy> = HashSet(),
    val roles: Set<Role> = HashSet(),
) : SecuredDomainObject {
    fun getAllPolicies(): Set<Policy> {
        val allPolicies = HashSet<Policy>()
        allPolicies.addAll(policies)
        roles.forEach { allPolicies.addAll(it.policies) }
        return allPolicies
    }

    override fun getId(): String? {
        return id?.toString()
    }

    override fun getDomainObjectType(): Resource {
        return Resource.USER
    }

    override fun getRelated(resource: Resource): SecuredDomainObject? {
        return when (resource) {
            Resource.USER -> this
            else -> null
        }
    }

    override fun auth(
        permission: Permission,
        userDetails: UserDetailsWithId,
        policies: List<PolicyGrantedAuthority>,
    ): Boolean {
        if (permission.resource != Resource.USER) {
            return false
        }
        // Only the user themselves are allowed to edit themselves,
        // or if they have the USER_EDIT_ROLES permission which is a special case for admins
        if (permission.action == "edit") {
            return userDetails.id == this.getId() ||
                policies.vote(Permission.USER_EDIT_ROLES, this).isAllowed()
        }
        return super.auth(permission, userDetails, policies)
    }
}

interface UserRepository : JpaRepository<UserEntity, String> {
    fun findByEmail(email: String): UserEntity?
    fun findBySubject(subject: String): UserEntity?
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

    fun findBySubject(subject: String): User? {
        val userEntity = userRepository.findBySubject(subject) ?: return null
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
            subject = user.subject,
            email = user.email,
        )
        val savedUserEntity = userRepository.save(userEntity)
        return savedUserEntity.toDto()
    }

    fun createOrUpdateUser(user: User): User {
        val userEntity = user.getId()?.let {
            userRepository.findByIdOrNull(it)
        }

        if (userEntity == null) {
            return createUser(user)
        } else {
            userEntity.fullName = user.fullName
            userEntity.password = user.password
            userEntity.subject = user.subject
            userEntity.email = user.email
            val savedUserEntity = userRepository.save(userEntity)
            return savedUserEntity.toDto()
        }
    }

    @Transactional
    fun updateUser(user: User): User {
        val userEntity = userRepository.findByIdOrNull(user.getId()) ?: throw EntityNotFound(
            "User not found",
            "User with id ${user.getId()} does not exist",
        )
        userEntity.fullName = user.fullName
        userEntity.password = user.password
        userEntity.subject = user.subject
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
