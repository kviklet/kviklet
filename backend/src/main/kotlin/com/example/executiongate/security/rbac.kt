package com.example.executiongate.security

import com.example.executiongate.service.dto.Policy
import org.springframework.security.core.GrantedAuthority

class PolicyGrantedAuthority(
    val policy: Policy
): GrantedAuthority {

    override fun getAuthority(): String? {
        return null
    }
}