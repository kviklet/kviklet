package dev.kviklet.kviklet.service

import dev.kviklet.kviklet.db.ConfigurationAdapter
import dev.kviklet.kviklet.security.Permission
import dev.kviklet.kviklet.security.Policy
import dev.kviklet.kviklet.service.dto.Configuration
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ConfigService(private val configurationAdapter: ConfigurationAdapter) {

    @Policy(Permission.CONFIGURATION_GET)
    @Transactional(readOnly = true)
    fun getConfiguration(): Configuration = configurationAdapter.getConfiguration()

    @Policy(Permission.CONFIGURATION_EDIT)
    @Transactional
    fun setConfiguration(configuration: Configuration): Configuration =
        configurationAdapter.setConfiguration(configuration)
}
