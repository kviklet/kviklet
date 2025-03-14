package dev.kviklet.kviklet

import com.gargoylesoftware.htmlunit.WebClient
import com.gargoylesoftware.htmlunit.html.HtmlInput
import com.gargoylesoftware.htmlunit.html.HtmlPage
import dev.kviklet.kviklet.db.User
import dev.kviklet.kviklet.db.UserAdapter
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
    properties = ["server.port=8081"],
)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class OIDCTest {

    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var userAdapter: UserAdapter

    companion object {

        @Container
        val dex = GenericContainer("ghcr.io/dexidp/dex:v2.37.0")
            .withExposedPorts(5556)
            .withClasspathResourceMapping(
                "dex/config.yaml",
                "/etc/dex/config.yaml",
                BindMode.READ_ONLY,
            )
            .withEnv("DEX_LOGGING_LEVEL", "debug")
            .waitingFor(Wait.forHttp("/dex/healthz").forStatusCode(200))
            .withLogConsumer { output -> println("DEX LOG: ${output.utf8String}") }
            .withCommand("dex serve /etc/dex/config.yaml")

        @JvmStatic
        @DynamicPropertySource
        fun configureOAuth2Properties(registry: DynamicPropertyRegistry) {
            val dexUrl = "http://${dex.host}:${dex.getMappedPort(5556)}/dex"

            // Set authorizationUri, tokenUri, jwkSetUri, and userInfoUri
            registry.add("kviklet.identity-provider.authorization-uri") { "$dexUrl/auth" }
            registry.add("kviklet.identity-provider.token-uri") { "$dexUrl/token" }
            registry.add("kviklet.identity-provider.jwk-set-uri") { "$dexUrl/keys" }
            registry.add("kviklet.identity-provider.user-info-uri") { "$dexUrl/userinfo" }
            registry.add("kviklet.identity-provider.client-id") { "example-app" }
            registry.add("kviklet.identity-provider.client-secret") { "example-app-secret" }
            registry.add("kviklet.identity-provider.type") { "dex" }
        }
    }

    @Autowired
    private lateinit var mockMvc: MockMvc

    @AfterEach
    fun tearDown() {
        userAdapter.deleteAll()
    }

    @Test
    fun `test oidc login`() {
        mockMvc.perform(
            get("/oauth2/authorization/dex"),
        ).andExpect(status().is3xxRedirection)
    }

    @Test
    fun `test complete OIDC flow with WebClient`() {
        // Create and configure WebClient
        val webClient = WebClient().apply {
            options.apply {
                isRedirectEnabled = true
                isJavaScriptEnabled = false
                isThrowExceptionOnScriptError = false
                isUseInsecureSSL = true
                isCssEnabled = false
            }
        }
        val userCountBeforeOIDC = userAdapter.listUsers().size

        try {
            // Start the login flow by accessing the OAuth2 authorization endpoint
            val loginUrl = "http://localhost:$port/oauth2/authorization/dex"

            // Get the login page
            val dexLoginPage = webClient.getPage<HtmlPage>(loginUrl)

            // Fill in login credentials
            dexLoginPage.getElementByName<HtmlInput>("login").type("admin@example.com")
            dexLoginPage.getElementByName<HtmlInput>("password").type("password")

            // Submit the login form
            val appPage = dexLoginPage.getElementById("submit-login").click<HtmlPage>()
            // Press the Grant access button
            val returnPage = appPage.getElementsByTagName("button").get(0).click<HtmlPage>()

            // Assert it redirect to the app on successful login
            assertThat(returnPage.url.toString()).isEqualTo("http://localhost:5173/requests")
        } finally {
            // Always close the WebClient to release resources
            webClient.close()
        }

        // Assert that a new user was created
        assertThat(userAdapter.listUsers().size).isEqualTo(userCountBeforeOIDC + 1)
    }

    @Test
    fun `assert that if user already exists login method is swapped and no new user is created`() {
        // Create a user with the same subject that the OIDC flow will return
        val existingUser = userAdapter.createUser(
            User( // This should match what Dex returns
                email = "admin@example.com",
                fullName = "Admin User",
                // This should actually be a hashed one but we dont care for this test
                // we just want to validate its emptied after OIDC login
                password = "password",
            ),
        )

        val userCountBeforeOIDC = userAdapter.listUsers().size

        // Create and configure WebClient
        val webClient = WebClient().apply {
            options.apply {
                isRedirectEnabled = true
                isJavaScriptEnabled = false
                isThrowExceptionOnScriptError = false
                isUseInsecureSSL = true
                isCssEnabled = false
            }
        }

        try {
            // Start the login flow by accessing the OAuth2 authorization endpoint
            val loginUrl = "http://localhost:$port/oauth2/authorization/dex"

            // Get the login page
            val dexLoginPage = webClient.getPage<HtmlPage>(loginUrl)

            // Fill in login credentials
            dexLoginPage.getElementByName<HtmlInput>("login").type("admin@example.com")
            dexLoginPage.getElementByName<HtmlInput>("password").type("password")

            // Submit the login form
            val appPage = dexLoginPage.getElementById("submit-login").click<HtmlPage>()
            // Press the Grant access button
            val returnPage = appPage.getElementsByTagName("button").get(0).click<HtmlPage>()

            // Assert it redirect to the app on successful login
            assertThat(returnPage.url.toString()).isEqualTo("http://localhost:5173/requests")
        } finally {
            // Always close the WebClient to release resources
            webClient.close()
        }

        // Assert that no new user was created
        assertThat(userAdapter.listUsers().size).isEqualTo(userCountBeforeOIDC)

        // Verify that the user's information was properly updated if needed
        // Start readonly transaction
        val updatedUser = userAdapter.findByEmail("admin@example.com")
        assertThat(updatedUser?.subject).isNotNull()
        assertThat(updatedUser?.password).isNull()
    }
}
