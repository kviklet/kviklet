package dev.kviklet.kviklet.telemetry

import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Lazy
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import java.net.URI
import java.time.Duration
import java.time.format.DateTimeFormatter
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Buffers events in memory and posts them to PostHog's batch endpoint in the background. Delivery is
 * best effort: a full queue drops events, a failed flush is logged at debug level and the batch is
 * discarded. Telemetry must never slow a request down or fill the logs of an air-gapped deployment.
 */
@Component
@Lazy(false)
class PostHogSink(private val properties: TelemetryProperties, private val restTemplate: RestTemplate) : TelemetrySink {

    // Not the shared RestTemplate bean: telemetry needs tight timeouts so a slow PostHog never holds a thread.
    @Autowired
    constructor(properties: TelemetryProperties) : this(
        properties,
        RestTemplate(
            SimpleClientHttpRequestFactory().apply {
                setConnectTimeout(Duration.ofSeconds(5))
                setReadTimeout(Duration.ofSeconds(5))
            },
        ),
    )

    private val logger = LoggerFactory.getLogger(javaClass)
    private val queue = LinkedBlockingQueue<TelemetryPayload>(properties.maxQueueSize)

    override fun send(payload: TelemetryPayload) {
        if (!queue.offer(payload)) {
            logger.debug("Telemetry queue is full, dropping event {}", payload.event)
        }
    }

    @Scheduled(fixedDelay = FLUSH_INTERVAL_SECONDS, timeUnit = TimeUnit.SECONDS)
    fun flush() {
        while (queue.isNotEmpty()) {
            val batch = mutableListOf<TelemetryPayload>()
            queue.drainTo(batch, MAX_BATCH_SIZE)
            if (batch.isEmpty()) return
            if (!post(batch)) return
        }
    }

    @PreDestroy
    fun shutdown() {
        runCatching { flush() }
    }

    private fun post(batch: List<TelemetryPayload>): Boolean {
        val body = mapOf(
            "api_key" to properties.posthog.key,
            "batch" to batch.map {
                mapOf(
                    "event" to it.event,
                    "distinct_id" to it.distinctId,
                    "properties" to it.properties,
                    "timestamp" to DateTimeFormatter.ISO_INSTANT.format(it.timestamp),
                )
            },
        )
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        return try {
            val url = URI.create(properties.posthog.host.trimEnd('/') + "/batch")
            restTemplate.postForEntity(url, HttpEntity(body, headers), String::class.java)
            logger.debug("Sent {} telemetry events", batch.size)
            true
        } catch (e: Exception) {
            logger.debug("Failed to send {} telemetry events: {}", batch.size, e.toString())
            false
        }
    }

    companion object {
        const val MAX_BATCH_SIZE = 100
        const val FLUSH_INTERVAL_SECONDS = 10L
    }
}
