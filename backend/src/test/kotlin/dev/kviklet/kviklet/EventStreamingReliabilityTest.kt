package dev.kviklet.kviklet

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import dev.kviklet.kviklet.db.ConfigurationAdapter
import dev.kviklet.kviklet.service.EventStreamingService
import dev.kviklet.kviklet.service.LicenseService
import dev.kviklet.kviklet.service.dto.EventStreamingSettings
import dev.kviklet.kviklet.service.dto.License
import dev.kviklet.kviklet.service.dto.LicenseFile
import dev.kviklet.kviklet.service.eventstream.EventFileWriter
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

class EventStreamingReliabilityTest {
    @TempDir lateinit var directory: Path
    private fun settings() = EventStreamingSettings(true, directory.toString(), 1, 2, 2)

    @Test fun `rotation failure must be reported to caller`() {
        EventFileWriter(settings()).use { writer ->
            Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("r-x------"))
            try {
                assertThrows(Exception::class.java) {
                    repeat(1600) { writer.write("{\"value\":\"" + "x".repeat(2048) + "\"}") }
                }
            } finally {
                Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwx------"))
            }
        }
    }

    @Test fun `startup retention removes archives older than the inactivity scan window`() {
        val old = directory.resolve("events.${LocalDate.now(ZoneOffset.UTC).minusDays(90)}.0.jsonl")
        Files.writeString(old, "{\"old\":true}\n")
        EventFileWriter(settings()).use { writer ->
            writer.maintain()
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
            while (Files.exists(old) && System.nanoTime() < deadline) Thread.sleep(20)
            assertFalse(Files.exists(old), "90-day archive survived 2-day retention on startup")
        }
    }

    @Test fun `license refresh must not block events holding database connections`() {
        val mapper = jacksonObjectMapper().findAndRegisterModules()
        val configuration = mockk<ConfigurationAdapter>(relaxed = true)
        every { configuration.getConfiguration(EventStreamingService.CONFIG_KEY) } returns
            mapper.writeValueAsString(settings())
        val licenses = mockk<LicenseService>()
        val license =
            License(
                LicenseFile("test", "test", LocalDateTime.now()),
                LocalDate.now().plusDays(2),
                LocalDateTime.now(),
                100u,
            )
        every { licenses.getActiveLicense() } returns license
        val service = EventStreamingService(configuration, licenses, mapper, ApplicationProperties())
        service.refresh()
        EventStreamingService::class.java.getDeclaredField("licenseCheckedAt").also {
            it.isAccessible = true
        }.set(service, Instant.EPOCH)
        // A real one-connection pool deterministically represents an exhausted application pool.
        val database = HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = "jdbc:h2:mem:stream_review"
                maximumPoolSize = 1
                connectionTimeout = 5000
            },
        )
        val requestConnection = database.connection
        val refreshWaitingForConnection = CountDownLatch(1)
        every { licenses.getActiveLicense() } answers {
            refreshWaitingForConnection.countDown()
            database.connection.use { connection ->
                connection.createStatement().use { it.executeQuery("SELECT 1").close() }
            }
            license
        }
        val executor = Executors.newFixedThreadPool(2)
        val refresh = executor.submit { service.refresh() }
        assertTrue(refreshWaitingForConnection.await(2, TimeUnit.SECONDS))
        val request = executor.submit { service.emit("role.changed", "iam") }
        try {
            // Completion before the held connection is released proves there is no monitor/pool cycle.
            request.get(1, TimeUnit.SECONDS)
        } finally {
            requestConnection.close()
            try {
                refresh.get(3, TimeUnit.SECONDS)
                request.get(3, TimeUnit.SECONDS)
            } finally {
                executor.shutdownNow()
                service.shutdown()
                database.close()
            }
        }
    }

    @Test fun `disabled and unlicensed capture does not prepare fields`() {
        val mapper = jacksonObjectMapper().findAndRegisterModules()
        val configuration = mockk<ConfigurationAdapter>(relaxed = true)
        val licenses = mockk<LicenseService>()
        every { licenses.getActiveLicense() } returns null
        for (enabled in listOf(false, true)) {
            every { configuration.getConfiguration(EventStreamingService.CONFIG_KEY) } returns
                mapper.writeValueAsString(settings().copy(enabled = enabled))
            val service = EventStreamingService(configuration, licenses, mapper, ApplicationProperties())
            try {
                service.refresh()
                var prepared = false
                service.emit("execution.attempted", "database", fields = {
                    prepared = true
                    error("Disabled capture must not inspect the statement")
                })
                assertFalse(prepared)
                assertEquals(0, service.getConfiguration().status.detectedFailures)
            } finally {
                service.shutdown()
            }
        }
    }

    @Test fun `field preparation failures do not escape into application requests`() {
        val service = licensedService()
        try {
            assertDoesNotThrow {
                service.emit("execution.attempted", "database", fields = { error("unsafe error detail") })
            }
            val status = service.getConfiguration().status
            assertEquals("degraded", status.state)
            assertEquals(1, status.detectedFailures)
            assertFalse(status.lastError!!.contains("unsafe error detail"))
        } finally {
            service.shutdown()
        }
    }

    @Test fun `rotation failure degrades health and saving after repair resumes capture`() {
        val service = licensedService()
        try {
            Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("r-x------"))
            val fields = mapOf("test" to "x".repeat(8192))
            repeat(150) { service.emit("test", "iam", fields = fields) }
            assertEquals("degraded", service.getConfiguration().status.state)
            Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwx------"))
            service.configure(settings())
            service.emit("after_repair", "iam")
            assertEquals("active", service.getConfiguration().status.state)
            assertTrue(Files.readString(directory.resolve("events.jsonl")).contains("after_repair"))
        } finally {
            Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwx------"))
            service.shutdown()
        }
    }

    @Test fun `failed archive deletion is visible to the writer`() {
        val old = directory.resolve("events.${LocalDate.now(ZoneOffset.UTC).minusDays(90)}.0.jsonl")
        EventFileWriter(settings()).use { writer ->
            awaitCleanup(writer)
            Files.writeString(old, "{}\n")
            Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("r-x------"))
            try {
                // Advance the maintenance deadline without making the test wait a full minute.
                EventFileWriter::class.java.getDeclaredField("cleanedAt").also {
                    it.isAccessible = true
                }.set(writer, Instant.EPOCH)
                writer.maintain()
                awaitCleanup(writer)
                assertThrows(IllegalStateException::class.java) { writer.write("{}") }
                assertTrue(Files.exists(old))
            } finally {
                Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwx------"))
            }
        }
        EventFileWriter(settings()).use { writer ->
            awaitCleanup(writer)
            assertFalse(Files.exists(old))
            assertDoesNotThrow { writer.write("{}") }
        }
    }

    @Test fun `a refresh started before disabling cannot restore the old settings`() {
        val mapper = jacksonObjectMapper().findAndRegisterModules()
        val configuration = mockk<ConfigurationAdapter>(relaxed = true)
        every { configuration.getConfiguration(EventStreamingService.CONFIG_KEY) } returns
            mapper.writeValueAsString(settings())
        val licenses = mockk<LicenseService>()
        val license = License(
            LicenseFile("test", "test", LocalDateTime.now()),
            LocalDate.now().plusDays(2),
            LocalDateTime.now(),
            100u,
        )
        every { licenses.getActiveLicense() } returns license
        val service = EventStreamingService(configuration, licenses, mapper, ApplicationProperties())
        service.refresh()
        EventStreamingService::class.java.getDeclaredField("licenseCheckedAt").also {
            it.isAccessible = true
        }.set(service, Instant.EPOCH)
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        every { licenses.getActiveLicense() } answers {
            started.countDown()
            check(release.await(3, TimeUnit.SECONDS))
            license
        }
        val executor = Executors.newSingleThreadExecutor()
        val pending = executor.submit { service.refresh() }
        try {
            assertTrue(started.await(2, TimeUnit.SECONDS))
            service.configure(settings().copy(enabled = false))
            release.countDown()
            pending.get(3, TimeUnit.SECONDS)
            assertEquals("disabled", service.getConfiguration().status.state)
            assertFalse(service.getConfiguration().settings.enabled)
        } finally {
            release.countDown()
            executor.shutdownNow()
            service.shutdown()
        }
    }

    private fun awaitCleanup(writer: EventFileWriter) {
        val field = EventFileWriter::class.java.getDeclaredField("cleanup").also { it.isAccessible = true }
        (field.get(writer) as Future<*>).get(3, TimeUnit.SECONDS)
    }

    private fun licensedService(): EventStreamingService {
        val mapper = jacksonObjectMapper().findAndRegisterModules()
        val configuration = mockk<ConfigurationAdapter>(relaxed = true)
        every { configuration.getConfiguration(EventStreamingService.CONFIG_KEY) } returns
            mapper.writeValueAsString(settings())
        val licenses = mockk<LicenseService>()
        every { licenses.getActiveLicense() } returns License(
            LicenseFile("test", "test", LocalDateTime.now()),
            LocalDate.now().plusDays(2),
            LocalDateTime.now(),
            100u,
        )
        return EventStreamingService(configuration, licenses, mapper, ApplicationProperties()).also { it.refresh() }
    }
}
