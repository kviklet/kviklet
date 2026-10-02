package dev.kviklet.kviklet

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import dev.kviklet.kviklet.db.ConfigurationAdapter
import dev.kviklet.kviklet.db.EventAdapter
import dev.kviklet.kviklet.db.ExecutePayload
import dev.kviklet.kviklet.db.ExecutionRequestAdapter
import dev.kviklet.kviklet.helper.EventFactory
import dev.kviklet.kviklet.helper.ExecutionRequestDetailsFactory
import dev.kviklet.kviklet.service.EventService
import dev.kviklet.kviklet.service.EventStreamingService
import dev.kviklet.kviklet.service.LicenseService
import dev.kviklet.kviklet.service.dto.ErrorResultLog
import dev.kviklet.kviklet.service.dto.EventStreamingSettings
import dev.kviklet.kviklet.service.dto.ExecutionRequestId
import dev.kviklet.kviklet.service.dto.License
import dev.kviklet.kviklet.service.dto.LicenseFile
import dev.kviklet.kviklet.service.dto.QueryResultLog
import dev.kviklet.kviklet.service.dto.UpdateResultLog
import dev.kviklet.kviklet.service.eventstream.EventFileWriter
import dev.kviklet.kviklet.service.eventstream.accessFields
import dev.kviklet.kviklet.service.eventstream.executionResultFields
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class EventStreamingTest {
    @TempDir lateinit var directory: Path
    private val mapper = jacksonObjectMapper().findAndRegisterModules()
    private val configuration = mockk<ConfigurationAdapter>(relaxed = true)
    private val licenseService = mockk<LicenseService>()
    private var validUntil = LocalDate.now().plusDays(2)
    private var licensed = true
    private val streams = mutableListOf<EventStreamingService>()

    private fun service(enabled: Boolean = true): EventStreamingService {
        every { configuration.getConfiguration(EventStreamingService.CONFIG_KEY) } returns
            mapper.writeValueAsString(settings(enabled))
        every { licenseService.getActiveLicense() } answers {
            if (licensed) {
                License(
                    LicenseFile("secret-license", "test", LocalDateTime.now()),
                    validUntil,
                    LocalDateTime.now(),
                    100u,
                )
            } else {
                null
            }
        }
        return EventStreamingService(configuration, licenseService, mapper, ApplicationProperties()).also {
            streams.add(it)
        }
    }
    private fun settings(enabled: Boolean = true) = EventStreamingSettings(enabled, directory.toString(), 1, 7, 2)
    private fun lines() = directory.resolve("events.jsonl").let {
        if (Files.exists(it)) Files.readAllLines(it) else emptyList()
    }

    @AfterEach fun cleanup() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization()
        }
        TransactionSynchronizationManager.setActualTransactionActive(false)
        streams.forEach { it.shutdown() }
    }

    @Test fun `disabled and unlicensed streams never write`() {
        service(false).emit("test", "iam")
        assertTrue(lines().isEmpty())
        licensed = false
        service().emit("test", "iam")
        assertTrue(lines().isEmpty())
    }

    @Test fun `only committed successes are emitted`() {
        val stream = service()
        TransactionSynchronizationManager.setActualTransactionActive(true)
        TransactionSynchronizationManager.initSynchronization()
        stream.emit("role.created", "iam", actorId = "admin")
        assertTrue(lines().isEmpty())
        val rolledBack = TransactionSynchronizationManager.getSynchronizations()
        rolledBack.forEach { it.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK) }
        TransactionSynchronizationManager.clearSynchronization()
        assertTrue(lines().isEmpty())
        TransactionSynchronizationManager.initSynchronization()
        stream.emit("role.created", "iam", fields = mapOf("role_id" to "target"), actorId = "admin")
        TransactionSynchronizationManager.getSynchronizations().forEach { it.afterCommit() }
        val event = mapper.readTree(lines().single())
        assertEquals("admin", event["user"]["id"].asText())
        assertEquals("target", event["kviklet"]["role_id"].asText())
        assertEquals("1.0.0", event["kviklet"]["schema_version"].asText())
        assertFalse(lines().single().contains("secret-license"))
    }

    @Test fun `concurrent events remain separate valid JSON lines with unique IDs`() {
        val stream = service()
        val pool = Executors.newFixedThreadPool(6)
        repeat(120) { n -> pool.submit { stream.emit("test", "iam", fields = mapOf("text" to "line\n$n\"")) } }
        pool.shutdown()
        assertTrue(pool.awaitTermination(20, TimeUnit.SECONDS))
        val events = lines().map { mapper.readTree(it) }
        assertEquals(120, events.size)
        assertTrue(events.all { it["service"]["ephemeral_id"]?.isTextual == true })
        assertEquals(1, events.map { it["service"]["ephemeral_id"].asText() }.toSet().size)
        assertEquals(120, events.map { it["event"]["id"].asText() }.toSet().size)
    }

    @Test fun `statement bound preserves UTF8 and JSON escaping`() {
        val text = "한".repeat(30000) + "\n\"tail"
        val fields = EventStreamingService.statement(text)
        val statement = fields["statement"] as String
        assertTrue(statement.toByteArray().size <= 64 * 1024)
        assertFalse(statement.contains('\uFFFD'))
        assertEquals(true, fields["statement_truncated"])
        assertEquals(text.toByteArray().size.toLong(), fields["statement_original_bytes"])
    }

    @Test fun `execution metadata excludes stored values and error messages`() {
        val event = EventFactory().createExecuteEvent(
            results = listOf(
                QueryResultLog(2, 17, emptyList(), listOf(mapOf("secret" to "returned-sensitive-value")), 1),
                UpdateResultLog(3),
                ErrorResultLog(42, "secret raw error"),
            ),
        )
        val json = mapper.writeValueAsString(accessFields(event, "web") + executionResultFields(event))
        val parsed = mapper.readTree(json)
        assertEquals(17, parsed["results"][0]["rows_returned"].asInt())
        assertEquals(3, parsed["results"][1]["rows_affected"].asInt())
        assertFalse(json.contains("returned-sensitive-value"))
        assertFalse(json.contains("secret raw error"))
        val proxy = mapper.writeValueAsString(accessFields(event, "database_proxy"))
        assertFalse(proxy.contains("rows_returned"))
    }

    @Test fun `proxy origin is captured without result counts and cannot leak into the next web event`() {
        val stream = service()
        val event = EventFactory().createExecuteEvent()
        val details = ExecutionRequestDetailsFactory().createExecutionRequestDetails(request = event.request)
        val requests = mockk<ExecutionRequestAdapter>()
        every { requests.getExecutionRequestDetailsForUpdate(any()) } returns details
        every { requests.addEvent(any(), any(), any()) } returns (details to event)
        val events = EventService(requests, mockk<EventAdapter>(), stream)
        val id = ExecutionRequestId(event.request.getId())
        val payload = ExecutePayload(query = event.query)
        events.saveProxyEvent(id, "actor", payload, "safe-session-id", "postgresql")
        events.saveEvent(id, "actor", payload)
        val proxy = mapper.readTree(lines()[0])
        val web = mapper.readTree(lines()[1])
        assertEquals("database_proxy", proxy["kviklet"]["execution"]["channel"].asText())
        assertEquals("safe-session-id", proxy["kviklet"]["proxy"]["session_id"].asText())
        assertEquals("unknown", proxy["event"]["outcome"].asText())
        assertFalse(proxy["kviklet"].has("results"))
        assertEquals("web", web["kviklet"]["execution"]["channel"].asText())
        assertFalse(web["kviklet"].has("proxy"))
    }

    @Test fun `one writer per directory and no symlink output`() {
        EventFileWriter(settings()).use {
            assertThrows(Exception::class.java) { EventFileWriter(settings()) }
        }
        Files.delete(directory.resolve("events.jsonl"))
        val target = directory.resolve("unrelated")
        Files.writeString(target, "keep")
        Files.createSymbolicLink(directory.resolve("events.jsonl"), target)
        assertThrows(IllegalArgumentException::class.java) { EventFileWriter(settings()) }
        assertEquals("keep", Files.readString(target))
    }

    @Test fun `size rotation preserves JSON and restrictive file permissions`() {
        EventFileWriter(settings()).use { output ->
            repeat(700) { output.write(mapper.writeValueAsString(mapOf("n" to it, "text" to "x".repeat(2048)))) }
        }
        val files = Files.list(directory).use { it.filter { p -> p.toString().endsWith(".jsonl") }.toList() }
        assertTrue(files.size >= 2)
        assertEquals(700, files.sumOf { Files.readAllLines(it).size })
        files.forEach { file ->
            assertEquals(PosixFilePermissions.fromString("rw-r-----"), Files.getPosixFilePermissions(file))
            Files.readAllLines(file).forEach { mapper.readTree(it) }
        }
    }

    @Test fun `retention removes old owned archives and keeps unrelated files`() {
        val old = directory.resolve("events.${LocalDate.now(java.time.ZoneOffset.UTC).minusDays(10)}.0.jsonl")
        Files.writeString(old, "{\"old\":true}\n")
        val unrelated = directory.resolve("unrelated.jsonl")
        Files.writeString(unrelated, "keep")
        EventFileWriter(settings().copy(retentionDays = 2)).use { writer ->
            writer.maintain()
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (Files.exists(old) && System.nanoTime() < deadline) Thread.sleep(20)
            assertFalse(Files.exists(old))
            assertEquals("keep", Files.readString(unrelated))
        }
    }

    @Test fun `events from a commit callback are emitted at completion`() {
        val stream = service()
        TransactionSynchronizationManager.setActualTransactionActive(true)
        TransactionSynchronizationManager.initSynchronization()
        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCommit() {
                stream.emit("proxy.session_ended", "network")
            }
        })
        TransactionSynchronizationManager.getSynchronizations().forEach { it.afterCommit() }
        val callbacks = TransactionSynchronizationManager.getSynchronizations()
        TransactionSynchronizationManager.clearSynchronization()
        callbacks.forEach { it.afterCompletion(TransactionSynchronization.STATUS_COMMITTED) }
        assertEquals("proxy.session_ended", mapper.readTree(lines().single())["event"]["action"].asText())
    }

    @Test fun `archive size cleanup excludes the active file`() {
        val today = LocalDate.now(java.time.ZoneOffset.UTC)
        val first = directory.resolve("events.$today.0.jsonl")
        val second = directory.resolve("events.$today.1.jsonl")
        Files.write(first, ByteArray(1200000))
        Files.write(second, ByteArray(1200000))
        Files.setLastModifiedTime(
            first,
            java.nio.file.attribute.FileTime.from(java.time.Instant.now().minusSeconds(60)),
        )
        EventFileWriter(settings()).use { writer ->
            writer.maintain()
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (Files.exists(first) && Files.exists(second) && System.nanoTime() < deadline) Thread.sleep(20)
            assertFalse(Files.exists(first) && Files.exists(second))
            assertTrue(Files.exists(directory.resolve("events.jsonl")))
        }
    }

    @Test fun `restart discards only incomplete trailing bytes`() {
        Files.writeString(directory.resolve("events.jsonl"), "{\"n\":1}\n{\"n\":")
        EventFileWriter(settings()).use { it.write("{\"n\":2}") }
        assertEquals(listOf("{\"n\":1}", "{\"n\":2}"), lines())
    }

    @Test fun `a rejected directory change keeps the old stream running`() {
        val stream = service()
        stream.emit("first", "iam")
        val nextDirectory = directory.resolve("replacement")
        EventFileWriter(settings().copy(directory = nextDirectory.toString())).use {
            assertThrows(IllegalArgumentException::class.java) {
                stream.configure(settings().copy(directory = nextDirectory.toString()))
            }
            stream.emit("second", "iam")
            assertEquals(directory.toString(), stream.getConfiguration().settings.directory)
            assertEquals(2, lines().size)
        }
    }

    @Test fun `output errors are visible and do not fail caller`() {
        Files.createSymbolicLink(directory.resolve("events.jsonl"), directory.resolve("missing"))
        val stream = service()
        assertDoesNotThrow { stream.emit("test", "iam") }
        assertEquals("degraded", stream.getConfiguration().status.state)
        assertTrue(stream.getConfiguration().status.detectedFailures > 0)
    }

    @Test fun `expired license requires explicit reenable`() {
        validUntil = LocalDate.now()
        val stream = service()
        stream.emit("test", "iam")
        assertEquals("license_expired", stream.getConfiguration().status.state)
        validUntil = LocalDate.now().plusDays(10)
        stream.configure(settings())
        stream.emit("test", "iam")
        assertEquals("active", stream.getConfiguration().status.state)
        assertTrue(lines().isNotEmpty())
    }
}
