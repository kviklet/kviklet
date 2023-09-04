package com.example.executiongate.service.dto

import java.io.Serializable

enum class PolicyEffect {
    ALLOW, DENY
}

data class Policy(
    val id: String,
    val action: String, // connection:list, connection:*
    val effect: PolicyEffect,
    val resource: String, // uuid
) : Serializable {
    companion object {
        fun create(id: String, action: String, effect: PolicyEffect, resource: String): Policy {
            return Policy(
                id = id,
                action = action,
                effect = effect,
                resource = resource,
            )
        }
    }
}
