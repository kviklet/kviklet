package dev.kviklet.kviklet.security

import dev.kviklet.kviklet.service.dto.Policy
import org.springframework.security.core.GrantedAuthority
import org.springframework.util.AntPathMatcher

class PolicyGrantedAuthority(private val policy: Policy) : GrantedAuthority {

    override fun getAuthority() = null

    fun vote(permission: Permission, domainObject: SecuredDomainObject?): Boolean {
        if (permission.action == null) {
            return true
        }
        return matchesAction(permission.getPermissionString()) && matchesId(domainObject)
    }

    private fun matchesId(domainObject: SecuredDomainObject?): Boolean {
        if (domainObject == null) return true
        return AntPathMatcher().match(policy.resource, domainObject.getSecuredObjectId()!!)
    }

    private fun matchesAction(action: String): Boolean = AntPathMatcher().match(policy.action, action)
}

fun List<PolicyGrantedAuthority>.vote(permission: Permission, obj: SecuredDomainObject? = null): Boolean {
    if (permission.action == null) {
        return true
    }
    return this.any { it.vote(permission, obj) }
}
