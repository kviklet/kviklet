package dev.kviklet.kviklet.security

import dev.kviklet.kviklet.db.UserAdapter
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service
import java.io.Serializable

@Service
class UserDetailsServiceImpl(
    private val userAdapter: UserAdapter,
) : UserDetailsService {

    override fun loadUserByUsername(email: String): UserDetails {
        val user = userAdapter.findByEmail(email)
            ?: throw UsernameNotFoundException("User '$email' not found.")

        val authorities = listOf(SimpleGrantedAuthority("USERS"))

        return UserDetailsWithId(user.getId()!!, user.email, user.password, authorities)
    }
}

class UserDetailsWithId(
    val id: String,
    email: String,
    password: String?,
    authorities: Collection<GrantedAuthority>,
) : User(email, password, authorities), Serializable {
    companion object {
        private const val serialVersionUID = 1L // Serializable version UID
    }
}
