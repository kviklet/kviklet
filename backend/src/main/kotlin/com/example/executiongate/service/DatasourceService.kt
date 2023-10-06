package com.example.executiongate.service

import com.example.executiongate.controller.UpdateDatasourceRequest
import com.example.executiongate.db.DatasourceAdapter
import com.example.executiongate.security.Permission
import com.example.executiongate.security.Policy
import com.example.executiongate.service.dto.Datasource
import com.example.executiongate.service.dto.DatasourceId
import com.example.executiongate.service.dto.DatasourceType
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
    @Policy(Permission.DATASOURCE_EDIT)
    fun updateDatasource(datasourceId: DatasourceId, datasource: UpdateDatasourceRequest): Datasource {
        return datasourceAdapter.updateDatasource(
            datasourceId,
            datasource.displayName,
            datasource.datasourceType,
            datasource.hostname,
            datasource.port,
        )
    }
}
