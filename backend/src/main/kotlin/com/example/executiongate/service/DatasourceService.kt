package com.example.executiongate.service

import com.example.executiongate.db.DatasourceAdapter
import com.example.executiongate.db.DatasourceConnectionAdapter
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
    val datasourceConnectionAdapter: DatasourceConnectionAdapter,
) {
    var logger: Logger = LoggerFactory.getLogger(DatasourceService::class.java)

//    @Policy("datasource:get")
//    fun listConnectionsLegacy(): List<Datasource> = datasourceRepository
//        .findAllDatasourcesAndConnections().map { it.toDto() }

    @Policy("datasource:get")
    fun listConnections(): List<Datasource> {
        return datasourceAdapter.findAllDatasources()
    }

    @Transactional
    fun createDatasource(
        displayName: String,
        datasourceType: DatasourceType,
        hostname: String,
        port: Int,
    ): Datasource = datasourceAdapter.createDatasource(
        displayName,
        datasourceType,
        hostname,
        port,
    )

    fun deleteDatasource(datasourceId: DatasourceId) {
        datasourceAdapter.deleteDatasource(datasourceId)
    }
}
