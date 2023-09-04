package com.example.executiongate.service.dto

import com.example.executiongate.db.ReviewConfig
import com.example.executiongate.security.SecuredDomainObject
import java.io.Serializable

enum class DatasourceType(val schema: String) {
    POSTGRESQL("postgresql"),
    MYSQL("mysql")
}


enum class AuthenticationType {
    USER_PASSWORD,
    // other: aws iam, gpc, env var
}

@JvmInline
value class DatasourceId(private val id: String): Serializable, SecuredDomainObject {
    override fun getId() = id
    override fun toString() = id
}

data class Datasource(
    val id: DatasourceId,
    val displayName: String,
    val type: DatasourceType,
    val hostname: String,
    val port: Int,
    var datasourceConnections: List<DatasourceConnection>
) : SecuredDomainObject {
    fun getConnectionString() = "jdbc:${type.schema}://$hostname:$port/"
    override fun getId() = id.toString()
}

@JvmInline
value class DatasourceConnectionId(private val id: String): Serializable {
    override fun toString() = id
}

data class DatasourceConnection(
    val id: DatasourceConnectionId,
    val datasource: Datasource,
    val displayName: String,
    val authenticationType: AuthenticationType,
    val username: String,
    val password: String,
    val description: String,
    val reviewConfig: ReviewConfig,
) : SecuredDomainObject {
    fun getConnectionString() = datasource.getConnectionString()
    override fun getId() = id.toString()
}
