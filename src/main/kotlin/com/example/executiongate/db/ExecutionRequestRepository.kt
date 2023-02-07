package com.example.executiongate.db

import com.example.executiongate.service.dto.ConnectionDto
import org.apache.commons.lang3.builder.ToStringBuilder
import org.apache.commons.lang3.builder.ToStringStyle.SHORT_PREFIX_STYLE
import org.hibernate.annotations.GenericGenerator
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDateTime
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.Id

@Entity(name = "execution_request")
data class ExecutionRequestEntity(
    val statement: String,
    val databaseId: String,
    val state: String,
    val createdAt: LocalDateTime = LocalDateTime.now(),
): BaseEntity() {

    override fun toString(): String {
        return ToStringBuilder(this, SHORT_PREFIX_STYLE)
            .append("id", id)
            .toString()
    }
}

interface ExecutionRequestRepository : JpaRepository<ExecutionRequestEntity?, String?>
