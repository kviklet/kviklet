package com.example.executiongate.service.dto

import com.example.executiongate.db.GroupEntity


data class Group(
    val id: String,
    val name: String,
    val description: String,
    val permissions: Set<Permission>
) {
    companion object {
        fun create(
            id: String,
            name: String,
            description: String,
            permissions: Set<Permission>
        ): Group {
            return Group(
                id = id,
                name = name,
                description = description,
                permissions = permissions
            )
        }
    }
}
