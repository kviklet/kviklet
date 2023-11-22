package dev.kviklet.kviklet.controller

import dev.kviklet.kviklet.MyProperties
import dev.kviklet.kviklet.service.DatasourceConnectionService
import dev.kviklet.kviklet.service.DatasourceService
import dev.kviklet.kviklet.service.dto.Datasource
import dev.kviklet.kviklet.service.dto.DatasourceConnection
import dev.kviklet.kviklet.service.dto.DatasourceId
import dev.kviklet.kviklet.service.dto.DatasourceType
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
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

data class TestDatabaseConnection(
    @Schema(example = "postgresql://localhost:5432/postgres")
    val url: String,
    @Schema(example = "root")
    val username: String,
    @Schema(example = "root")
    val password: String,
)

data class CreateDatasourceRequest(
    @Schema(example = "postgres-db")
    @field:Size(max = 255, message = "Maximum length 255")
    @field:Pattern(regexp = "^[a-zA-Z0-9-]+$", message = "Only alphanumeric and dashes (-) allowed")
    val id: String,

    @Schema(example = "My Postgres Db")
    @field:Size(max = 255, message = "Maximum length 255")
    val displayName: String,

    @Schema(example = "POSTGRESQL")
    val datasourceType: DatasourceType,

    @Schema(example = "localhost")
    @field:Size(min = 1, max = 255, message = "Maximum length 255")
    val hostname: String,

    @Schema(example = "5432")
    @field:Max(value = 65535)
    val port: Int,

)

data class UpdateDatasourceRequest(
    @Schema(example = "My Postgres Db")
    @field:Size(max = 255, message = "Maximum length 255")
    val displayName: String,

    @Schema(example = "POSTGRESQL")
    val datasourceType: DatasourceType,

    @Schema(example = "localhost")
    @field:Size(min = 1, max = 255, message = "Maximum length 255")
    val hostname: String,

    @Schema(example = "5432")
    @field:Max(value = 65535)
    val port: Int,
)

data class DatasourceResponse(
    val id: DatasourceId,
    val displayName: String,
    val datasourceType: DatasourceType,
    val hostname: String,
    val port: Int,
    val datasourceConnections: List<dev.kviklet.kviklet.controller.DatasourceConnectionResponse>,
) {
    companion object {
        fun fromDto(datasource: Datasource, connections: List<DatasourceConnection>) =
            dev.kviklet.kviklet.controller.DatasourceResponse(
                id = datasource.id,
                displayName = datasource.displayName,
                datasourceType = datasource.type,
                hostname = datasource.hostname,
                port = datasource.port,
                datasourceConnections = connections.map {
                    dev.kviklet.kviklet.controller.DatasourceConnectionResponse.Companion.fromDto(it)
                },
            )
    }
}

data class ListDatasourceResponse(
    val databases: List<dev.kviklet.kviklet.controller.DatasourceResponse>,
)

@RestController()
@Validated
@RequestMapping("/datasources")
@Tag(
    name = "Datasources",
)
class DatasourceController(
    val datasourceService: DatasourceService,
    val datasourceConnectionService: DatasourceConnectionService,
    val config: dev.kviklet.kviklet.MyProperties,
) {

    @GetMapping("/{datasourceId}")
    fun getDatasource(@PathVariable datasourceId: String): dev.kviklet.kviklet.controller.DatasourceResponse {
        return datasourceService.getDatasource(
            DatasourceId(datasourceId),
        ).let { dev.kviklet.kviklet.controller.DatasourceResponse.Companion.fromDto(it, emptyList()) }
    }

    @GetMapping("/")
    fun listDatasources(): dev.kviklet.kviklet.controller.ListDatasourceResponse {
        val connections = datasourceConnectionService.listDatasourceConnections()
        val datasources = datasourceService.listDatasources()
        val datasourceToConnection = connections.groupBy { it.datasource }

        return dev.kviklet.kviklet.controller.ListDatasourceResponse(
            databases = datasources.map { datasource ->
                val datasourceConnections = datasourceToConnection[datasource]
                dev.kviklet.kviklet.controller.DatasourceResponse.Companion.fromDto(
                    datasource,
                    datasourceConnections
                        ?: emptyList(),
                )
            },
        )
    }

    @PostMapping("/")
    fun createDatasource(
        @Valid @RequestBody
        datasourceConnection: dev.kviklet.kviklet.controller.CreateDatasourceRequest,
    ): dev.kviklet.kviklet.controller.DatasourceResponse {
        val datasource = datasourceService.createDatasource(
            id = datasourceConnection.id,
            displayName = datasourceConnection.displayName,
            datasourceType = datasourceConnection.datasourceType,
            hostname = datasourceConnection.hostname,
            port = datasourceConnection.port,
        )
        return dev.kviklet.kviklet.controller.DatasourceResponse.Companion.fromDto(datasource, emptyList())
    }

    @PatchMapping("/{datasourceId}")
    fun updateDatasource(
        @PathVariable datasourceId: String,
        @Valid @RequestBody
        datasource: dev.kviklet.kviklet.controller.UpdateDatasourceRequest,
    ): dev.kviklet.kviklet.controller.DatasourceResponse {
        return datasourceService.updateDatasource(DatasourceId(datasourceId), datasource).let {
            dev.kviklet.kviklet.controller.DatasourceResponse.Companion.fromDto(it, emptyList())
        }
    }

    @DeleteMapping("/{datasourceId}")
    fun deleteDatasource(@PathVariable datasourceId: String) {
        datasourceService.deleteDatasource(datasourceId = DatasourceId(datasourceId))
    }

    /*
    @PostMapping("/test")
    fun testConnection(@Valid @RequestBody connection: TestDatabaseConnection): ResponseEntity<Any> {
        println(config.name)
        println(repo.findAll()[0]?.id)

        val valid: Boolean = datasourceService.testConnection(
            DbType.POSTGRESQL,
            "localhost",
            5432,
            "postgres",
            connection.username,
            connection.password
        )
        return if (valid) {
            ResponseEntity.noContent().build()
        } else {
            ResponseEntity(HttpStatus.BAD_REQUEST)
        }
    }
     */
}
