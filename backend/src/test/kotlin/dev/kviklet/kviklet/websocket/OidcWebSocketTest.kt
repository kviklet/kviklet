package dev.kviklet.kviklet.websocket

import com.gargoylesoftware.htmlunit.WebClient
import com.gargoylesoftware.htmlunit.html.HtmlInput
import com.gargoylesoftware.htmlunit.html.HtmlPage
import dev.kviklet.kviklet.db.ConnectionAdapter
import dev.kviklet.kviklet.db.ExecutionRequestAdapter
import dev.kviklet.kviklet.db.GroupReviewConfig
import dev.kviklet.kviklet.db.ReviewConfig
import dev.kviklet.kviklet.db.UserAdapter
import dev.kviklet.kviklet.db.UserId
import dev.kviklet.kviklet.helper.RoleHelper
import dev.kviklet.kviklet.service.UserService
import dev.kviklet.kviklet.service.dto.AuthenticationType
import dev.kviklet.kviklet.service.dto.ConnectionId
import dev.kviklet.kviklet.service.dto.DatabaseProtocol
import dev.kviklet.kviklet.service.dto.DatasourceType
import dev.kviklet.kviklet.service.dto.ExecutionStatus
import dev.kviklet.kviklet.service.dto.RequestType
import dev.kviklet.kviklet.service.dto.ReviewStatus
import dev.kviklet.kviklet.service.dto.Role
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketHttpHeaders
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.client.standard.StandardWebSocketClient
import org.springframework.web.socket.handler.TextWebSocketHandler
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.net.URI
import java.net.URLEncoder
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * Test to reproduce GitHub Issue #405:
 * WebSocket connection fails for OIDC-authenticated users with ClassCastException:
 * "class dev.kviklet.kviklet.security.CustomOidcUser cannot be cast to
 * class dev.kviklet.kviklet.security.UserDetailsWithId"
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
    properties = ["server.port=8081"],
)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class OidcWebSocketTest {

    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var userAdapter: UserAdapter

    @Autowired
    private lateinit var connectionAdapter: ConnectionAdapter

    @Autowired
    private lateinit var executionRequestAdapter: ExecutionRequestAdapter

    @Autowired
    private lateinit var roleHelper: RoleHelper

    @Autowired
    private lateinit var userService: UserService

    companion object {
        @Container
        val dex: GenericContainer<*> = GenericContainer("ghcr.io/dexidp/dex:v2.37.0")
            .withExposedPorts(5556)
            .withClasspathResourceMapping(
                "dex/config.yaml",
                "/etc/dex/config.yaml",
                BindMode.READ_ONLY,
            )
            .withEnv("DEX_LOGGING_LEVEL", "debug")
            .waitingFor(Wait.forHttp("/dex/healthz").forStatusCode(200))
            .withCommand("dex serve /etc/dex/config.yaml")

        @Container
        val db: PostgreSQLContainer<*> = PostgreSQLContainer(DockerImageName.parse("postgres:11.1"))
            .withUsername("root")
            .withPassword("root")
            .withDatabaseName("test_db")

        @JvmStatic
        @DynamicPropertySource
        fun configureProperties(registry: DynamicPropertyRegistry) {
            val dexUrl = "http://${dex.host}:${dex.getMappedPort(5556)}/dex"

            registry.add("kviklet.identity-provider.authorization-uri") { "$dexUrl/auth" }
            registry.add("kviklet.identity-provider.token-uri") { "$dexUrl/token" }
            registry.add("kviklet.identity-provider.jwk-set-uri") { "$dexUrl/keys" }
            registry.add("kviklet.identity-provider.user-info-uri") { "$dexUrl/userinfo" }
            registry.add("kviklet.identity-provider.client-id") { "example-app" }
            registry.add("kviklet.identity-provider.client-secret") { "example-app-secret" }
            registry.add("kviklet.identity-provider.type") { "dex" }
        }
    }

    @AfterEach
    fun tearDown() {
        executionRequestAdapter.deleteAll()
        connectionAdapter.deleteAll()
        userAdapter.deleteAll()
    }

    @Test
    fun `WebSocket connection should work for OIDC authenticated users`() {
        // Step 1: Perform OIDC login and capture session cookie
        val webClient = WebClient().apply {
            options.apply {
                isRedirectEnabled = true
                isJavaScriptEnabled = false
                isThrowExceptionOnScriptError = false
                isUseInsecureSSL = true
                isCssEnabled = false
            }
        }

        var sessionCookie: String? = null

        try {
            val loginUrl = "http://localhost:$port/oauth2/authorization/dex"
            val dexLoginPage = webClient.getPage<HtmlPage>(loginUrl)

            dexLoginPage.getElementByName<HtmlInput>("login").type("admin@example.com")
            dexLoginPage.getElementByName<HtmlInput>("password").type("password")

            val appPage = dexLoginPage.getElementById("submit-login").click<HtmlPage>()

            try {
                appPage.getElementsByTagName("button").get(0).click<HtmlPage>()
            } catch (e: Exception) {
                // Expected - frontend not running
            }

            // Extract the session cookie
            sessionCookie = webClient.cookieManager.cookies
                .find { it.name == "SESSION" }?.value
        } finally {
            webClient.close()
        }

        assert(sessionCookie != null) { "Session cookie should be set after OIDC login" }

        // Step 2: Get the OIDC user and give them permissions
        val oidcUser = userAdapter.findByEmail("admin@example.com")!!
        val role = roleHelper.createRole(permissions = listOf("*"))
        userService.updateUserWithRoles(
            userId = UserId(oidcUser.getId()!!),
            roles = listOf(role.getId()!!, Role.DEFAULT_ROLE_ID.toString()),
        )

        // Create a connection for the execution request
        val connection = connectionAdapter.createDatasourceConnection(
            ConnectionId("test-conn"),
            "Test Connection",
            AuthenticationType.USER_PASSWORD,
            db.databaseName,
            1,
            db.username,
            db.password,
            "Test connection",
            ReviewConfig(groupConfigs = listOf(GroupReviewConfig("*", 0))),
            db.getMappedPort(5432),
            db.host,
            DatasourceType.POSTGRESQL,
            DatabaseProtocol.POSTGRESQL,
            additionalJDBCOptions = "",
            dumpsEnabled = false,
            temporaryAccessEnabled = true,
            explainEnabled = false,
        )

        // Create an execution request
        val executionRequest = executionRequestAdapter.createExecutionRequest(
            connectionId = connection.id,
            title = "Test Request",
            type = RequestType.TemporaryAccess,
            description = "Test",
            statement = "SELECT 1",
            executionStatus = ExecutionStatus.EXECUTABLE,
            reviewStatus = ReviewStatus.APPROVED,
            authorId = oidcUser.getId()!!,
        )

        // Step 3: Try to connect to WebSocket with OIDC session
        val webSocketClient = StandardWebSocketClient()
        val messages = CompletableFuture<String>()
        val connectionError = CompletableFuture<Throwable>()

        val handler = object : TextWebSocketHandler() {
            override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
                messages.complete(message.payload)
            }

            override fun handleTransportError(session: WebSocketSession, exception: Throwable) {
                connectionError.complete(exception)
            }
        }

        val encodedRequestId = URLEncoder.encode(executionRequest.getId(), "UTF-8")
        val url = "ws://localhost:$port/sql/$encodedRequestId"
        val headers = WebSocketHttpHeaders()
        headers.add("Cookie", "SESSION=$sessionCookie")

        val session = webSocketClient.execute(handler, headers, URI(url)).get(10, TimeUnit.SECONDS)

        // If we get here, the connection succeeded - wait for a message
        val message = messages.get(5, TimeUnit.SECONDS)
        assert(message.contains("sessionId")) { "Should receive a status message" }

        session.close()
    }
}
