package dev.kviklet.kviklet.controller

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import dev.kviklet.kviklet.db.ConnectionType
import dev.kviklet.kviklet.service.ConnectionService
import dev.kviklet.kviklet.service.dto.AuthenticationType
import dev.kviklet.kviklet.service.dto.Connection
import dev.kviklet.kviklet.service.dto.ConnectionId
import dev.kviklet.kviklet.service.dto.DatasourceConnection
import dev.kviklet.kviklet.service.dto.DatasourceType
import dev.kviklet.kviklet.service.dto.KubernetesConnection
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "connectionType")
@JsonSubTypes(
    JsonSubTypes.Type(value = CreateDatasourceConnectionRequest::class, name = "DATASOURCE"),
    JsonSubTypes.Type(value = CreateKubernetesConnectionRequest::class, name = "KUBERNETES"),
)
sealed class ConnectionRequest

data class CreateKubernetesConnectionRequest(
    @Schema(example = "k8s-conn1")
    @field:Size(max = 255, message = "Maximum length 255")
    @field:Pattern(regexp = "^[a-zA-Z0-9-]+$", message = "Only alphanumeric and dashes (-) allowed")
    val id: String,

    @Schema(example = "My Kubernetes Connection")
    @field:Size(max = 255, message = "Maximum length 255")
    val displayName: String,

    val description: String = "",

    val reviewConfig: ReviewConfigRequest,
) : ConnectionRequest()

data class CreateDatasourceConnectionRequest(
    @Schema(example = "postgres-read-only")
    @field:Size(max = 255, message = "Maximum length 255")
    @field:Pattern(regexp = "^[a-zA-Z0-9-]+$", message = "Only alphanumeric and dashes (-) allowed")
    val id: String,

    @Schema(example = "My Postgres Db User")
    @field:Size(max = 255, message = "Maximum length 255")
    val displayName: String,

    @Schema(example = "postgres")
    val databaseName: String? = null,

    @Schema(example = "root")
    @field:Size(min = 1, max = 255, message = "Maximum length 255")
    val username: String,

    @Schema(example = "root")
    @field:Size(min = 1, max = 255, message = "Maximum length 255")
    val password: String,

    val description: String = "",

    val reviewConfig: ReviewConfigRequest,

    val type: DatasourceType,
    val hostname: String,
    val port: Int,
) : ConnectionRequest()

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "connectionType")
@JsonSubTypes(
    JsonSubTypes.Type(value = UpdateDatasourceConnectionRequest::class, name = "DATASOURCE"),
    JsonSubTypes.Type(value = UpdateKubernetesConnectionRequest::class, name = "KUBERNETES"),
)
sealed class UpdateConnectionRequest

data class UpdateDatasourceConnectionRequest(
    @Schema(example = "My Postgres Db User")
    @field:Size(max = 255, message = "Maximum length 255")
    val displayName: String? = null,

    @Schema(example = "postgres")
    val databaseName: String? = null,

    @Schema(example = "root")
    @field:Size(min = 1, max = 255, message = "Maximum length 255")
    val username: String? = null,

    @Schema(example = "root")
    @field:Size(min = 1, max = 255, message = "Maximum length 255")
    val password: String? = null,

    val description: String? = null,

    val reviewConfig: ReviewConfigRequest? = null,
) : UpdateConnectionRequest()

data class UpdateKubernetesConnectionRequest(
    @Schema(example = "My Kubernetes Connection")
    @field:Size(max = 255, message = "Maximum length 255")
    val displayName: String? = null,

    val description: String? = null,

    val reviewConfig: ReviewConfigRequest? = null,
) : UpdateConnectionRequest()

data class ReviewConfigRequest(
    val numTotalRequired: Int = 0,
)

data class ReviewConfigResponse(
    val numTotalRequired: Int = 0,
)

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "connectionType")
@JsonSubTypes(
    JsonSubTypes.Type(value = UpdateDatasourceConnectionRequest::class, name = "DATASOURCE"),
    JsonSubTypes.Type(value = UpdateKubernetesConnectionRequest::class, name = "KUBERNETES"),
)
sealed class ConnectionResponse(
    val connectionType: ConnectionType,
) {
    companion object {
        fun fromDto(connection: Connection): ConnectionResponse {
            return when (connection) {
                is DatasourceConnection -> DatasourceConnectionResponse.fromDto(connection)
                is KubernetesConnection -> KubernetesConnectionResponse.fromDto(connection)
                else -> throw IllegalArgumentException("Unsupported connection type")
            }
        }
    }
}

data class DatasourceConnectionResponse(
    val id: ConnectionId,
    val authenticationType: AuthenticationType,
    val displayName: String,
    val databaseName: String?,
    val username: String,
    val description: String,
    val reviewConfig: ReviewConfigResponse,
) : ConnectionResponse(ConnectionType.DATASOURCE) {
    companion object {
        fun fromDto(datasourceConnection: DatasourceConnection) = DatasourceConnectionResponse(
            id = datasourceConnection.id,
            authenticationType = datasourceConnection.authenticationType,
            displayName = datasourceConnection.displayName,
            databaseName = datasourceConnection.databaseName,
            username = datasourceConnection.username,
            description = datasourceConnection.description,
            reviewConfig = ReviewConfigResponse(
                datasourceConnection.reviewConfig.numTotalRequired,
            ),
        )
    }
}

data class KubernetesConnectionResponse(
    val id: ConnectionId,
    val displayName: String,
    val description: String,
    val reviewConfig: ReviewConfigResponse,
) : ConnectionResponse(connectionType = ConnectionType.KUBERNETES) {
    companion object {
        fun fromDto(kubernetesConnection: KubernetesConnection) = KubernetesConnectionResponse(
            id = kubernetesConnection.id,
            displayName = kubernetesConnection.displayName,
            description = kubernetesConnection.description,
            reviewConfig = ReviewConfigResponse(
                kubernetesConnection.reviewConfig.numTotalRequired,
            ),
        )
    }
}

@RestController()
@Validated
@RequestMapping("/connections")
@Tag(
    name = "Datasource Connections",
)
class ConnectionController(
    val connectionService: ConnectionService,
) {

    @GetMapping("/{connectionId}")
    fun getConnection(@PathVariable connectionId: String): ConnectionResponse {
        val connection = connectionService.getDatasourceConnection(
            connectionId = ConnectionId(connectionId),
        )
        return ConnectionResponse.fromDto(connection)
    }

    @GetMapping("/")
    fun getDatasourceConnections(): List<ConnectionResponse> {
        val datasourceConnections = connectionService.listConnections()
        return datasourceConnections.map { ConnectionResponse.fromDto(it) }
    }

    private fun createDatasourceConnection(request: CreateDatasourceConnectionRequest): Connection {
        return connectionService.createDatasourceConnection(
            connectionId = ConnectionId(request.id),
            displayName = request.displayName,
            databaseName = request.databaseName,
            username = request.username,
            password = request.password,
            description = request.description,
            reviewsRequired = request.reviewConfig.numTotalRequired,
            port = request.port,
            hostname = request.hostname,
            type = request.type,
        )
    }

    private fun createKubernetesConnection(request: CreateKubernetesConnectionRequest): Connection {
        return connectionService.createKubernetesConnection(
            connectionId = ConnectionId(request.id),
            displayName = request.displayName,
            description = request.description,
            reviewsRequired = request.reviewConfig.numTotalRequired,
        )
    }

    @PostMapping("/")
    fun createConnection(
        @Valid @RequestBody
        datasourceConnection: ConnectionRequest,
    ): ConnectionResponse {
        val connection = when (datasourceConnection) {
            is CreateDatasourceConnectionRequest -> createDatasourceConnection(datasourceConnection)
            is CreateKubernetesConnectionRequest -> createKubernetesConnection(datasourceConnection)
        }

        return ConnectionResponse.fromDto(connection)
    }

    @DeleteMapping("/{connectionId}")
    fun deleteConnection(@PathVariable connectionId: String) {
        connectionService.deleteDatasourceConnection(
            connectionId = ConnectionId(connectionId),
        )
    }

    @PatchMapping("/{connectionId}")
    fun updateConnection(
        @PathVariable connectionId: String,
        @Valid @RequestBody
        datasourceConnection: UpdateConnectionRequest,
    ): ConnectionResponse {
        val connection = connectionService.updateConnection(
            connectionId = ConnectionId(connectionId),
            request = datasourceConnection,
        )
        return ConnectionResponse.fromDto(connection)
    }
}
