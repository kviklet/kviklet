package com.example.executiongate.controller

import com.example.executiongate.MyProperties
import com.example.executiongate.service.DatasourceConnectionService
import com.example.executiongate.service.DatasourceService
import com.example.executiongate.service.dto.Datasource
import com.example.executiongate.service.dto.DatasourceConnection
import com.example.executiongate.service.dto.DatasourceId
import com.example.executiongate.service.dto.DatasourceType
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
    val datasourceConnections: List<DatasourceConnectionResponse>,
) {
    companion object {
        fun fromDto(datasource: Datasource, connections: List<DatasourceConnection>) = DatasourceResponse(
            id = datasource.id,
            displayName = datasource.displayName,
            datasourceType = datasource.type,
            hostname = datasource.hostname,
            port = datasource.port,
            datasourceConnections = connections.map { DatasourceConnectionResponse.fromDto(it) },
        )
    }
}

data class ListDatasourceResponse(
    val databases: List<DatasourceResponse>,
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
    val config: MyProperties,
) {

    @GetMapping("/{datasourceId}")
    fun getDatasource(@PathVariable datasourceId: String): DatasourceResponse {
        return datasourceService.getDatasource(
            DatasourceId(datasourceId),
        ).let { DatasourceResponse.fromDto(it, emptyList()) }
    }

    @GetMapping("/")
    fun listDatasources(): ListDatasourceResponse {
        val connections = datasourceConnectionService.listDatasourceConnections()
        val datasources = datasourceService.listDatasources()
        val datasourceToConnection = connections.groupBy { it.datasource }

        return ListDatasourceResponse(
            databases = datasources.map { datasource ->
                val datasourceConnections = datasourceToConnection[datasource]
                DatasourceResponse.fromDto(datasource, datasourceConnections ?: emptyList())
            },
        )
    }

    @PostMapping("/")
    fun createDatasource(@Valid @RequestBody datasourceConnection: CreateDatasourceRequest): DatasourceResponse {
        val datasource = datasourceService.createDatasource(
            id = datasourceConnection.id,
            displayName = datasourceConnection.displayName,
            datasourceType = datasourceConnection.datasourceType,
            hostname = datasourceConnection.hostname,
            port = datasourceConnection.port,
        )
        return DatasourceResponse.fromDto(datasource, emptyList())
    }

    @PatchMapping("/{datasourceId}")
    fun updateDatasource(
        @PathVariable datasourceId: String,
        @Valid @RequestBody datasource: UpdateDatasourceRequest,
    ): DatasourceResponse {
        return datasourceService.updateDatasource(DatasourceId(datasourceId), datasource).let {
            DatasourceResponse.fromDto(it, emptyList())
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
