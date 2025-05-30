package dev.kviklet.kviklet.service

import dev.kviklet.kviklet.controller.UpdateConnectionRequest
import dev.kviklet.kviklet.controller.UpdateDatasourceConnectionRequest
import dev.kviklet.kviklet.controller.UpdateKubernetesConnectionRequest
import dev.kviklet.kviklet.db.ConnectionAdapter
import dev.kviklet.kviklet.db.ReviewConfig
import dev.kviklet.kviklet.security.Permission
import dev.kviklet.kviklet.security.Policy
import dev.kviklet.kviklet.service.dto.AuthenticationDetails
import dev.kviklet.kviklet.service.dto.AuthenticationType
import dev.kviklet.kviklet.service.dto.Connection
import dev.kviklet.kviklet.service.dto.ConnectionId
import dev.kviklet.kviklet.service.dto.DatabaseProtocol
import dev.kviklet.kviklet.service.dto.DatasourceConnection
import dev.kviklet.kviklet.service.dto.DatasourceType
import dev.kviklet.kviklet.service.dto.KubernetesConnection
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service

class EntityNotFound(override val message: String, val detail: String) : Exception(message)

data class TestConnectionResult(
    val success: Boolean,
    val message: String,
    val accessibleDatabases: List<String> = emptyList(),
)

@Service
class ConnectionService(
    private val connectionAdapter: ConnectionAdapter,
    private val JDBCExecutor: JDBCExecutor,
    private val mongoDBExecutor: MongoDBExecutor,
) {

    @Transactional
    @Policy(Permission.DATASOURCE_CONNECTION_GET)
    fun listConnections(): List<Connection> = connectionAdapter.listConnections()

    private fun updateDatasourceConnection(
        connectionId: ConnectionId,
        request: UpdateDatasourceConnectionRequest,
    ): Connection {
        val connection = connectionAdapter.getConnection(
            connectionId,
        ) as DatasourceConnection

        return connectionAdapter.updateDatasourceConnection(
            connectionId,
            request.displayName ?: connection.displayName,
            request.description ?: connection.description,
            request.type ?: connection.type,
            request.protocol ?: connection.protocol,
            request.maxExecutions ?: connection.maxExecutions,
            request.hostname ?: connection.hostname,
            request.port ?: connection.port,
            request.authenticationType ?: connection.authenticationType,
            getUpdatedAuth(connection.auth, request),
            request.databaseName ?: connection.databaseName,
            request.reviewConfig?.let {
                ReviewConfig(
                    it.numTotalRequired,
                )
            } ?: connection.reviewConfig,
            request.additionalJDBCOptions ?: connection.additionalOptions,
            dumpsEnabled = request.dumpsEnabled ?: connection.dumpsEnabled,
            temporaryAccessEnabled = request.temporaryAccessEnabled ?: connection.temporaryAccessEnabled,
            explainEnabled = request.explainEnabled ?: connection.explainEnabled,
            roleArn = request.roleArn,
        )
    }

    private fun getUpdatedAuth(
        currentAuth: AuthenticationDetails,
        request: UpdateDatasourceConnectionRequest,
    ): AuthenticationDetails {
        // If auth type is changing, create new auth details
        if (request.authenticationType != null) {
            return when (request.authenticationType) {
                AuthenticationType.USER_PASSWORD -> AuthenticationDetails.UserPassword(
                    username = request.username ?: currentAuth.username,
                    password = request.password
                        ?: (currentAuth as? AuthenticationDetails.UserPassword)?.password
                        ?: throw IllegalArgumentException("Password required for USER_PASSWORD authentication"),
                )
                AuthenticationType.AWS_IAM -> AuthenticationDetails.AwsIam(
                    username = request.username ?: currentAuth.username,
                    roleArn = request.roleArn ?: (currentAuth as? AuthenticationDetails.AwsIam)?.roleArn,
                )
            }
        }

        // If auth type isn't changing, just update the existing fields
        return when (currentAuth) {
            is AuthenticationDetails.UserPassword -> currentAuth.copy(
                username = request.username ?: currentAuth.username,
                password = request.password ?: currentAuth.password,
            )
            is AuthenticationDetails.AwsIam -> currentAuth.copy(
                username = request.username ?: currentAuth.username,
            )
        }
    }

    private fun updateKubernetesConnection(
        connectionId: ConnectionId,
        request: UpdateKubernetesConnectionRequest,
    ): Connection {
        val connection = connectionAdapter.getConnection(
            connectionId,
        ) as KubernetesConnection

        return connectionAdapter.updateKubernetesConnection(
            connectionId,
            request.displayName ?: connection.displayName,
            request.description ?: connection.description,
            request.reviewConfig?.let {
                ReviewConfig(
                    it.numTotalRequired,
                )
            } ?: connection.reviewConfig,
            request.maxExecutions ?: connection.maxExecutions,
        )
    }

    @Transactional
    @Policy(Permission.DATASOURCE_CONNECTION_EDIT)
    fun updateConnection(connectionId: ConnectionId, request: UpdateConnectionRequest): Connection {
        val connection = connectionAdapter.getConnection(
            connectionId,
        )

        if (request is UpdateDatasourceConnectionRequest && connection is DatasourceConnection) {
            return updateDatasourceConnection(connectionId, request)
        } else if (request is UpdateKubernetesConnectionRequest && connection is KubernetesConnection) {
            return updateKubernetesConnection(connectionId, request)
        } else {
            throw EntityNotFound("Connection not found", "Connection with id $connectionId not found")
        }
    }

    @Transactional
    @Policy(Permission.DATASOURCE_CONNECTION_CREATE)
    fun createDatasourceConnection(
        connectionId: ConnectionId,
        displayName: String,
        databaseName: String?,
        maxExecutions: Int?,
        username: String,
        password: String?,
        authenticationType: AuthenticationType,
        description: String,
        reviewsRequired: Int,
        port: Int,
        hostname: String,
        type: DatasourceType,
        protocol: DatabaseProtocol,
        additionalJDBCOptions: String,
        dumpsEnabled: Boolean,
        temporaryAccessEnabled: Boolean,
        explainEnabled: Boolean,
        roleArn: String?,
    ): Connection {
        if (authenticationType == AuthenticationType.USER_PASSWORD && password == null) {
            throw IllegalArgumentException("Password is required for USER_PASSWORD authentication")
        }
        return connectionAdapter.createDatasourceConnection(
            connectionId,
            displayName,
            authenticationType,
            databaseName,
            maxExecutions,
            username,
            password,
            description,
            ReviewConfig(
                numTotalRequired = reviewsRequired,
            ),
            port,
            hostname,
            type,
            protocol,
            additionalJDBCOptions,
            dumpsEnabled,
            temporaryAccessEnabled,
            explainEnabled,
            roleArn,
        )
    }

    @Policy(Permission.DATASOURCE_CONNECTION_CREATE, checkIsPresentOnly = true)
    fun testDatabaseConnection(
        connectionId: ConnectionId,
        displayName: String,
        databaseName: String?,
        maxExecutions: Int?,
        username: String,
        password: String?,
        description: String,
        reviewsRequired: Int,
        port: Int,
        hostname: String,
        type: DatasourceType,
        protocol: DatabaseProtocol,
        additionalJDBCOptions: String,
        dumpsEnabled: Boolean,
        authenticationType: AuthenticationType,
        temporaryAccessEnabled: Boolean,
        explainEnabled: Boolean,
        roleArn: String?,
    ): TestConnectionResult {
        val connection = DatasourceConnection(
            connectionId,
            displayName,
            description,
            reviewConfig = ReviewConfig(reviewsRequired),
            maxExecutions,
            databaseName,
            authenticationType,
            when (authenticationType) {
                AuthenticationType.USER_PASSWORD -> AuthenticationDetails.UserPassword(username, password ?: "")
                AuthenticationType.AWS_IAM -> AuthenticationDetails.AwsIam(username, roleArn)
            },
            port,
            hostname,
            type,
            protocol,
            additionalJDBCOptions,
            dumpsEnabled,
            temporaryAccessEnabled,
            explainEnabled,
        )
        val accessibleDatabases = mutableListOf<String>()
        if (!databaseName.isNullOrBlank()) {
            accessibleDatabases.add(databaseName)
        }
        val credentialsResult = when (protocol) {
            DatabaseProtocol.MONGODB, DatabaseProtocol.MONGODB_SRV -> {
                mongoDBExecutor.testCredentials(
                    connectionString = connection.getConnectionString(),
                )
            }
            else ->
                JDBCExecutor.testCredentials(
                    connectionString = connection.getConnectionString(),
                    authenticationDetails = connection.auth,
                )
        }
        if (type == DatasourceType.POSTGRESQL && credentialsResult.success) {
            val databases = JDBCExecutor.getAccessibleDatabasesPostgres(
                connectionString = "jdbc:${protocol.uriString}://$hostname:$port/$databaseName",
                authenticationDetails = connection.auth,
            )
            accessibleDatabases.addAll(databases)
        }
        return TestConnectionResult(
            success = credentialsResult.success,
            message = credentialsResult.message,
            accessibleDatabases = accessibleDatabases,
        )
    }

    @Transactional
    @Policy(Permission.DATASOURCE_CONNECTION_CREATE)
    fun createKubernetesConnection(
        connectionId: ConnectionId,
        displayName: String,
        description: String,
        reviewsRequired: Int,
        maxExecutions: Int?,
    ): Connection = connectionAdapter.createKubernetesConnection(
        connectionId,
        displayName,
        description,
        ReviewConfig(
            numTotalRequired = reviewsRequired,
        ),
        maxExecutions,
    )

    @Transactional
    @Policy(Permission.DATASOURCE_CONNECTION_EDIT)
    fun deleteDatasourceConnection(connectionId: ConnectionId) {
        connectionAdapter.deleteConnection(connectionId)
    }

    @Transactional
    @Policy(Permission.DATASOURCE_CONNECTION_GET)
    fun getDatasourceConnection(connectionId: ConnectionId): Connection = connectionAdapter.getConnection(
        connectionId = connectionId,
    )
}
