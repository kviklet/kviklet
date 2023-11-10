package com.example.executiongate.service.dto

data class Role(
    val id: String = "",
    val description: String,
    val policies: Set<Policy> = HashSet(),
) {
    companion object {
        fun create(id: String, description: String, policies: Set<Policy>): Role {
            return Role(
                id = id,
                description = description,
                policies = policies,
            )
        }
    }
}
