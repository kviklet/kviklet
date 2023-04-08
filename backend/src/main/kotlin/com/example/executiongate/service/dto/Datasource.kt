package com.example.executiongate.service.dto

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
value class DatasourceId(private val id: String): Serializable {
    override fun toString() = id
}

data class DatasourceDto(
    val id: DatasourceId,
    val displayName: String,
    val type: DatasourceType,
    val hostname: String,
    val port: Int,
    val datasourceConnections: List<DatasourceConnectionDto>
) {
    fun getConnectionString() = "jdbc:${type.schema}://$hostname:$port"
}

@JvmInline
value class DatasourceConnectionId(private val id: String): Serializable {
    override fun toString() = id
}

data class DatasourceConnectionDto(
    val id: DatasourceConnectionId,
    val displayName: String,
    val authenticationType: AuthenticationType,
    val username: String,
    val password: String,
)


