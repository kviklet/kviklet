package com.example.executiongate.security

import com.example.executiongate.service.dto.Policy
import com.example.executiongate.service.dto.PolicyEffect
import org.springframework.security.core.GrantedAuthority
import org.springframework.util.AntPathMatcher

class PolicyGrantedAuthority(
    private val policy: Policy,
) : GrantedAuthority {

    override fun getAuthority() = null

    fun vote(action: String): VoteResult {
        if (matchesAction(action)) {
            return effectToResult()
        }
        return VoteResult.ABSTAIN
    }

    fun vote(action: String, domainObject: SecuredDomainObject?): VoteResult {
        // DENY is only effective on specific domainObjects
        if (domainObject == null && policy.effect == PolicyEffect.DENY && policy.resource != "*") {
            return VoteResult.ABSTAIN
        }

        if (matchesAction(action) && matchesId(domainObject)) {
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
        return AntPathMatcher().match(policy.resource, domainObject.getId())
    }

    private fun matchesAction(action: String): Boolean {
        return AntPathMatcher().match(policy.action, action)
    }
}

enum class VoteResult {
    ALLOW,
    DENY,
    ABSTAIN,
}

fun List<PolicyGrantedAuthority>.vote(permission: Permission, obj: SecuredDomainObject? = null): List<VoteResult> {
    return this.map { it.vote(permission.getPermissionString(), obj) }
}

fun List<VoteResult>.isAllowed(): Boolean {
    return this.any { it == VoteResult.ALLOW } && this.none { it == VoteResult.DENY }
}
