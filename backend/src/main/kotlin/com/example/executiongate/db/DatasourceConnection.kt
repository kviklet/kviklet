package com.example.executiongate.db

import com.example.executiongate.db.util.BaseEntity
import com.example.executiongate.db.util.EventPayloadConverter
import com.example.executiongate.db.util.ReviewConfigConverter
import com.example.executiongate.service.EntityNotFound
import com.example.executiongate.service.dto.AuthenticationType
import com.example.executiongate.service.dto.DatasourceConnectionDto
import com.example.executiongate.service.dto.DatasourceConnectionId
import com.example.executiongate.service.dto.DatasourceId
import com.example.executiongate.service.dto.ExecutionRequestId
import org.apache.commons.lang3.builder.ToStringBuilder
import org.apache.commons.lang3.builder.ToStringStyle.SHORT_PREFIX_STYLE
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import javax.persistence.Column
import javax.persistence.Convert
import javax.persistence.Entity
import javax.persistence.EnumType
import javax.persistence.Enumerated
import javax.persistence.JoinColumn
import javax.persistence.ManyToOne


data class ReviewConfig(
    val numTotalRequired: Int,
)


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
    @Convert(converter = ReviewConfigConverter::class)
    @Column(columnDefinition = "json")
    val reviewConfig: ReviewConfig,
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
        reviewConfig = reviewConfig,
    )

}

interface DatasourceConnectionRepository : JpaRepository<DatasourceConnectionEntity, String>

@Service
class DatasourceConnectionAdapter(
    val datasourceConnectionRepository: DatasourceConnectionRepository
) {

    fun getDatasourceConnection(id: DatasourceConnectionId): DatasourceConnectionDto =
        datasourceConnectionRepository.findByIdOrNull(id.toString())?.toDto()
            ?: throw EntityNotFound("Datasource Connection Not Found", "Datasource Connection with id $id does not exist.")

}