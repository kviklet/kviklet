package com.example.executiongate.db

import com.example.executiongate.service.dto.DatasourceDto
import com.example.executiongate.service.dto.DatasourceType
import org.apache.commons.lang3.builder.ToStringBuilder
import org.apache.commons.lang3.builder.ToStringStyle.SHORT_PREFIX_STYLE
import org.springframework.data.jpa.repository.JpaRepository
import javax.persistence.Entity
import javax.persistence.EnumType
import javax.persistence.Enumerated

@Entity(name = "datasource")
class DatasourceEntity(
    val displayName: String,
    @Enumerated(EnumType.STRING)
    val type: DatasourceType,
    val hostname: String,
    val port: Int,
): BaseEntity() {

    override fun toString(): String = ToStringBuilder(this, SHORT_PREFIX_STYLE)
        .append("id", id)
        .append("displayName", displayName)
        .toString()

    fun toDto() = DatasourceDto(
        id = id,
        displayName = displayName,
        type = type,
        hostname = hostname,
        port = port,
    )
}

interface DatasourceRepository : JpaRepository<DatasourceEntity, String>
