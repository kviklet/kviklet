package dev.kviklet.kviklet.db

import dev.kviklet.kviklet.db.util.BaseEntity
import dev.kviklet.kviklet.service.dto.Policy
import dev.kviklet.kviklet.service.dto.PolicyEffect
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository

@Entity
@Table(name = "policy")
class PolicyEntity constructor() : BaseEntity() {
    lateinit var action: String

    @Enumerated(EnumType.STRING)
    lateinit var effect: PolicyEffect
    lateinit var resource: String

    constructor(
        id: String? = null,
        action: String,
        effect: PolicyEffect,
        resource: String,
    ) : this () {
        this.id = id
        this.action = action
        this.effect = effect
        this.resource = resource
    }
    fun toDto() = Policy(
        id = id,
        action = action,
        effect = effect,
        resource = resource,
    )
}

interface PolicyRepository : JpaRepository<PolicyEntity, String>
