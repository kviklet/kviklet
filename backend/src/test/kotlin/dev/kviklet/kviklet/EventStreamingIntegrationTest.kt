package dev.kviklet.kviklet

import com.fasterxml.jackson.databind.ObjectMapper
import dev.kviklet.kviklet.db.ExecutionRequestAdapter
import dev.kviklet.kviklet.db.LicenseAdapter
import dev.kviklet.kviklet.helper.ConnectionHelper
import dev.kviklet.kviklet.helper.ExecutionRequestHelper
import dev.kviklet.kviklet.helper.RoleHelper
import dev.kviklet.kviklet.helper.UserHelper
import dev.kviklet.kviklet.service.EventStreamingService
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

@SpringBootTest
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
            mockMvc.perform(post("/execution-requests/${request.getId()}/execute").cookie(newCookie))
                .andExpect(status().isOk)
            val viewerCookie = userHelper.login(email = viewer.email, mockMvc = mockMvc)
            mockMvc.perform(get("/config/event-streaming").cookie(viewerCookie)).andExpect(status().isOk)
            mockMvc.perform(
                put("/config/event-streaming").cookie(viewerCookie).contentType("application/json")
                    .content(mapper.writeValueAsString(settings)),
            ).andExpect(status().isForbidden)
            mockMvc.perform(post("/logout").cookie(newCookie)).andExpect(status().isOk)
            val lines = Files.readAllLines(directory.resolve("events.jsonl"))
            val events = lines.map { mapper.readTree(it) }
            val actions = events.map { it["event"]["action"].asText() }
            val completed = events.first { it["event"]["action"].asText() == "execution.completed" }
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
}
