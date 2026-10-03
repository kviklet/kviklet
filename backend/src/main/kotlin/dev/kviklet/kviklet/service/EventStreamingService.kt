// This file is not MIT licensed
package dev.kviklet.kviklet.service

import com.fasterxml.jackson.databind.ObjectMapper
import dev.kviklet.kviklet.ApplicationProperties
import dev.kviklet.kviklet.db.ConfigurationAdapter
import dev.kviklet.kviklet.db.UserAdapter
import dev.kviklet.kviklet.security.ApiKeyAuthentication
import dev.kviklet.kviklet.security.EnterpriseFeatureException
import dev.kviklet.kviklet.security.KvikletOAuthPrincipal
import dev.kviklet.kviklet.security.NoPolicy
import dev.kviklet.kviklet.security.Permission
import dev.kviklet.kviklet.security.Policy
import dev.kviklet.kviklet.security.SecurityEventRequestFilter
import dev.kviklet.kviklet.security.UserDetailsWithId
import dev.kviklet.kviklet.service.dto.EventLoggingLevel
import dev.kviklet.kviklet.service.dto.EventStreamingResponse
import dev.kviklet.kviklet.service.dto.EventStreamingSettings
import dev.kviklet.kviklet.service.dto.EventStreamingStatus
import dev.kviklet.kviklet.service.eventstream.EventFileWriter
import dev.kviklet.kviklet.service.eventstream.EventLoggingPolicy
import dev.kviklet.kviklet.service.eventstream.userFields
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Lazy
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import java.nio.file.Path
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.locks.ReentrantLock

@Service
@Lazy(false)
class EventStreamingService(
    private val configurationAdapter: ConfigurationAdapter,
    private val licenseService: LicenseService,
    private val mapper: ObjectMapper,
    private val applicationProperties: ApplicationProperties,
    private val userAdapter: UserAdapter,
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private var settings = EventStreamingSettings()
    private var initialized = false
    private var configurationRevision = 0L
    private var licensedUntil: LocalDate? = null
    private var licenseCheckedAt = Instant.EPOCH
    private var writer: EventFileWriter? = null
    private var lastWriteAt: Instant? = null
    private var lastError: String? = null
    private var failures = 0L
    private var recoveryAt = Instant.EPOCH
    private val instanceId = UUID.randomUUID().toString()
    private val configurationLock = ReentrantLock()

    @Policy(Permission.CONFIGURATION_GET, checkIsPresentOnly = true)
    @Synchronized
    fun getConfiguration(): EventStreamingResponse {
        maintainWriter()
        return response()
    }

    @Policy(Permission.CONFIGURATION_EDIT, checkIsPresentOnly = true)
    @Transactional
    fun configure(next: EventStreamingSettings): EventStreamingResponse = serializeConfiguration {
        require(next.maxFileSizeMiB in 1..1024) { "File size must be between 1 and 1024 MiB" }
        require(next.retentionDays in 1..365) { "Retention must be between 1 and 365 days" }
        require(next.maxArchiveSizeMiB in (next.maxFileSizeMiB + 1)..102400) {
            "Archive budget must be at least 1 MiB larger than the file-size threshold and at most 102400 MiB"
        }
        require(Path.of(next.directory).isAbsolute) { "Event output directory must be absolute" }
        val previous = synchronized(this) { settings }
        require(!previous.enabled || next.directory == previous.directory) {
            "Disable streaming and save before changing the output directory"
        }
        // Never wait for a database connection while holding the writer monitor.
        val license = if (next.enabled) licenseService.getActiveLicense() else null
        if (next.enabled && license == null) {
            throw EnterpriseFeatureException("Event Log Streaming requires a valid enterprise license")
        }
        val actor = actor()
        configurationAdapter.setConfiguration(CONFIG_KEY, mapper.writeValueAsString(next))
        afterCommit {
            synchronized(this) {
                // The disable event belongs to the previously enabled stream.
                if (previous.enabled && eligible()) {
                    append(
                        record(
                            "event_stream.configuration_changed",
                            "configuration",
                            "success",
                            mapOf(
                                "before" to previous,
                                "after" to next,
                            ),
                            actor,
                        ),
                    )
                }
                runCatching { writer?.close() }
                writer = null
                settings = next
                initialized = true
                configurationRevision++
                licensedUntil = license?.validUntil
                licenseCheckedAt = Instant.now()
                recoveryAt = Instant.EPOCH
                lastError = null
                maintainWriter()
                if (!previous.enabled && eligible()) {
                    append(record("event_stream.enabled", "configuration", "success", emptyMap(), actor))
                }
            }
        }
        synchronized(this) { response().copy(settings = next) }
    }

    /** Serialize saves through transaction completion without holding the file-writer monitor. */
    private fun <T> serializeConfiguration(operation: () -> T): T {
        configurationLock.lock()
        val transactional = TransactionSynchronizationManager.isActualTransactionActive() &&
            TransactionSynchronizationManager.isSynchronizationActive()
        try {
            return operation()
        } finally {
            if (transactional) {
                try {
                    TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
                        override fun afterCompletion(status: Int) {
                            configurationLock.unlock()
                        }
                    })
                } catch (e: Exception) {
                    configurationLock.unlock()
                    throw e
                }
            } else {
                configurationLock.unlock()
            }
        }
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
                    if (eligible() && EventLoggingPolicy.includes(action, settings.loggingLevel)) append(event)
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
        val snapshot = synchronized(this) {
            if (initialized && (!settings.enabled || Instant.now().isBefore(licenseCheckedAt.plusSeconds(60)))) {
                maintainWriter()
                return
            }
            RefreshSnapshot(settings, initialized, configurationRevision)
        }
        try {
            val loaded = if (snapshot.initialized) {
                snapshot.settings
            } else {
                (configurationAdapter.getConfiguration(CONFIG_KEY) as? String)?.let {
                    mapper.readValue(it, EventStreamingSettings::class.java)
                } ?: EventStreamingSettings()
            }
            val license = if (loaded.enabled) licenseService.getActiveLicense() else null
            synchronized(this) {
                // Ignore a refresh that raced with a committed settings change.
                if (configurationRevision != snapshot.revision) return
                settings = loaded
                initialized = true
                licensedUntil = license?.validUntil
                licenseCheckedAt = Instant.now()
                maintainWriter()
            }
        } catch (e: Exception) {
            synchronized(this) {
                if (configurationRevision == snapshot.revision) failure("Event streaming configuration is unavailable")
            }
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
                if (settings.maxArchiveSizeMiB <= settings.maxFileSizeMiB) {
                    failure("Archive budget must be at least 1 MiB larger than the file-size threshold")
                    return
                }
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
            val json = mapper.writeValueAsString(EventLoggingPolicy.apply(event, settings.loggingLevel))
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

    private data class RefreshSnapshot(
        val settings: EventStreamingSettings,
        val initialized: Boolean,
        val revision: Long,
    )

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
        const val CONFIG_KEY = "eventStreaming"

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
