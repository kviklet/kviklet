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
    properties = ["server.port=8084"],
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
            registry.add("kviklet.identity-provider.github.orgs-uri") { "$base/user/orgs" }
            registry.add("kviklet.identity-provider.github.allowed-orgs") { "kviklet" }
        }
    }

    @AfterEach
    fun tearDown() {
        userAdapter.deleteAll()
    }

    private fun setDispatcher(
        userJson: String,
        emailsJson: String? = null,
        orgsJson: String = """[{"login":"kviklet"}]""",
    ) {
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
                                    """"scope":"read:user,user:email,read:org"}""",
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

                    path.startsWith("/user/orgs") -> {
                        MockResponse()
                            .setResponseCode(200)
                            .addHeader("Content-Type", "application/json")
                            .setBody(orgsJson)
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
                // Success path redirects to localhost:5173 (frontend) and fails with a connect
                // exception; rejection path redirects to /login?error which 404s on the backend.
                // Suppress the latter so both flows are observable via DB state.
                isThrowExceptionOnFailingStatusCode = false
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
    fun `primary verified email is used and user-profile email is ignored`() {
        setDispatcher(
            userJson = """{"id":12345,"login":"octocat","name":"Octo Cat","email":"public@example.com"}""",
            emailsJson = """
                [
                    {"email":"public@example.com","primary":false,"verified":true},
                    {"email":"primary@example.com","primary":true,"verified":true}
                ]
            """.trimIndent(),
        )
        val before = userAdapter.listUsers().size

        runOauthFlow()

        assertThat(userAdapter.listUsers().size).isEqualTo(before + 1)
        val user = userAdapter.findByEmail("primary@example.com")
        assertThat(user).isNotNull
        assertThat(user!!.githubId).isEqualTo("12345")
        assertThat(user.fullName).isEqualTo("Octo Cat")
        assertThat(user.password).isNull()
        assertThat(userAdapter.findByEmail("public@example.com")).isNull()
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
            emailsJson = """[{"email":"octo@example.com","primary":true,"verified":true}]""",
        )
        runOauthFlow()

        assertThat(userAdapter.listUsers().size).isEqualTo(before)
        val user = userAdapter.findByEmail("octo@example.com")
        assertThat(user).isNotNull
        assertThat(user!!.githubId).isEqualTo("12345")
        assertThat(user.password).isNull()
    }

    @Test
    fun `unverified primary email is ignored even if it matches an existing user`() {
        userAdapter.createUser(
            User(
                email = "victim@example.com",
                fullName = "Real User",
                password = "some-bcrypt-hash",
            ),
        )
        val before = userAdapter.listUsers().size

        setDispatcher(
            userJson = """{"id":42,"login":"attacker","name":"Attacker","email":"victim@example.com"}""",
            emailsJson = """[{"email":"victim@example.com","primary":true,"verified":false}]""",
        )
        runOauthFlow()

        // No verified email → login fails, existing user is untouched.
        assertThat(userAdapter.listUsers().size).isEqualTo(before)
        val victim = userAdapter.findByEmail("victim@example.com")
        assertThat(victim).isNotNull
        assertThat(victim!!.githubId).isNull()
        assertThat(victim.password).isEqualTo("some-bcrypt-hash")
    }

    @Test
    fun `login is rejected when user is not in an allowed org`() {
        setDispatcher(
            userJson = """{"id":99999,"login":"outsider","name":"Outsider","email":"out@example.com"}""",
            orgsJson = """[{"login":"some-other-org"}]""",
        )
        val before = userAdapter.listUsers().size

        runOauthFlow()

        assertThat(userAdapter.listUsers().size).isEqualTo(before)
        assertThat(userAdapter.findByEmail("out@example.com")).isNull()
    }

    @Test
    fun `org name comparison is case-insensitive`() {
        setDispatcher(
            userJson = """{"id":11111,"login":"mixedcase","name":"Mixed Case","email":"mixed@example.com"}""",
            emailsJson = """[{"email":"mixed@example.com","primary":true,"verified":true}]""",
            orgsJson = """[{"login":"KVIKLET"}]""",
        )
        val before = userAdapter.listUsers().size

        runOauthFlow()

        assertThat(userAdapter.listUsers().size).isEqualTo(before + 1)
        assertThat(userAdapter.findByEmail("mixed@example.com")).isNotNull
    }
}
