package dev.kviklet.kviklet.db

import dev.kviklet.kviklet.db.util.BaseEntity
import dev.kviklet.kviklet.service.dto.Policy
import jakarta.persistence.Entity
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository

@Entity
@Table(name = "policy")
class PolicyEntity constructor() : BaseEntity() {
    lateinit var action: String
    lateinit var resource: String

    constructor(
        id: String? = null,
        action: String,
        resource: String,
    ) : this () {
        this.id = id
        this.action = action
        this.resource = resource
    }
    fun toDto() = Policy(
        id = id,
        action = action,
        resource = resource,
    )
}

interface PolicyRepository : JpaRepository<PolicyEntity, String>
