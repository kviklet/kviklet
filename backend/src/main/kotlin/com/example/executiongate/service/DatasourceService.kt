package com.example.executiongate.service

import com.example.executiongate.controller.CreateDatasourceConnectionRequest
import com.example.executiongate.db.DatasourceConnectionEntity
import com.example.executiongate.db.DatasourceConnectionRepository
import com.example.executiongate.db.DatasourceEntity
import com.example.executiongate.db.DatasourceRepository
import com.example.executiongate.db.ReviewConfig
import com.example.executiongate.controller.UpdateDataSourceConnectionRequest
import com.example.executiongate.db.DatasourceConnectionAdapter
import com.example.executiongate.security.Policy
import com.example.executiongate.service.dto.AuthenticationType
import com.example.executiongate.service.dto.DatasourceConnection
import com.example.executiongate.service.dto.DatasourceConnectionId
import com.example.executiongate.service.dto.Datasource
import com.example.executiongate.service.dto.DatasourceId
import com.example.executiongate.service.dto.DatasourceType
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.data.repository.findByIdOrNull
import org.springframework.security.access.annotation.Secured
import org.springframework.security.access.prepost.PostFilter
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import jakarta.transaction.Transactional

class EntityNotFound(override val message: String, val detail: String): Exception(message)

@Service
class DatasourceService(
    val datasourceRepository: DatasourceRepository,
    val datasourceConnectionRepository: DatasourceConnectionRepository,
    val datasourceConnectionAdapter: DatasourceConnectionAdapter,
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
    fun updateDatasourceConnection(
        connectionId: DatasourceConnectionId,
        request: UpdateDataSourceConnectionRequest
    ): DatasourceConnection {
        val datasourceConnection = datasourceConnectionAdapter.getDatasourceConnection(connectionId)

        return datasourceConnectionAdapter.updateDatasourceConnection(
            connectionId,
            request.displayName ?: datasourceConnection.displayName,
            request.username ?: datasourceConnection.username,
            request.password ?: datasourceConnection.password,
            request.description ?: datasourceConnection.description,
            request.reviewConfig?.let { ReviewConfig(it.numTotalRequired) } ?: datasourceConnection.reviewConfig
        )
    }

    @Transactional
    fun createDatasourceConnection(
        datasourceId: DatasourceId,
        request: CreateDatasourceConnectionRequest
    ): DatasourceConnection {
        val datasource = getDatasourceEntity(datasourceId)

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
                ),
                executionRequests = emptySet()
            )
        ).toDto().also {
            logger.info("Created $it")
        }
    }

    fun deleteDatasource(datasourceId: DatasourceId) {
        val datasource = getDatasourceEntity(datasourceId)
        datasourceRepository.delete(datasource)
    }

    @Policy("connection:edit")
    fun deleteDatasourceConnection(
        connectionId: DatasourceConnectionId
    ) {
        val datasourceConnection = getDatasourceConnection(connectionId)
        datasourceConnectionRepository.delete(datasourceConnection)
    }

    private fun getDatasourceEntity(datasourceId: DatasourceId?): DatasourceEntity =
        datasourceRepository.findByIdOrNull(datasourceId.toString())
            ?: throw EntityNotFound("Datasource Not Found", "Datasource with id $datasourceId does not exist.")

    private fun getDatasourceConnection(id: DatasourceConnectionId): DatasourceConnectionEntity =
        datasourceConnectionRepository.findByIdOrNull(id.toString())
            ?: throw EntityNotFound("Datasource Connection Not Found", "Datasource Connection with id $id does not exist.")

    @Policy("datasource:get", )
    fun listConnections(): List<Datasource> = datasourceRepository
        .findAllDatasourcesAndConnections().map { it.toDto() }

}
