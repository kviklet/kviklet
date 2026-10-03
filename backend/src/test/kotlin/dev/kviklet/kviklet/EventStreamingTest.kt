package dev.kviklet.kviklet

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import dev.kviklet.kviklet.db.ConfigurationAdapter
import dev.kviklet.kviklet.db.EventAdapter
import dev.kviklet.kviklet.db.ExecutePayload
import dev.kviklet.kviklet.db.ExecutionRequestAdapter
import dev.kviklet.kviklet.db.User
import dev.kviklet.kviklet.db.UserAdapter
import dev.kviklet.kviklet.db.UserId
import dev.kviklet.kviklet.helper.EventFactory
import dev.kviklet.kviklet.helper.ExecutionRequestDetailsFactory
import dev.kviklet.kviklet.helper.ExecutionRequestFactory
import dev.kviklet.kviklet.service.EventService
import dev.kviklet.kviklet.service.EventStreamingService
import dev.kviklet.kviklet.service.LicenseService
import dev.kviklet.kviklet.service.dto.ErrorResultLog
import dev.kviklet.kviklet.service.dto.EventLoggingLevel
import dev.kviklet.kviklet.service.dto.EventStreamingSettings
import dev.kviklet.kviklet.service.dto.ExecutionRequestId
import dev.kviklet.kviklet.service.dto.License
import dev.kviklet.kviklet.service.dto.LicenseFile
import dev.kviklet.kviklet.service.dto.QueryResultLog
import dev.kviklet.kviklet.service.dto.Role
import dev.kviklet.kviklet.service.dto.RoleId
import dev.kviklet.kviklet.service.dto.UpdateResultLog
import dev.kviklet.kviklet.service.eventstream.EventFileWriter
import dev.kviklet.kviklet.service.eventstream.accessFields
import dev.kviklet.kviklet.service.eventstream.executionResultFields
import dev.kviklet.kviklet.service.eventstream.requestFields
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
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
import java.time.Instant
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

    private fun service(
        enabled: Boolean = true,
        level: EventLoggingLevel = EventLoggingLevel.FULL,
        users: UserAdapter = mockk(relaxed = true),
    ): EventStreamingService {
        every { configuration.getConfiguration(EventStreamingService.CONFIG_KEY) } returns
            mapper.writeValueAsString(settings(enabled).copy(loggingLevel = level))
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
        return EventStreamingService(configuration, licenseService, mapper, ApplicationProperties(), users).also {
            streams.add(it)
            it.refresh()
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

    @Test fun `actor fields snapshot current identity and roles without secrets or unverified login identity`() {
        val users = mockk<UserAdapter>()
        val admin = Role.create(RoleId("admin"), "Admin", "private-role-description", emptySet())
        val reviewer = Role.create(RoleId("reviewer"), "Reviewer", "", emptySet())
        var user = User(
            UserId("actor"),
            fullName = "Dan Nguyen",
            email = "dan@example.com",
            password = "private-password-hash",
            roles = setOf(reviewer, admin),
        )
        every { users.findById("actor") } answers { user }
        val stream = service(users = users)
        stream.emit("execution.completed", "database", actorId = "actor", authentication = null)
        user = user.copy(fullName = "Daniel Nguyen", roles = setOf(reviewer))
        stream.emit("role.changed", "iam", actorId = "actor", authentication = null)
        stream.emit("authentication.login", "authentication", "failure", authentication = null)
        val records = lines().map { mapper.readTree(it) }
        assertEquals("actor", records[0]["user"]["id"].asText())
        assertEquals("Dan Nguyen", records[0]["user"]["name"].asText())
        assertEquals("dan@example.com", records[0]["user"]["email"].asText())
        assertEquals(listOf("Admin", "Reviewer"), records[0]["user"]["roles"].map { it.asText() })
        assertEquals("Daniel Nguyen", records[1]["user"]["name"].asText())
        assertEquals(listOf("Reviewer"), records[1]["user"]["roles"].map { it.asText() })
        assertFalse(records[2].has("user"))
        assertFalse(lines().joinToString().contains("private-password-hash"))
        assertFalse(lines().joinToString().contains("private-role-description"))
        verify(exactly = 2) { users.findById(any()) }
    }

    @Test fun `disabled and unlicensed streams never write`() {
        service(false).emit("test", "iam")
        assertTrue(lines().isEmpty())
        licensed = false
        service().emit("test", "iam")
        assertTrue(lines().isEmpty())
    }

    @Test fun `legacy persisted settings default to full logging`() {
        every { configuration.getConfiguration(EventStreamingService.CONFIG_KEY) } returns
            """{"enabled":false,"directory":"/var/log/kviklet/events","retentionDays":180}"""
        val stream =
            EventStreamingService(
                configuration,
                licenseService,
                mapper,
                ApplicationProperties(),
                mockk<UserAdapter>(relaxed = true),
            )
        streams.add(stream)
        stream.refresh()
        assertEquals(EventLoggingLevel.FULL, stream.getConfiguration().settings.loggingLevel)
    }

    @Test fun `security-only logging excludes routine events before preparing fields`() {
        val stream = service(level = EventLoggingLevel.SECURITY_ONLY)
        val included = listOf(
            "authentication.login",
            "authentication.logout",
            "authorization.denied",
            "user.roles_changed",
            "role.created",
            "api_key.revoked",
            "role_sync.mapping_created",
            "connection.security_changed",
        )
        included.forEach { action -> stream.emit(action, "configuration") }
        val excluded = listOf(
            "request.created",
            "request.reason_changed",
            "review.approve",
            "comment.added",
            "execution.attempted",
            "execution.completed",
            "proxy.session_created",
            "unknown.security_event",
        )
        excluded.forEach { action ->
            stream.emit(action, "iam", fields = { error("Excluded events must never prepare fields") })
        }
        assertEquals(included, lines().map { mapper.readTree(it)["event"]["action"].asText() })
        assertEquals(0, stream.getConfiguration().status.detectedFailures)
    }

    @Test fun `without-query-text logging preserves reasons and results across execution channels`() {
        val stream = service(level = EventLoggingLevel.WITHOUT_QUERY_TEXT)
        val request = ExecutionRequestFactory().createDatasourceExecutionRequest(description = "Investigate SEC-42")
        val query = EventFactory().createExecuteEvent(
            request = request,
            query = "SELECT sensitive_literal",
            results = listOf(UpdateResultLog(3)),
        )
        for (channel in listOf("web", "api", "database_proxy", "kubernetes")) {
            val event = if (channel ==
                "kubernetes"
            ) {
                query.copy(query = null, command = "echo sensitive_literal")
            } else {
                query
            }
            stream.emit("execution.completed", "database", fields = { level ->
                assertEquals(EventLoggingLevel.WITHOUT_QUERY_TEXT, level)
                val fields = accessFields(event, channel, level)
                assertFalse((fields["execution"] as Map<*, *>).containsKey("statement"))
                fields + executionResultFields(event)
            })
        }
        for (line in lines()) {
            val fields = mapper.readTree(line)["kviklet"]
            assertEquals("Investigate SEC-42", fields["request"]["reason"].asText())
            assertEquals(3, fields["results"][0]["rows_affected"].asInt())
            assertFalse(fields["execution"].has("statement"))
            assertFalse(fields["execution"].has("statement_truncated"))
            assertFalse(fields["execution"].has("statement_original_bytes"))
            assertFalse(line.contains("sensitive_literal"))
        }
        assertEquals(4, lines().size)
    }

    @Test fun `downgrade before transaction commit removes already prepared query text and audits the change`() {
        val stream = service()
        val event = EventFactory().createExecuteEvent(query = "SELECT private_literal")
        TransactionSynchronizationManager.setActualTransactionActive(true)
        TransactionSynchronizationManager.initSynchronization()
        stream.emit("execution.attempted", "database", fields = { accessFields(event, "web", it) })
        val pending = TransactionSynchronizationManager.getSynchronizations()
        TransactionSynchronizationManager.clearSynchronization()
        TransactionSynchronizationManager.setActualTransactionActive(false)
        stream.configure(settings().copy(loggingLevel = EventLoggingLevel.WITHOUT_QUERY_TEXT))
        pending.forEach { it.afterCommit() }
        val records = lines().map { mapper.readTree(it) }
        val change = records.first { it["event"]["action"].asText() == "event_stream.configuration_changed" }
        assertEquals("FULL", change["kviklet"]["before"]["loggingLevel"].asText())
        assertEquals("WITHOUT_QUERY_TEXT", change["kviklet"]["after"]["loggingLevel"].asText())
        val execution = records.first { it["event"]["action"].asText() == "execution.attempted" }
        assertEquals("query", execution["kviklet"]["execution"]["mode"].asText())
        assertFalse(execution["kviklet"]["execution"].has("statement"))
        assertFalse(lines().joinToString().contains("private_literal"))
    }

    @Test fun `security-only downgrade excludes pending execution while retaining the settings audit`() {
        val stream = service()
        TransactionSynchronizationManager.setActualTransactionActive(true)
        TransactionSynchronizationManager.initSynchronization()
        stream.emit("execution.completed", "database")
        val pending = TransactionSynchronizationManager.getSynchronizations()
        TransactionSynchronizationManager.clearSynchronization()
        TransactionSynchronizationManager.setActualTransactionActive(false)
        stream.configure(settings().copy(loggingLevel = EventLoggingLevel.SECURITY_ONLY))
        pending.forEach { it.afterCommit() }
        assertEquals(
            listOf("event_stream.configuration_changed"),
            lines().map {
                mapper.readTree(it)["event"]["action"].asText()
            },
        )
        stream.configure(settings().copy(loggingLevel = EventLoggingLevel.WITHOUT_QUERY_TEXT))
        assertEquals(2, lines().size)
    }

    @Test fun `upgrade does not add text to an event captured without query text`() {
        val stream = service(level = EventLoggingLevel.WITHOUT_QUERY_TEXT)
        val event = EventFactory().createExecuteEvent(query = "SELECT private_literal")
        TransactionSynchronizationManager.setActualTransactionActive(true)
        TransactionSynchronizationManager.initSynchronization()
        stream.emit("execution.attempted", "database", fields = { accessFields(event, "web", it) })
        // Explicit maps must obey the capture-time level even without the standard field helper.
        stream.emit(
            "execution.attempted",
            "database",
            fields = mapOf(
                "execution" to mapOf("mode" to "query", "statement" to "SELECT private_literal"),
            ),
        )
        val pending = TransactionSynchronizationManager.getSynchronizations()
        TransactionSynchronizationManager.clearSynchronization()
        TransactionSynchronizationManager.setActualTransactionActive(false)
        stream.configure(settings())
        pending.forEach { it.afterCommit() }
        assertFalse(lines().joinToString().contains("private_literal"))
        assertEquals(3, lines().size)
    }

    @Test fun `rolled-back settings changes do not change the active logging level`() {
        val stream = service()
        TransactionSynchronizationManager.setActualTransactionActive(true)
        TransactionSynchronizationManager.initSynchronization()
        stream.configure(settings().copy(loggingLevel = EventLoggingLevel.SECURITY_ONLY))
        TransactionSynchronizationManager.getSynchronizations().forEach {
            it.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK)
        }
        TransactionSynchronizationManager.clearSynchronization()
        TransactionSynchronizationManager.setActualTransactionActive(false)
        assertEquals(EventLoggingLevel.FULL, stream.getConfiguration().settings.loggingLevel)
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
        assertEquals(setOf("statement"), fields.keys)
    }

    @Test fun `access reason retains long text and truncates only beyond 64 KiB without splitting UTF8`() {
        val prefix = "Investigate ticket SEC-42\n\"Customer access\" ".repeat(400)
        val fields = EventStreamingService.reason(prefix)
        assertEquals(prefix, fields["reason"])
        assertEquals(setOf("reason"), fields.keys)
        val text = "x".repeat(64 * 1024 - 1) + "한😀"
        val truncated = EventStreamingService.reason(text)
        assertEquals("x".repeat(64 * 1024 - 1), truncated["reason"])
        assertEquals(setOf("reason"), truncated.keys)
        val exact = "x".repeat(64 * 1024 - 4) + "😀"
        assertEquals(exact, EventStreamingService.reason(exact)["reason"])
        assertEquals("", EventStreamingService.reason("")["reason"])
        val missing = ExecutionRequestFactory().createDatasourceExecutionRequest(description = null)
        val missingFields = mapper.valueToTree<com.fasterxml.jackson.databind.JsonNode>(requestFields(missing))
        assertEquals(missing.title, missingFields["request"]["title"].asText())
        assertFalse(missingFields["request"].has("reason"))
        val untitled = ExecutionRequestFactory().createDatasourceExecutionRequest(title = "", description = null)
        assertFalse(requestFields(untitled).containsKey("request"))
    }

    @Test fun `review and proxy execution fields carry the reason without review comment text`() {
        val request = ExecutionRequestFactory().createDatasourceExecutionRequest(description = "Investigate SEC-42")
        val review = EventFactory().createReviewApprovedEvent(request = request, comment = "private review comment")
        val proxy = EventFactory().createExecuteEvent(request = request)
        val expectedConnection = mapOf(
            "id" to request.connection.getId(),
            "type" to "DATASOURCE",
            "name" to request.connection.displayName,
            "database_type" to request.connection.type.name,
            "hostname" to request.connection.hostname,
            "port" to request.connection.port,
            "database_name" to request.connection.databaseName,
        )
        assertEquals(expectedConnection, requestFields(request)["connection"])
        for (fields in listOf(accessFields(review, "web"), accessFields(proxy, "database_proxy"))) {
            assertEquals(expectedConnection, fields["connection"])
            val json = mapper.writeValueAsString(fields)
            assertEquals("Investigate SEC-42", mapper.readTree(json)["request"]["reason"].asText())
            assertEquals(request.title, mapper.readTree(json)["request"]["title"].asText())
            assertFalse(json.contains("private review comment"))
        }
    }

    @Test fun `fully escaped reasons and statements fit together in one bounded JSONL event`() {
        val text = "\u0001".repeat(64 * 1024)
        val request = ExecutionRequestFactory().createDatasourceExecutionRequest(description = text)
        val event = EventFactory().createExecuteEvent(request = request, query = text)
        val stream = service()
        stream.emit("execution.attempted", "database", fields = { accessFields(event, "web") })
        val line = lines().single()
        val json = mapper.readTree(line)
        assertEquals(text, json["kviklet"]["request"]["reason"].asText())
        assertEquals(text, json["kviklet"]["execution"]["statement"].asText())
        assertFalse(json["kviklet"]["request"].has("reason_truncated"))
        assertTrue(line.toByteArray(Charsets.UTF_8).size < 1024 * 1024)
        assertEquals(0, stream.getConfiguration().status.detectedFailures)
        stream.emit("too_large", "configuration", fields = mapOf("extra" to "x".repeat(1024 * 1024)))
        assertEquals(1, lines().size)
        assertEquals(1, stream.getConfiguration().status.detectedFailures)
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

    @Test fun `directory changes require a separately saved disable`() {
        val stream = service()
        stream.emit("first", "iam")
        val nextDirectory = directory.resolve("replacement")
        for (enabled in listOf(true, false)) {
            assertThrows(IllegalArgumentException::class.java) {
                stream.configure(settings(enabled).copy(directory = nextDirectory.toString()))
            }
        }
        assertFalse(Files.exists(nextDirectory))
        stream.emit("second", "iam")
        assertEquals(directory.toString(), stream.getConfiguration().settings.directory)
        assertEquals(2, lines().size)
        stream.configure(settings(false))
        stream.configure(settings(false).copy(directory = nextDirectory.toString()))
        assertFalse(Files.exists(nextDirectory))
        stream.configure(settings().copy(directory = nextDirectory.toString()))
        stream.emit("new_directory", "iam")
        assertEquals(directory.resolve("replacement").toString(), stream.getConfiguration().settings.directory)
        assertTrue(Files.readString(nextDirectory.resolve("events.jsonl")).contains("new_directory"))
    }

    @Test fun `output errors are visible and do not fail caller`() {
        Files.createSymbolicLink(directory.resolve("events.jsonl"), directory.resolve("missing"))
        val stream = service()
        assertDoesNotThrow { stream.emit("test", "iam") }
        assertEquals("degraded", stream.getConfiguration().status.state)
        assertTrue(stream.getConfiguration().status.detectedFailures > 0)
    }

    @Test fun `enabled stream resumes after license renewal without another settings save`() {
        validUntil = LocalDate.now()
        val stream = service()
        stream.emit("test", "iam")
        assertEquals("license_expired", stream.getConfiguration().status.state)
        validUntil = LocalDate.now().plusDays(10)
        EventStreamingService::class.java.getDeclaredField("licenseCheckedAt").also {
            it.isAccessible = true
        }.set(stream, Instant.EPOCH)
        stream.refresh()
        stream.emit("test", "iam")
        assertEquals("active", stream.getConfiguration().status.state)
        assertTrue(lines().isNotEmpty())
    }

    @Test fun `license refresh waits a minute between checks`() {
        val stream = service()
        val checkedAt = EventStreamingService::class.java.getDeclaredField("licenseCheckedAt").also {
            it.isAccessible = true
        }
        checkedAt.set(stream, Instant.now().minusSeconds(30))
        stream.refresh()
        verify(exactly = 1) { licenseService.getActiveLicense() }
        checkedAt.set(stream, Instant.now().minusSeconds(61))
        stream.refresh()
        verify(exactly = 2) { licenseService.getActiveLicense() }
    }
}
