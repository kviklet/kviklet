package dev.kviklet.kviklet.security

import dev.kviklet.kviklet.db.RoleAdapter
import dev.kviklet.kviklet.db.UserAdapter
import dev.kviklet.kviklet.service.dto.Role
import org.springframework.ldap.core.LdapTemplate
import org.springframework.ldap.query.LdapQueryBuilder
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service
import java.io.Serializable
import javax.naming.directory.Attributes

@Service
class UserDetailsServiceImpl(
    private val userAdapter: UserAdapter,
    private val ldapProperties: LdapProperties,
    private val ldapTemplate: LdapTemplate,
    private val roleAdapter: RoleAdapter,
) : UserDetailsService {

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

    fun loadUserByLdapIdentifier(ldapIdentifier: String): UserDetails {
        val user = loadLdapUser(ldapIdentifier)

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

    private fun loadLdapUser(username: String): dev.kviklet.kviklet.db.User {
        val ldapUser = loadLdapUserAttributes(username)
        return findOrCreateLdapUser(ldapUser)
    }

    private fun loadLdapUserAttributes(username: String): Map<String, String?> = ldapTemplate.search(
        LdapQueryBuilder.query().where(ldapProperties.uniqueIdentifierAttribute).`is`(username),
    ) { attrs: Attributes ->
        mapOf(
            "uid" to attrs.get(ldapProperties.uniqueIdentifierAttribute)?.get()?.toString(),
            "email" to attrs.get(ldapProperties.emailAttribute)?.get()?.toString(),
            "fullName" to attrs.get(ldapProperties.fullNameAttribute)?.get()?.toString(),
        )
    }.firstOrNull() ?: throw UsernameNotFoundException("LDAP user '$username' not found.")

    private fun findOrCreateLdapUser(ldapUser: Map<String, String?>): dev.kviklet.kviklet.db.User {
        val email = ldapUser["email"] ?: throw IllegalStateException("Email attribute in LDAP user not found")
        val fullName = ldapUser["fullName"] ?: throw IllegalStateException("Full Name attribute in LDAP user not found")
        val uniqueId = ldapUser["uid"] ?: throw IllegalStateException("UID attribute in LDAP user not found")

        var user = userAdapter.findByLdapIdentifier(uniqueId)

        if (user == null) {
            val defaultRole = roleAdapter.findById(Role.DEFAULT_ROLE_ID)
            user = dev.kviklet.kviklet.db.User(
                ldapIdentifier = uniqueId,
                email = email,
                fullName = fullName,
                roles = setOf(defaultRole),
            )
        } else {
            user = user.copy(
                email = email,
                fullName = fullName,
            )
        }

        return userAdapter.createOrUpdateUser(user)
    }

    private fun isLdapAuthentication(): Boolean = true
}

class UserDetailsWithId(val id: String, email: String, password: String?, authorities: Collection<GrantedAuthority>) :
    User(email, password, authorities),
    Serializable {
    companion object {
        private const val serialVersionUID = 1L // Serializable version UID
    }
}
