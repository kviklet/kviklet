package dev.kviklet.kviklet

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import dev.kviklet.kviklet.db.UserAdapter
import dev.kviklet.kviklet.helper.eventStreamingProperties
import dev.kviklet.kviklet.service.EventStreamingService
import dev.kviklet.kviklet.service.LicenseService
import dev.kviklet.kviklet.service.dto.EventLoggingLevel
import dev.kviklet.kviklet.service.dto.EventStreamingSettings
import dev.kviklet.kviklet.service.eventstream.EventFileWriter
import dev.kviklet.kviklet.service.eventstream.EventStreamingProperties
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.SystemEnvironmentPropertySource
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

class EventStreamingConfigurationTest {
    @TempDir lateinit var directory: Path
    private val mapper = jacksonObjectMapper().findAndRegisterModules()
    private fun settings() = EventStreamingSettings(true, directory.toString(), 1, 7, 2)

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(EventStreamingProperties::class)
    class StartupConfiguration {
        @Bean
        fun stream(properties: EventStreamingProperties) = EventStreamingService(
            properties,
            mockk<LicenseService>(relaxed = true),
            jacksonObjectMapper(),
            ApplicationProperties(),
            mockk<UserAdapter>(relaxed = true),
        )
    }

    private val context = ApplicationContextRunner().withUserConfiguration(StartupConfiguration::class.java)

    @Test fun `startup defaults are disabled full logging with 180 days and 100 MiB archives`() {
        context.run {
            assertTrue(it.startupFailure == null)
            assertEquals(
                EventStreamingSettings(),
                it.getBean(EventStreamingService::class.java).getConfiguration().settings,
            )
        }
    }

    @Test fun `documented environment variables bind all six startup settings`() {
        context.withInitializer {
            it.environment.propertySources.addFirst(
                SystemEnvironmentPropertySource(
                    "systemEnvironment",
                    mapOf(
                        "KVIKLET_EVENTSTREAMING_ENABLED" to "true",
                        "KVIKLET_EVENTSTREAMING_DIRECTORY" to directory.toString(),
                        "KVIKLET_EVENTSTREAMING_MAXFILESIZEMIB" to "20",
                        "KVIKLET_EVENTSTREAMING_RETENTIONDAYS" to "90",
                        "KVIKLET_EVENTSTREAMING_MAXARCHIVESIZEMIB" to "200",
                        "KVIKLET_EVENTSTREAMING_LOGGINGLEVEL" to "WITHOUT_QUERY_TEXT",
                    ),
                ),
            )
        }.run {
            assertTrue(it.startupFailure == null, it.startupFailure?.toString())
            assertEquals(
                EventStreamingSettings(true, directory.toString(), 20, 90, 200, EventLoggingLevel.WITHOUT_QUERY_TEXT),
                it.getBean(EventStreamingService::class.java).getConfiguration().settings,
            )
        }
    }

    @Test fun `invalid deployment values fail startup even when disabled`() {
        for (invalid in listOf(
            "directory=relative/path", "max-file-size-mib=0", "max-file-size-mib=1025",
            "retention-days=0", "retention-days=366", "max-archive-size-mib=10",
            "max-archive-size-mib=102401", "logging-level=DEBUG", "logging-level=0",
        )) {
            context.withPropertyValues("kviklet.event-streaming.$invalid").run {
                assertTrue(it.startupFailure != null, "Accepted invalid setting: $invalid")
            }
        }
    }

    @Test fun `bound properties cannot change the running settings snapshot`() {
        val properties = eventStreamingProperties(settings().copy(enabled = false))
        val service =
            EventStreamingService(
                properties,
                mockk(relaxed = true),
                mapper,
                ApplicationProperties(),
                mockk(relaxed = true),
            )
        try {
            properties.enabled = true
            properties.loggingLevel = EventLoggingLevel.SECURITY_ONLY
            assertEquals(settings().copy(enabled = false), service.getConfiguration().settings)
            assertFalse(Files.exists(directory.resolve("events.jsonl")))
        } finally {
            service.shutdown()
        }
    }

    @Test fun `invalid archive budgets leave existing files untouched`() {
        val archive = directory.resolve("events.2025-01-01.0.jsonl")
        Files.writeString(archive, "{}\n")
        val invalid = settings().copy(maxArchiveSizeMiB = 1)
        assertThrows(IllegalArgumentException::class.java) { eventStreamingProperties(invalid).toSettings() }
        assertThrows(IllegalArgumentException::class.java) { EventFileWriter(invalid) }
        assertTrue(Files.exists(archive))
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
