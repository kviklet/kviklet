package dev.kviklet.kviklet.db

import dev.kviklet.kviklet.service.dto.Configuration
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Service

@Entity
@Table(name = "configuration")
data class ConfigurationEntity(
    @Id
    val key: String,
    val value: String,
)

interface ConfigurationRepository : JpaRepository<ConfigurationEntity, String>

@Service
class ConfigurationAdapter(
    private val configurationRepository: ConfigurationRepository,
) {
    fun getConfiguration(key: String): String? {
        return configurationRepository.findById(key).map { it.value }.orElse(null)
    }

    fun setConfiguration(key: String, value: String) {
        configurationRepository.save(ConfigurationEntity(key, value))
    }

    fun getConfiguration(): Configuration {
        return configurationRepository.findAll().toDto()
    }

    fun setConfiguration(configuration: Configuration): Configuration {
        val configurationEntities = configurationRepository.saveAll(
            listOfNotNull(
                configuration.teamsUrl?.let { ConfigurationEntity("teamsUrl", it) },
                configuration.slackUrl?.let { ConfigurationEntity("slackUrl", it) },
                configuration.host?.let { ConfigurationEntity("host", it) },
            ),
        )
        return configurationEntities.toDto()
    }
}

fun List<ConfigurationEntity>.toDto(): Configuration {
    return Configuration(
        teamsUrl = this.find { it.key == "teamsUrl" }?.value,
        slackUrl = this.find { it.key == "slackUrl" }?.value,
        host = this.find { it.key == "host" }?.value,
    )
}
