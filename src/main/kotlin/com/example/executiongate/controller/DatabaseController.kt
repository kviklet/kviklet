package com.example.executiongate.controller

import com.example.executiongate.service.DatasourceService
import com.example.executiongate.service.DbType
import com.example.executiongate.MyProperties
import com.example.executiongate.db.ConnectionRepository
import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import javax.validation.Valid
import javax.validation.constraints.Size

data class TestDatabaseConnection(
    @Schema(example = "postgresql://localhost:5432/postgres")
    val url: String,
    @Schema(example = "root")
    val username: String,
    @Schema(example = "root")
    val password: String
)

data class CreateDatabaseConnection(
    @Schema(example = "My Postgres Db")
    @field:Size(max = 255, message = "Maximum length 255")
    val name: String,
    @Schema(example = "postgresql://localhost:5432/postgres")
    @field:Size(min=1, max = 255, message = "Maximum length 255")
    val url: String,
    @Schema(example = "root")
    @field:Size(max = 255, message = "Maximum length 255")
    val username: String,
    @Schema(example = "root")
    @field:Size(max = 255, message = "Maximum length 255")
    val password: String
)

data class ListDatabaseResponse(
    val databases: List<DatabaseResponse>
)

data class DatabaseResponse(
    @Schema(example = "cJpuWgFcyUsyZUnjU9G5tW")
    val id: String,
    @Schema(example = "My Postgres Db")
    val name: String,
    @Schema(example = "postgresql://localhost:5432/postgres")
    val uri: String,
    @Schema(example = "root")
    val username: String,
)

@RestController()
@Validated
@RequestMapping("/connection")
class DatabaseController(
    val datasourceService: DatasourceService,
    val config: MyProperties,
    val repo: ConnectionRepository,
) {

    @PostMapping("/")
    fun createConnection(@Valid @RequestBody connection: CreateDatabaseConnection): ResponseEntity<Any> {
        datasourceService.createConnection(connection.name, connection.url, connection.username, connection.password)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/")
    fun getConnections(): ListDatabaseResponse {
        val dbs = datasourceService.listConnections()
        return ListDatabaseResponse(
            databases = dbs.map { DatabaseResponse(
                id = it.id,
                name = it.name,
                uri = it.uri,
                username = it.username,
            )
            }
        )
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
