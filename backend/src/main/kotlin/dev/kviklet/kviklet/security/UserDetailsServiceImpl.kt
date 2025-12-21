package dev.kviklet.kviklet.security

import dev.kviklet.kviklet.db.UserAdapter
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service
import java.io.Serializable

@Service
class UserDetailsServiceImpl(private val userAdapter: UserAdapter) : UserDetailsService {

    override fun loadUserByUsername(username: String): UserDetails {
        val user = loadDatabaseUser(username)

        val authorities = user.roles.flatMap { it.policies }.map { PolicyGrantedAuthority(it) }

        return UserDetailsWithId(
            user.getId()!!,
            user.email,
            user.password ?: "a random password since spring somehow requires one",
            authorities,
        )
    }

    private fun loadDatabaseUser(email: String): dev.kviklet.kviklet.db.User = userAdapter.findByEmail(email)
        ?: throw UsernameNotFoundException("User '$email' not found.")
}

class UserDetailsWithId(val id: String, email: String, password: String?, authorities: Collection<GrantedAuthority>) :
    User(email, password, authorities),
    Serializable {
    companion object {
        private const val serialVersionUID = 1L // Serializable version UID
    }
}
