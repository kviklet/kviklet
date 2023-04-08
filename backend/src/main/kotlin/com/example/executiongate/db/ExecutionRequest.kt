package com.example.executiongate.db

import com.example.executiongate.db.util.BaseEntity
import com.example.executiongate.service.dto.ExecutionRequest
import com.example.executiongate.service.dto.ExecutionRequestId
import org.apache.commons.lang3.builder.ToStringBuilder
import org.apache.commons.lang3.builder.ToStringStyle.SHORT_PREFIX_STYLE
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDateTime
import javax.persistence.Entity
import javax.persistence.JoinColumn
import javax.persistence.ManyToOne

@Entity(name = "execution_request")
class ExecutionRequestEntity(
    @ManyToOne
    @JoinColumn(name = "datasource_id")
    val connection: DatasourceConnectionEntity,

    private val title: String,
    private val description: String?,
    private val statement: String,
    private val readOnly: Boolean,

    private val reviewStatus: String,
    private val executionStatus: String,

    private val createdAt: LocalDateTime = LocalDateTime.now(),
): BaseEntity() {

    override fun toString(): String {
        return ToStringBuilder(this, SHORT_PREFIX_STYLE)
            .append("id", id)
            .toString()
    }

    fun toDto(): ExecutionRequest {
        return ExecutionRequest(
            id = ExecutionRequestId(id),
            connection = connection.toDto(),
            title = title,
            description = description,
            statement = statement,
            readOnly = readOnly,
            reviewStatus = reviewStatus,
            executionStatus = executionStatus,
            createdAt = createdAt,
        )
    }

}

interface ExecutionRequestRepository : JpaRepository<ExecutionRequestEntity, String>
