package dev.kviklet.kviklet.security

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.GrantedAuthority

/**
 * An authentication established from an API key rather than a browser session. Behaves exactly like
 * the session token everywhere else; the distinct type only exists so code that cares (telemetry) can
 * tell how the caller got in.
 */
class ApiKeyAuthentication(principal: UserDetailsWithId, authorities: Collection<GrantedAuthority>) :
    UsernamePasswordAuthenticationToken(principal, null, authorities)
