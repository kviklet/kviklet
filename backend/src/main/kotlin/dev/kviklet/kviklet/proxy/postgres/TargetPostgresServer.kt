package dev.kviklet.kviklet.proxy.postgres

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
) {
    private val targetPgConnProps: Properties
    private val hostSpec: Array<HostSpec>

    init {
        val props = Properties()
        props.setProperty("user", authenticationDetails.username)
        props.setProperty("password", authenticationDetails.password)
        val database = if (databaseName != "") databaseName else authenticationDetails.username
        props.setProperty("PGDBNAME", database)

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
