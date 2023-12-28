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

data class DatasourceConnectionId
@JsonCreator constructor(private val id: String) : Serializable, SecuredDomainId {
    @JsonValue
    override fun toString() = id
}

data class DatasourceConnection(
    val id: DatasourceConnectionId,
    val displayName: String,
    val databaseName: String?,
    val authenticationType: AuthenticationType,
    val username: String,
    val password: String,
    val description: String,
    val reviewConfig: ReviewConfig,
    val port: Int,
    val hostname: String,
    val type: DatasourceType,
) : SecuredDomainObject {
    fun getConnectionString() = "jdbc:${type.schema}://$hostname:$port/" + (databaseName ?: "")
    override fun getId() = id.toString()
    override fun getDomainObjectType() = Resource.DATASOURCE_CONNECTION

    override fun getRelated(resource: Resource): SecuredDomainObject {
        return when (resource) {
            Resource.DATASOURCE_CONNECTION -> this
            else -> throw IllegalStateException("Unexpected resource: $resource")
        }
    }
}
