// This file is not MIT licensed
package dev.kviklet.kviklet.service

import com.fasterxml.jackson.databind.ObjectMapper
import dev.kviklet.kviklet.ApplicationProperties
import dev.kviklet.kviklet.db.UserAdapter
import dev.kviklet.kviklet.security.ApiKeyAuthentication
import dev.kviklet.kviklet.security.KvikletOAuthPrincipal
import dev.kviklet.kviklet.security.NoPolicy
import dev.kviklet.kviklet.security.Permission
import dev.kviklet.kviklet.security.Policy
import dev.kviklet.kviklet.security.SecurityEventRequestFilter
import dev.kviklet.kviklet.security.UserDetailsWithId
import dev.kviklet.kviklet.service.dto.EventLoggingLevel
import dev.kviklet.kviklet.service.dto.EventStreamingResponse
import dev.kviklet.kviklet.service.dto.EventStreamingStatus
import dev.kviklet.kviklet.service.eventstream.EventFileWriter
import dev.kviklet.kviklet.service.eventstream.EventLoggingPolicy
import dev.kviklet.kviklet.service.eventstream.EventStreamingProperties
import dev.kviklet.kviklet.service.eventstream.userFields
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Lazy
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Service
@Lazy(false)
class EventStreamingService(
    properties: EventStreamingProperties,
    private val licenseService: LicenseService,
    private val mapper: ObjectMapper,
    private val applicationProperties: ApplicationProperties,
    private val userAdapter: UserAdapter,
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val settings = properties.toSettings()
    private var licensedUntil: LocalDate? = null
    private var licenseCheckedAt = Instant.EPOCH
    private var writer: EventFileWriter? = null
    private var lastWriteAt: Instant? = null
    private var lastError: String? = null
    private var failures = 0L
    private var recoveryAt = Instant.EPOCH
    private val instanceId = UUID.randomUUID().toString()

    @Policy(Permission.CONFIGURATION_GET, checkIsPresentOnly = true)
    @Synchronized
    fun getConfiguration(): EventStreamingResponse {
        maintainWriter()
        return response()
    }

    /** Callers pass explicit safe fields, never domain objects carrying credentials or result values. */
    @NoPolicy
    fun emit(
        action: String,
        category: String,
        outcome: String = "success",
        fields: Map<String, Any?> = emptyMap(),
        actorId: String? = null,
        authentication: Authentication? = SecurityContextHolder.getContext().authentication,
    ) = emit(action, category, outcome, { fields }, actorId, authentication)

    /** Expensive field preparation belongs inside the capture gate and failure boundary. */
    @NoPolicy
    fun emit(
        action: String,
        category: String,
        outcome: String = "success",
        fields: (EventLoggingLevel) -> Map<String, Any?>,
        actorId: String? = null,
        authentication: Authentication? = SecurityContextHolder.getContext().authentication,
    ) {
        try {
            val captureLevel = synchronized(this) {
                maintainWriter()
                if (!eligible() || !EventLoggingPolicy.includes(action, settings.loggingLevel)) return
                settings.loggingLevel
            }
            val event = EventLoggingPolicy.apply(
                record(action, category, outcome, fields(captureLevel), actor(authentication, actorId)),
                captureLevel,
            )
            afterCommit {
                synchronized(this) {
                    if (eligible()) append(event)
                }
            }
        } catch (e: Exception) {
            synchronized(this) { failure("Event capture failed") }
        }
    }

    @NoPolicy
    @PostConstruct
    @Scheduled(fixedDelay = 60000)
    fun refresh() {
        synchronized(this) {
            if (!settings.enabled || Instant.now().isBefore(licenseCheckedAt.plusSeconds(60))) {
                maintainWriter()
                return
            }
        }
        try {
            // Never wait for a database connection while holding the writer monitor.
            val license = licenseService.getActiveLicense()
            synchronized(this) {
                licensedUntil = license?.validUntil
                licenseCheckedAt = Instant.now()
                maintainWriter()
            }
        } catch (e: Exception) {
            synchronized(this) { failure("Event streaming license is unavailable") }
        }
    }

    /** Only cached state and file operations are allowed here; the caller holds the writer monitor. */
    private fun maintainWriter() {
        try {
            if (!eligible()) {
                runCatching { writer?.close() }
                writer = null
                return
            }
            if (writer == null && !Instant.now().isBefore(recoveryAt)) {
                writer = EventFileWriter(settings)
                lastError = null
            }
            writer?.maintain()
        } catch (e: Exception) {
            failure("Event file output is unavailable; check the directory, permissions and storage")
        }
    }

    private fun hasLicense() = licensedUntil?.isAfter(LocalDate.now()) == true
    private fun eligible() = settings.enabled && hasLicense()

    private fun record(
        action: String,
        category: String,
        outcome: String,
        fields: Map<String, Any?>,
        actor: Map<String, Any?>,
    ): Map<String, Any?> {
        val request = (RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes)?.request
        val sourceIp = fields["source_ip"] ?: request?.getAttribute(SecurityEventRequestFilter.PEER_ATTRIBUTE)
        val type = when {
            action.endsWith(".denied") -> "denied"
            action.endsWith(".created") -> "creation"
            action.endsWith(".deleted") || action.endsWith(".revoked") -> "deletion"
            action.endsWith(".completed") || action.endsWith(".ended") || action.endsWith(".logout") -> "end"
            action.endsWith(".attempted") || action.endsWith(".login") || action.endsWith(".enabled") -> "start"
            else -> "change"
        }
        return buildMap {
            put("@timestamp", Instant.now().toString())
            put("ecs", mapOf("version" to "8.11.0"))
            put(
                "service",
                mapOf(
                    "name" to "Kviklet",
                    "version" to applicationProperties.version,
                    "ephemeral_id" to instanceId,
                ),
            )
            put(
                "event",
                mapOf(
                    "id" to UUID.randomUUID().toString(),
                    "kind" to "event",
                    "dataset" to "kviklet.audit",
                    "category" to listOf(category),
                    "type" to listOf(type),
                    "action" to action,
                    "outcome" to outcome,
                ),
            )
            if (actor.isNotEmpty()) put("user", actor)
            if (sourceIp != null) put("source", mapOf("ip" to sourceIp))
            put("kviklet", mapOf("schema_version" to "1.0.0") + fields)
        }
    }

    private fun append(event: Map<String, Any?>) {
        try {
            val json = mapper.writeValueAsString(event)
            // Allow two 64 KiB text fields even when JSON escaping expands each byte sixfold.
            if (json.toByteArray(Charsets.UTF_8).size + 1 > EventFileWriter.MAX_RECORD_BYTES) {
                failures++
                logger.warn("Event exceeded the 1 MiB record limit")
                return
            }
            val output = writer ?: run {
                failures++
                return
            }
            output.write(json)
            lastWriteAt = Instant.now()
        } catch (e: Exception) {
            failure("Event file write failed; check permissions and storage")
        }
    }

    private fun failure(message: String) {
        failures++
        if (lastError != message) logger.warn(message)
        lastError = message
        runCatching { writer?.close() }
        writer = null
        recoveryAt = Instant.now().plusSeconds(30)
    }

    private fun response() = EventStreamingResponse(
        settings,
        EventStreamingStatus(
            state = when {
                !settings.enabled -> "disabled"
                !hasLicense() -> "license_expired"
                writer == null || lastError != null -> "degraded"
                else -> "active"
            },
            lastWriteAt = lastWriteAt,
            lastError = lastError,
            detectedFailures = failures,
        ),
    )

    @NoPolicy
    @PreDestroy
    @Synchronized
    fun shutdown() {
        runCatching { writer?.close() }
        writer = null
    }

    private fun actor(
        authentication: Authentication? = SecurityContextHolder.getContext().authentication,
        id: String? = null,
    ): Map<String, Any?> {
        val principal = authentication?.principal
        val details = when (principal) {
            is UserDetailsWithId -> principal
            is KvikletOAuthPrincipal -> principal.getUserDetails()
            else -> null
        }
        val actorId = id ?: details?.id ?: return emptyMap()
        // Read before taking the writer lock; only selected identity fields enter the record.
        val user = userAdapter.findById(actorId)
        return userFields(user) + ("id" to actorId)
    }

    companion object {
        fun channel(): String =
            if (SecurityContextHolder.getContext().authentication is ApiKeyAuthentication) "api" else "web"

        fun afterCommit(callback: () -> Unit) {
            if (TransactionSynchronizationManager.isActualTransactionActive() &&
                TransactionSynchronizationManager.isSynchronizationActive()
            ) {
                TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
                    private var emitted = false
                    override fun afterCommit() {
                        emitted = true
                        callback()
                    }

                    // A session-end hook can emit while another afterCommit callback is running.
                    override fun afterCompletion(status: Int) {
                        if (!emitted && status == TransactionSynchronization.STATUS_COMMITTED) callback()
                    }
                })
            } else {
                callback()
            }
        }

        fun statement(text: String?): Map<String, Any?> = boundedText(text, "statement")

        fun reason(text: String?): Map<String, Any?> = boundedText(text, "reason")

        private fun boundedText(text: String?, field: String): Map<String, Any?> {
            if (text == null) return emptyMap()
            // Bound temporary allocations too: do not encode arbitrarily large text in full.
            val bytes = text.take(64 * 1024 + 1).toByteArray(Charsets.UTF_8)
            var end = minOf(bytes.size, 64 * 1024)
            // Stop before a partial UTF-8 code point.
            if (end < bytes.size) while (end > 0 && bytes[end].toInt() and 0xc0 == 0x80) end--
            return mapOf(field to String(bytes, 0, end, Charsets.UTF_8))
        }
    }
}
