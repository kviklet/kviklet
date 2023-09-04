package com.example.executiongate.db

import com.example.executiongate.db.util.BaseEntity
import com.example.executiongate.service.dto.Policy
import com.example.executiongate.service.dto.PolicyEffect
import jakarta.persistence.CascadeType
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository

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
) : BaseEntity() {
    fun toDto() =
        Policy(
            id = id,
            action = action,
            effect = effect,
            resource = resource,
        )
}

interface PolicyRepository : JpaRepository<PolicyEntity, String>
