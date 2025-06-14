package dev.kviklet.kviklet

import com.gargoylesoftware.htmlunit.WebClient
import com.gargoylesoftware.htmlunit.html.HtmlInput
import com.gargoylesoftware.htmlunit.html.HtmlPage
import dev.kviklet.kviklet.db.LicenseAdapter
import dev.kviklet.kviklet.db.UserAdapter
import dev.kviklet.kviklet.service.dto.LicenseFile
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
    properties = ["server.port=8082"],
)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class SAMLTest {

    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var userAdapter: UserAdapter

    @Autowired
    private lateinit var licenseAdapter: LicenseAdapter

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
        fun configureSamlProperties(registry: DynamicPropertyRegistry) {
            setupKeycloak()

            val keycloakUrl = "http://${keycloak.host}:${keycloak.getMappedPort(8080)}"

            registry.add("saml.enabled") { "true" }
            registry.add("saml.entityId") { "$keycloakUrl/realms/test-saml-realm" }
            registry.add("saml.ssoServiceLocation") { "$keycloakUrl/realms/test-saml-realm/protocol/saml" }
            registry.add("saml.verificationCertificate") {
                "-----BEGIN CERTIFICATE-----\n${getKeycloakSamlCertificate(keycloakUrl)}\n-----END CERTIFICATE-----"
            }
            registry.add("saml.userAttributes.emailAttribute") { "urn:oid:1.2.840.113549.1.9.1" }
            registry.add("saml.userAttributes.nameAttribute") { "urn:oid:2.5.4.3" }
            registry.add("saml.userAttributes.idAttribute") { "urn:oid:0.9.2342.19200300.100.1.1" }
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

                // Create SAML client
                createSamlClient(adminClient, keycloakUrl, accessToken)

                // Create test user
                createTestUser(adminClient, keycloakUrl, accessToken)
            } catch (e: Exception) {
                e.printStackTrace()
                throw e
            }
        }

        private fun getKeycloakSamlCertificate(keycloakUrl: String): String {
            val adminClient = HttpClient.newHttpClient()
            val metadataUrl = "$keycloakUrl/realms/test-saml-realm/protocol/saml/descriptor"

            try {
                val response = adminClient.send(
                    HttpRequest.newBuilder().uri(URI.create(metadataUrl)).build(),
                    HttpResponse.BodyHandlers.ofString(),
                )

                // Parse XML to extract certificate from <ds:X509Certificate> element
                val certificateRegex = "<ds:X509Certificate>([^<]+)</ds:X509Certificate>".toRegex()
                val match = certificateRegex.find(response.body())
                return match?.groupValues?.get(1)?.replace("\n", "")
                    ?: throw Exception("Certificate not found in SAML metadata")
            } catch (e: Exception) {
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
                    "realm": "test-saml-realm",
                    "enabled": true,
                    "displayName": "Test SAML Realm"
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

        private fun createSamlClient(client: HttpClient, keycloakUrl: String, token: String) {
            val clientJson = """
                {
                    "clientId": "http://localhost:8082/saml2/service-provider-metadata/saml",
                    "name": "kviklet-saml-client",
                    "protocol": "saml",
                    "enabled": true,
                    "attributes": {
                        "saml.assertion.signature": "true",
                        "saml.force.post.binding": "true",
                        "saml.multivalued.roles": "false",
                        "saml.encrypt": "false",
                        "saml.server.signature": "true",
                        "saml.server.signature.keyinfo.ext": "false",
                        "exclude.session.state.from.auth.response": "false",
                        "saml.signature.algorithm": "RSA_SHA256",
                        "saml.client.signature": "false",
                        "tls.client.certificate.bound.access.tokens": "false",
                        "saml.authnstatement": "true",
                        "display.on.consent.screen": "false",
                        "saml.onetimeuse.condition": "false"
                    },
                    "redirectUris": [
                        "http://localhost:8082/login/saml2/sso/saml"
                    ],
                    "webOrigins": [
                        "http://localhost:8082"
                    ]
                }
            """.trimIndent()

            val request = HttpRequest.newBuilder()
                .uri(URI.create("$keycloakUrl/admin/realms/test-saml-realm/clients"))
                .header("Authorization", "Bearer $token")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(clientJson))
                .build()

            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            val clientId = extractClientIdFromLocation(response.headers().firstValue("Location").orElse(""))

            // Add attribute mappers
            addAttributeMappers(client, keycloakUrl, token, clientId)
        }

        private fun extractClientIdFromLocation(location: String): String = location.substringAfterLast("/")

        private fun addAttributeMappers(client: HttpClient, keycloakUrl: String, token: String, clientId: String) {
            val mappers = listOf(
                """
                {
                    "name": "email",
                    "protocol": "saml",
                    "protocolMapper": "saml-user-property-mapper",
                    "config": {
                        "attribute.nameformat": "urn:oasis:names:tc:SAML:2.0:attrname-format:uri",
                        "user.attribute": "email",
                        "friendly.name": "email",
                        "attribute.name": "urn:oid:1.2.840.113549.1.9.1"
                    }
                }
                """.trimIndent(),
                """
                {
                    "name": "username",
                    "protocol": "saml",
                    "protocolMapper": "saml-user-property-mapper",
                    "config": {
                        "attribute.nameformat": "urn:oasis:names:tc:SAML:2.0:attrname-format:uri",
                        "user.attribute": "username",
                        "friendly.name": "uid",
                        "attribute.name": "urn:oid:0.9.2342.19200300.100.1.1"
                    }
                }
                """.trimIndent(),
                """
                {
                    "name": "displayName",
                    "protocol": "saml",
                    "protocolMapper": "saml-user-property-mapper",
                    "config": {
                        "attribute.nameformat": "urn:oasis:names:tc:SAML:2.0:attrname-format:uri",
                        "user.attribute": "firstName",
                        "friendly.name": "cn",
                        "attribute.name": "urn:oid:2.5.4.3"
                    }
                }
                """.trimIndent(),
            )

            mappers.forEach { mapperJson ->
                val request = HttpRequest.newBuilder()
                    .uri(
                        URI.create(
                            "$keycloakUrl/admin/realms/test-saml-realm/clients/$clientId/protocol-mappers/models",
                        ),
                    )
                    .header("Authorization", "Bearer $token")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapperJson))
                    .build()

                client.send(request, HttpResponse.BodyHandlers.ofString())
            }
        }

        private fun createTestUser(client: HttpClient, keycloakUrl: String, token: String) {
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
                .uri(URI.create("$keycloakUrl/admin/realms/test-saml-realm/users"))
                .header("Authorization", "Bearer $token")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(userJson))
                .build()

            client.send(request, HttpResponse.BodyHandlers.ofString())
        }
    }

    @Autowired
    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        // WARNING: This is a test-only license limited to 2 users with test_license flag.
        // DO NOT use in production. Real licenses must be obtained from Kviklet.
        val licenseJson = """
            {
                "license_data":{"max_users":2,"expiry_date":"2100-01-01","test_license":true},
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
    }

    @Test
    fun `test saml login endpoint exists`() {
        mockMvc.perform(
            get("/saml2/authenticate/saml"),
        ).andExpect(status().is3xxRedirection)
    }

    @Test
    fun `test complete SAML flow with WebClient`() {
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
        val userCountBeforeSAML = userAdapter.listUsers().size

        try {
            // Start the login flow by accessing the SAML authentication endpoint
            val loginUrl = "http://localhost:$port/saml2/authenticate/saml"

            // Get the SAML redirect page
            val samlRedirectPage = webClient.getPage<HtmlPage>(loginUrl)

            // Submit the SAML request form (this will redirect to Keycloak)
            val samlForm = samlRedirectPage.forms[0]
            val keycloakLoginPage = samlForm.getElementsByTagName("input")
                .filterIsInstance<HtmlInput>()
                .find { it.getAttribute("type") == "submit" }
                ?.click<HtmlPage>() ?: throw RuntimeException("Could not find submit button in SAML form")

            // Fill in login credentials (using Keycloak test user)
            val usernameElement = keycloakLoginPage.getElementById("username")
            val passwordElement = keycloakLoginPage.getElementById("password")

            if (usernameElement != null && passwordElement != null) {
                (usernameElement as HtmlInput).type("testuser")
                (passwordElement as HtmlInput).type("testpass")
            } else {
                throw RuntimeException("Could not find login form elements on Keycloak page")
            }

            // Submit the login form by clicking the Sign In button
            val submitButton = keycloakLoginPage.getElementsByTagName("input")
                .filterIsInstance<HtmlInput>()
                .find { it.getAttribute("type") == "submit" }

            val redirectPage = submitButton?.click<HtmlPage>()

            // Check if this is the SAML response redirect page and submit the form
            if (redirectPage?.asXml()?.contains("Authentication Redirect") == true) {
                // Submit the SAML response form directly
                val samlResponseForm = redirectPage.forms[0]
                try {
                    // Try to find a submit button first
                    val submitBtn = samlResponseForm.getElementsByTagName("input")
                        .filterIsInstance<HtmlInput>()
                        .find { it.getAttribute("type") == "submit" }

                    if (submitBtn != null) {
                        val finalPage = submitBtn.click<HtmlPage>()
                        assertThat(finalPage?.url?.toString()).contains("localhost:5173")
                    } else {
                        // No submit button found, submit the form directly
                        val finalPage = samlResponseForm.getElementsByTagName("input")[0].click<HtmlPage>()
                        assertThat(finalPage?.url?.toString()).contains("localhost:5173")
                    }
                } catch (e: Exception) {
                    if (!e.message.orEmpty().contains("HttpHostConnectException: Connect to localhost:5173")) {
                        throw e
                    }
                }
            } else {
                assertThat(redirectPage?.url?.toString()).contains("localhost:5173")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            throw e // Re-throw to fail the test if necessary
        } finally {
            // Always close the WebClient to release resources
            webClient.close()
        }

        // Assert that a new user was created
        assertThat(userAdapter.listUsers().size).isEqualTo(userCountBeforeSAML + 1)

        // Verify the created user has SAML attributes
        val createdUser = userAdapter.findByEmail("testuser@example.com")
        assertThat(createdUser).isNotNull()
        assertThat(createdUser?.samlNameId).isNotNull()
        assertThat(createdUser?.fullName).isNotNull()
        assertThat(createdUser?.password).isNull()
    }

    @Test
    fun `test existing user with password auth updates to SAML auth when logging in via SAML`() {
        // Create a user with password authentication first
        val existingUser = dev.kviklet.kviklet.db.User(
            email = "testuser@example.com",
            fullName = "Test User",
            password = "hashedpassword123",
            roles = setOf(),
        )
        userAdapter.createOrUpdateUser(existingUser)

        // Verify user was created with password auth
        val userBeforeSaml = userAdapter.findByEmail("testuser@example.com")
        assertThat(userBeforeSaml).isNotNull()
        assertThat(userBeforeSaml?.password).isEqualTo("hashedpassword123")
        assertThat(userBeforeSaml?.samlNameId).isNull()
        assertThat(userBeforeSaml?.subject).isNull()
        assertThat(userBeforeSaml?.ldapIdentifier).isNull()

        // Create and configure WebClient for SAML login
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
            // Start the login flow by accessing the SAML authentication endpoint
            val loginUrl = "http://localhost:$port/saml2/authenticate/saml"

            // Get the SAML redirect page
            val samlRedirectPage = webClient.getPage<HtmlPage>(loginUrl)

            // Submit the SAML request form (this will redirect to Keycloak)
            val samlForm = samlRedirectPage.forms[0]
            val keycloakLoginPage = samlForm.getElementsByTagName("input")
                .filterIsInstance<HtmlInput>()
                .find { it.getAttribute("type") == "submit" }
                ?.click<HtmlPage>() ?: throw RuntimeException("Could not find submit button in SAML form")

            // Fill in login credentials
            val usernameElement = keycloakLoginPage.getElementById("username") as HtmlInput
            val passwordElement = keycloakLoginPage.getElementById("password") as HtmlInput
            usernameElement.type("testuser")
            passwordElement.type("testpass")

            // Submit the login form
            val submitButton = keycloakLoginPage.getElementsByTagName("input")
                .filterIsInstance<HtmlInput>()
                .find { it.getAttribute("type") == "submit" }

            val redirectPage = submitButton?.click<HtmlPage>()

            // Handle SAML response redirect if needed
            if (redirectPage?.asXml()?.contains("Authentication Redirect") == true) {
                val samlResponseForm = redirectPage.forms[0]
                val submitBtn = samlResponseForm.getElementsByTagName("input")
                    .filterIsInstance<HtmlInput>()
                    .find { it.getAttribute("type") == "submit" }

                if (submitBtn != null) {
                    try {
                        submitBtn.click<HtmlPage>()
                    } catch (e: RuntimeException) {
                        // if the frontend hasn't started this is expected
                        if (!e.message.orEmpty().contains("HttpHostConnectException: Connect to localhost:5173")) {
                            throw e
                        }
                    }
                } else {
                    samlResponseForm.getElementsByTagName("input")[0].click<HtmlPage>()
                }
            }
        } finally {
            webClient.close()
        }

        // Verify the user's auth method was updated to SAML
        val userAfterSaml = userAdapter.findByEmail("testuser@example.com")
        assertThat(userAfterSaml).isNotNull()
        assertThat(userAfterSaml?.samlNameId).isNotNull()
        assertThat(userAfterSaml?.password).isNull() // Password should be cleared
        assertThat(userAfterSaml?.subject).isNull() // OIDC subject should remain null
        assertThat(userAfterSaml?.ldapIdentifier).isNull() // LDAP identifier should remain null
        assertThat(userAfterSaml?.fullName).isEqualTo("Test") // Should update from Keycloak
    }
}
