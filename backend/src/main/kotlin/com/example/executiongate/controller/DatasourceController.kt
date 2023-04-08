package com.example.executiongate.controller

import com.example.executiongate.MyProperties
import com.example.executiongate.service.DatasourceService
import com.example.executiongate.service.dto.AuthenticationType
import com.example.executiongate.service.dto.DatasourceConnectionDto
import com.example.executiongate.service.dto.DatasourceConnectionId
import com.example.executiongate.service.dto.DatasourceDto
import com.example.executiongate.service.dto.DatasourceId
import com.example.executiongate.service.dto.DatasourceType
import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import javax.validation.Valid
import javax.validation.constraints.Max
import javax.validation.constraints.Size

data class TestDatabaseConnection(
    @Schema(example = "postgresql://localhost:5432/postgres")
    val url: String,
    @Schema(example = "root")
    val username: String,
    @Schema(example = "root")
    val password: String
)

data class CreateDatasourceRequest(
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
    val port: Int

)

data class CreateDatasourceConnectionRequest(
    @Schema(example = "My Postgres Db User")
    @field:Size(max = 255, message = "Maximum length 255")
    val displayName: String,

    @Schema(example = "root")
    @field:Size(min = 1, max = 255, message = "Maximum length 255")
    val username: String,

    @Schema(example = "root")
    @field:Size(min = 1, max = 255, message = "Maximum length 255")
    val password: String,

)

data class DatasourceConnectionResponse(
    val id: DatasourceConnectionId,
    val authenticationType: AuthenticationType,
    val displayName: String,
    val username: String,
    val password: String,
) {
    companion object {
        fun fromDto(datasourceConnection: DatasourceConnectionDto) = DatasourceConnectionResponse(
            id = datasourceConnection.id,
            authenticationType = datasourceConnection.authenticationType,
            displayName = datasourceConnection.displayName,
            username = datasourceConnection.username,
            password = datasourceConnection.password
        )
    }
}

data class DatasourceResponse(
    val id: DatasourceId,
    val displayName: String,
    val datasourceType: DatasourceType,
    val hostname: String,
    val port: Int,
    val datasourceConnections: List<DatasourceConnectionResponse>,
) {
    companion object {
        fun fromDto(dto: DatasourceDto) = DatasourceResponse(
            id = dto.id,
            displayName = dto.displayName,
            datasourceType = dto.type,
            hostname = dto.hostname,
            port = dto.port,
            datasourceConnections = dto.datasourceConnections.map { DatasourceConnectionResponse.fromDto(it) }
        )
    }
}

data class ListDatasourceResponse(
    val databases: List<DatasourceResponse>
)

@RestController()
@Validated
@CrossOrigin(origins = ["http://localhost:3000"])
@RequestMapping("/datasources")
class DatasourceController(
    val datasourceService: DatasourceService,
    val config: MyProperties
) {

    @PostMapping("/")
    fun createDatasource(
        @Valid @RequestBody datasourceConnection: CreateDatasourceRequest
    ): DatasourceResponse {
        val datasource = datasourceService.createDatasource(
            displayName = datasourceConnection.displayName,
            datasourceType = datasourceConnection.datasourceType,
            hostname = datasourceConnection.hostname,
            port = datasourceConnection.port
        )
        return DatasourceResponse.fromDto(datasource)
    }

    @PostMapping("/{datasourceId}/connections")
    fun createDatasourceConnection(
        @PathVariable datasourceId: DatasourceId,
        @Valid @RequestBody datasourceConnection: CreateDatasourceConnectionRequest
    ): DatasourceConnectionResponse {
        val datasource = datasourceService.createDatasourceConnection(
            displayName = datasourceConnection.displayName,
            datasourceId = datasourceId,
            username = datasourceConnection.username,
            password = datasourceConnection.password,
        )
        return DatasourceConnectionResponse.fromDto(datasource)
    }


    @GetMapping("")
    fun getDatasources(): ListDatasourceResponse {
        val dbs = datasourceService.listConnections()
        return ListDatasourceResponse(databases = dbs.map { DatasourceResponse.fromDto(it) })
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
