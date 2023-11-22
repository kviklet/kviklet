package dev.kviklet.kviklet.service

import dev.kviklet.kviklet.controller.UpdateDatasourceRequest
import dev.kviklet.kviklet.db.DatasourceAdapter
import dev.kviklet.kviklet.security.Permission
import dev.kviklet.kviklet.security.Policy
import dev.kviklet.kviklet.service.dto.Datasource
import dev.kviklet.kviklet.service.dto.DatasourceId
import dev.kviklet.kviklet.service.dto.DatasourceType
import jakarta.transaction.Transactional
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

class EntityNotFound(override val message: String, val detail: String) : Exception(message)

@Service
class DatasourceService(
    val datasourceAdapter: DatasourceAdapter,
) {
    var logger: Logger = LoggerFactory.getLogger(DatasourceService::class.java)

    @Transactional
    @Policy(Permission.DATASOURCE_CREATE)
    fun createDatasource(
        id: String,
        displayName: String,
        datasourceType: DatasourceType,
        hostname: String,
        port: Int,
    ): Datasource = datasourceAdapter.createDatasource(
        id,
        displayName,
        datasourceType,
        hostname,
        port,
    )

    @Transactional
    @Policy(Permission.DATASOURCE_EDIT)
    fun deleteDatasource(datasourceId: DatasourceId) {
        print("deleting $datasourceId")
        datasourceAdapter.deleteDatasource(datasourceId)
    }

    @Transactional
    @Policy(Permission.DATASOURCE_GET)
    fun getDatasource(datasourceId: DatasourceId): Datasource {
        return datasourceAdapter.getDatasource(datasourceId)
    }

    @Transactional
    @Policy(Permission.DATASOURCE_GET)
    fun listDatasources(): List<Datasource> {
        return datasourceAdapter.findAllDatasources()
    }

    @Transactional
    @Policy(Permission.DATASOURCE_EDIT)
    fun updateDatasource(
        datasourceId: DatasourceId,
        datasource: dev.kviklet.kviklet.controller.UpdateDatasourceRequest,
    ): Datasource {
        return datasourceAdapter.updateDatasource(
            datasourceId,
            datasource.displayName,
            datasource.datasourceType,
            datasource.hostname,
            datasource.port,
        )
    }
}
