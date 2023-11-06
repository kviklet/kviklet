package com.example.executiongate.service.dto

import com.example.executiongate.db.ReviewConfig
import com.example.executiongate.security.Resource
import com.example.executiongate.security.SecuredDomainId
import com.example.executiongate.security.SecuredDomainObject
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import java.io.Serializable

enum class DatasourceType(val schema: String) {
    POSTGRESQL("postgresql"),
    MYSQL("mysql"),
}

enum class AuthenticationType {
    USER_PASSWORD,
    // other: aws iam, gpc, env var
}

@JsonDeserialize(using = IdDeserializer::class)
@JsonSerialize(using = IdSerializer::class)
data class DatasourceId
@JsonCreator(mode = JsonCreator.Mode.DELEGATING)
constructor(
    private val id: String,
) : Serializable, SecuredDomainId {
    override fun toString() = id
}

class IdDeserializer : JsonDeserializer<DatasourceId>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): DatasourceId {
        return DatasourceId(ctxt.readValue(p, String::class.java))
    }
}
class IdSerializer : JsonSerializer<DatasourceId>() {
    override fun serialize(value: DatasourceId?, gen: JsonGenerator?, serializers: SerializerProvider?) {
        gen?.writeString(value.toString())
    }
}

data class Datasource(
    val id: DatasourceId,
    val displayName: String,
    val type: DatasourceType,
    val hostname: String,
    val port: Int,
//    var datasourceConnections: List<DatasourceConnection>,
) : SecuredDomainObject {
    fun getConnectionString() = "jdbc:${type.schema}://$hostname:$port/"
    override fun getId() = id.toString()
    override fun getDomainObjectType() = Resource.DATASOURCE

    override fun getRelated(resource: Resource): SecuredDomainObject? {
        return when (resource) {
            Resource.DATASOURCE -> this
            Resource.DATASOURCE_CONNECTION -> null
            else -> throw IllegalStateException("Unexpected resource: $resource")
        }
    }
}

@JvmInline
value class DatasourceConnectionId(private val id: String) : Serializable, SecuredDomainId {
    override fun toString() = id
}

data class DatasourceConnection(
    val id: DatasourceConnectionId,
    val datasource: Datasource,
    val displayName: String,
    val databaseName: String?,
    val authenticationType: AuthenticationType,
    val username: String,
    val password: String,
    val description: String,
    val reviewConfig: ReviewConfig,
) : SecuredDomainObject {
    fun getConnectionString() = datasource.getConnectionString() + (databaseName ?: "")
    override fun getId() = id.toString()
    override fun getDomainObjectType() = Resource.DATASOURCE_CONNECTION

    override fun getRelated(resource: Resource): SecuredDomainObject {
        return when (resource) {
            Resource.DATASOURCE_CONNECTION -> this
            Resource.DATASOURCE -> datasource
            else -> throw IllegalStateException("Unexpected resource: $resource")
        }
    }
}
