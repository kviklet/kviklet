package dev.kviklet.kviklet.db

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import dev.kviklet.kviklet.service.dto.Configuration
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Service

@Entity
@Table(name = "configuration")
data class ConfigurationEntity(
    @Id
    @Column(name = "`key`")
    val key: String,

    @Column(name = "`value`")
    val value: String,
)

interface ConfigurationRepository : JpaRepository<ConfigurationEntity, String>

@Service
class ConfigurationAdapter(
    private val configurationRepository: ConfigurationRepository,
) {
    fun getConfiguration(key: String): Any? {
        return configurationRepository.findById(key).map { it.value }.orElse(null)
    }

    fun setConfiguration(key: String, value: String) {
        configurationRepository.save(ConfigurationEntity(key, value))
    }

    fun getConfiguration(): Configuration {
        return configurationRepository.findAll().toDto(jacksonObjectMapper())
    }

    fun setConfiguration(configuration: Configuration): Configuration {
        val configurationEntities = configurationRepository.saveAll(
            listOfNotNull(
                configuration.teamsUrl?.let { ConfigurationEntity("teamsUrl", it) },
                configuration.slackUrl?.let { ConfigurationEntity("slackUrl", it) },
            ),
        )
        return configurationEntities.toDto(objectMapper = jacksonObjectMapper())
    }
}

fun List<ConfigurationEntity>.toDto(objectMapper: ObjectMapper): Configuration {
    return Configuration(
        teamsUrl = this.find { it.key == "teamsUrl" }?.value as? String,
        slackUrl = this.find { it.key == "slackUrl" }?.value as? String,
    )
}
