package dev.kviklet.kviklet.service.dto

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
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

@JsonDeserialize(using = DatasourceIdDeserializer::class)
@JsonSerialize(using = DatasourceIdSerializer::class)
data class DatasourceId
@JsonCreator(mode = JsonCreator.Mode.DELEGATING)
constructor(
    private val id: String,
) : Serializable, SecuredDomainId {
    override fun toString() = id
}

class DatasourceIdDeserializer : JsonDeserializer<DatasourceId>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): DatasourceId {
        return DatasourceId(ctxt.readValue(p, String::class.java))
    }
}
class DatasourceIdSerializer : JsonSerializer<DatasourceId>() {
    override fun serialize(value: DatasourceId?, gen: JsonGenerator?, serializers: SerializerProvider?) {
        gen?.writeString(value.toString())
    }
}

class DatasourceConnectionIdDeserializer : JsonDeserializer<DatasourceConnectionId>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): DatasourceConnectionId {
        return DatasourceConnectionId(ctxt.readValue(p, String::class.java))
    }
}
class DatasourceConnectionIdSerializer : JsonSerializer<DatasourceConnectionId>() {
    override fun serialize(value: DatasourceConnectionId?, gen: JsonGenerator?, serializers: SerializerProvider?) {
        gen?.writeString(value.toString())
    }
}

@JsonDeserialize(using = DatasourceConnectionIdDeserializer::class)
@JsonSerialize(using = DatasourceConnectionIdSerializer::class)
data class DatasourceConnectionId(private val id: String) : Serializable, SecuredDomainId {
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
