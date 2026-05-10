package dev.kviklet.kviklet

import com.gargoylesoftware.htmlunit.WebClient
import com.gargoylesoftware.htmlunit.html.HtmlPage
import dev.kviklet.kviklet.db.User
import dev.kviklet.kviklet.db.UserAdapter
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
    properties = ["server.port=8082"],
)
@ActiveProfiles("test")
class GitHubOAuthTest {

    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var userAdapter: UserAdapter

    companion object {
        private val mockGithub = MockWebServer().apply { start() }

        @JvmStatic
        @AfterAll
        fun shutdownMock() {
            mockGithub.shutdown()
        }

        @JvmStatic
        @DynamicPropertySource
        fun configureProperties(registry: DynamicPropertyRegistry) {
            val base = mockGithub.url("/").toString().trimEnd('/')
            registry.add("kviklet.identity-provider.type") { "github" }
            registry.add("kviklet.identity-provider.client-id") { "fake-client-id" }
            registry.add("kviklet.identity-provider.client-secret") { "fake-client-secret" }
            registry.add("kviklet.identity-provider.github.authorization-uri") { "$base/login/oauth/authorize" }
            registry.add("kviklet.identity-provider.github.token-uri") { "$base/login/oauth/access_token" }
            registry.add("kviklet.identity-provider.github.user-info-uri") { "$base/user" }
            registry.add("kviklet.identity-provider.github.emails-uri") { "$base/user/emails" }
        }
    }

    @AfterEach
    fun tearDown() {
        userAdapter.deleteAll()
    }

    private fun setDispatcher(userJson: String, emailsJson: String? = null) {
        mockGithub.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path ?: ""
                return when {
                    path.startsWith("/login/oauth/authorize") -> {
                        // Extract redirect_uri and state from query params and bounce back with a code
                        val query = path.substringAfter("?", "")
                        val params = query.split("&").associate {
                            val (k, v) = it.split("=", limit = 2).let { p ->
                                p[0] to (p.getOrNull(1) ?: "")
                            }
                            k to java.net.URLDecoder.decode(v, Charsets.UTF_8)
                        }
                        val redirect = params["redirect_uri"] ?: error("missing redirect_uri")
                        val state = params["state"] ?: ""
                        val location = "$redirect?code=fake-code&state=" +
                            java.net.URLEncoder.encode(state, Charsets.UTF_8)
                        MockResponse().setResponseCode(302).addHeader("Location", location)
                    }
                    path.startsWith("/login/oauth/access_token") -> {
                        MockResponse()
                            .setResponseCode(200)
                            .addHeader("Content-Type", "application/json")
                            .setBody(
                                """{"access_token":"fake-access-token","token_type":"bearer",""" +
                                    """"scope":"read:user,user:email"}""",
                            )
                    }
                    path == "/user" -> {
                        MockResponse()
                            .setResponseCode(200)
                            .addHeader("Content-Type", "application/json")
                            .setBody(userJson)
                    }
                    path == "/user/emails" -> {
                        MockResponse()
                            .setResponseCode(200)
                            .addHeader("Content-Type", "application/json")
                            .setBody(emailsJson ?: "[]")
                    }
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
    }

    private fun runOauthFlow() {
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
            val loginUrl = "http://localhost:$port/oauth2/authorization/github"
            try {
                webClient.getPage<HtmlPage>(loginUrl)
            } catch (e: Exception) {
                // Frontend not running on 5173 — Kviklet's success handler redirects there.
                // The OAuth dance up to that point still completed successfully.
                assertThat(e.message).contains("Connect to localhost:5173")
            }
        } finally {
            webClient.close()
        }
    }

    @Test
    fun `new user is created when GitHub returns email directly`() {
        setDispatcher(
            userJson = """{"id":12345,"login":"octocat","name":"Octo Cat","email":"octo@example.com"}""",
        )
        val before = userAdapter.listUsers().size

        runOauthFlow()

        assertThat(userAdapter.listUsers().size).isEqualTo(before + 1)
        val user = userAdapter.findByEmail("octo@example.com")
        assertThat(user).isNotNull
        assertThat(user!!.githubId).isEqualTo("12345")
        assertThat(user.fullName).isEqualTo("Octo Cat")
        assertThat(user.password).isNull()
    }

    @Test
    fun `existing password user is migrated to GitHub login`() {
        userAdapter.createUser(
            User(
                email = "octo@example.com",
                fullName = "Old Name",
                password = "some-bcrypt-hash",
            ),
        )
        val before = userAdapter.listUsers().size

        setDispatcher(
            userJson = """{"id":12345,"login":"octocat","name":"Octo Cat","email":"octo@example.com"}""",
        )
        runOauthFlow()

        assertThat(userAdapter.listUsers().size).isEqualTo(before)
        val user = userAdapter.findByEmail("octo@example.com")
        assertThat(user).isNotNull
        assertThat(user!!.githubId).isEqualTo("12345")
        assertThat(user.password).isNull()
    }

    @Test
    fun `private email falls back to user emails endpoint`() {
        setDispatcher(
            userJson = """{"id":67890,"login":"private-user","name":"Private User","email":null}""",
            emailsJson = """[
                {"email":"old@example.com","primary":false,"verified":true},
                {"email":"primary@example.com","primary":true,"verified":true}
            ]""".trimIndent(),
        )
        val before = userAdapter.listUsers().size

        runOauthFlow()

        assertThat(userAdapter.listUsers().size).isEqualTo(before + 1)
        val user = userAdapter.findByEmail("primary@example.com")
        assertThat(user).isNotNull
        assertThat(user!!.githubId).isEqualTo("67890")
    }
}
