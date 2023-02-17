package com.example.executiongate.db

import com.example.executiongate.service.dto.DatasourceDto
import com.example.executiongate.service.dto.DatasourceType
import com.querydsl.jpa.impl.JPAQuery
import org.apache.commons.lang3.builder.ToStringBuilder
import org.apache.commons.lang3.builder.ToStringStyle.SHORT_PREFIX_STYLE
import org.springframework.data.jpa.repository.JpaRepository
import javax.persistence.Entity
import javax.persistence.EntityManager
import javax.persistence.EnumType
import javax.persistence.Enumerated
import javax.persistence.OneToMany


@Entity(name = "datasource")
class DatasourceEntity(
    val displayName: String,
    @Enumerated(EnumType.STRING)
    val type: DatasourceType,
    val hostname: String,
    val port: Int,
    @OneToMany(mappedBy = "datasource")
    val datasourceConnections: Set<DatasourceConnectionEntity>,
): BaseEntity() {

    override fun toString(): String = ToStringBuilder(this, SHORT_PREFIX_STYLE)
        .append("id", id)
        .append("displayName", displayName)
        .toString()

    fun toDto() = DatasourceDto(
        id = id,
        displayName = displayName,
        type = type,
        hostname = hostname,
        port = port,
        datasourceConnections = datasourceConnections.map { it.toDto() }
    )
}

interface CustomDatasourceRepository {
    fun findAllDatasourcesAndConnections(): Set<DatasourceEntity>
}


class CustomDatasourceRepositoryImpl(
    val em: EntityManager,
): CustomDatasourceRepository {

    val qDatasourceEntity = QDatasourceEntity.datasourceEntity

    override fun findAllDatasourcesAndConnections(): Set<DatasourceEntity> {
        return JPAQuery<DatasourceEntity>(em).from(qDatasourceEntity)
            .leftJoin(qDatasourceEntity.datasourceConnections)
            .fetchJoin()
            .fetch()
            .toSet()
    }
}


interface DatasourceRepository : JpaRepository<DatasourceEntity, String>, CustomDatasourceRepository {
}
