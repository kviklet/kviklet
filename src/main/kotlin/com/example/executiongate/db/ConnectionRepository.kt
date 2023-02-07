package com.example.executiongate.db

import com.example.executiongate.service.dto.ConnectionDto
import org.apache.commons.lang3.builder.ToStringBuilder
import org.apache.commons.lang3.builder.ToStringStyle.SHORT_PREFIX_STYLE
import org.hibernate.annotations.GenericGenerator
import org.springframework.data.jpa.repository.JpaRepository
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.Id

@Entity(name = "connection")
class ConnectionEntity(
    val name: String,
    val uri: String,
    val username: String,
    val password: String,
): BaseEntity() {

    override fun toString(): String {
        return ToStringBuilder(this, SHORT_PREFIX_STYLE)
            .append("id", id)
            .append("name", name)
            .toString()
    }

    fun toDto() = ConnectionDto(
        id = id,
        name = name,
        uri = uri,
        username = username,
        password = password,
    )
}

interface ConnectionRepository : JpaRepository<ConnectionEntity?, String>
