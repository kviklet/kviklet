// This file is not MIT licensed
package dev.kviklet.kviklet.security.mcp

import dev.kviklet.kviklet.db.UserAdapter
import dev.kviklet.kviklet.security.PolicyGrantedAuthority
import dev.kviklet.kviklet.security.UserDetailsWithId
import dev.kviklet.kviklet.service.EntityNotFound
import dev.kviklet.kviklet.service.dto.Role
import org.springframework.core.convert.converter.Converter
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException

/**
 * An authentication established from an MCP access token. Like the session and API key tokens it
 * carries the user as [UserDetailsWithId], so `@Policy` checks work unchanged.
 */
class McpAuthentication(principal: UserDetailsWithId, authorities: Collection<GrantedAuthority>) :
    UsernamePasswordAuthenticationToken(principal, null, authorities)

/**
 * Turns a verified access token into the Kviklet user it was issued for. The token only names the
 * user (`sub` is the user id); account state and roles are read on every request, so deactivating a
 * user or changing their roles takes effect immediately rather than when the token expires.
 */
class McpJwtAuthenticationConverter(private val userAdapter: UserAdapter) :
    Converter<Jwt, AbstractAuthenticationToken> {
    override fun convert(jwt: Jwt): AbstractAuthenticationToken {
        val user = try {
            userAdapter.findById(jwt.subject ?: throw InvalidBearerTokenException("The token names no user"))
        } catch (e: EntityNotFound) {
            throw InvalidBearerTokenException("The user this token was issued for no longer exists")
        }
        if (!user.active) {
            throw InvalidBearerTokenException("The user this token was issued for is deactivated")
        }
        val policies = user.roles.flatMap(Role::policies).map(::PolicyGrantedAuthority)
        val userDetails = UserDetailsWithId(user.getId()!!, user.email, "", policies)
        return McpAuthentication(userDetails, policies)
    }
}
