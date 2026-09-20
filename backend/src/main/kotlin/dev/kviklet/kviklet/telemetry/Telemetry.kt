package dev.kviklet.kviklet.telemetry

import dev.kviklet.kviklet.ApplicationProperties
import dev.kviklet.kviklet.db.ConfigurationAdapter
import dev.kviklet.kviklet.security.ApiKeyAuthentication
import dev.kviklet.kviklet.security.KvikletOAuthPrincipal
import dev.kviklet.kviklet.security.UserDetailsWithId
import dev.kviklet.kviklet.service.BaseUrlResolver
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.annotation.Lazy
import org.springframework.context.event.EventListener
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.time.Instant
import java.util.UUID
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor

/**
 * The single entry point for usage telemetry. Turns a [TelemetryEvent] into a PostHog payload,
 * stamps it with the instance id, the domain Kviklet is reached on and the version, and hands it to
 * the [TelemetrySink]. Users are identified only by an opaque id scoped to this instance; no email or
 * name is ever sent, and no user IP address either, since only the server talks to PostHog.
 *
 * [track] never throws and is safe to call inside a transaction: the event is only delivered once
 * the transaction commits, so a rolled-back action does not show up in the statistics.
 */
@Component
@Lazy(false)
class Telemetry(
    private val properties: TelemetryProperties,
    private val sink: TelemetrySink,
    private val configurationAdapter: ConfigurationAdapter,
    private val baseUrlResolver: BaseUrlResolver,
    private val applicationProperties: ApplicationProperties,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    val enabled: Boolean = properties.enabled && properties.posthog.key.isNotBlank()

    @Volatile
    private var instanceId: String? = null

    @EventListener(ApplicationReadyEvent::class)
    fun onReady() {
        if (!properties.enabled) {
            logger.info("Usage telemetry is disabled (KVIKLET_TELEMETRY_ENABLED=false)")
            return
        }
        if (properties.posthog.key.isBlank()) {
            logger.info("Usage telemetry is disabled: no PostHog key is configured")
            return
        }
        logger.info(
            "Anonymous usage telemetry is enabled (instance {}). See the README section \"Telemetry\" for what is " +
                "reported; set KVIKLET_TELEMETRY_ENABLED=false to turn it off.",
            instanceId(),
        )
    }

    /**
     * Records [event] for the current user (from the security context), or for the instance itself
     * when no user is authenticated. [userId] overrides the security context, for callers that run
     * before it is populated, such as login listeners.
     */
    fun track(event: TelemetryEvent, userId: String? = null) {
        if (!enabled) return
        try {
            val authentication = SecurityContextHolder.getContext().authentication
            val payload = toPayload(event, userId ?: userIdOf(authentication), clientOf(authentication))
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(
                    object : TransactionSynchronization {
                        override fun afterCommit() = deliver(payload)
                    },
                )
            } else {
                deliver(payload)
            }
        } catch (e: Exception) {
            logger.debug("Failed to record telemetry event {}", event.name, e)
        }
    }

    private fun deliver(payload: TelemetryPayload) {
        try {
            logger.debug("Queued telemetry event {} for {}", payload.event, payload.distinctId)
            sink.send(payload)
        } catch (e: Exception) {
            logger.debug("Failed to queue telemetry event {}", payload.event, e)
        }
    }

    private fun toPayload(event: TelemetryEvent, userId: String?, client: TelemetryClient?): TelemetryPayload {
        val instanceId = instanceId()
        val properties = LinkedHashMap<String, Any?>()
        properties["instance_id"] = instanceId
        properties["domain"] = baseUrlResolver.resolve()
        properties["version"] = applicationProperties.version
        // The request reaches PostHog from the deployment's egress address, never a user's browser, and
        // PostHog turns it into the deployment's country and region. That is deliberate: it is the one
        // location signal that survives an internal hostname or localhost as the domain.
        // Opaque ids only: no person profiles, so nothing can ever be attached to a user.
        properties["\$process_person_profile"] = false
        properties["\$lib"] = "kviklet"
        if (userId != null) properties["client"] = client?.name
        properties.putAll(eventProperties(event))
        return TelemetryPayload(
            event = event.name,
            distinctId = if (userId != null) "$instanceId:$userId" else instanceId,
            properties = properties,
            timestamp = Instant.now(),
        )
    }

    /**
     * Created once per deployment and kept in the configuration table, so it survives restarts and
     * upgrades. Falls back to a per-process id if the table is not reachable, rather than failing.
     */
    fun instanceId(): String {
        instanceId?.let { return it }
        val id = try {
            configurationAdapter.getConfiguration(INSTANCE_ID_KEY) as? String
                ?: UUID.randomUUID().toString().also { configurationAdapter.setConfiguration(INSTANCE_ID_KEY, it) }
        } catch (e: Exception) {
            logger.debug("Could not read or store the telemetry instance id", e)
            UUID.randomUUID().toString()
        }
        instanceId = id
        return id
    }

    private fun userIdOf(authentication: Authentication?): String? = when (val principal = authentication?.principal) {
        is UserDetailsWithId -> principal.id
        is KvikletOAuthPrincipal -> principal.getUserDetails().id
        else -> null
    }

    /** Null without an authentication (login listeners, the heartbeat): nothing to say about the client. */
    private fun clientOf(authentication: Authentication?): TelemetryClient? = when (authentication) {
        null -> null
        is ApiKeyAuthentication -> TelemetryClient.API_KEY
        else -> TelemetryClient.WEB
    }

    companion object {
        const val INSTANCE_ID_KEY = "telemetryInstanceId"

        /**
         * The event's constructor parameters, as `snake_case` property names. Enums are sent by name.
         * An event declared as an `object` has no parameters and therefore no properties of its own.
         */
        fun eventProperties(event: TelemetryEvent): Map<String, Any?> {
            val kClass = event::class
            val parameters = kClass.primaryConstructor?.parameters ?: return emptyMap()
            val byName = kClass.memberProperties.associateBy { it.name }
            return parameters.associate { parameter ->
                val value = byName.getValue(parameter.name!!).getter.call(event)
                snakeCase(parameter.name!!) to if (value is Enum<*>) value.name else value
            }
        }

        private fun snakeCase(name: String): String = name
            .replace(Regex("([a-z0-9])([A-Z])"), "$1_$2")
            .lowercase()
    }
}
