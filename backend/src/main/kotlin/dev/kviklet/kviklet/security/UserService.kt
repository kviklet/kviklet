package dev.kviklet.kviklet.security

import dev.kviklet.kviklet.db.RoleAdapter
import dev.kviklet.kviklet.db.User
import dev.kviklet.kviklet.db.UserAdapter
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UserService(
    private val userAdapter: UserAdapter,
    private val passwordEncoder: PasswordEncoder,
    private val roleAdapter: RoleAdapter,
) {

    fun createUser(email: String, password: String, fullName: String): User {
        val user = User(
            email = email,
            fullName = fullName,
            password = passwordEncoder.encode(password),
        )
        return userAdapter.createUser(user)
    }

    @Transactional
    fun updateUser(
        id: String,
        email: String? = null,
        fullName: String? = null,
        roles: List<String>? = null,
        password: String? = null,
    ): User {
        val user = userAdapter.findById(id)
        // Update user details if present in the request
        val updatedUser = user.copy(
            email = email ?: user.email,
            fullName = fullName ?: user.fullName,
            roles = (roles?.let { roleAdapter.findByIds(it) })?.toSet() ?: user.roles,
            password = password?.let { passwordEncoder.encode(it) } ?: user.password,
        )
        val savedUser = userAdapter.updateUser(updatedUser)
        return savedUser
    }

    fun getUser(id: String): User {
        return userAdapter.findById(id)
    }
}
