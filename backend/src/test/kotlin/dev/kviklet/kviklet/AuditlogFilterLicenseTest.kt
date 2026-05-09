package dev.kviklet.kviklet

import dev.kviklet.kviklet.db.LicenseAdapter
import dev.kviklet.kviklet.db.User
import dev.kviklet.kviklet.helper.ConnectionHelper
import dev.kviklet.kviklet.helper.ExecutionRequestHelper
import dev.kviklet.kviklet.helper.RoleHelper
import dev.kviklet.kviklet.helper.UserHelper
import dev.kviklet.kviklet.service.dto.Connection
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.io.FileUrlResource
import org.springframework.jdbc.datasource.init.ScriptUtils
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.time.Instant

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuditlogFilterLicenseTest {

    @Autowired private lateinit var mockMvc: MockMvc

    @Autowired private lateinit var userHelper: UserHelper

    @Autowired private lateinit var roleHelper: RoleHelper

    @Autowired private lateinit var connectionHelper: ConnectionHelper

    @Autowired private lateinit var executionRequestHelper: ExecutionRequestHelper

    @Autowired private lateinit var licenseAdapter: LicenseAdapter

    private lateinit var testUser: User
    private lateinit var testConnection: Connection

    companion object {
        val db: PostgreSQLContainer<*> = PostgreSQLContainer(DockerImageName.parse("postgres:11.1"))
            .withUsername("root")
            .withPassword("root")
            .withReuse(true)
            .withDatabaseName("test_db")

        init {
            db.start()
        }
    }

    @BeforeEach
    fun setup() {
        val initScript = this::class.java.classLoader.getResource("psql_init.sql")!!
        ScriptUtils.executeSqlScript(db.createConnection(""), FileUrlResource(initScript))

        // Deliberately do NOT set up a license — these tests verify unlicensed behaviour.
        testUser = userHelper.createUser(permissions = listOf("*"))
        testConnection = connectionHelper.createPostgresConnection(db)
    }

    @AfterEach
    fun tearDown() {
        executionRequestHelper.deleteAll()
        connectionHelper.deleteAll()
        userHelper.deleteAll()
        roleHelper.deleteAll()
        licenseAdapter.deleteAll()
    }

    @Test
    fun `GET executions with from param without license returns 402`() {
        val userCookie = userHelper.login(email = testUser.email, mockMvc = mockMvc)
        val fromTimestamp = Instant.now().minusSeconds(3600).toString()

        mockMvc.perform(
            get("/executions/")
                .param("from", fromTimestamp)
                .cookie(userCookie),
        ).andExpect(status().isPaymentRequired)
    }

    @Test
    fun `GET executions with to param without license returns 402`() {
        val userCookie = userHelper.login(email = testUser.email, mockMvc = mockMvc)
        val toTimestamp = Instant.now().toString()

        mockMvc.perform(
            get("/executions/")
                .param("to", toTimestamp)
                .cookie(userCookie),
        ).andExpect(status().isPaymentRequired)
    }

    @Test
    fun `GET executions with no filter params without license returns 200`() {
        val userCookie = userHelper.login(email = testUser.email, mockMvc = mockMvc)

        mockMvc.perform(
            get("/executions/").cookie(userCookie),
        ).andExpect(status().isOk)
    }
}
