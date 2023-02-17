package com.example.executiongate.service

import com.example.executiongate.db.DatasourceConnectionEntity
import com.example.executiongate.db.DatasourceConnectionRepository
import com.example.executiongate.db.DatasourceEntity
import com.example.executiongate.db.DatasourceRepository
import com.example.executiongate.service.dto.AuthenticationType
import com.example.executiongate.service.dto.DatasourceConnectionDto
import com.example.executiongate.service.dto.DatasourceDto
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

//    fun listConnections() = connectionRepository.findAll().mapNotNull { it?.toDto() }

    @Transactional
    fun createDatasource(
        displayName: String,
        datasourceType: DatasourceType,
        hostname: String,
        port: Int
    ): DatasourceDto {
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
        datasourceId: String,
        displayName: String,
        username: String,
        password: String
    ): DatasourceConnectionDto {
        val datasource = getDatasource(datasourceId)

        return datasourceConnectionRepository.save(
            DatasourceConnectionEntity(
                displayName = displayName,
                datasource = datasource,
                authenticationType = AuthenticationType.USER_PASSWORD,
                username = username,
                password = password
            )
        ).toDto().also {
            logger.info("Created $it")
        }
    }

    private fun getDatasource(datasourceId: String): DatasourceEntity =
        datasourceRepository.findByIdOrNull(datasourceId)
            ?: throw EntityNotFound("Datasource Not Found", "Datasource with id $datasourceId does not exist.")

    fun listConnections(): List<DatasourceDto> = datasourceRepository.findAllDatasourcesAndConnections().map { it.toDto() }
}
