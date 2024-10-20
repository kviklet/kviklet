package dev.kviklet.kviklet.db

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
class ConfigurationAdapter(private val configurationRepository: ConfigurationRepository) {
    fun getConfiguration(key: String): Any? = configurationRepository.findById(key).map { it.value }.orElse(null)

    fun setConfiguration(key: String, value: String) {
        configurationRepository.save(ConfigurationEntity(key, value))
    }

    fun getConfiguration(): Configuration = configurationRepository.findAll().toDto()

    fun setConfiguration(configuration: Configuration): Configuration {
        val configurationEntities = configurationRepository.saveAll(
            listOfNotNull(
                configuration.teamsUrl?.let { ConfigurationEntity("teamsUrl", it) },
                configuration.slackUrl?.let { ConfigurationEntity("slackUrl", it) },
                configuration.liveSessionEnabled?.let { ConfigurationEntity("liveSessionEnabled", it.toString()) },
            ),
        )
        return configurationEntities.toDto()
    }
}

fun List<ConfigurationEntity>.toDto(): Configuration = Configuration(
    teamsUrl = this.find { it.key == "teamsUrl" }?.value,
    slackUrl = this.find { it.key == "slackUrl" }?.value,
    liveSessionEnabled = this.find { it.key == "liveSessionEnabled" }?.value?.toBoolean(),
)
