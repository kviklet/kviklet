package dev.kviklet.kviklet.security.ldap

import dev.kviklet.kviklet.security.IdpIdentifier
import dev.kviklet.kviklet.security.PolicyGrantedAuthority
import dev.kviklet.kviklet.security.UserAuthService
import dev.kviklet.kviklet.security.UserDetailsWithId
import org.springframework.ldap.core.LdapTemplate
import org.springframework.ldap.query.LdapQueryBuilder
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import javax.naming.directory.Attributes

@Service
class LdapUserDetailsService(
    private val ldapProperties: LdapProperties,
    private val ldapTemplate: LdapTemplate,
    private val userAuthService: UserAuthService,
) {
    @Transactional
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

        return userAuthService.findOrCreateUser(
            idpIdentifier = IdpIdentifier.Ldap(uniqueId),
            email = email,
            fullName = fullName,
            requireLicense = false,
        )
    }
}
