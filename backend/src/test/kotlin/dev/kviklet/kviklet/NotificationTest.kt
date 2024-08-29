package dev.kviklet.kviklet

import com.ninjasquad.springmockk.MockkBean
import dev.kviklet.kviklet.db.ConnectionAdapter
import dev.kviklet.kviklet.db.ExecutionRequestAdapter
import dev.kviklet.kviklet.db.ReviewConfig
import dev.kviklet.kviklet.helper.RoleHelper
import dev.kviklet.kviklet.helper.UserHelper
import dev.kviklet.kviklet.service.ConfigService
import dev.kviklet.kviklet.service.dto.AuthenticationType
import dev.kviklet.kviklet.service.dto.Configuration
import dev.kviklet.kviklet.service.dto.ConnectionId
import dev.kviklet.kviklet.service.dto.DatabaseProtocol
import dev.kviklet.kviklet.service.dto.DatasourceType
import dev.kviklet.kviklet.service.notifications.SlackApiClient
import dev.kviklet.kviklet.service.notifications.TeamsApiClient
import io.mockk.every
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificationTest {

    @Autowired
    private lateinit var configService: ConfigService

    @MockkBean
    private lateinit var teamsApiClient: TeamsApiClient

    @MockkBean
    private lateinit var slackApiClient: SlackApiClient

    @Autowired
    private lateinit var datasourceConnectionAdapter: ConnectionAdapter

    @Autowired
    private lateinit var executionRequestAdapter: ExecutionRequestAdapter

    @Autowired
    private lateinit var userHelper: UserHelper

    @Autowired
    private lateinit var roleHelper: RoleHelper

    @Autowired
    lateinit var mockMvc: MockMvc

    @AfterEach
    fun tearDown() {
        executionRequestAdapter.deleteAll()
        userHelper.deleteAll()
        roleHelper.deleteAll()
        configService.setConfiguration(
            Configuration(
                "",
                "",
            ),
        )
    }

    @BeforeEach
    fun setupMocks() {
        every { teamsApiClient.sendMessage(any(), any(), any()) } returns Unit
        every { slackApiClient.sendMessage(any(), any()) } returns Unit
    }

    @Test
    fun `calls notification apis when urls are configured`() {
        val config = Configuration(
            teamsUrl = "https://teams.com",
            slackUrl = "https://slack.com",
        )
        configService.setConfiguration(config)
        val connection = datasourceConnectionAdapter.createDatasourceConnection(
            ConnectionId("ds-conn-test"),
            "Test Connection",
            AuthenticationType.USER_PASSWORD,
            "test",
            1,
            "username",
            "password",
            "A test connection",
            ReviewConfig(
                numTotalRequired = 1,
                fourEyesRequired = false
            ),

            3306,
            "postgres",
            DatasourceType.POSTGRESQL,
            DatabaseProtocol.POSTGRESQL,
            additionalJDBCOptions = "",
        )
        userHelper.createUser(permissions = listOf("*"))
        val cookie = userHelper.login(mockMvc = mockMvc)

        mockMvc.perform(
            MockMvcRequestBuilders.post("/execution-requests/").cookie(cookie).content(
                """
                {
                    "connectionId": "${connection.id}",
                    "title": "Test Execution",
                    "type": "SingleExecution",
                    "statement": "SELECT * FROM test",
                    "description": "A test execution request",
                    "readOnly": true,
                    "connectionType": "DATASOURCE"
                }
                """.trimIndent(),
            ).contentType("application/json"),
        ).andExpect(MockMvcResultMatchers.status().isOk)

        verify {
            teamsApiClient.sendMessage(
                webhookUrl = "https://teams.com",
                "New Request: \"Test Execution\"",
                any(),
            )
        }
        verify { slackApiClient.sendMessage(webhookUrl = "https://slack.com", any()) }
    }
}
