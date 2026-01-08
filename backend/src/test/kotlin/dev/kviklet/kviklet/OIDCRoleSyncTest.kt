package dev.kviklet.kviklet

import com.gargoylesoftware.htmlunit.WebClient
import com.gargoylesoftware.htmlunit.html.HtmlInput
import com.gargoylesoftware.htmlunit.html.HtmlPage
import dev.kviklet.kviklet.db.LicenseAdapter
import dev.kviklet.kviklet.db.RoleAdapter
import dev.kviklet.kviklet.db.RoleSyncConfigAdapter
import dev.kviklet.kviklet.db.UserAdapter
import dev.kviklet.kviklet.service.dto.LicenseFile
import dev.kviklet.kviklet.service.dto.Role
import dev.kviklet.kviklet.service.dto.RoleId
import dev.kviklet.kviklet.service.dto.SyncMode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
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
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.LocalDateTime

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
    properties = ["server.port=8083"],
)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class OIDCRoleSyncTest {

    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var userAdapter: UserAdapter

    @Autowired
    private lateinit var licenseAdapter: LicenseAdapter

    @Autowired
    private lateinit var roleAdapter: RoleAdapter

    @Autowired
    private lateinit var roleSyncConfigAdapter: RoleSyncConfigAdapter

    @Autowired
    private lateinit var mockMvc: MockMvc

    // Track test-created role for cleanup
    private var testRole: Role? = null

    companion object {

        @Container
        val keycloak = GenericContainer("quay.io/keycloak/keycloak:23.0")
            .withExposedPorts(8080)
            .withEnv(
                mapOf(
                    "KEYCLOAK_ADMIN" to "admin",
                    "KEYCLOAK_ADMIN_PASSWORD" to "admin",
                    "KC_HTTP_ENABLED" to "true",
                    "KC_HOSTNAME_STRICT" to "false",
                    "KC_HOSTNAME_STRICT_HTTPS" to "false",
                ),
            )
            .withCommand("start-dev")
            .waitingFor(Wait.forHttp("/").forPort(8080).forStatusCode(200))
            .withLogConsumer { output -> println("KEYCLOAK LOG: ${output.utf8String}") }

        @JvmStatic
        @DynamicPropertySource
        fun configureOidcProperties(registry: DynamicPropertyRegistry) {
            setupKeycloak()

            val keycloakUrl = "http://${keycloak.host}:${keycloak.getMappedPort(8080)}"

            registry.add("kviklet.identity-provider.type") { "keycloak" }
            registry.add("kviklet.identity-provider.issuer-uri") { "$keycloakUrl/realms/test-oidc-realm" }
            registry.add("kviklet.identity-provider.client-id") { "kviklet-oidc" }
            registry.add("kviklet.identity-provider.client-secret") { "oidc-client-secret" }
        }

        private fun setupKeycloak() {
            val keycloakUrl = "http://${keycloak.host}:${keycloak.getMappedPort(8080)}"
            val adminClient = HttpClient.newHttpClient()

            try {
                // Get admin access token
                val tokenRequest = HttpRequest.newBuilder()
                    .uri(URI.create("$keycloakUrl/realms/master/protocol/openid-connect/token"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(
                        HttpRequest.BodyPublishers.ofString(
                            "username=admin&password=admin&grant_type=password&client_id=admin-cli",
                        ),
                    )
                    .build()

                val tokenResponse = adminClient.send(tokenRequest, HttpResponse.BodyHandlers.ofString())
                val accessToken = extractAccessToken(tokenResponse.body())

                // Create test realm
                createRealm(adminClient, keycloakUrl, accessToken)

                // Create OIDC client
                val clientId = createOidcClient(adminClient, keycloakUrl, accessToken)

                // Add groups claim mapper to the client
                addGroupsMapper(adminClient, keycloakUrl, accessToken, clientId)

                // Create test user
                val userId = createTestUser(adminClient, keycloakUrl, accessToken)

                // Create groups and add user to them
                val groupId = createGroup(adminClient, keycloakUrl, accessToken, "oidc-developers")
                addUserToGroup(adminClient, keycloakUrl, accessToken, userId, groupId)
            } catch (e: Exception) {
                e.printStackTrace()
                throw e
            }
        }

        private fun extractAccessToken(response: String): String {
            val regex = "\"access_token\":\"([^\"]+)\"".toRegex()
            return regex.find(response)?.groupValues?.get(1) ?: throw Exception("Failed to extract access token")
        }

        private fun createRealm(client: HttpClient, keycloakUrl: String, token: String) {
            val realmJson = """
                {
                    "realm": "test-oidc-realm",
                    "enabled": true,
                    "displayName": "Test OIDC Realm"
                }
            """.trimIndent()

            val request = HttpRequest.newBuilder()
                .uri(URI.create("$keycloakUrl/admin/realms"))
                .header("Authorization", "Bearer $token")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(realmJson))
                .build()

            client.send(request, HttpResponse.BodyHandlers.ofString())
        }

        private fun createOidcClient(client: HttpClient, keycloakUrl: String, token: String): String {
            val clientJson = """
                {
                    "clientId": "kviklet-oidc",
                    "name": "kviklet-oidc-client",
                    "protocol": "openid-connect",
                    "enabled": true,
                    "publicClient": false,
                    "secret": "oidc-client-secret",
                    "standardFlowEnabled": true,
                    "directAccessGrantsEnabled": true,
                    "redirectUris": [
                        "http://localhost:8083/login/oauth2/code/keycloak"
                    ],
                    "webOrigins": [
                        "http://localhost:8083"
                    ]
                }
            """.trimIndent()

            val request = HttpRequest.newBuilder()
                .uri(URI.create("$keycloakUrl/admin/realms/test-oidc-realm/clients"))
                .header("Authorization", "Bearer $token")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(clientJson))
                .build()

            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            return extractClientIdFromLocation(response.headers().firstValue("Location").orElse(""))
        }

        private fun extractClientIdFromLocation(location: String): String = location.substringAfterLast("/")

        private fun addGroupsMapper(client: HttpClient, keycloakUrl: String, token: String, clientId: String) {
            val mapperJson = """
                {
                    "name": "groups",
                    "protocol": "openid-connect",
                    "protocolMapper": "oidc-group-membership-mapper",
                    "config": {
                        "full.path": "false",
                        "id.token.claim": "true",
                        "access.token.claim": "true",
                        "claim.name": "groups",
                        "userinfo.token.claim": "true"
                    }
                }
            """.trimIndent()

            val request = HttpRequest.newBuilder()
                .uri(
                    URI.create(
                        "$keycloakUrl/admin/realms/test-oidc-realm/clients/$clientId/protocol-mappers/models",
                    ),
                )
                .header("Authorization", "Bearer $token")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapperJson))
                .build()

            client.send(request, HttpResponse.BodyHandlers.ofString())
        }

        private fun createTestUser(client: HttpClient, keycloakUrl: String, token: String): String {
            val userJson = """
                {
                    "username": "testuser",
                    "email": "testuser@example.com",
                    "firstName": "Test",
                    "lastName": "User",
                    "enabled": true,
                    "credentials": [
                        {
                            "type": "password",
                            "value": "testpass",
                            "temporary": false
                        }
                    ]
                }
            """.trimIndent()

            val request = HttpRequest.newBuilder()
                .uri(URI.create("$keycloakUrl/admin/realms/test-oidc-realm/users"))
                .header("Authorization", "Bearer $token")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(userJson))
                .build()

            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            return extractClientIdFromLocation(response.headers().firstValue("Location").orElse(""))
        }

        private fun createGroup(client: HttpClient, keycloakUrl: String, token: String, groupName: String): String {
            val groupJson = """{"name": "$groupName"}"""

            val request = HttpRequest.newBuilder()
                .uri(URI.create("$keycloakUrl/admin/realms/test-oidc-realm/groups"))
                .header("Authorization", "Bearer $token")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(groupJson))
                .build()

            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            return extractClientIdFromLocation(response.headers().firstValue("Location").orElse(""))
        }

        private fun addUserToGroup(
            client: HttpClient,
            keycloakUrl: String,
            token: String,
            userId: String,
            groupId: String,
        ) {
            val request = HttpRequest.newBuilder()
                .uri(URI.create("$keycloakUrl/admin/realms/test-oidc-realm/users/$userId/groups/$groupId"))
                .header("Authorization", "Bearer $token")
                .PUT(HttpRequest.BodyPublishers.noBody())
                .build()

            client.send(request, HttpResponse.BodyHandlers.ofString())
        }
    }

    @BeforeEach
    fun setUp() {
        // WARNING: This is a test-only license limited to 10 users with test_license flag.
        // DO NOT use in production. Real licenses must be obtained from Kviklet.
        val licenseJson = """
            {
                "license_data":{"max_users":10,"expiry_date":"2100-01-01","test_license":true},
                "signature":"E3cqrsVzWccsyWwIeCE2J4Mn/eHyP8j4T05Q4o2dtXH1lhum71rEyPqv9MLn//IcVGsLBY6MwWJGxxa+IBqZTvx0fkLix7e44BRJ5xnV83WzZbKyacNCsNqYEbNpeRcDmtC0pbk7/OSff8VDs5xdqWl7zsI+HA5KNdw878BZKVxusHkHhLtxOhHtbm7Gvcyia4XE86USTWUMYf6aCgNkQgRSOnTo5Zrs+vBUvgSI33l3XyBDx+cQcr9Mell2ytOYrTxQ4zUbRkzcsQtGRTHbh8uXQb5wS389F0zQWSLh7RrCRuaEZ0IDTt8tFkN+72fZ64504bsSR9mNgkgKTv/FvQiVCppKO8vpW0T0hg2xziXMnNSJ3MbihcNlpFsz9C2SEnGm18rQ4UagnLCWTqhz5DtWCxeaAExIT261o6J/wBwlsHHMJRiDaLo/cQOLVOUm43psOt4nlTdbijPoKhBejBuSgqSxTid1R7+8YaFlco/SaprzEspWHcOcVIPUN2jk"
            }
        """.trimIndent()

        val licenseFile = LicenseFile(
            fileContent = licenseJson,
            fileName = "test-license.json",
            createdAt = LocalDateTime.now(),
        )
        licenseAdapter.createLicense(licenseFile)
    }

    @AfterEach
    fun tearDown() {
        userAdapter.deleteAll()
        licenseAdapter.deleteAll()
        // Reset role sync config
        roleSyncConfigAdapter.updateConfig(enabled = false)
        // Clean up mappings
        roleSyncConfigAdapter.deleteAllMappings()
        // Only delete the test-created role, not all roles (the default role must remain)
        testRole?.let { roleAdapter.delete(RoleId(it.getId()!!)) }
        testRole = null
    }

    @Test
    fun `test OIDC login endpoint exists`() {
        mockMvc.perform(
            get("/oauth2/authorization/keycloak"),
        ).andExpect(status().is3xxRedirection)
    }

    @Test
    fun `OIDC login syncs roles from groups claim`() {
        // Create test role for mapping
        testRole = roleAdapter.create(
            Role(
                id = null,
                name = "Developer",
                description = "Developer role for testing",
                policies = emptySet(),
            ),
        )

        // Setup role sync config
        roleSyncConfigAdapter.updateConfig(
            enabled = true,
            syncMode = SyncMode.FULL_SYNC,
            groupsAttribute = "groups",
        )

        // Add mapping: oidc-developers group -> Developer role
        roleSyncConfigAdapter.addMapping("oidc-developers", testRole!!.getId()!!)

        // Perform OIDC login
        performOidcLogin()

        // Verify user was created with Developer role
        val user = userAdapter.findByEmail("testuser@example.com")
        assertThat(user).isNotNull
        assertThat(user?.subject).isNotNull

        // Check that user has the Developer role (from role sync)
        val roleNames = user?.roles?.map { it.name }
        assertThat(roleNames).contains("Developer")
    }

    @Test
    fun `OIDC login without license skips role sync and user gets default role only`() {
        // Remove the license
        licenseAdapter.deleteAll()

        // Create test role for mapping (even though it won't be used)
        testRole = roleAdapter.create(
            Role(
                id = null,
                name = "Developer",
                description = "Developer role for testing",
                policies = emptySet(),
            ),
        )

        // Setup role sync config
        roleSyncConfigAdapter.updateConfig(
            enabled = true,
            syncMode = SyncMode.FULL_SYNC,
            groupsAttribute = "groups",
        )

        // Add mapping: oidc-developers group -> Developer role
        roleSyncConfigAdapter.addMapping("oidc-developers", testRole!!.getId()!!)

        // Perform OIDC login - should work but role sync should be skipped
        performOidcLogin()

        // Verify user was created
        val user = userAdapter.findByEmail("testuser@example.com")
        assertThat(user).isNotNull
        assertThat(user?.subject).isNotNull

        // Check that user does NOT have the Developer role (role sync was skipped)
        val roleNames = user?.roles?.map { it.name }
        assertThat(roleNames).doesNotContain("Developer")
        // User should have the default role
        assertThat(roleNames).isNotEmpty
    }

    private fun performOidcLogin() {
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
            val loginUrl = "http://localhost:$port/oauth2/authorization/keycloak"

            // Get the Keycloak login page
            val keycloakLoginPage = webClient.getPage<HtmlPage>(loginUrl)

            // Fill in login credentials
            val usernameElement = keycloakLoginPage.getElementById("username") as HtmlInput
            val passwordElement = keycloakLoginPage.getElementById("password") as HtmlInput
            usernameElement.type("testuser")
            passwordElement.type("testpass")

            // Submit the login form
            val submitButton = keycloakLoginPage.getElementsByTagName("input")
                .filterIsInstance<HtmlInput>()
                .find { it.getAttribute("type") == "submit" }

            try {
                submitButton?.click<HtmlPage>()
                // OIDC flow should redirect to the frontend after successful login
            } catch (e: RuntimeException) {
                // If the frontend hasn't started this exception is expected but the redirect worked
                if (!e.message.orEmpty().contains("HttpHostConnectException: Connect to localhost:5173")) {
                    throw e
                }
            }
        } finally {
            webClient.close()
        }
    }
}
