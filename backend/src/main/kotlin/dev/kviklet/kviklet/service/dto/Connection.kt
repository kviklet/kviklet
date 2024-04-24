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
}

enum class AuthenticationType {
    USER_PASSWORD,
    // other: aws iam, gpc, env var
}

data class ConnectionId
@JsonCreator constructor(private val id: String) : Serializable, SecuredDomainId {
    @JsonValue
    override fun toString() = id
}

sealed class Connection(
    open val id: ConnectionId,
    open val displayName: String,
    open val description: String,
    open val reviewConfig: ReviewConfig,
) : SecuredDomainObject {
    override fun getId() = id.toString()
    override fun getDomainObjectType() = Resource.DATASOURCE_CONNECTION

    override fun getRelated(resource: Resource): SecuredDomainObject {
        return when (resource) {
            Resource.DATASOURCE_CONNECTION -> this
            else -> throw IllegalStateException("Unexpected resource: $resource")
        }
    }
}

data class DatasourceConnection(
    override val id: ConnectionId,
    override val displayName: String,
    override val description: String,
    override val reviewConfig: ReviewConfig,
    val databaseName: String?,
    val authenticationType: AuthenticationType,
    val username: String,
    val password: String,
    val port: Int,
    val hostname: String,
    val type: DatasourceType,
) : Connection(id, displayName, description, reviewConfig) {
    fun getConnectionString() = "jdbc:${type.schema}://$hostname:$port/" + (databaseName ?: "")
}

data class KubernetesConnection(
    override val id: ConnectionId,
    override val displayName: String,
    override val description: String,
    override val reviewConfig: ReviewConfig,
) : Connection(id, displayName, description, reviewConfig) {
    // methods
}
