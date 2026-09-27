package dev.kviklet.kviklet.service.dto

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue
import dev.kviklet.kviklet.security.Permission
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
    AWS_IAM,
    // other: aws iam, gpc, env var
}

enum class ConnectionType {
    DATASOURCE,
    KUBERNETES,
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
    open val category: String?,
) : SecuredDomainObject {

    fun getId() = id.toString()
    override fun getSecuredObjectId() = id.toString()
    override fun getDomainObjectType() = Resource.DATASOURCE_CONNECTION

    val connectionType: ConnectionType
        get() = when (this) {
            is DatasourceConnection -> ConnectionType.DATASOURCE
            is KubernetesConnection -> ConnectionType.KUBERNETES
        }

    override fun getRelated(resource: Resource): SecuredDomainObject? = when (resource) {
        Resource.DATASOURCE_CONNECTION -> this
        Resource.EXECUTION_REQUEST -> null
        else -> throw IllegalStateException("Unexpected resource: $resource")
    }
}

/**
 * A connection plus what the current user may do to it, i.e. the policy vote scoped to this
 * connection id plus [SecuredDomainObject.auth]. Delegates [SecuredDomainObject] to the connection
 * so `@Policy` collection filtering keeps working on lists of these.
 */
data class ConnectionWithPermissions(val connection: Connection, val permissions: Set<Permission>) :
    SecuredDomainObject by connection

sealed class AuthenticationDetails(open val username: String) {
    data class UserPassword(override val username: String, val password: String) : AuthenticationDetails(username)

    data class AwsIam(override val username: String, val roleArn: String? = null) : AuthenticationDetails(username)
}

data class DatasourceConnection(
    override val id: ConnectionId,
    override val displayName: String,
    override val description: String,
    override val reviewConfig: ReviewConfig,
    override val maxExecutions: Int?,
    override val category: String? = null,
    val databaseName: String?,
    val authenticationType: AuthenticationType,
    val auth: AuthenticationDetails,
    val port: Int,
    val hostname: String,
    val type: DatasourceType,
    val protocol: DatabaseProtocol,
    val additionalOptions: String,
    val dumpsEnabled: Boolean,
    val temporaryAccessEnabled: Boolean,
    val explainEnabled: Boolean,
    val storeResults: Boolean,
    val maxTemporaryAccessDuration: Long? = null,
    val dryRunEnabled: Boolean,
    val dryRunRequiresApproval: Boolean,
) : Connection(id, displayName, description, reviewConfig, maxExecutions, category) {
    fun getConnectionString(): String = when (auth) {
        is AuthenticationDetails.UserPassword -> when (type) {
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
                val credentialString = if (auth.username.isNotBlank() && auth.password.isNotBlank()) {
                    "${auth.username}:${auth.password}@"
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

        // RDS only accepts IAM tokens over TLS, so the driver is told to require it unless the connection's
        // own options already ask for that or more. The driver setting is appended, and every driver lets
        // the last value win, so it is only appended when it does not downgrade a configured verify-* mode.
        is AuthenticationDetails.AwsIam -> {
            val options = parseUrlOptions(additionalOptions)
            val (baseUrl, requiredSsl) = when (type) {
                DatasourceType.POSTGRESQL -> Pair(
                    "jdbc:postgresql://$hostname:$port/$databaseName",
                    // ssl=true without sslmode means verify-full in pgjdbc; sslmode takes precedence over ssl.
                    "sslmode=require".takeUnless {
                        options["sslmode"]?.lowercase() in setOf("require", "verify-ca", "verify-full") ||
                            (options["sslmode"] == null && options["ssl"]?.lowercase()?.let { it != "false" } == true)
                    },
                )

                DatasourceType.MYSQL -> Pair(
                    "jdbc:mysql://$hostname:$port/$databaseName",
                    "sslMode=REQUIRED".takeUnless {
                        options["sslmode"]?.uppercase() in setOf("REQUIRED", "VERIFY_CA", "VERIFY_IDENTITY") ||
                            (options["sslmode"] == null && options["verifyservercertificate"]?.lowercase() == "true")
                    },
                )

                DatasourceType.MARIADB -> Pair(
                    "jdbc:mariadb://$hostname:$port/$databaseName",
                    // MariaDB Connector/J has no REQUIRED mode; trust enforces TLS without cert verification
                    "sslMode=trust".takeUnless {
                        options["sslmode"]?.lowercase() in setOf("trust", "verify-ca", "verify-full")
                    },
                )

                else -> throw IllegalArgumentException("AWS IAM is not supported for $type")
            }
            if (requiredSsl == null) {
                baseUrl + additionalOptions
            } else {
                val delimiter = if (additionalOptions.isEmpty()) "?" else "&"
                baseUrl + additionalOptions + delimiter + requiredSsl
            }
        }
    }
}

// The key=value pairs of a JDBC URL's query string, keys lower-cased so lookups are spelling-insensitive
// (pgjdbc keys are lower-case, Connector/J's are camelCase).
private fun parseUrlOptions(additionalOptions: String): Map<String, String> = additionalOptions
    .removePrefix("?")
    .split("&")
    .mapNotNull { param ->
        val separator = param.indexOf('=')
        if (separator <= 0) null else param.substring(0, separator).trim().lowercase() to param.substring(separator + 1)
    }
    .toMap()

data class KubernetesConnection(
    override val id: ConnectionId,
    override val displayName: String,
    override val description: String,
    override val reviewConfig: ReviewConfig,
    override val maxExecutions: Int?,
    override val category: String? = null,
    val temporaryAccessEnabled: Boolean,
    val storeResults: Boolean,
    val kubernetesExecInitialWaitTimeoutSeconds: Long = 5L,
    val kubernetesExecTimeoutMinutes: Long = 60L,
) : Connection(id, displayName, description, reviewConfig, maxExecutions, category)
