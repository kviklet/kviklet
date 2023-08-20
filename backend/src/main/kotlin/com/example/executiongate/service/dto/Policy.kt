package com.example.executiongate.service.dto

data class Policy(
    val id: String,
    val action: String,
    val effect: String,
    val resource: String,
) {
    companion object {
        fun create(id: String, action: String, effect: String, resource: String): Policy {
            return Policy(
                id = id,
                action = action,
                effect = effect,
                resource = resource,
            )
        }
    }
}
