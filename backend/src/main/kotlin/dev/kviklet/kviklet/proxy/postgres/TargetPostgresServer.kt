// This file is not MIT licensed
package dev.kviklet.kviklet.proxy.postgres

import dev.kviklet.kviklet.proxy.core.parseAdditionalOptions
import dev.kviklet.kviklet.service.dto.AuthenticationDetails
import org.postgresql.core.PGStream
import org.postgresql.core.QueryExecutorBase
import org.postgresql.core.v3.ConnectionFactoryImpl
import org.postgresql.util.HostSpec
import java.util.*
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible

class TargetPostgresConnection(private val connInfo: Pair<PGStream, Map<String, String>>) {
    fun getPGStream(): PGStream = connInfo.first

    fun getConnProps(): Map<String, String> = connInfo.second
}

class TargetPostgresSocketFactory(
    authenticationDetails: AuthenticationDetails.UserPassword,
    databaseName: String,
    targetHost: String,
    targetPort: Int,
    additionalOptions: String = "",
) {
    private val targetPgConnProps: Properties
    private val hostSpec: Array<HostSpec>

    companion object {
        // The TLS options honored on the upstream leg, so a connection's sslmode/sslrootcert applies to
        // proxied sessions exactly like it applies to JDBC executions. pgjdbc itself does the enforcement:
        // sslmode=require and above refuse a server that cannot do TLS instead of falling back to
        // plaintext, and verify-ca/verify-full validate the chain (and hostname) against sslrootcert.
        // With no sslmode configured, pgjdbc's PREFER default applies -- opportunistic TLS with silent
        // plaintext fallback -- which keeps unconfigured connections working as before. Every other
        // additionalOptions key is deliberately dropped: options tuned for the JDBC executor (fetch sizes,
        // timeouts, rewrites) must not leak into a connection whose socket is extracted for a byte relay.
        private val UPSTREAM_TLS_OPTIONS = setOf("ssl", "sslmode", "sslrootcert", "sslcert", "sslkey", "sslpassword")
    }

    init {
        val props = Properties()
        props.setProperty("user", authenticationDetails.username)
        props.setProperty("password", authenticationDetails.password)
        val database = if (databaseName != "") databaseName else authenticationDetails.username
        props.setProperty("PGDBNAME", database)
        parseAdditionalOptions(additionalOptions)
            .filterKeys { it.lowercase() in UPSTREAM_TLS_OPTIONS }
            .forEach { (key, value) -> props.setProperty(key.lowercase(), value) }

        this.targetPgConnProps = props
        this.hostSpec = arrayOf(HostSpec(targetHost, targetPort))
    }

    fun createTargetPgConnection(): TargetPostgresConnection {
        val factory = ConnectionFactoryImpl()
        val queryExecutor = factory.openConnectionImpl(
            this.hostSpec,
            this.targetPgConnProps,
        ) as QueryExecutorBase

        val queryExecutorClass = QueryExecutorBase::class

        val pgStreamProperty = queryExecutorClass.memberProperties.firstOrNull { it.name == "pgStream" }
            ?: throw NoSuchElementException("Property 'pgStream' is not found")
        pgStreamProperty.isAccessible = true

        return TargetPostgresConnection(
            Pair(
                pgStreamProperty.get(queryExecutor) as PGStream,
                queryExecutor.parameterStatuses,
            ),
        )
    }
}
