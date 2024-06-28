package dev.kviklet.kviklet.service

import dev.kviklet.kviklet.db.RoleAdapter
import dev.kviklet.kviklet.db.User
import dev.kviklet.kviklet.db.UserAdapter
import dev.kviklet.kviklet.db.UserId
import dev.kviklet.kviklet.security.Permission
import dev.kviklet.kviklet.security.Policy
import dev.kviklet.kviklet.service.dto.Role
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UserService(
    private val userAdapter: UserAdapter,
    private val passwordEncoder: PasswordEncoder,
    private val roleAdapter: RoleAdapter,
) {

    @Transactional
    @Policy(Permission.USER_CREATE)
    fun createUser(email: String, password: String, fullName: String): User {
        val existingUser: User? = userAdapter.findByEmail(email)

        if (existingUser != null) {
            throw EmailAlreadyExistsException(email)
        }
        val defaultRole = roleAdapter.findById(Role.DEFAULT_ROLE_ID)

        val user = User(
            email = email,
            fullName = fullName,
            password = passwordEncoder.encode(password),
            roles = setOf(defaultRole),
        )
        return userAdapter.createUser(user)
    }

    @Transactional
    @Policy(Permission.USER_EDIT_ROLES)
    fun updateUserWithRoles(
        userId: UserId,
        email: String? = null,
        fullName: String? = null,
        password: String? = null,
        roles: List<String>,
    ): User {
        val user = userAdapter.findById(userId.toString())
        val newRoles = roleAdapter.findByIds(roles).toSet()
        if (newRoles.none { it.isDefault }) {
            throw IllegalArgumentException("Every User has to keep the default role")
        }
        val updatedUser = user.copy(
            email = email ?: user.email,
            fullName = fullName ?: user.fullName,
            password = password?.let { passwordEncoder.encode(it) } ?: user.password,
            roles = newRoles,
        )
        val savedUser = userAdapter.updateUser(updatedUser)
        return savedUser
    }

    @Transactional
    @Policy(Permission.USER_EDIT)
    fun updateUser(userId: UserId, email: String? = null, fullName: String? = null, password: String? = null): User {
        val user = userAdapter.findById(userId.toString())
        if (user.subject != null && (email != null || password != null)) {
            throw Exception("Cannot change email or password for OAuth users")
        }
        val updatedUser = user.copy(
            email = email ?: user.email,
            fullName = fullName ?: user.fullName,
            password = password?.let { passwordEncoder.encode(it) } ?: user.password,
        )
        val savedUser = userAdapter.updateUser(updatedUser)
        return savedUser
    }

    @Transactional(readOnly = true)
    @Policy(Permission.USER_GET)
    fun getUsers(): List<User> = userAdapter.listUsers()

    @Transactional(readOnly = true)
    @Policy(Permission.USER_GET)
    fun getUser(id: UserId): User = userAdapter.findById(id.toString())

    @Transactional
    @Policy(Permission.USER_CREATE)
    fun deleteUser(userId: UserId) {
        userAdapter.deleteUser(userId.toString())
    }
}

class EmailAlreadyExistsException(email: String) : Exception("User with email $email already exists")
