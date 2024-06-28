package dev.kviklet.kviklet.security

import dev.kviklet.kviklet.service.dto.Policy
import dev.kviklet.kviklet.service.dto.PolicyEffect
import org.springframework.security.core.GrantedAuthority
import org.springframework.util.AntPathMatcher

class PolicyGrantedAuthority(private val policy: Policy) : GrantedAuthority {

    override fun getAuthority() = null

    fun vote(action: String): VoteResult {
        if (matchesAction(action)) {
            return effectToResult()
        }
        return VoteResult.ABSTAIN
    }

    fun vote(permission: Permission, domainObject: SecuredDomainObject?): VoteResult {
        if (permission.action == null) {
            return VoteResult.ALLOW
        }

        // DENY is only effective on specific domainObjects
        if (domainObject == null && policy.effect == PolicyEffect.DENY && policy.resource != "*") {
            return VoteResult.ABSTAIN
        }

        if (matchesAction(permission.getPermissionString()) && matchesId(domainObject)) {
            return effectToResult()
        }
        return VoteResult.ABSTAIN
    }

    private fun effectToResult(): VoteResult = when (policy.effect) {
        PolicyEffect.ALLOW -> VoteResult.ALLOW
        PolicyEffect.DENY -> VoteResult.DENY
    }

    private fun matchesId(domainObject: SecuredDomainObject?): Boolean {
        if (domainObject == null) return true
        return AntPathMatcher().match(policy.resource, domainObject.getSecuredObjectId()!!)
    }

    private fun matchesAction(action: String): Boolean = AntPathMatcher().match(policy.action, action)
}

enum class VoteResult {
    ALLOW,
    DENY,
    ABSTAIN,
}

fun List<PolicyGrantedAuthority>.vote(permission: Permission, obj: SecuredDomainObject? = null): List<VoteResult> {
    if (permission.action == null) {
        return listOf(VoteResult.ALLOW)
    }

    return this.map { it.vote(permission, obj) }
}

fun List<VoteResult>.isAllowed(): Boolean = this.any { it == VoteResult.ALLOW } && this.none { it == VoteResult.DENY }
