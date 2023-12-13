package dev.kviklet.kviklet.db

import dev.kviklet.kviklet.db.util.ReviewConfigConverter
import dev.kviklet.kviklet.service.EntityNotFound
import dev.kviklet.kviklet.service.dto.AuthenticationType
import dev.kviklet.kviklet.service.dto.DatasourceConnection
import dev.kviklet.kviklet.service.dto.DatasourceConnectionId
import dev.kviklet.kviklet.service.dto.DatasourceType
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
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
    @Enumerated(EnumType.STRING)
    var type: DatasourceType,
    var hostname: String,
    var port: Int,
) {

    override fun toString(): String = ToStringBuilder(this, SHORT_PREFIX_STYLE)
        .append("id", id)
        .append("name", displayName)
        .toString()

    fun toDto() = DatasourceConnection(
        id = DatasourceConnectionId(id),
        displayName = displayName,
        authenticationType = authenticationType,
        databaseName = databaseName,
        username = username,
        password = password,
        description = description,
        reviewConfig = reviewConfig,
        port = port,
        hostname = hostname,
        type = type,
    )
}

interface DatasourceConnectionRepository : JpaRepository<DatasourceConnectionEntity, String>

@Service
class DatasourceConnectionAdapter(
    val datasourceConnectionRepository: DatasourceConnectionRepository,
) {

    fun getDatasourceConnection(datasourceConnectionId: DatasourceConnectionId): DatasourceConnection {
        return datasourceConnectionRepository.findByIdOrNull(datasourceConnectionId.toString())?.toDto()
            ?: throw EntityNotFound(
                "Datasource Connection Not Found",
                "Datasource Connection $$datasourceConnectionId does not exist.",
            )
    }

    fun createDatasourceConnection(
        datasourceConnectionId: DatasourceConnectionId,
        displayName: String,
        authenticationType: AuthenticationType,
        databaseName: String?,
        username: String,
        password: String,
        description: String,
        reviewConfig: ReviewConfig,
        port: Int,
        hostname: String,
        type: DatasourceType,
    ): DatasourceConnection {
        return datasourceConnectionRepository.save(
            DatasourceConnectionEntity(
                id = datasourceConnectionId.toString(),
                displayName = displayName,
                authenticationType = authenticationType,
                databaseName = databaseName,
                username = username,
                password = password,
                description = description,
                reviewConfig = reviewConfig,
                executionRequests = emptySet(),
                port = port,
                hostname = hostname,
                type = type,
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
