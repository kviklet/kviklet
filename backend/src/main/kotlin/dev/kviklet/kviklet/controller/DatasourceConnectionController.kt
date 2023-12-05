package dev.kviklet.kviklet.controller

import dev.kviklet.kviklet.service.DatasourceConnectionService
import dev.kviklet.kviklet.service.dto.AuthenticationType
import dev.kviklet.kviklet.service.dto.DatasourceConnection
import dev.kviklet.kviklet.service.dto.DatasourceConnectionId
import dev.kviklet.kviklet.service.dto.DatasourceId
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
)

data class UpdateDataSourceConnectionRequest(
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
)

data class ReviewConfigRequest(
    val numTotalRequired: Int = 0,
)

data class ReviewConfigResponse(
    val numTotalRequired: Int = 0,
)

data class DatasourceConnectionResponse(
    val id: DatasourceConnectionId,
    val authenticationType: AuthenticationType,
    val displayName: String,
    val databaseName: String?,
    val username: String,
    val description: String,
    val reviewConfig: ReviewConfigResponse,
) {
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

@RestController()
@Validated
@RequestMapping("/datasources")
@Tag(
    name = "Datasource Connections",
)
class DatasourceConnectionController(
    val datasourceConnectionService: DatasourceConnectionService,
) {

    @GetMapping("/{datasourceId}/connections/{connectionId}")
    fun getDatasourceConnection(
        @PathVariable datasourceId: String,
        @PathVariable connectionId: String,
    ): DatasourceConnectionResponse {
        val datasourceConnection = datasourceConnectionService.getDatasourceConnection(
            datasourceId = DatasourceId(datasourceId),
            datasourceConnectionId = DatasourceConnectionId(connectionId),
        )
        return DatasourceConnectionResponse.fromDto(datasourceConnection)
    }

    @PostMapping("/{datasourceId}/connections")
    fun createDatasourceConnection(
        @PathVariable datasourceId: String,
        @Valid @RequestBody
        datasourceConnection: CreateDatasourceConnectionRequest,
    ): DatasourceConnectionResponse {
        val datasource = datasourceConnectionService.createDatasourceConnection(
            datasourceId = DatasourceId(datasourceId),
            request = datasourceConnection,
        )
        return DatasourceConnectionResponse.fromDto(datasource)
    }

    @DeleteMapping("/{datasourceId}/connections/{connectionId}")
    fun deleteDatasourceConnection(@PathVariable datasourceId: String, @PathVariable connectionId: String) {
        datasourceConnectionService.deleteDatasourceConnection(
            connectionId = DatasourceConnectionId(connectionId),
        )
    }

    @PatchMapping("/{datasourceId}/connections/{connectionId}")
    fun updateDatasourceConnection(
        @PathVariable datasourceId: String,
        @PathVariable connectionId: String,
        @Valid @RequestBody
        datasourceConnection: UpdateDataSourceConnectionRequest,
    ): DatasourceConnectionResponse {
        val datasource = datasourceConnectionService.updateDatasourceConnection(
            datasourceId = DatasourceId(datasourceId),
            connectionId = DatasourceConnectionId(connectionId),
            request = datasourceConnection,
        )
        return DatasourceConnectionResponse.fromDto(datasource)
    }
}
