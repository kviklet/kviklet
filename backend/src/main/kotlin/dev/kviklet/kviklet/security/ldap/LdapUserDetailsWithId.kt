package dev.kviklet.kviklet.security.ldap

import dev.kviklet.kviklet.security.UserDetailsWithId
import org.springframework.security.core.GrantedAuthority

/**
 * The principal of a user who signed in through LDAP. Spring's LDAP provider issues the same token type
 * as the local password provider, so the principal is the only place a login can be told apart, which
 * telemetry uses to count LDAP logins separately.
 */
class LdapUserDetailsWithId(id: String, email: String, password: String?, authorities: Collection<GrantedAuthority>) :
    UserDetailsWithId(id, email, password, authorities) {
    companion object {
        private const val serialVersionUID = 1L
    }
}
