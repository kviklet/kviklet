package dev.kviklet.kviklet.db

import dev.kviklet.kviklet.db.util.ReviewConfigConverter
import dev.kviklet.kviklet.service.EncryptionConfigProperties
import dev.kviklet.kviklet.service.EncryptionService
import dev.kviklet.kviklet.service.EntityNotFound
import dev.kviklet.kviklet.service.dto.AuthenticationType
import dev.kviklet.kviklet.service.dto.Connection
import dev.kviklet.kviklet.service.dto.ConnectionId
import dev.kviklet.kviklet.service.dto.DatabaseProtocol
import dev.kviklet.kviklet.service.dto.DatasourceConnection
import dev.kviklet.kviklet.service.dto.DatasourceType
import dev.kviklet.kviklet.service.dto.KubernetesConnection
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
import org.springframework.transaction.annotation.Transactional

data class ReviewConfig(val numTotalRequired: Int)

enum class ConnectionType {
    DATASOURCE,
    KUBERNETES,
}

@Entity(name = "connection")
class ConnectionEntity(
    @Id
    val id: String,
    @Enumerated(EnumType.STRING)
    var connectionType: ConnectionType,
    var displayName: String,
    var description: String,
    @Column(columnDefinition = "json")
    @Convert(converter = ReviewConfigConverter::class)
    @ColumnTransformer(write = "?::json")
    var reviewConfig: ReviewConfig,
    @OneToMany(mappedBy = "connection", cascade = [CascadeType.ALL])
    val executionRequests: Set<ExecutionRequestEntity> = emptySet(),
    var maxExecutions: Int? = null,
    var isEncrypted: Boolean = false,

    // Datasource Connection fields
    @Enumerated(EnumType.STRING)
    var authenticationType: AuthenticationType? = null,
    var databaseName: String? = null,
    var username: String? = null,
    var password: String? = null,
    @Enumerated(EnumType.STRING)
    var datasourceType: DatasourceType? = null,
    @Enumerated(EnumType.STRING)
    var protocol: DatabaseProtocol? = null,
    var hostname: String? = null,
    var port: Int? = null,
    @Column(name = "additional_jdbc_options")
    var additionalJDBCOptions: String? = null,
) {

    override fun toString(): String = ToStringBuilder(this, SHORT_PREFIX_STYLE)
        .append("id", id)
        .append("name", displayName)
        .toString()

    fun toDto(): Connection = when (connectionType) {
        ConnectionType.DATASOURCE ->
            DatasourceConnection(
                id = ConnectionId(id),
                displayName = displayName,
                authenticationType = authenticationType!!,
                databaseName = databaseName,
                maxExecutions = maxExecutions,
                username = username!!,
                password = password!!,
                description = description,
                reviewConfig = reviewConfig,
                port = port!!,
                hostname = hostname!!,
                type = datasourceType!!,
                protocol = protocol ?: datasourceType!!.toProtocol(),
                additionalOptions = additionalJDBCOptions ?: "",
            )
        ConnectionType.KUBERNETES ->
            KubernetesConnection(
                id = ConnectionId(id),
                displayName = displayName,
                description = description,
                reviewConfig = reviewConfig,
                maxExecutions = maxExecutions,
            )
    }
}

interface ConnectionRepository : JpaRepository<ConnectionEntity, String>

@Service
class ConnectionAdapter(
    val connectionRepository: ConnectionRepository,
    private val encryptionService: EncryptionService,
    private val encryptionConfig: EncryptionConfigProperties,
) {
    private fun save(connection: ConnectionEntity): ConnectionEntity {
        if (encryptionConfig.enabled && !connection.isEncrypted) {
            connection.username = connection.username?.let { encryptionService.encrypt(it) }
            connection.password = connection.password?.let { encryptionService.encrypt(it) }
            connection.isEncrypted = true
        }
        return connectionRepository.save(connection)
    }

    @Transactional
    fun getConnection(connectionId: ConnectionId): Connection {
        val entity = connectionRepository.findByIdOrNull(connectionId.toString())
            ?: throw EntityNotFound("Connection Not Found", "Connection $connectionId does not exist.")

        return decryptCredentialsIfNeeded(entity)
    }

    private fun decryptCredentialsIfNeeded(connection: ConnectionEntity): Connection {
        if (!encryptionConfig.enabled && !connection.isEncrypted) return connection.toDto()
        if (!encryptionConfig.enabled) {
            throw IllegalStateException(
                "Connection is encrypted but encryption is not enabled, please add encryption key.",
            )
        }
        if (connection.isEncrypted) {
            connection.username = connection.username?.let { encryptionService.decrypt(it) }
            connection.password = connection.password?.let { encryptionService.decrypt(it) }
            connection.isEncrypted = false

            val dto = connection.toDto()
            reEncryptAndSaveIfNeeded(connection)
            return dto
        } else {
            val dto = connection.toDto()
            // encrypt and save again to ensure that the connection is now encrypted
            save(connection)
            return dto
        }
    }

    private fun reEncryptAndSaveIfNeeded(connection: ConnectionEntity) {
        val needsReEncryption = encryptionConfig.enabled && (encryptionConfig.key?.bothKeysProvided() ?: false)
        if (needsReEncryption) {
            save(connection)
        }
    }

    @Transactional
    fun createDatasourceConnection(
        connectionId: ConnectionId,
        displayName: String,
        authenticationType: AuthenticationType,
        databaseName: String?,
        maxExecutions: Int?,
        username: String,
        password: String,
        description: String,
        reviewConfig: ReviewConfig,
        port: Int,
        hostname: String,
        type: DatasourceType,
        protocol: DatabaseProtocol,
        additionalJDBCOptions: String,
    ): Connection = save(
        ConnectionEntity(
            id = connectionId.toString(),
            displayName = displayName,
            authenticationType = authenticationType,
            databaseName = databaseName,
            maxExecutions = maxExecutions,
            username = username,
            password = password,
            description = description,
            reviewConfig = reviewConfig,
            executionRequests = emptySet(),
            port = port,
            hostname = hostname,
            datasourceType = type,
            connectionType = ConnectionType.DATASOURCE,
            additionalJDBCOptions = additionalJDBCOptions,
        ),
    ).toDto()

    fun updateDatasourceConnection(
        id: ConnectionId,
        displayName: String,
        description: String,
        type: DatasourceType,
        protocol: DatabaseProtocol,
        maxExecutions: Int?,
        hostname: String,
        port: Int,
        username: String,
        password: String,
        databaseName: String?,
        reviewConfig: ReviewConfig,
        additionalJDBCOptions: String,
    ): Connection {
        val datasourceConnection = connectionRepository.findByIdOrNull(id.toString())
            ?: throw EntityNotFound(
                "Datasource Connection Not Found",
                "Datasource Connection with id $id does not exist.",
            )
        if (datasourceConnection.connectionType != ConnectionType.DATASOURCE) {
            throw IllegalStateException("Connection is not a Datasource Connection")
        }
        datasourceConnection.displayName = displayName
        datasourceConnection.description = description
        datasourceConnection.datasourceType = type
        datasourceConnection.protocol = protocol
        datasourceConnection.hostname = hostname
        datasourceConnection.maxExecutions = maxExecutions
        datasourceConnection.port = port
        datasourceConnection.username = username
        datasourceConnection.password = password
        datasourceConnection.databaseName = databaseName
        datasourceConnection.reviewConfig = reviewConfig
        datasourceConnection.additionalJDBCOptions = additionalJDBCOptions
        datasourceConnection.isEncrypted = false

        return save(datasourceConnection).toDto()
    }

    fun updateKubernetesConnection(
        id: ConnectionId,
        displayName: String,
        description: String,
        reviewConfig: ReviewConfig,
        maxExecutions: Int?,
    ): Connection {
        val datasourceConnection = connectionRepository.findByIdOrNull(id.toString())
            ?: throw EntityNotFound(
                "Datasource Connection Not Found",
                "Datasource Connection with id $id does not exist.",
            )
        if (datasourceConnection.connectionType != ConnectionType.KUBERNETES) {
            throw IllegalStateException("Connection is not a Kubernetes Connection")
        }
        datasourceConnection.displayName = displayName
        datasourceConnection.description = description
        datasourceConnection.reviewConfig = reviewConfig
        datasourceConnection.maxExecutions = maxExecutions

        return save(datasourceConnection).toDto()
    }

    @Transactional
    fun createKubernetesConnection(
        connectionId: ConnectionId,
        displayName: String,
        description: String,
        reviewConfig: ReviewConfig,
        maxExecutions: Int?,
    ): Connection = save(
        ConnectionEntity(
            id = connectionId.toString(),
            displayName = displayName,
            description = description,
            reviewConfig = reviewConfig,
            connectionType = ConnectionType.KUBERNETES,
            maxExecutions = maxExecutions,
        ),
    ).toDto()

    fun deleteConnection(id: ConnectionId) {
        connectionRepository.deleteById(id.toString())
    }

    fun listConnections(): List<Connection> = connectionRepository.findAll().map { decryptCredentialsIfNeeded(it) }

    fun deleteAll() {
        connectionRepository.deleteAll()
    }
}
