package com.example.executiongate.service.dto

data class Role(
    val id: String = "",
    val name: String,
    val description: String,
    val policies: Set<Policy> = HashSet(),
) {
    companion object {
        fun create(
            id: String,
            name: String,
            description: String,
            policies: Set<Policy>,
        ): Role {
            return Role(
                id = id,
                name = name,
                description = description,
                policies = policies,
            )
        }
    }
}
