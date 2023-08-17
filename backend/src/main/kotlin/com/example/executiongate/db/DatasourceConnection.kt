package com.example.executiongate.db

import com.example.executiongate.db.util.BaseEntity
import com.example.executiongate.db.util.ReviewConfigConverter
import com.example.executiongate.service.EntityNotFound
import com.example.executiongate.service.dto.AuthenticationType
import com.example.executiongate.service.dto.Datasource
import com.example.executiongate.service.dto.DatasourceConnection
import com.example.executiongate.service.dto.DatasourceConnectionId
import org.apache.commons.lang3.builder.ToStringBuilder
import org.apache.commons.lang3.builder.ToStringStyle.SHORT_PREFIX_STYLE
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import javax.persistence.*


data class ReviewConfig(
    val numTotalRequired: Int,
)


@Entity(name = "datasource_connection")
class DatasourceConnectionEntity(
    @ManyToOne(cascade = [CascadeType.ALL])
    @JoinColumn(name = "datasource_id")
    val datasource: DatasourceEntity,
    val displayName: String,
    @Enumerated(EnumType.STRING)
    val authenticationType: AuthenticationType,
    val username: String,
    val password: String,
    val description: String,
    @Convert(converter = ReviewConfigConverter::class)
    @Column(columnDefinition = "json")
    val reviewConfig: ReviewConfig,
    @OneToMany(mappedBy = "connection", cascade = [CascadeType.ALL])
    val executionRequests: Set<ExecutionRequestEntity> = emptySet(),
): BaseEntity() {

    override fun toString(): String = ToStringBuilder(this, SHORT_PREFIX_STYLE)
        .append("id", id)
        .append("name", displayName)
        .toString()

    fun toDto(datasourceDto: Datasource? = null) = DatasourceConnection(
        id = DatasourceConnectionId(id),
        datasource = datasourceDto ?: datasource.toDto(),
        displayName = displayName,
        authenticationType = authenticationType,
        username = username,
        password = password,
        description = description,
        reviewConfig = reviewConfig,

    )

}

interface DatasourceConnectionRepository : JpaRepository<DatasourceConnectionEntity, String>

@Service
class DatasourceConnectionAdapter(
    val datasourceConnectionRepository: DatasourceConnectionRepository
) {

    fun getDatasourceConnection(id: DatasourceConnectionId): DatasourceConnection =
        datasourceConnectionRepository.findByIdOrNull(id.toString())?.toDto()
            ?: throw EntityNotFound("Datasource Connection Not Found", "Datasource Connection with id $id does not exist.")

}