package com.example.executiongate.db

import com.example.executiongate.db.util.BaseEntity
import com.example.executiongate.db.util.EventPayloadConverter
import com.example.executiongate.service.dto.Policy
import org.springframework.data.jpa.repository.JpaRepository
import jakarta.persistence.*

@Entity
@Table(name = "policy")
data class PolicyEntity(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id", nullable = false)
    val role: RoleEntity,
    val action: String, // "connection:read", "connection:*", ...
    val effect: String, // allow/deny
    val resource: String, // ids: *   ids: 1,2,3   tags: foo, bar
    // TODO: conditions

) : BaseEntity(

) {
    fun toDto() =
        Policy(
            id = id,
            action = action,
            effect = effect,
            resource = resource,
        )
}


interface PolicyRepository : JpaRepository<PolicyEntity, String>