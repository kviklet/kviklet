package dev.kviklet.kviklet.telemetry

import dev.kviklet.kviklet.ApplicationProperties
import dev.kviklet.kviklet.db.ConfigurationAdapter
import dev.kviklet.kviklet.db.ConnectionAdapter
import dev.kviklet.kviklet.db.EncryptionConfigProperties
import dev.kviklet.kviklet.db.EventAdapter
import dev.kviklet.kviklet.db.ExecutionRequestAdapter
import dev.kviklet.kviklet.db.UserAdapter
import dev.kviklet.kviklet.security.IdentityProviderProperties
import dev.kviklet.kviklet.security.ldap.LdapProperties
import dev.kviklet.kviklet.security.saml.SamlProperties
import dev.kviklet.kviklet.service.LicenseService
import dev.kviklet.kviklet.service.dto.DatasourceConnection
import dev.kviklet.kviklet.service.dto.DatasourceType
import dev.kviklet.kviklet.service.dto.KubernetesConnection
import dev.kviklet.kviklet.service.dto.utcTimeNow
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Lazy
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * A daily snapshot of how this instance is set up and how much it is used. Counts only: no names,
 * hostnames, or credentials of the connections it counts.
 */
@Component
@Lazy(false)
class TelemetryHeartbeat(
    private val telemetry: Telemetry,
    private val applicationProperties: ApplicationProperties,
    private val userAdapter: UserAdapter,
    private val connectionAdapter: ConnectionAdapter,
    private val executionRequestAdapter: ExecutionRequestAdapter,
    private val eventAdapter: EventAdapter,
    private val configurationAdapter: ConfigurationAdapter,
    private val licenseService: LicenseService,
    private val identityProviderProperties: IdentityProviderProperties,
    private val ldapProperties: LdapProperties,
    private val samlProperties: SamlProperties,
    private val encryptionConfigProperties: EncryptionConfigProperties,
    @Value("\${spring.datasource.url:}") private val datasourceUrl: String,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    // The first heartbeat waits a little so the domain has usually been observed by then.
    @Scheduled(initialDelayString = "PT10M", fixedDelayString = "PT24H")
    @Transactional(readOnly = true)
    fun sendHeartbeat() {
        if (!telemetry.enabled) return
        try {
            telemetry.track(snapshot())
        } catch (e: Exception) {
            logger.debug("Failed to build the telemetry heartbeat", e)
        }
    }

    fun snapshot(): InstanceHeartbeat {
        val since = utcTimeNow().minusHours(24)
        val connections = connectionAdapter.listConnections()
        val datasourceTypes = connections.filterIsInstance<DatasourceConnection>().map { it.type }
        return InstanceHeartbeat(
            version = applicationProperties.version,
            gitCommit = applicationProperties.gitCommit,
            metadataDatabase = metadataDatabase(datasourceUrl),
            inDocker = applicationProperties.inDocker,
            usersActive = userAdapter.countActiveUsers(),
            usersTotal = userAdapter.countUsers(),
            connectionsPostgresql = datasourceTypes.count { it == DatasourceType.POSTGRESQL },
            connectionsMysql = datasourceTypes.count { it == DatasourceType.MYSQL },
            connectionsMariadb = datasourceTypes.count { it == DatasourceType.MARIADB },
            connectionsMssql = datasourceTypes.count { it == DatasourceType.MSSQL },
            connectionsMongodb = datasourceTypes.count { it == DatasourceType.MONGODB },
            connectionsKubernetes = connections.count { it is KubernetesConnection },
            requestsLast24h = executionRequestAdapter.countCreatedSince(since),
            executionsLast24h = eventAdapter.countExecutionsSince(since),
            oidcProvider = identityProviderProperties.type?.lowercase()?.takeIf { it.isNotBlank() },
            ldapEnabled = ldapProperties.enabled,
            samlEnabled = samlProperties.isSamlEnabled(),
            licenseValid = licenseService.getLicenses().any { it.isValid() },
            proxyEnabled = configurationAdapter.getConfiguration("proxyEnabled") == "true",
            encryptionEnabled = encryptionConfigProperties.enabled,
        )
    }

    companion object {
        fun metadataDatabase(jdbcUrl: String): MetadataDatabase = when {
            jdbcUrl.startsWith("jdbc:postgresql:") -> MetadataDatabase.POSTGRESQL
            jdbcUrl.startsWith("jdbc:h2:") -> MetadataDatabase.H2
            else -> MetadataDatabase.OTHER
        }
    }
}
