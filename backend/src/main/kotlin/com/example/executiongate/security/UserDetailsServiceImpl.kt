package com.example.executiongate.security

import com.example.executiongate.db.UserAdapter
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service

@Service
class UserDetailsServiceImpl(
    private val userAdapter: UserAdapter,
) : UserDetailsService {

    override fun loadUserByUsername(email: String): UserDetails {
        val user = userAdapter.findByEmail(email)
            ?: throw UsernameNotFoundException("User '$email' not found.")

        val authorities = listOf(SimpleGrantedAuthority("USERS"))

        return UserDetailsWithId(user.id, user.email, authorities)
    }
}

class UserDetailsWithId(
    val id: String,
    email: String,
    authorities: Collection<out GrantedAuthority>,
) : User(email, "", authorities) {
    init {
        this.eraseCredentials()
    }
}
