package dev.kviklet.kviklet

import dev.kviklet.kviklet.db.ConfigurationAdapter
import dev.kviklet.kviklet.db.LicenseAdapter
import dev.kviklet.kviklet.db.User
import dev.kviklet.kviklet.helper.ConnectionHelper
import dev.kviklet.kviklet.helper.ExecutionRequestHelper
import dev.kviklet.kviklet.helper.RoleHelper
import dev.kviklet.kviklet.helper.UserHelper
import dev.kviklet.kviklet.service.dto.Configuration
import dev.kviklet.kviklet.service.dto.LicenseFile
import dev.kviklet.kviklet.service.dto.RequestType
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.time.LocalDateTime

/**
 * The database proxy is enterprise-only and additionally gated behind the proxyEnabled
 * configuration toggle. These tests cover both gates on the proxy endpoint and the
 * license rules around flipping the toggle itself.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProxyGateTest {

    @Autowired private lateinit var mockMvc: MockMvc

    @Autowired private lateinit var userHelper: UserHelper

    @Autowired private lateinit var roleHelper: RoleHelper

    @Autowired private lateinit var connectionHelper: ConnectionHelper

    @Autowired private lateinit var executionRequestHelper: ExecutionRequestHelper

    @Autowired private lateinit var licenseAdapter: LicenseAdapter

    @Autowired private lateinit var configurationAdapter: ConfigurationAdapter

    private lateinit var adminUser: User
    private lateinit var reviewerUser: User

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
        // The test license allows only 2 users, so create both before any test installs it.
        adminUser = userHelper.createUser(permissions = listOf("*"))
        reviewerUser = userHelper.createUser(permissions = listOf("execution_request:get"))
    }

    @AfterEach
    fun tearDown() {
        executionRequestHelper.deleteAll()
        connectionHelper.deleteAll()
        userHelper.deleteAll()
        roleHelper.deleteAll()
        licenseAdapter.deleteAll()
        configurationAdapter.setConfiguration(Configuration(teamsUrl = "", slackUrl = "", proxyEnabled = false))
    }

    private fun installTestLicense() {
        // Same signed test license as in SAMLTest / ApiKeyIntegrationTest.
        val licenseJson = """
            {
                "license_data":{"max_users":2,"expiry_date":"2100-01-01","test_license":true},
                "signature":"E3cqrsVzWccsyWwIeCE2J4Mn/eHyP8j4T05Q4o2dtXH1lhum71rEyPqv9MLn//IcVGsLBY6MwWJGxxa+IBqZTvx0fkLix7e44BRJ5xnV83WzZbKyacNCsNqYEbNpeRcDmtC0pbk7/OSff8VDs5xdqWl7zsI+HA5KNdw878BZKVxusHkHhLtxOhHtbm7Gvcyia4XE86USTWUMYf6aCgNkQgRSOnTo5Zrs+vBUvgSI33l3XyBDx+cQcr9Mell2ytOYrTxQ4zUbRkzcsQtGRTHbh8uXQb5wS389F0zQWSLh7RrCRuaEZ0IDTt8tFkN+72fZ64504bsSR9mNgkgKTv/FvQiVCppKO8vpW0T0hg2xziXMnNSJ3MbihcNlpFsz9C2SEnGm18rQ4UagnLCWTqhz5DtWCxeaAExIT261o6J/wBwlsHHMJRiDaLo/cQOLVOUm43psOt4nlTdbijPoKhBejBuSgqSxTid1R7+8YaFlco/SaprzEspWHcOcVIPUN2jk"
            }
        """.trimIndent()
        licenseAdapter.createLicense(
            LicenseFile(
                fileContent = licenseJson,
                fileName = "test-license.json",
                createdAt = LocalDateTime.now(),
            ),
        )
    }

    private fun createApprovedTempAccessRequest() = executionRequestHelper.createApprovedRequest(
        dbcontainer = db,
        author = adminUser,
        approver = reviewerUser,
        requestType = RequestType.TemporaryAccess,
    )

    @Test
    fun `starting the proxy without a license returns 402`() {
        configurationAdapter.setConfiguration(Configuration(teamsUrl = null, slackUrl = null, proxyEnabled = true))
        val request = createApprovedTempAccessRequest()
        val cookie = userHelper.login(email = adminUser.email, mockMvc = mockMvc)

        mockMvc.perform(post("/execution-requests/${request.getId()}/proxy").cookie(cookie))
            .andExpect(status().isPaymentRequired)
    }

    @Test
    fun `starting the proxy with a license but the toggle off returns 400`() {
        installTestLicense()
        val request = createApprovedTempAccessRequest()
        val cookie = userHelper.login(email = adminUser.email, mockMvc = mockMvc)

        mockMvc.perform(post("/execution-requests/${request.getId()}/proxy").cookie(cookie))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message", containsString("disabled")))
    }

    @Test
    fun `starting the proxy with a license and the toggle on passes both gates`() {
        installTestLicense()
        configurationAdapter.setConfiguration(Configuration(teamsUrl = null, slackUrl = null, proxyEnabled = true))
        val request = createApprovedTempAccessRequest()
        val cookie = userHelper.login(email = adminUser.email, mockMvc = mockMvc)

        // The test profile disables the proxy listener (port -1), so a request that clears
        // both feature gates fails on the "listener is not available" check with a 500 —
        // distinct from the 402/400 the gates produce.
        mockMvc.perform(post("/execution-requests/${request.getId()}/proxy").cookie(cookie))
            .andExpect(status().isInternalServerError)
    }

    @Test
    fun `enabling the proxy without a license is rejected`() {
        val cookie = userHelper.login(email = adminUser.email, mockMvc = mockMvc)

        mockMvc.perform(
            put("/config/")
                .cookie(cookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"proxyEnabled": true}"""),
        )
            .andExpect(status().isBadRequest)

        mockMvc.perform(get("/config/").cookie(cookie))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.proxyEnabled").value(false))
    }

    @Test
    fun `enabling the proxy with a license works and every user can read the flag`() {
        installTestLicense()
        val adminCookie = userHelper.login(email = adminUser.email, mockMvc = mockMvc)

        mockMvc.perform(
            put("/config/")
                .cookie(adminCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"proxyEnabled": true}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.proxyEnabled").value(true))

        // The review page needs the flag for users without any configuration permission.
        val reviewerCookie = userHelper.login(email = reviewerUser.email, mockMvc = mockMvc)
        mockMvc.perform(get("/config/").cookie(reviewerCookie))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.proxyEnabled").value(true))
    }

    @Test
    fun `disabling the proxy does not require a license`() {
        configurationAdapter.setConfiguration(Configuration(teamsUrl = null, slackUrl = null, proxyEnabled = true))
        val cookie = userHelper.login(email = adminUser.email, mockMvc = mockMvc)

        mockMvc.perform(
            put("/config/")
                .cookie(cookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"proxyEnabled": false}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.proxyEnabled").value(false))
    }

    @Test
    fun `saving other settings leaves the proxy flag unchanged`() {
        configurationAdapter.setConfiguration(Configuration(teamsUrl = null, slackUrl = null, proxyEnabled = true))
        val cookie = userHelper.login(email = adminUser.email, mockMvc = mockMvc)

        // No license installed: a plain webhook save must neither flip the flag nor be
        // rejected by the license gate that guards enabling.
        mockMvc.perform(
            put("/config/")
                .cookie(cookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"teamsUrl": "https://teams.example.com", "slackUrl": null}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.proxyEnabled").value(true))
            .andExpect(jsonPath("$.teamsUrl").value("https://teams.example.com"))
    }
}
