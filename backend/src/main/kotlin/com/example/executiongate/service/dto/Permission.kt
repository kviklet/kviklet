package com.example.executiongate.service.dto

import com.example.executiongate.db.PermissionEntity

data class Permission(
    val id: String,
    val scope: String,
    val action: String
) {
    companion object {
        fun create(id: String, scope: String, action: String): Permission {
            return Permission(
                id = id,
                scope = scope,
                action = action
            )
        }
    }
}
