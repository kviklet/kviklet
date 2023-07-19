package com.example.executiongate.service

import com.example.executiongate.controller.CreateDatasourceConnectionRequest
import com.example.executiongate.db.DatasourceConnectionEntity
import com.example.executiongate.db.DatasourceConnectionRepository
import com.example.executiongate.db.DatasourceEntity
import com.example.executiongate.db.DatasourceRepository
import com.example.executiongate.db.ReviewConfig
import com.example.executiongate.service.dto.AuthenticationType
import com.example.executiongate.service.dto.DatasourceConnection
import com.example.executiongate.service.dto.DatasourceConnectionId
import com.example.executiongate.service.dto.Datasource
import com.example.executiongate.service.dto.DatasourceId
import com.example.executiongate.service.dto.DatasourceType
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import javax.transaction.Transactional

class EntityNotFound(override val message: String, val detail: String): Exception(message)

@Service
class DatasourceService(
    val datasourceRepository: DatasourceRepository,
    val datasourceConnectionRepository: DatasourceConnectionRepository
) {
    var logger: Logger = LoggerFactory.getLogger(DatasourceService::class.java)

    @Transactional
    fun createDatasource(
        displayName: String,
        datasourceType: DatasourceType,
        hostname: String,
        port: Int
    ): Datasource {
        return datasourceRepository.save(
            DatasourceEntity(
                displayName = displayName,
                type = datasourceType,
                hostname = hostname,
                port = port,
                datasourceConnections = emptySet()
            )
        ).toDto().also {
            logger.info("Created $it")
        }
    }

    @Transactional
    fun createDatasourceConnection(
        datasourceId: DatasourceId,
        request: CreateDatasourceConnectionRequest
    ): DatasourceConnection {
        val datasource = getDatasource(datasourceId)

        return datasourceConnectionRepository.save(
            DatasourceConnectionEntity(
                datasource = datasource,
                displayName = request.displayName,
                authenticationType = AuthenticationType.USER_PASSWORD,
                username = request.username,
                password = request.password,
                description = request.description,
                reviewConfig = ReviewConfig(
                    numTotalRequired = request.reviewConfig.numTotalRequired,
                )
            )
        ).toDto().also {
            logger.info("Created $it")
        }
    }

    private fun getDatasource(datasourceId: DatasourceId): DatasourceEntity =
        datasourceRepository.findByIdOrNull(datasourceId.toString())
            ?: throw EntityNotFound("Datasource Not Found", "Datasource with id $datasourceId does not exist.")

    fun getDatasourceConnection(id: DatasourceConnectionId): DatasourceConnectionEntity =
        datasourceConnectionRepository.findByIdOrNull(id.toString())
            ?: throw EntityNotFound("Datasource Connection Not Found", "Datasource Connection with id $id does not exist.")

    fun listConnections(): List<Datasource> = datasourceRepository.findAllDatasourcesAndConnections().map { it.toDto() }
}
