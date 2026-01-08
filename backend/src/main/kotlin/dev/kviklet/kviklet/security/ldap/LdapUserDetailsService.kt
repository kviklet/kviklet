package dev.kviklet.kviklet.security.ldap

import dev.kviklet.kviklet.security.IdpIdentifier
import dev.kviklet.kviklet.security.PolicyGrantedAuthority
import dev.kviklet.kviklet.security.UserAuthService
import dev.kviklet.kviklet.security.UserDetailsWithId
import dev.kviklet.kviklet.service.RoleSyncService
import org.springframework.ldap.core.LdapTemplate
import org.springframework.ldap.query.LdapQueryBuilder
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import javax.naming.directory.Attribute
import javax.naming.directory.Attributes

@Service
class LdapUserDetailsService(
    private val ldapProperties: LdapProperties,
    private val ldapTemplate: LdapTemplate,
    private val userAuthService: UserAuthService,
    private val roleSyncService: RoleSyncService,
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

    private fun loadLdapUserAttributes(username: String): Map<String, Any?> = ldapTemplate.search(
        LdapQueryBuilder.query()
            .attributes(
                ldapProperties.uniqueIdentifierAttribute,
                ldapProperties.emailAttribute,
                ldapProperties.fullNameAttribute,
                "memberOf",
            )
            .where(ldapProperties.uniqueIdentifierAttribute).`is`(username),
    ) { attrs: Attributes ->
        mapOf(
            "uid" to attrs.get(ldapProperties.uniqueIdentifierAttribute)?.get()?.toString(),
            "email" to attrs.get(ldapProperties.emailAttribute)?.get()?.toString(),
            "fullName" to attrs.get(ldapProperties.fullNameAttribute)?.get()?.toString(),
            "memberOf" to extractMemberOf(attrs.get("memberOf")),
        )
    }.firstOrNull() ?: throw UsernameNotFoundException("LDAP user '$username' not found.")

    private fun extractMemberOf(attr: Attribute?): List<String> {
        if (attr == null) return emptyList()
        return (0 until attr.size()).map { attr.get(it).toString() }
    }

    private fun findOrCreateLdapUser(ldapUser: Map<String, Any?>): dev.kviklet.kviklet.db.User {
        val email = ldapUser["email"] as? String
            ?: throw IllegalStateException("Email attribute in LDAP user not found")
        val fullName = ldapUser["fullName"] as? String
            ?: throw IllegalStateException("Full Name attribute in LDAP user not found")
        val uniqueId = ldapUser["uid"] as? String
            ?: throw IllegalStateException("UID attribute in LDAP user not found")

        // Extract groups from LDAP memberOf attribute for role sync
        @Suppress("UNCHECKED_CAST")
        val memberOfDns = ldapUser["memberOf"] as? List<String> ?: emptyList()
        val groups = roleSyncService.extractGroupsFromLdapDn(memberOfDns)

        return userAuthService.findOrCreateUser(
            idpIdentifier = IdpIdentifier.Ldap(uniqueId),
            email = email,
            fullName = fullName,
            idpGroups = groups,
            requireLicense = false,
        )
    }
}
