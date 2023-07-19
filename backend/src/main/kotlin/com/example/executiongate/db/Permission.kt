package com.example.executiongate.db

import com.example.executiongate.db.GroupEntity
import com.example.executiongate.db.UserEntity
import com.example.executiongate.db.util.BaseEntity
import com.example.executiongate.service.dto.Permission
import org.springframework.data.jpa.repository.JpaRepository
import javax.persistence.*

@Entity
@Table(name = "permissions")
data class PermissionEntity(
    val scope: String = "",

    val action: String = "",

    @Column(name="group_id", nullable = true)
    var groupId: String? = null,

    @Column(name="user_id", nullable = true)
    var userId: String? = null
) : BaseEntity(
) {
    fun toDto() =
        Permission(
            id = id,
            scope = scope,
            action = action
        )
}


interface PermissionRepository : JpaRepository<PermissionEntity, String>