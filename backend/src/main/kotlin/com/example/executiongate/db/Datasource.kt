package com.example.executiongate.db

import com.example.executiongate.service.dto.Datasource
import com.example.executiongate.service.dto.DatasourceId
import com.example.executiongate.service.dto.DatasourceType
import com.querydsl.jpa.impl.JPAQuery
import jakarta.persistence.CascadeType
import jakarta.persistence.Entity
import jakarta.persistence.EntityManager
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import org.apache.commons.lang3.builder.ToStringBuilder
import org.apache.commons.lang3.builder.ToStringStyle.SHORT_PREFIX_STYLE
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Service

@Entity(name = "datasource")
class DatasourceEntity(
    @Id val id: String,
    val displayName: String,
    @Enumerated(EnumType.STRING)
    val type: DatasourceType,
    val hostname: String,
    val port: Int,
    @OneToMany(mappedBy = "datasource", cascade = [CascadeType.ALL])
    val datasourceConnections: Set<DatasourceConnectionEntity>,
) {

    override fun toString(): String = ToStringBuilder(this, SHORT_PREFIX_STYLE)
        .append("id", id)
        .append("displayName", displayName)
        .toString()

    fun toDto(): Datasource = Datasource(
        id = DatasourceId(id),
        displayName = displayName,
        type = type,
        hostname = hostname,
        port = port,
//        datasourceConnections = emptyList(),
    )
}

interface DatasourceRepository : JpaRepository<DatasourceEntity, String>, CustomDatasourceRepository

interface CustomDatasourceRepository {
    fun findAllDatasourcesAndConnections(): Set<DatasourceEntity>
}

class CustomDatasourceRepositoryImpl(
    private val entityManager: EntityManager,
) : CustomDatasourceRepository {

    private val qDatasourceEntity: QDatasourceEntity = QDatasourceEntity.datasourceEntity

    override fun findAllDatasourcesAndConnections(): Set<DatasourceEntity> {
        return JPAQuery<DatasourceEntity>(entityManager).from(qDatasourceEntity)
            .leftJoin(qDatasourceEntity.datasourceConnections)
            .fetchJoin()
            .fetch()
            .toSet()
    }
}

@Service
class DatasourceAdapter(
    val datasourceRepository: DatasourceRepository,
) {
    fun findAllDatasources(): List<Datasource> = datasourceRepository.findAll().map { it.toDto() }

    fun createDatasource(
        id: String,
        displayName: String,
        datasourceType: DatasourceType,
        hostname: String,
        port: Int,
    ): Datasource {
        return datasourceRepository.save(
            DatasourceEntity(
                id = id,
                displayName = displayName,
                type = datasourceType,
                hostname = hostname,
                port = port,
                datasourceConnections = emptySet(),
            ),
        ).toDto()
    }

    fun getDatasource(datasourceId: DatasourceId): Datasource =
        datasourceRepository.getReferenceById(datasourceId.toString()).toDto()

    fun deleteDatasource(datasourceId: DatasourceId) {
        datasourceRepository.deleteById(datasourceId.toString())
    }
}
