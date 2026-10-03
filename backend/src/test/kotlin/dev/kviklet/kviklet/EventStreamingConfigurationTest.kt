package dev.kviklet.kviklet

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import dev.kviklet.kviklet.db.ConfigurationAdapter
import dev.kviklet.kviklet.db.UserAdapter
import dev.kviklet.kviklet.service.EventStreamingService
import dev.kviklet.kviklet.service.LicenseService
import dev.kviklet.kviklet.service.dto.EventLoggingLevel
import dev.kviklet.kviklet.service.dto.EventStreamingSettings
import dev.kviklet.kviklet.service.dto.License
import dev.kviklet.kviklet.service.dto.LicenseFile
import dev.kviklet.kviklet.service.eventstream.EventFileWriter
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.transaction.support.TransactionTemplate
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class EventStreamingConfigurationTest {
    @TempDir lateinit var directory: Path
    private val mapper = jacksonObjectMapper().findAndRegisterModules()
    private fun settings() = EventStreamingSettings(true, directory.toString(), 1, 180, 2)

    private fun licenses() = mockk<LicenseService>().also {
        every { it.getActiveLicense() } returns License(
            LicenseFile("test", "test", LocalDateTime.now()),
            LocalDate.now().plusDays(2),
            LocalDateTime.now(),
            100u,
        )
    }

    private fun service(settings: EventStreamingSettings = settings()): EventStreamingService {
        val adapter = mockk<ConfigurationAdapter>(relaxed = true)
        every { adapter.getConfiguration(EventStreamingService.CONFIG_KEY) } returns mapper.writeValueAsString(settings)
        return EventStreamingService(
            adapter,
            licenses(),
            mapper,
            ApplicationProperties(),
            mockk<UserAdapter>(relaxed = true),
        ).also {
            it.refresh()
        }
    }

    @Test fun `settings saves preserve commit order without blocking ordinary events`() {
        val jdbc = JdbcTemplate(DriverManagerDataSource("jdbc:h2:mem:stream_${UUID.randomUUID()};DB_CLOSE_DELAY=-1"))
        val tx = TransactionTemplate(DataSourceTransactionManager(jdbc.dataSource!!))
        val initial = settings().copy(loggingLevel = EventLoggingLevel.WITHOUT_QUERY_TEXT)
        jdbc.execute("CREATE TABLE settings (id INT PRIMARY KEY, payload VARCHAR)")
        jdbc.update("INSERT INTO settings VALUES (1, ?)", mapper.writeValueAsString(initial))
        val adapter = mockk<ConfigurationAdapter>()
        every { adapter.getConfiguration(any()) } answers {
            jdbc.queryForObject("SELECT payload FROM settings WHERE id=1", String::class.java)
        }
        every { adapter.setConfiguration(any<String>(), any<String>()) } answers {
            jdbc.update("UPDATE settings SET payload=? WHERE id=1", secondArg<String>())
            Unit
        }
        val stream =
            EventStreamingService(
                adapter,
                licenses(),
                mapper,
                ApplicationProperties(),
                mockk<UserAdapter>(relaxed = true),
            )
        stream.refresh()
        val committed = CountDownLatch(1)
        val release = CountDownLatch(1)
        val saving = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(3)
        try {
            val older = executor.submit {
                tx.executeWithoutResult {
                    TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
                        override fun afterCommit() {
                            committed.countDown()
                            check(release.await(10, TimeUnit.SECONDS))
                        }
                    })
                    stream.configure(initial.copy(loggingLevel = EventLoggingLevel.FULL))
                }
            }
            assertTrue(committed.await(5, TimeUnit.SECONDS))
            val newer = executor.submit {
                tx.executeWithoutResult {
                    saving.countDown()
                    stream.configure(initial.copy(loggingLevel = EventLoggingLevel.SECURITY_ONLY))
                }
            }
            assertTrue(saving.await(5, TimeUnit.SECONDS))
            assertThrows(TimeoutException::class.java) { newer.get(100, TimeUnit.MILLISECONDS) }
            executor.submit { stream.emit("role.created", "iam") }.get(1, TimeUnit.SECONDS)
            release.countDown()
            older.get(5, TimeUnit.SECONDS)
            newer.get(5, TimeUnit.SECONDS)
            val saved = mapper.readValue(
                jdbc.queryForObject("SELECT payload FROM settings WHERE id=1", String::class.java),
                EventStreamingSettings::class.java,
            )
            assertEquals(EventLoggingLevel.SECURITY_ONLY, saved.loggingLevel)
            assertEquals(saved.loggingLevel, stream.getConfiguration().settings.loggingLevel)
            val records = Files.readAllLines(directory.resolve("events.jsonl")).map { mapper.readTree(it) }
            val changes = records.filter { it["event"]["action"].asText() == "event_stream.configuration_changed" }
            assertEquals(
                listOf("WITHOUT_QUERY_TEXT", "FULL"),
                changes.map {
                    it["kviklet"]["before"]["loggingLevel"].asText()
                },
            )
            assertEquals(
                listOf("FULL", "SECURITY_ONLY"),
                changes.map {
                    it["kviklet"]["after"]["loggingLevel"].asText()
                },
            )
            val count = records.size
            stream.emit(
                "execution.attempted",
                "database",
                fields = mapOf("execution" to mapOf("statement" to "SELECT 1")),
            )
            assertEquals(count, Files.readAllLines(directory.resolve("events.jsonl")).size)
        } finally {
            release.countDown()
            executor.shutdown()
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
            stream.shutdown()
            jdbc.execute("SHUTDOWN")
        }
    }

    @Test fun `rolled-back enable leaves destination files untouched and the save lock released`() {
        val tx =
            TransactionTemplate(
                DataSourceTransactionManager(DriverManagerDataSource("jdbc:h2:mem:rollback_${UUID.randomUUID()}")),
            )
        val initial = settings().copy(enabled = false, directory = directory.resolve("live").toString())
        val stream = service(initial)
        val target = Files.createDirectory(directory.resolve("destination"))
        val archive = target.resolve("events.${LocalDate.now(ZoneOffset.UTC).minusDays(10)}.0.jsonl")
        val active = target.resolve("events.jsonl")
        val tail = "{\"keep\":true}\n{\"unfinished\":"
        Files.writeString(archive, "{\"archive\":true}\n")
        Files.writeString(active, tail)
        val next = initial.copy(enabled = true, directory = target.toString(), retentionDays = 2)
        try {
            tx.executeWithoutResult { status ->
                stream.configure(next)
                assertEquals(tail, Files.readString(active))
                assertTrue(Files.exists(archive))
                status.setRollbackOnly()
            }
            assertEquals(initial, stream.getConfiguration().settings)
            assertEquals(tail, Files.readString(active))
            assertTrue(Files.exists(archive))
            assertFalse(Files.exists(target.resolve("events.lock")))
            // A different thread must be able to save after rollback, including the same directory.
            val executor = Executors.newSingleThreadExecutor()
            try {
                executor.submit { stream.configure(next) }.get(5, TimeUnit.SECONDS)
            } finally {
                executor.shutdownNow()
            }
            assertEquals(next, stream.getConfiguration().settings)
            val records = Files.readAllLines(active).map { mapper.readTree(it) }
            assertEquals(2, records.size)
            assertTrue(records.first()["keep"].asBoolean())
            assertEquals("event_stream.enabled", records.last()["event"]["action"].asText())
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
            while (Files.exists(archive) && System.nanoTime() < deadline) Thread.sleep(10)
            assertFalse(Files.exists(archive))
        } finally {
            stream.shutdown()
        }
    }

    @Test fun `failed settings persistence preserves destination data and releases the save lock`() {
        val initial = settings().copy(enabled = false, directory = directory.resolve("live").toString())
        val adapter = mockk<ConfigurationAdapter>(relaxed = true)
        every { adapter.getConfiguration(EventStreamingService.CONFIG_KEY) } returns mapper.writeValueAsString(initial)
        val stream = EventStreamingService(
            adapter,
            licenses(),
            mapper,
            ApplicationProperties(),
            mockk<UserAdapter>(relaxed = true),
        ).also {
            it.refresh()
        }
        val target = Files.createDirectory(directory.resolve("destination"))
        val archive = target.resolve("events.${LocalDate.now(ZoneOffset.UTC).minusDays(10)}.0.jsonl")
        Files.writeString(archive, "{\"keep\":true}\n")
        val tx =
            TransactionTemplate(
                DataSourceTransactionManager(DriverManagerDataSource("jdbc:h2:mem:failure_${UUID.randomUUID()}")),
            )
        val next = initial.copy(enabled = true, directory = target.toString(), retentionDays = 2)
        every { adapter.setConfiguration(any<String>(), any<String>()) } throws
            IllegalStateException("database unavailable")
        try {
            assertThrows(IllegalStateException::class.java) { tx.executeWithoutResult { stream.configure(next) } }
            assertTrue(Files.exists(archive))
            assertFalse(Files.exists(target.resolve("events.lock")))
            assertEquals(initial, stream.getConfiguration().settings)
            every { adapter.setConfiguration(any<String>(), any<String>()) } returns Unit
            val executor = Executors.newSingleThreadExecutor()
            try {
                executor.submit { stream.configure(next) }.get(5, TimeUnit.SECONDS)
                assertEquals("active", stream.getConfiguration().status.state)
            } finally {
                executor.shutdownNow()
            }
        } finally {
            stream.shutdown()
        }
    }

    @Test fun `committed enable reports degraded health when the directory cannot be opened`() {
        val tx = TransactionTemplate(
            DataSourceTransactionManager(DriverManagerDataSource("jdbc:h2:mem:enable_${UUID.randomUUID()}")),
        )
        val initial = settings().copy(enabled = false)
        val stream = service(initial)
        val target = Files.writeString(directory.resolve("not-a-directory"), "keep")
        val next = initial.copy(enabled = true, directory = target.toString())
        try {
            tx.executeWithoutResult { stream.configure(next) }
            val response = stream.getConfiguration()
            assertEquals(next, response.settings)
            assertEquals("degraded", response.status.state)
            assertTrue(response.status.lastError!!.contains("Event file output is unavailable"))
            assertEquals("keep", Files.readString(target))
        } finally {
            stream.shutdown()
        }
    }

    @Test fun `unsafe archive budgets are rejected without deleting existing files`() {
        val initial = settings().copy(maxArchiveSizeMiB = 1)
        val archive = directory.resolve("events.${LocalDate.now(ZoneOffset.UTC).minusDays(200)}.0.jsonl")
        Files.writeString(archive, "{\"keep\":true}\n")
        val stream = service(initial)
        try {
            assertEquals("degraded", stream.getConfiguration().status.state)
            assertTrue(stream.getConfiguration().status.lastError!!.contains("at least 1 MiB larger"))
            assertTrue(Files.exists(archive))
            assertThrows(IllegalArgumentException::class.java) { stream.configure(initial) }
            assertThrows(IllegalArgumentException::class.java) { EventFileWriter(initial) }
            assertTrue(Files.exists(archive))
        } finally {
            stream.shutdown()
        }
    }

    @Test fun `one MiB headroom retains an archive even after a maximum-size record`() {
        EventFileWriter(settings()).use { writer ->
            val cleanup = EventFileWriter::class.java.getDeclaredField("cleanup").also { it.isAccessible = true }
            (cleanup.get(writer) as Future<*>).get(3, TimeUnit.SECONDS)
            writer.write("{\"warmup\":true}")
            val large = mapper.writeValueAsString(mapOf("value" to "x".repeat(EventFileWriter.MAX_RECORD_BYTES - 13)))
            assertEquals(EventFileWriter.MAX_RECORD_BYTES, large.toByteArray(Charsets.UTF_8).size + 1)
            writer.write(large)
            writer.write("{\"after\":true}")
            (cleanup.get(writer) as Future<*>).get(3, TimeUnit.SECONDS)
            val archives = Files.list(directory).use { paths ->
                paths.filter {
                    it.fileName.toString().matches(Regex("events\\.\\d{4}-\\d{2}-\\d{2}\\.\\d+\\.jsonl"))
                }.toList()
            }
            assertEquals(1, archives.size)
            assertTrue(Files.size(archives.single()) > 1024 * 1024)
            assertTrue(Files.size(archives.single()) <= 2 * 1024 * 1024)
            val records = Files.readAllLines(archives.single()) + Files.readAllLines(directory.resolve("events.jsonl"))
            assertEquals(3, records.size)
            records.forEach { mapper.readTree(it) }
        }
    }
}
