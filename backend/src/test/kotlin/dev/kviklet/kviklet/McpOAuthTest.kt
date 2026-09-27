package dev.kviklet.kviklet

import dev.kviklet.kviklet.db.ApiKeyRepository
import dev.kviklet.kviklet.db.LicenseAdapter
import dev.kviklet.kviklet.db.User
import dev.kviklet.kviklet.helper.RoleHelper
import dev.kviklet.kviklet.helper.UserHelper
import dev.kviklet.kviklet.security.UserDetailsWithId
import dev.kviklet.kviklet.service.dto.LicenseFile
import jakarta.servlet.http.Cookie
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.authentication.event.AuthenticationSuccessEvent
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.session.web.http.SessionRepositoryFilter
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.event.ApplicationEvents
import org.springframework.test.context.event.RecordApplicationEvents
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import org.springframework.web.util.UriComponentsBuilder
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.security.MessageDigest
import java.time.LocalDateTime
import java.util.Base64

/**
 * The OAuth flow an MCP client like Claude Code goes through, end to end: dynamic client
 * registration, login, consent, the PKCE token exchange, and finally a tool call on /mcp.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@RecordApplicationEvents
open class McpOAuthTest {

    /** The public base URL of the backend. Differs from the frontend's behind the bundled nginx. */
    protected open val backendUrl = "http://localhost"

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var context: WebApplicationContext

    @Autowired
    private lateinit var userHelper: UserHelper

    @Autowired
    private lateinit var roleHelper: RoleHelper

    @Autowired
    private lateinit var licenseAdapter: LicenseAdapter

    @Autowired
    private lateinit var apiKeyRepository: ApiKeyRepository

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var applicationEvents: ApplicationEvents

    private val redirectUri = "http://localhost:53682/callback"
    private val mcpResource get() = "$backendUrl/mcp"
    private val codeVerifier = "a-code-verifier-that-is-long-enough-for-pkce-0123456789"

    @BeforeEach
    fun setUp() {
        installLicense()
    }

    @AfterEach
    fun tearDown() {
        jdbcTemplate.update("DELETE FROM oauth2_authorization")
        jdbcTemplate.update("DELETE FROM oauth2_registered_client")
        apiKeyRepository.deleteAll()
        userHelper.deleteAll()
        roleHelper.deleteAll()
        licenseAdapter.deleteAll()
    }

    @Test
    fun `an MCP client logs in with OAuth and calls a tool as the user`() {
        val user = userHelper.createUser()
        val token = obtainAccessToken(user)

        val response = callWhoami(token).andExpect(status().isOk).andReturn().response.contentAsString
        val result = objectMapper.readTree(response)["result"]
        assertThat(result["isError"].asBoolean()).isFalse()
        val whoami = objectMapper.readTree(result["content"][0]["text"].asString())
        assertThat(whoami["email"].asString()).isEqualTo(user.email)
        assertThat(whoami["id"].asString()).isEqualTo(user.getId())
    }

    @Test
    fun `only the user's own Kviklet login counts as a login, not the clients and tokens of the flow`() {
        // Login telemetry counts every successful authentication Spring reports.
        val user = userHelper.createUser()
        val clientId = registerClient()
        val tokens = obtainTokens(user, clientId)
        val refreshed = refresh(clientId, tokens["refresh_token"].asString()).andExpect(status().isOk)
            .andReturn().response.contentAsString.let { objectMapper.readTree(it) }
        callWhoami(refreshed["access_token"].asString()).andExpect(status().isOk)

        val authentications = applicationEvents.stream(AuthenticationSuccessEvent::class.java)
            .map { it.authentication.principal }.toList()
        assertThat(authentications).hasSize(1)
        assertThat(authentications.single()).isInstanceOf(UserDetailsWithId::class.java)
    }

    @Test
    fun `a client that registers with the advertised scope, like Claude Code, can log in`() {
        val user = userHelper.createUser()
        val clientId = registerClient(scope = "mcp")

        val tokens = obtainTokens(user, clientId)

        callWhoami(tokens["access_token"].asString()).andExpect(status().isOk)
    }

    @Test
    fun `registering with any other scope is refused`() {
        mockMvc.perform(
            post("/oauth2/register").contentType(MediaType.APPLICATION_JSON)
                .content(registrationRequest(scope = "mcp admin")),
        ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("invalid_scope"))
    }

    @Test
    fun `only public clients limited to the login and refresh grants can register`() {
        // Registration is open to anyone and every client gets the mcp scope, so any other grant,
        // e.g. token exchange, would let a client mint tokens for users who never consented.
        val requests = listOf(
            registrationRequest(grantTypes = """["urn:ietf:params:oauth:grant-type:token-exchange"]"""),
            registrationRequest(grantTypes = """["authorization_code", "client_credentials"]"""),
            registrationRequest(authMethod = "client_secret_basic"),
            registrationRequest(authMethod = null),
        )
        requests.forEach { request ->
            mockMvc.perform(post("/oauth2/register").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error").value("invalid_client_metadata"))
        }
    }

    @Test
    fun `tokens are audience restricted to the MCP endpoint`() {
        val user = userHelper.createUser()
        val token = obtainAccessToken(user, resource = "http://localhost/some-other-api")

        callWhoami(token).andExpect(status().isUnauthorized)
    }

    @Test
    fun `a client that names no resource still gets a token for the MCP endpoint`() {
        val user = userHelper.createUser()
        val token = obtainAccessToken(user, resource = null)

        callWhoami(token).andExpect(status().isOk)
    }

    @Test
    fun `an unauthenticated MCP call points the client to the protected resource metadata`() {
        mockMvc.perform(post("/mcp").contentType(MediaType.APPLICATION_JSON).content(whoamiCall()))
            .andExpect(status().isUnauthorized)
            .andExpect(
                header().string(
                    HttpHeaders.WWW_AUTHENTICATE,
                    containsString("resource_metadata=$backendUrl/.well-known/oauth-protected-resource/mcp"),
                ),
            )

        mockMvc.perform(get("/.well-known/oauth-protected-resource/mcp"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resource").value(mcpResource))
            .andExpect(jsonPath("$.authorization_servers[0]").value(backendUrl))
            .andExpect(jsonPath("$.scopes_supported[0]").value("mcp"))
            .andExpect(jsonPath("$.tls_client_certificate_bound_access_tokens").value(false))

        mockMvc.perform(get("/.well-known/oauth-authorization-server"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.issuer").value(backendUrl))
            .andExpect(jsonPath("$.authorization_endpoint").value("$backendUrl/oauth2/authorize"))
            .andExpect(jsonPath("$.registration_endpoint").value("$backendUrl/oauth2/register"))
    }

    @Test
    fun `api keys are not accepted on the MCP endpoint`() {
        val user = userHelper.createUser()
        val cookie = userHelper.login(mockMvc = mockMvc, email = user.email)
        val apiKey = objectMapper.readTree(
            mockMvc.perform(
                post("/api-keys/").cookie(cookie).contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name": "key", "expiresInDays": 30}"""),
            ).andExpect(status().isCreated).andReturn().response.contentAsString,
        )["key"].asString()

        callWhoami(apiKey).andExpect(status().isUnauthorized)
    }

    @Test
    fun `a browser session is not accepted on the MCP endpoint`() {
        val user = userHelper.createUser()
        val cookie = userHelper.login(mockMvc = mockMvc, email = user.email)

        mockMvc.perform(
            post("/mcp").cookie(cookie).contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.ACCEPT, "application/json, text/event-stream")
                .content(whoamiCall()),
        ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `tokens of a deactivated user stop working`() {
        val user = userHelper.createUser()
        val token = obtainAccessToken(user)
        val admin = userHelper.createUser(email = "admin@example.com")
        val adminCookie = userHelper.login(mockMvc = mockMvc, email = admin.email)

        mockMvc.perform(
            put("/users/${user.getId()}/status").cookie(adminCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"active": false}"""),
        ).andExpect(status().isOk)

        callWhoami(token).andExpect(status().isUnauthorized)
    }

    @Test
    fun `everything MCP answers 402 without a license`() {
        val user = userHelper.createUser()
        val token = obtainAccessToken(user)
        licenseAdapter.deleteAll()

        callWhoami(token).andExpect(status().isPaymentRequired)
        mockMvc.perform(get("/.well-known/oauth-protected-resource/mcp")).andExpect(status().isPaymentRequired)
        mockMvc.perform(get("/.well-known/oauth-authorization-server")).andExpect(status().isPaymentRequired)
        mockMvc.perform(
            post("/oauth2/register").contentType(MediaType.APPLICATION_JSON).content(registrationRequest()),
        ).andExpect(status().isPaymentRequired)
        mockMvc.perform(get("/oauth2/consent/details").param("state", "any").cookie(login(user)))
            .andExpect(status().isPaymentRequired)
    }

    @Test
    fun `an authorization without a Kviklet session sends the browser to the frontend login`() {
        val clientId = registerClient()

        val location = mockMvc.perform(authorizationRequest(clientId, mcpResource))
            .andExpect(status().isFound)
            .andReturn().response.getHeader(HttpHeaders.LOCATION)!!

        assertThat(location).startsWith("http://localhost/oauth/authorize?")
        assertThat(location).contains("client_id=$clientId")
    }

    @Test
    fun `consent is asked for every time, even for a client the user approved before`() {
        val user = userHelper.createUser()
        obtainAccessToken(user)
        val clientId = registerClient()
        val cookie = login(user)

        repeat(2) {
            val location = mockMvc.perform(authorizationRequest(clientId, mcpResource).cookie(cookie))
                .andExpect(status().isFound)
                .andReturn().response.getHeader(HttpHeaders.LOCATION)!!
            assertThat(location).startsWith("$backendUrl/oauth2/consent?")
            submitConsent(cookie, clientId, queryParam(location, "state"), approve = true)
        }
    }

    @Test
    fun `the consent page forwards to the frontend and can look up what the client asked for`() {
        val user = userHelper.createUser()
        val clientId = registerClient()
        val cookie = login(user)
        val consentLocation = startAuthorization(cookie, clientId, mcpResource)

        val frontendLocation = mockMvc.perform(get(consentLocation.removePrefix(backendUrl)).cookie(cookie))
            .andExpect(status().isFound)
            .andReturn().response.getHeader(HttpHeaders.LOCATION)!!
        assertThat(frontendLocation).startsWith("http://localhost/oauth/consent?")

        val state = queryParam(consentLocation, "state")
        mockMvc.perform(get("/oauth2/consent/details").param("state", state).cookie(cookie))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.clientId").value(clientId))
            .andExpect(jsonPath("$.clientName").value("Claude Code"))
            .andExpect(jsonPath("$.redirectUri").value(redirectUri))
            .andExpect(jsonPath("$.scopes[0]").value("mcp"))

        val otherUser = userHelper.createUser(email = "other@example.com")
        mockMvc.perform(get("/oauth2/consent/details").param("state", state).cookie(login(otherUser)))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `denying consent sends the client an access_denied error`() {
        val user = userHelper.createUser()
        val clientId = registerClient()
        val cookie = login(user)
        val state = queryParam(startAuthorization(cookie, clientId, mcpResource), "state")

        val clientRedirect = submitConsent(cookie, clientId, state, approve = false)

        assertThat(clientRedirect).startsWith(redirectUri)
        assertThat(queryParam(clientRedirect, "error")).isEqualTo("access_denied")
    }

    @Test
    fun `denying consent works for a client the user approved before`() {
        val user = userHelper.createUser()
        val clientId = registerClient()
        obtainTokens(user, clientId)
        val cookie = login(user)
        val state = queryParam(startAuthorization(cookie, clientId, mcpResource), "state")

        val clientRedirect = submitConsent(cookie, clientId, state, approve = false)

        assertThat(queryParam(clientRedirect, "error")).isEqualTo("access_denied")
        assertThat(clientRedirect).doesNotContain("code=")
    }

    @Test
    fun `consent can only be submitted with the Kviklet request header`() {
        // Without the header a form on another site could approve a client in the user's name.
        val mockMvcWithoutHeader = MockMvcBuilders.webAppContextSetup(context)
            .addFilters<DefaultMockMvcBuilder>(context.getBean(SessionRepositoryFilter::class.java))
            .apply<DefaultMockMvcBuilder>(springSecurity())
            .build()
        val user = userHelper.createUser()
        val clientId = registerClient()
        val cookie = login(user)
        val state = queryParam(startAuthorization(cookie, clientId, mcpResource), "state")

        mockMvcWithoutHeader.perform(
            post("/oauth2/authorize").cookie(cookie)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("client_id", clientId)
                .param("state", state)
                .param("scope", "mcp"),
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `a client refreshes its token without the user, and every refresh token works once`() {
        val user = userHelper.createUser()
        val clientId = registerClient()
        val tokens = obtainTokens(user, clientId)

        // Clients may leave out the resource on refresh; the token must still work on /mcp.
        val refreshed = refresh(clientId, tokens["refresh_token"].asString()).andExpect(status().isOk)
            .andReturn().response.contentAsString.let { objectMapper.readTree(it) }
        assertThat(refreshed["refresh_token"].asString()).isNotEqualTo(tokens["refresh_token"].asString())
        callWhoami(refreshed["access_token"].asString()).andExpect(status().isOk)

        refresh(clientId, tokens["refresh_token"].asString()).andExpect(status().isBadRequest)
    }

    @Test
    fun `a refresh token only works for the client it was issued to`() {
        val user = userHelper.createUser()
        val tokens = obtainTokens(user, registerClient())

        refresh(registerClient(), tokens["refresh_token"].asString()).andExpect(status().isBadRequest)
    }

    private fun refresh(clientId: String, refreshToken: String) = mockMvc.perform(
        post("/oauth2/token")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .param("grant_type", "refresh_token")
            .param("refresh_token", refreshToken)
            .param("client_id", clientId),
    )

    private fun obtainAccessToken(user: User, resource: String? = mcpResource): String =
        obtainTokens(user, registerClient(), resource)["access_token"].asString()

    private fun obtainTokens(user: User, clientId: String, resource: String? = mcpResource): JsonNode {
        val cookie = login(user)
        val state = queryParam(startAuthorization(cookie, clientId, resource), "state")
        val clientRedirect = submitConsent(cookie, clientId, state, approve = true)
        assertThat(queryParam(clientRedirect, "state")).isEqualTo("client-state")

        val tokenRequest = post("/oauth2/token")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .param("grant_type", "authorization_code")
            .param("code", queryParam(clientRedirect, "code"))
            .param("redirect_uri", redirectUri)
            .param("client_id", clientId)
            .param("code_verifier", codeVerifier)
        resource?.let { tokenRequest.param("resource", it) }
        val response = mockMvc.perform(tokenRequest).andExpect(status().isOk).andReturn().response
        return objectMapper.readTree(response.contentAsString)
    }

    private fun registerClient(scope: String? = null): String {
        val response = mockMvc.perform(
            post("/oauth2/register").contentType(MediaType.APPLICATION_JSON).content(registrationRequest(scope)),
        ).andExpect(status().isCreated).andReturn().response
        return objectMapper.readTree(response.contentAsString)["client_id"].asString()
    }

    private fun registrationRequest(
        scope: String? = null,
        grantTypes: String = """["authorization_code", "refresh_token"]""",
        authMethod: String? = "none",
    ): String = objectMapper.writeValueAsString(
        buildMap {
            put("client_name", "Claude Code")
            put("redirect_uris", listOf(redirectUri))
            put("grant_types", objectMapper.readTree(grantTypes))
            put("response_types", listOf("code"))
            authMethod?.let { put("token_endpoint_auth_method", it) }
            scope?.let { put("scope", it) }
        },
    )

    // Built as a URL: the authorization server reads GET parameters from the query string, which
    // MockMvc's param() leaves empty.
    private fun authorizationRequest(clientId: String, resource: String?) = get(
        UriComponentsBuilder.fromPath("/oauth2/authorize")
            .queryParam("response_type", "code")
            .queryParam("client_id", clientId)
            .queryParam("redirect_uri", redirectUri)
            .queryParam("state", "client-state")
            .queryParam("code_challenge", codeChallenge())
            .queryParam("code_challenge_method", "S256")
            .also { builder -> resource?.let { builder.queryParam("resource", it) } }
            .encode()
            .build()
            .toUriString(),
    )

    /** Starts an authorization for a logged-in user and returns where it asks for consent. */
    private fun startAuthorization(cookie: Cookie, clientId: String, resource: String?): String {
        val location = mockMvc.perform(authorizationRequest(clientId, resource).cookie(cookie))
            .andExpect(status().isFound)
            .andReturn().response.getHeader(HttpHeaders.LOCATION)!!
        assertThat(location).startsWith("$backendUrl/oauth2/consent?")
        return location
    }

    /** Submits the consent screen the way the frontend does and returns the redirect back to the client. */
    private fun submitConsent(cookie: Cookie, clientId: String, state: String, approve: Boolean): String {
        val request = post("/oauth2/authorize").cookie(cookie)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .param("client_id", clientId)
            .param("state", state)
        if (approve) request.param("scope", "mcp")
        val response = mockMvc.perform(request).andExpect(status().isOk).andReturn().response
        return objectMapper.readTree(response.contentAsString)["redirectUri"].asString()
    }

    private fun callWhoami(token: String) = mockMvc.perform(
        post("/mcp")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            .header(HttpHeaders.ACCEPT, "application/json, text/event-stream")
            .contentType(MediaType.APPLICATION_JSON)
            .content(whoamiCall()),
    )

    private fun whoamiCall() = """
        {"jsonrpc": "2.0", "id": 1, "method": "tools/call", "params": {"name": "whoami", "arguments": {}}}
    """.trimIndent()

    private fun login(user: User) = userHelper.login(mockMvc = mockMvc, email = user.email)

    private fun codeChallenge(): String = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(MessageDigest.getInstance("SHA-256").digest(codeVerifier.toByteArray()))

    private fun queryParam(url: String, name: String): String =
        UriComponentsBuilder.fromUriString(url).build().queryParams.getFirst(name)
            ?.let { java.net.URLDecoder.decode(it, Charsets.UTF_8) }
            ?: throw AssertionError("$url has no $name parameter")

    private fun installLicense() {
        // Same test license as in ApiKeyIntegrationTest and SAMLTest
        val licenseJson = """
            {
                "license_data":{"max_users":2,"expiry_date":"2100-01-01","test_license":true},
                "signature":"E3cqrsVzWccsyWwIeCE2J4Mn/eHyP8j4T05Q4o2dtXH1lhum71rEyPqv9MLn//IcVGsLBY6MwWJGxxa+IBqZTvx0fkLix7e44BRJ5xnV83WzZbKyacNCsNqYEbNpeRcDmtC0pbk7/OSff8VDs5xdqWl7zsI+HA5KNdw878BZKVxusHkHhLtxOhHtbm7Gvcyia4XE86USTWUMYf6aCgNkQgRSOnTo5Zrs+vBUvgSI33l3XyBDx+cQcr9Mell2ytOYrTxQ4zUbRkzcsQtGRTHbh8uXQb5wS389F0zQWSLh7RrCRuaEZ0IDTt8tFkN+72fZ64504bsSR9mNgkgKTv/FvQiVCppKO8vpW0T0hg2xziXMnNSJ3MbihcNlpFsz9C2SEnGm18rQ4UagnLCWTqhz5DtWCxeaAExIT261o6J/wBwlsHHMJRiDaLo/cQOLVOUm43psOt4nlTdbijPoKhBejBuSgqSxTid1R7+8YaFlco/SaprzEspWHcOcVIPUN2jk"
            }
        """.trimIndent()
        licenseAdapter.createLicense(
            LicenseFile(fileContent = licenseJson, fileName = "test-license.json", createdAt = LocalDateTime.now()),
        )
    }
}
