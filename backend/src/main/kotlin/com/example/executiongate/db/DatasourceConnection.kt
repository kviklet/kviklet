package com.example.executiongate.db

import com.example.executiongate.db.util.BaseEntity
import com.example.executiongate.service.dto.AuthenticationType
import com.example.executiongate.service.dto.DatasourceConnectionDto
import com.example.executiongate.service.dto.DatasourceConnectionId
import org.apache.commons.lang3.builder.ToStringBuilder
import org.apache.commons.lang3.builder.ToStringStyle.SHORT_PREFIX_STYLE
import org.springframework.data.jpa.repository.JpaRepository
import javax.persistence.Entity
import javax.persistence.EnumType
import javax.persistence.Enumerated
import javax.persistence.JoinColumn
import javax.persistence.ManyToOne


@Entity(name = "datasource_connection")
class DatasourceConnectionEntity(
    @ManyToOne
    @JoinColumn(name = "datasource_id")
    val datasource: DatasourceEntity,
    val displayName: String,
    @Enumerated(EnumType.STRING)
    val authenticationType: AuthenticationType,
    val username: String,
    val password: String,
): BaseEntity() {

    override fun toString(): String = ToStringBuilder(this, SHORT_PREFIX_STYLE)
        .append("id", id)
        .append("name", displayName)
        .toString()

    fun toDto() = DatasourceConnectionDto(
        id = DatasourceConnectionId(id),
        displayName = displayName,
        authenticationType = authenticationType,
        username = username,
        password = password,
    )

}

interface DatasourceConnectionRepository : JpaRepository<DatasourceConnectionEntity, String>

