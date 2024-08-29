package dev.kviklet.kviklet.db

import dev.kviklet.kviklet.db.util.ReviewConfigConverter
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

data class ReviewConfig(val numTotalRequired: Int, val fourEyesRequired: Boolean)

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

    @Column(name = "username")
    var storedUsername: String? = null,

    @Column(name = "password")
    var storedPassword: String? = null,

    @Transient
    var username: String? = null,

    @Transient
    var password: String? = null,

    // Datasource Connection fields
    @Enumerated(EnumType.STRING)
    var authenticationType: AuthenticationType? = null,
    var databaseName: String? = null,
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
}

interface ConnectionRepository : JpaRepository<ConnectionEntity, String>

@Service
class ConnectionAdapter(
    val connectionRepository: ConnectionRepository,
    private val encryptionService: EncryptionService,
    private val encryptionConfig: EncryptionConfigProperties,
) {
    private fun save(connection: ConnectionEntity): ConnectionEntity {
        val unencryptedUsername = connection.username
        val unencryptedPassword = connection.password
        if (encryptionConfig.enabled) {
            connection.storedUsername = unencryptedUsername?.let { encryptionService.encrypt(it) }
            connection.storedPassword = unencryptedPassword?.let { encryptionService.encrypt(it) }
            connection.isEncrypted = true
        } else {
            connection.storedUsername = unencryptedUsername
            connection.storedPassword = unencryptedPassword
            connection.isEncrypted = false
        }
        val connectionEntity = connectionRepository.save(connection)
        connectionEntity.username = unencryptedUsername
        connectionEntity.password = unencryptedPassword
        return connectionEntity
    }

    @Transactional
    fun getConnection(connectionId: ConnectionId): Connection {
        val entity = connectionRepository.findByIdOrNull(connectionId.toString())
            ?: throw EntityNotFound("Connection Not Found", "Connection $connectionId does not exist.")

        return decryptCredentialsIfNeeded(entity)
    }

    /*
     * Decrypts the credentials of a connection if needed. And populates the username and password fields.
     * Always use this method to get the connection object, don't call toDtoDirectly directly.
     */
    private fun decryptCredentialsIfNeeded(connection: ConnectionEntity): Connection {
        if (!encryptionConfig.enabled && !connection.isEncrypted) {
            connection.username = connection.storedUsername
            connection.password = connection.storedPassword
            return toDtoDirectly(connection)
        }
        if (!encryptionConfig.enabled) {
            tryDecryptAndSave(connection)
            return toDtoDirectly(connection)
        }
        if (connection.isEncrypted) {
            connection.username = connection.storedUsername?.let { encryptionService.decrypt(it) }
            connection.password = connection.storedPassword?.let { encryptionService.decrypt(it) }

            val dto = toDtoDirectly(connection)
            reEncryptAndSaveIfNeeded(connection)
            return dto
        } else {
            connection.username = connection.storedUsername
            connection.password = connection.storedPassword
            val dto = toDtoDirectly(connection)
            // encrypt and save again to ensure that the connection is now encrypted
            save(connection)
            return dto
        }
    }

    private fun tryDecryptAndSave(connection: ConnectionEntity) {
        connection.username = connection.storedUsername?.let { encryptionService.decrypt(it) }
        connection.password = connection.storedPassword?.let { encryptionService.decrypt(it) }
        save(connection)
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
    ): Connection = decryptCredentialsIfNeeded(
        save(
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
        ),
    )

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

        return decryptCredentialsIfNeeded(save(datasourceConnection))
    }

    fun updateKubernetesConnection(
        id: ConnectionId,
        displayName: String,
        description: String,
        reviewConfig: ReviewConfig,
        maxExecutions: Int?
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

        return decryptCredentialsIfNeeded(save(datasourceConnection))
    }

    @Transactional
    fun createKubernetesConnection(
        connectionId: ConnectionId,
        displayName: String,
        description: String,
        reviewConfig: ReviewConfig,
        maxExecutions: Int?,
    ): Connection = decryptCredentialsIfNeeded(
        save(
            ConnectionEntity(
                id = connectionId.toString(),
                displayName = displayName,
                description = description,
                reviewConfig = reviewConfig,
                connectionType = ConnectionType.KUBERNETES,
                maxExecutions = maxExecutions,
            ),
        ),
    )

    fun deleteConnection(id: ConnectionId) {
        connectionRepository.deleteById(id.toString())
    }

    fun listConnections(): List<Connection> = connectionRepository.findAll().map { decryptCredentialsIfNeeded(it) }

    fun deleteAll() {
        connectionRepository.deleteAll()
    }

    fun toDto(connection: ConnectionEntity): Connection = decryptCredentialsIfNeeded(connection)

    private fun toDtoDirectly(connection: ConnectionEntity): Connection = when (connection.connectionType) {
        ConnectionType.DATASOURCE ->
            DatasourceConnection(
                id = ConnectionId(connection.id),
                displayName = connection.displayName,
                authenticationType = connection.authenticationType!!,
                databaseName = connection.databaseName,
                maxExecutions = connection.maxExecutions,
                username = connection.username!!,
                password = connection.password!!,
                description = connection.description,
                reviewConfig = connection.reviewConfig,
                port = connection.port!!,
                hostname = connection.hostname!!,
                type = connection.datasourceType!!,
                protocol = connection.protocol ?: connection.datasourceType!!.toProtocol(),
                additionalOptions = connection.additionalJDBCOptions ?: "",
            )
        ConnectionType.KUBERNETES ->
            KubernetesConnection(
                id = ConnectionId(connection.id),
                displayName = connection.displayName,
                description = connection.description,
                reviewConfig = connection.reviewConfig,
                maxExecutions = connection.maxExecutions,
            )
    }
}
