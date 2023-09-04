package com.example.executiongate.db

import com.example.executiongate.db.util.BaseEntity
import com.example.executiongate.service.dto.Policy
import com.example.executiongate.service.dto.PolicyEffect
import org.springframework.data.jpa.repository.JpaRepository
import jakarta.persistence.*

@Entity
@Table(name = "policy")
class PolicyEntity(
    @ManyToOne(cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id", nullable = false)
    val role: RoleEntity,
    val action: String, // "connection:read", "connection:*", ...
    @Enumerated(EnumType.STRING)
    val effect: PolicyEffect, // allow/deny
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