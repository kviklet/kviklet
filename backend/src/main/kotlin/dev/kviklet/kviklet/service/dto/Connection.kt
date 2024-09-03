package dev.kviklet.kviklet.service.dto

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue
import dev.kviklet.kviklet.db.ReviewConfig
import dev.kviklet.kviklet.security.Resource
import dev.kviklet.kviklet.security.SecuredDomainId
import dev.kviklet.kviklet.security.SecuredDomainObject
import java.io.Serializable

enum class DatasourceType(val schema: String) {
    POSTGRESQL("postgresql"),
    MYSQL("mysql"),
    MSSQL("sqlserver"),
    MONGODB("mongodb"),
    MARIADB("mariadb"),
    ;

    fun toProtocol(): DatabaseProtocol = when (this) {
        POSTGRESQL -> DatabaseProtocol.POSTGRESQL
        MYSQL -> DatabaseProtocol.MYSQL
        MSSQL -> DatabaseProtocol.MSSQL
        MONGODB -> DatabaseProtocol.MONGODB
        MARIADB -> DatabaseProtocol.MARIADB
    }
}

enum class DatabaseProtocol(val uriString: String) {
    POSTGRESQL("postgresql"),
    MYSQL("mysql"),
    MSSQL("sqlserver"),
    MARIADB("mariadb"),
    MONGODB("mongodb"),
    MONGODB_SRV("mongodb+srv"),
}

enum class AuthenticationType {
    USER_PASSWORD,
    // other: aws iam, gpc, env var
}

data class ConnectionId
@JsonCreator constructor(private val id: String) :
    Serializable,
    SecuredDomainId {
    @JsonValue
    override fun toString() = id
}

sealed class Connection(
    open val id: ConnectionId,
    open val displayName: String,
    open val description: String,
    open val reviewConfig: ReviewConfig,
    open val maxExecutions: Int?,
) : SecuredDomainObject {

    fun getId() = id.toString()
    override fun getSecuredObjectId() = id.toString()
    override fun getDomainObjectType() = Resource.DATASOURCE_CONNECTION

    override fun getRelated(resource: Resource): SecuredDomainObject? = when (resource) {
        Resource.DATASOURCE_CONNECTION -> this
        Resource.EXECUTION_REQUEST -> null
        else -> throw IllegalStateException("Unexpected resource: $resource")
    }
}

data class DatasourceConnection(
    override val id: ConnectionId,
    override val displayName: String,
    override val description: String,
    override val reviewConfig: ReviewConfig,
    override val maxExecutions: Int?,
    val databaseName: String?,
    val authenticationType: AuthenticationType,
    val username: String,
    val password: String,
    val port: Int,
    val hostname: String,
    val type: DatasourceType,
    val protocol: DatabaseProtocol,
    val additionalOptions: String,
    val dumpsEnabled: Boolean,
) : Connection(id, displayName, description, reviewConfig, maxExecutions) {
    fun getConnectionString(): String = when (type) {
        DatasourceType.POSTGRESQL ->
            "jdbc:postgresql://$hostname:$port/" +
                databaseName +
                additionalOptions
        DatasourceType.MYSQL ->
            "jdbc:mysql://$hostname:$port/" +
                databaseName +
                additionalOptions
        DatasourceType.MSSQL ->
            "jdbc:sqlserver://$hostname:$port" +
                (databaseName?.takeIf { it.isNotBlank() }?.let { ";databaseName=$databaseName" } ?: "") +
                additionalOptions
        DatasourceType.MARIADB ->
            "jdbc:mariadb://$hostname:$port/" +
                databaseName +
                additionalOptions
        DatasourceType.MONGODB -> {
            val credentialString = if (username.isNotBlank() && password.isNotBlank()) {
                "$username:$password@"
            } else {
                ""
            }
            "${protocol.uriString}://$credentialString$hostname${if (protocol == DatabaseProtocol.MONGODB_SRV) {
                ""
            } else {
                ":$port"
            }}/" +
                (databaseName ?: "") +
                additionalOptions
        }
    }
}

data class KubernetesConnection(
    override val id: ConnectionId,
    override val displayName: String,
    override val description: String,
    override val reviewConfig: ReviewConfig,
    override val maxExecutions: Int?,
) : Connection(id, displayName, description, reviewConfig, maxExecutions)
