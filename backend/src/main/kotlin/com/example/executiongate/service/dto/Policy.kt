package com.example.executiongate.service.dto

import java.io.Serializable

enum class PolicyEffect {
    ALLOW,
    DENY,
}

data class Policy(
    val id: String,
    // connection:list, connection:*
    val action: String,
    val effect: PolicyEffect,
    // uuid
    val resource: String,
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
