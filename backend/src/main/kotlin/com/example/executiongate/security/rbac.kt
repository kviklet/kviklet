package com.example.executiongate.security

import com.example.executiongate.service.dto.Policy
import com.example.executiongate.service.dto.PolicyEffect
import org.springframework.security.core.GrantedAuthority
import org.springframework.util.AntPathMatcher

class PolicyGrantedAuthority(
    private val policy: Policy
): GrantedAuthority {

    override fun getAuthority() = null

    fun vote(action: String): VoteResult {
        if (matchesAction(action)) {
            return effectToResult()
        }
        return VoteResult.ABSTAIN
    }

    fun vote(action: String, domainObject: SecuredDomainObject?): VoteResult {
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
    ALLOW, DENY, ABSTAIN
}