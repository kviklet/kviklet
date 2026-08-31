package dev.kviklet.kviklet.service

import dev.kviklet.kviklet.db.ConfigurationAdapter
import dev.kviklet.kviklet.security.NoPolicy
import dev.kviklet.kviklet.security.Permission
import dev.kviklet.kviklet.security.Policy
import dev.kviklet.kviklet.service.dto.Configuration
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ConfigService(
    private val configurationAdapter: ConfigurationAdapter,
    private val licenseService: LicenseService,
) {

    @Policy(Permission.CONFIGURATION_GET)
    @Transactional(readOnly = true)
    fun getConfiguration(): Configuration = configurationAdapter.getConfiguration()

    @Policy(Permission.CONFIGURATION_EDIT)
    @Transactional
    fun setConfiguration(configuration: Configuration): Configuration {
        // The following block is not MIT licensed - it gates enabling the database proxy
        // behind a valid enterprise license. Removing it bypasses license enforcement.
        // Turning the proxy on is enterprise-only. Only the false->true transition is gated, so a
        // config save that merely re-sends an already-enabled flag (or turns it off) still works
        // after a license expires.
        if (configuration.proxyEnabled == true &&
            configurationAdapter.getConfiguration("proxyEnabled") != "true" &&
            licenseService.getActiveLicense() == null
        ) {
            throw LicenseRestrictionException("Enabling the database proxy requires a valid enterprise license")
        }
        return configurationAdapter.setConfiguration(configuration)
    }

    // Read by the review page (and the proxy gate) for every user, so no permission is required:
    // like the license-validity flag it only reveals whether the feature is switched on.
    @NoPolicy
    @Transactional(readOnly = true)
    fun isProxyEnabled(): Boolean = configurationAdapter.getConfiguration("proxyEnabled") == "true"
}
