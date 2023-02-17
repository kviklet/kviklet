package com.example.executiongate.service.dto

enum class DatasourceType(val schema: String) {
    POSTGRESQL("postgresql"),
    MYSQL("mysql")
}


enum class AuthenticationType {
    USER_PASSWORD,
    // other: aws iam, gpc, env var
}

data class DatasourceDto(
    val id: String,
    val displayName: String,
    val type: DatasourceType,
    val hostname: String,
    val port: Int,
    val datasourceConnections: List<DatasourceConnectionDto>
) {
    fun getConnectionString() = "jdbc:${type.schema}://$hostname:$port"
}

data class DatasourceConnectionDto(
    val id: String,
    val displayName: String,
    val authenticationType: AuthenticationType,
    val username: String,
    val password: String,
)


