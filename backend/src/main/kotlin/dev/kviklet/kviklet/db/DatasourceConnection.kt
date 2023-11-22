package dev.kviklet.kviklet.db

import dev.kviklet.kviklet.db.util.ReviewConfigConverter
import dev.kviklet.kviklet.service.EntityNotFound
import dev.kviklet.kviklet.service.dto.AuthenticationType
import dev.kviklet.kviklet.service.dto.Datasource
import dev.kviklet.kviklet.service.dto.DatasourceConnection
import dev.kviklet.kviklet.service.dto.DatasourceConnectionId
import dev.kviklet.kviklet.service.dto.DatasourceId
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import org.apache.commons.lang3.builder.ToStringBuilder
import org.apache.commons.lang3.builder.ToStringStyle.SHORT_PREFIX_STYLE
import org.hibernate.annotations.ColumnTransformer
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service

data class ReviewConfig(
    val numTotalRequired: Int,
)

@Entity(name = "datasource_connection")
class DatasourceConnectionEntity(
    @Id
    val id: String,
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "datasource_id")
    val datasource: DatasourceEntity,
    var displayName: String,
    @Enumerated(EnumType.STRING)
    var authenticationType: AuthenticationType,
    var databaseName: String?,
    var username: String,
    var password: String,
    var description: String,
    @Column(columnDefinition = "json")
    @Convert(converter = ReviewConfigConverter::class)
    @ColumnTransformer(write = "?::json")
    var reviewConfig: ReviewConfig,
    @OneToMany(mappedBy = "connection", cascade = [CascadeType.ALL])
    val executionRequests: Set<ExecutionRequestEntity> = emptySet(),
) {

    override fun toString(): String = ToStringBuilder(this, SHORT_PREFIX_STYLE)
        .append("id", id)
        .append("name", displayName)
        .toString()

    fun toDto(datasourceDto: Datasource? = null) = DatasourceConnection(
        id = DatasourceConnectionId(id),
        datasource = datasourceDto ?: datasource.toDto(),
        displayName = displayName,
        authenticationType = authenticationType,
        databaseName = databaseName,
        username = username,
        password = password,
        description = description,
        reviewConfig = reviewConfig,

    )
}

interface DatasourceConnectionRepository : JpaRepository<DatasourceConnectionEntity, String> {
    fun findByDatasourceAndId(datasource: DatasourceEntity, id: String): DatasourceConnectionEntity?
}

@Service
class DatasourceConnectionAdapter(
    val datasourceRepository: DatasourceRepository,
    val datasourceConnectionRepository: DatasourceConnectionRepository,
) {

    fun getDatasourceConnection(
        datasourceId: DatasourceId?,
        datasourceConnectionId: DatasourceConnectionId,
    ): DatasourceConnection {
        if (datasourceId != null) {
            val datasource = datasourceRepository.findByIdOrNull(datasourceId.toString()) ?: throw EntityNotFound(
                "Datasource Not Found",
                "Datasource $datasourceId does not exist.",
            )

            return datasourceConnectionRepository
                .findByDatasourceAndId(datasource, datasourceConnectionId.toString())?.toDto()
                ?: throw EntityNotFound(
                    "Datasource Connection Not Found",
                    "Datasource Connection $datasourceId/$datasourceConnectionId does not exist.",
                )
        } else {
            return datasourceConnectionRepository.findByIdOrNull(datasourceConnectionId.toString())?.toDto()
                ?: throw EntityNotFound(
                    "Datasource Connection Not Found",
                    "Datasource Connection $datasourceId/$datasourceConnectionId does not exist.",
                )
        }
    }

    fun createDatasourceConnection(
        datasourceId: DatasourceId,
        datasourceConnectionId: DatasourceConnectionId,
        displayName: String,
        authenticationType: AuthenticationType,
        databaseName: String?,
        username: String,
        password: String,
        description: String,
        reviewConfig: ReviewConfig,
    ): DatasourceConnection {
        return datasourceConnectionRepository.save(
            DatasourceConnectionEntity(
                datasource = datasourceRepository.getReferenceById(datasourceId.toString()),
                id = datasourceConnectionId.toString(),
                displayName = displayName,
                authenticationType = authenticationType,
                databaseName = databaseName,
                username = username,
                password = password,
                description = description,
                reviewConfig = reviewConfig,
                executionRequests = emptySet(),
            ),
        ).toDto()
    }

    fun updateDatasourceConnection(
        id: DatasourceConnectionId,
        displayName: String,
        databaseName: String?,
        username: String,
        password: String,
        description: String,
        reviewConfig: ReviewConfig,
    ): DatasourceConnection {
        val datasourceConnection = datasourceConnectionRepository.findByIdOrNull(id.toString())
            ?: throw EntityNotFound(
                "Datasource Connection Not Found",
                "Datasource Connection with id $id does not exist.",
            )
        datasourceConnection.displayName = displayName
        datasourceConnection.username = username
        datasourceConnection.password = password
        datasourceConnection.description = description
        datasourceConnection.reviewConfig = reviewConfig
        datasourceConnection.databaseName = databaseName

        return datasourceConnectionRepository.save(datasourceConnection).toDto()
    }

    fun deleteDatasourceConnection(id: DatasourceConnectionId) {
        datasourceConnectionRepository.deleteById(id.toString())
    }

    fun listDatasourceConnections(): List<DatasourceConnection> {
        return datasourceConnectionRepository.findAll().map { it.toDto() }
    }
}
