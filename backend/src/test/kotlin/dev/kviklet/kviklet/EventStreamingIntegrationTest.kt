package dev.kviklet.kviklet

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import dev.kviklet.kviklet.db.ExecutionRequestAdapter
import dev.kviklet.kviklet.db.LicenseAdapter
import dev.kviklet.kviklet.helper.ConnectionHelper
import dev.kviklet.kviklet.helper.ExecutionRequestHelper
import dev.kviklet.kviklet.helper.RoleHelper
import dev.kviklet.kviklet.helper.UserHelper
import dev.kviklet.kviklet.service.EventStreamingService
import dev.kviklet.kviklet.service.dto.EventLoggingLevel
import dev.kviklet.kviklet.service.dto.EventStreamingSettings
import dev.kviklet.kviklet.service.dto.LicenseFile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.nio.file.Files
import java.nio.file.Path
import java.sql.DriverManager
import java.time.LocalDateTime

@SpringBootTest(
    properties = [
        // Test cleanup must never run against a developer's database, even with datasource env overrides.
        "spring.datasource.url=jdbc:tc:postgresql:16-alpine:///event_stream_test",
        "spring.datasource.driver-class-name=org.testcontainers.jdbc.ContainerDatabaseDriver",
    ],
)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class EventStreamingIntegrationTest {
    companion object {
        @Container
        @JvmStatic
        val database = PostgreSQLContainer<Nothing>("postgres:16-alpine")
    }

    @Autowired lateinit var requests: ExecutionRequestHelper

    @Autowired lateinit var requestAdapter: ExecutionRequestAdapter

    @Autowired lateinit var connections: ConnectionHelper

    @Autowired lateinit var mockMvc: MockMvc

    @Autowired lateinit var userHelper: UserHelper

    @Autowired lateinit var roleHelper: RoleHelper

    @Autowired lateinit var licenses: LicenseAdapter

    @Autowired lateinit var mapper: ObjectMapper

    @Autowired lateinit var streaming: EventStreamingService

    @TempDir lateinit var directory: Path

    @Test fun `licensed settings enforce permissions and capture login role changes and logout without secrets`() {
        val admin = userHelper.createUser()
        val cookie = userHelper.login(email = admin.email, mockMvc = mockMvc)
        val settings = EventStreamingSettings(true, directory.toString(), 1, 7, 2)
        try {
            mockMvc.perform(get("/config/event-streaming")).andExpect(status().isUnauthorized)
            mockMvc.perform(
                put("/config/event-streaming").cookie(cookie).contentType("application/json")
                    .content(mapper.writeValueAsString(settings)),
            ).andExpect(status().isPaymentRequired)
            assertFalse(Files.exists(directory.resolve("events.jsonl")))
            licenses.createLicense(
                LicenseFile(
                    javaClass.getResource("/event-stream-test-license.json")!!.readText(),
                    "test",
                    LocalDateTime.now(),
                ),
            )
            mockMvc.perform(
                put("/config/event-streaming").cookie(cookie).contentType("application/json")
                    .content(mapper.writeValueAsString(settings)),
            ).andExpect(status().isOk)
            mockMvc.perform(
                post("/login").contentType("application/json")
                    .header("X-Forwarded-For", "198.51.100.77")
                    .content("""{"email":"missing@example.com","password":"sensitive-password"}"""),
            ).andExpect(status().isUnauthorized)
            val newCookie = userHelper.login(email = admin.email, mockMvc = mockMvc)
            val role = mockMvc.perform(
                post("/roles/").cookie(newCookie).contentType("application/json")
                    .content("""{"name":"Event role","description":"secret-description","policies":[]}"""),
            )
                .andExpect(status().isOk).andReturn()
            val roleId = mapper.readTree(role.response.contentAsString)["id"].asText()
            mockMvc.perform(delete("/roles/$roleId").cookie(newCookie)).andExpect(status().isOk)
            val viewer = userHelper.createUser(permissions = listOf("configuration:get"))
            DriverManager.getConnection(database.jdbcUrl, database.username, database.password).use { connection ->
                connection.createStatement().use {
                    it.execute("CREATE TABLE event_stream_sensitive (value TEXT)")
                    it.execute("INSERT INTO event_stream_sensitive VALUES ('seeded-secret-one'), ('seeded-secret-two')")
                }
            }
            val request = requests.createApprovedRequest(
                database,
                admin,
                viewer,
                "SELECT value FROM event_stream_sensitive",
            )
            val reason = "Investigate ticket SEC-42\n\"Customer access\" " + "한".repeat(8000)
            val createdResponse = mockMvc.perform(
                post("/execution-requests/").cookie(newCookie).contentType("application/json")
                    .content(
                        mapper.writeValueAsString(
                            mapOf(
                                "connectionType" to "DATASOURCE",
                                "connectionId" to request.request.connection.getId(),
                                "title" to "Reason capture",
                                "type" to "SingleExecution",
                                "description" to reason,
                                "statement" to "SELECT 1;",
                            ),
                        ),
                    ),
            ).andExpect(status().isOk).andReturn()
            val createdId = mapper.readTree(createdResponse.response.contentAsString)["id"].asText()
            val changedReason = "Investigate SEC-43 instead"
            for (description in listOf(changedReason, changedReason, "")) {
                mockMvc.perform(
                    patch("/execution-requests/$createdId").cookie(newCookie).contentType("application/json")
                        .content(
                            mapper.writeValueAsString(mapOf("description" to description, "statement" to "SELECT 1;")),
                        ),
                ).andExpect(status().isOk)
            }
            mockMvc.perform(post("/execution-requests/${request.getId()}/execute").cookie(newCookie))
                .andExpect(status().isOk)
            val viewerCookie = userHelper.login(email = viewer.email, mockMvc = mockMvc)
            mockMvc.perform(get("/config/event-streaming").cookie(viewerCookie)).andExpect(status().isOk)
            mockMvc.perform(
                put("/config/event-streaming").cookie(viewerCookie).contentType("application/json")
                    .content(mapper.writeValueAsString(settings.copy(loggingLevel = EventLoggingLevel.SECURITY_ONLY))),
            ).andExpect(status().isForbidden)
            mockMvc.perform(post("/logout").cookie(newCookie)).andExpect(status().isOk)
            val lines = Files.readAllLines(directory.resolve("events.jsonl"))
            val events = lines.map { mapper.readTree(it) }
            val actions = events.map { it["event"]["action"].asText() }
            val completed = events.first { it["event"]["action"].asText() == "execution.completed" }
            val created = events.single { it["event"]["action"].asText() == "request.created" }
            assertEquals(createdId, created["kviklet"]["request_id"].asText())
            assertEquals(reason, created["kviklet"]["request"]["reason"].asText())
            assertFalse(created["kviklet"]["request"]["reason_truncated"].asBoolean())
            val changed = events.filter { it["event"]["action"].asText() == "request.reason_changed" }
            assertEquals(listOf(changedReason, ""), changed.map { it["kviklet"]["request"]["reason"].asText() })
            assertTrue(changed.all { it["user"]["id"].asText() == admin.getId() })
            assertEquals("A test execution request", completed["kviklet"]["request"]["reason"].asText())
            assertEquals(2, completed["kviklet"]["results"][0]["rows_returned"].asInt())
            assertEquals("web", completed["kviklet"]["execution"]["channel"].asText())
            assertFalse(lines.joinToString().contains("seeded-secret"))
            assertTrue(
                actions.containsAll(
                    listOf(
                        "authentication.login",
                        "authentication.logout",
                        "role.created",
                        "role.deleted",
                        "authorization.denied",
                    ),
                ),
            )
            val failedLogin = events.first {
                it["event"]["action"].asText() == "authentication.login" &&
                    it["event"]["outcome"].asText() == "failure"
            }
            assertEquals("127.0.0.1", failedLogin["source"]["ip"].asText())
            assertFalse(lines.joinToString().contains("sensitive-password"))
            assertFalse(lines.joinToString().contains("secret-description"))
        } finally {
            streaming.configure(settings.copy(enabled = false))
            licenses.deleteAll()
            requestAdapter.deleteAll()
            connections.deleteAll()
            userHelper.deleteAll()
            roleHelper.deleteAll()
        }
    }

    @Test fun `connection target changes are captured at every level without credentials or JDBC options`() {
        val admin = userHelper.createUser()
        val cookie = userHelper.login(email = admin.email, mockMvc = mockMvc)
        val settings = EventStreamingSettings(true, directory.toString(), 1, 7, 2)
        val password = "connection-password-secret"
        val options = "?password=jdbc-options-secret"
        fun records() = Files.readAllLines(directory.resolve("events.jsonl")).map { mapper.readTree(it) }
        try {
            licenses.createLicense(
                LicenseFile(
                    javaClass.getResource("/event-stream-test-license.json")!!.readText(),
                    "test",
                    LocalDateTime.now(),
                ),
            )
            for (level in EventLoggingLevel.entries) {
                streaming.configure(settings.copy(loggingLevel = level))
                val id = "target-${level.name.lowercase().replace('_', '-')}"
                val expected = mutableMapOf<String, Any?>(
                    "hostname" to "db-old.internal",
                    "port" to 5432,
                    "database_name" to null,
                    "database_type" to "POSTGRESQL",
                    "protocol" to "POSTGRESQL",
                )
                mockMvc.perform(
                    post("/connections/").cookie(cookie).contentType("application/json")
                        .content(
                            mapper.writeValueAsString(
                                mapOf(
                                    "connectionType" to "DATASOURCE",
                                    "id" to id,
                                    "displayName" to "Target audit test",
                                    "username" to "connection-username-secret",
                                    "password" to password,
                                    "reviewConfig" to mapOf("numTotalRequired" to 1),
                                    "type" to "POSTGRESQL",
                                    "protocol" to "POSTGRESQL",
                                    "hostname" to expected["hostname"],
                                    "port" to expected["port"],
                                    "additionalJDBCOptions" to options,
                                ),
                            ),
                        ),
                ).andExpect(status().isOk)
                val created = records().single {
                    it["event"]["action"].asText() == "connection.created" &&
                        it["kviklet"]["connection_id"].asText() == id
                }
                assertEquals(admin.getId(), created["user"]["id"].asText())
                for ((field, value) in expected) {
                    assertEquals(mapper.valueToTree<JsonNode>(value), created["kviklet"]["security"][field])
                }
                val changes = listOf(
                    Triple("hostname", "hostname", "db-new.internal"),
                    Triple("port", "port", 3306),
                    Triple("databaseName", "database_name", "payments"),
                    Triple("type", "database_type", "MYSQL"),
                    Triple("protocol", "protocol", "MYSQL"),
                )
                for ((apiField, eventField, value) in changes) {
                    val start = records().size
                    val update = mapper.writeValueAsString(
                        mapOf("connectionType" to "DATASOURCE", apiField to value),
                    )
                    mockMvc.perform(
                        patch("/connections/$id").cookie(cookie).contentType("application/json").content(update),
                    ).andExpect(status().isOk)
                    val changed = records().drop(start).single {
                        it["event"]["action"].asText() == "connection.security_changed"
                    }
                    assertEquals(id, changed["kviklet"]["connection_id"].asText())
                    assertEquals(admin.getId(), changed["user"]["id"].asText())
                    assertFalse(changed["kviklet"]["credentials_changed"].asBoolean())
                    for ((field, oldValue) in expected) {
                        assertEquals(mapper.valueToTree<JsonNode>(oldValue), changed["kviklet"]["before"][field])
                    }
                    expected[eventField] = value
                    for ((field, newValue) in expected) {
                        assertEquals(mapper.valueToTree<JsonNode>(newValue), changed["kviklet"]["after"][field])
                    }
                    val afterChange = records().size
                    mockMvc.perform(
                        patch("/connections/$id").cookie(cookie).contentType("application/json").content(update),
                    ).andExpect(status().isOk)
                    assertEquals(afterChange, records().size)
                }
                mockMvc.perform(delete("/connections/$id").cookie(cookie)).andExpect(status().isNoContent)
                assertTrue(
                    records().any {
                        it["event"]["action"].asText() == "connection.deleted" &&
                            it["kviklet"]["connection_id"].asText() == id
                    },
                )
            }
            val output = Files.readString(directory.resolve("events.jsonl"))
            for (secret in listOf(password, "connection-username-secret", "jdbc-options-secret")) {
                assertFalse(output.contains(secret))
            }
        } finally {
            streaming.configure(settings.copy(enabled = false))
            licenses.deleteAll()
            connections.deleteAll()
            userHelper.deleteAll()
            roleHelper.deleteAll()
        }
    }

    @Test fun `logging levels persist through the API and filter real query execution without changing its results`() {
        val admin = userHelper.createUser()
        val viewer = userHelper.createUser(permissions = listOf("configuration:get"))
        val cookie = userHelper.login(email = admin.email, mockMvc = mockMvc)
        val settings = EventStreamingSettings(true, directory.toString(), 1, 7, 2)
        try {
            licenses.createLicense(
                LicenseFile(
                    javaClass.getResource("/event-stream-test-license.json")!!.readText(),
                    "test",
                    LocalDateTime.now(),
                ),
            )
            val invalid = mapper.valueToTree<ObjectNode>(settings)
            for (value in listOf("DEBUG", 0)) {
                invalid.set<JsonNode>("loggingLevel", mapper.valueToTree<JsonNode>(value))
                mockMvc.perform(
                    put("/config/event-streaming").cookie(cookie).contentType("application/json")
                        .content(mapper.writeValueAsString(invalid)),
                ).andExpect(status().isBadRequest)
            }
            for (level in EventLoggingLevel.entries) {
                val start = if (Files.exists(directory.resolve("events.jsonl"))) {
                    Files.readAllLines(directory.resolve("events.jsonl")).size
                } else {
                    0
                }
                mockMvc.perform(
                    put("/config/event-streaming").cookie(cookie).contentType("application/json")
                        .content(mapper.writeValueAsString(settings.copy(loggingLevel = level))),
                ).andExpect(status().isOk)
                val configured = mockMvc.perform(get("/config/event-streaming").cookie(cookie))
                    .andExpect(status().isOk).andReturn()
                assertEquals(
                    level.name,
                    mapper.readTree(configured.response.contentAsString)["settings"]["loggingLevel"].asText(),
                )
                val request = requests.createApprovedRequest(database, admin, viewer, "SELECT 7")
                mockMvc.perform(post("/execution-requests/${request.getId()}/execute").cookie(cookie))
                    .andExpect(status().isOk)
                val role = mockMvc.perform(
                    post("/roles/").cookie(cookie).contentType("application/json")
                        .content("""{"name":"Level $level","description":"private-role-description","policies":[]}"""),
                ).andExpect(status().isOk).andReturn()
                val roleId = mapper.readTree(role.response.contentAsString)["id"].asText()
                mockMvc.perform(delete("/roles/$roleId").cookie(cookie)).andExpect(status().isOk)
                val records = Files.readAllLines(directory.resolve("events.jsonl")).drop(start).map {
                    mapper.readTree(it)
                }
                assertTrue(records.any { it["event"]["action"].asText() == "role.created" })
                assertTrue(records.any { it["event"]["action"].asText() == "role.deleted" })
                val executions = records.filter { it["event"]["action"].asText().startsWith("execution.") }
                if (level == EventLoggingLevel.SECURITY_ONLY) {
                    assertTrue(executions.isEmpty())
                } else {
                    assertEquals(2, executions.size)
                    val completed = executions.first { it["event"]["action"].asText() == "execution.completed" }
                    assertEquals(1, completed["kviklet"]["results"][0]["rows_returned"].asInt())
                    assertEquals("A test execution request", completed["kviklet"]["request"]["reason"].asText())
                    for (execution in executions) {
                        assertEquals(
                            level == EventLoggingLevel.FULL,
                            execution["kviklet"]["execution"].has("statement"),
                        )
                    }
                }
                assertFalse(records.joinToString().contains("private-role-description"))
            }
        } finally {
            streaming.configure(settings.copy(enabled = false))
            licenses.deleteAll()
            requestAdapter.deleteAll()
            connections.deleteAll()
            userHelper.deleteAll()
            roleHelper.deleteAll()
        }
    }
}
