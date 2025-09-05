package dev.kviklet.kviklet

import com.fasterxml.jackson.databind.ObjectMapper
import dev.kviklet.kviklet.db.ApiKeyRepository
import dev.kviklet.kviklet.db.LicenseAdapter
import dev.kviklet.kviklet.helper.RoleHelper
import dev.kviklet.kviklet.helper.UserHelper
import dev.kviklet.kviklet.service.dto.LicenseFile
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.CoreMatchers.notNullValue
import org.hamcrest.CoreMatchers.nullValue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDateTime

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiKeyControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var userHelper: UserHelper

    @Autowired
    private lateinit var roleHelper: RoleHelper

    @Autowired
    private lateinit var licenseAdapter: LicenseAdapter

    @Autowired
    private lateinit var apiKeyRepository: ApiKeyRepository

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @BeforeEach
    fun setUp() {
        // Setup test license (same as used in SAMLTest)
        val licenseJson = """
            {
                "license_data":{"max_users":2,"expiry_date":"2100-01-01","test_license":true},
                "signature":"E3cqrsVzWccsyWwIeCE2J4Mn/eHyP8j4T05Q4o2dtXH1lhum71rEyPqv9MLn//IcVGsLBY6MwWJGxxa+IBqZTvx0fkLix7e44BRJ5xnV83WzZbKyacNCsNqYEbNpeRcDmtC0pbk7/OSff8VDs5xdqWl7zsI+HA5KNdw878BZKVxusHkHhLtxOhHtbm7Gvcyia4XE86USTWUMYf6aCgNkQgRSOnTo5Zrs+vBUvgSI33l3XyBDx+cQcr9Mell2ytOYrTxQ4zUbRkzcsQtGRTHbh8uXQb5wS389F0zQWSLh7RrCRuaEZ0IDTt8tFkN+72fZ64504bsSR9mNgkgKTv/FvQiVCppKO8vpW0T0hg2xziXMnNSJ3MbihcNlpFsz9C2SEnGm18rQ4UagnLCWTqhz5DtWCxeaAExIT261o6J/wBwlsHHMJRiDaLo/cQOLVOUm43psOt4nlTdbijPoKhBejBuSgqSxTid1R7+8YaFlco/SaprzEspWHcOcVIPUN2jk"
            }
        """.trimIndent()

        val licenseFile = LicenseFile(
            fileContent = licenseJson,
            fileName = "test-license.json",
            createdAt = LocalDateTime.now()
        )
        licenseAdapter.createLicense(licenseFile)
    }

    @AfterEach
    fun tearDown() {
        apiKeyRepository.deleteAll()  // Delete API keys first due to foreign key constraints
        userHelper.deleteAll()
        roleHelper.deleteAll()
        licenseAdapter.deleteAll()
    }

    @Test
    fun `test create API key with valid license and permissions`() {
        // Create user with API key permissions
        val user = userHelper.createUser(permissions = listOf("api_key:create", "api_key:get"))
        val cookie = userHelper.login(mockMvc = mockMvc, email = user.email)

        val createRequest = """
            {
                "name": "Test API Key",
                "expiresInDays": 30
            }
        """.trimIndent()

        mockMvc.perform(
            post("/api-keys/")
                .cookie(cookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createRequest)
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.id", notNullValue()))
            .andExpect(jsonPath("$.name", `is`("Test API Key")))
            .andExpect(jsonPath("$.key", notNullValue()))
            .andExpect(jsonPath("$.createdAt", notNullValue()))
            .andExpect(jsonPath("$.expiresAt", notNullValue()))
    }

    @Test
    fun `test create API key without expiration`() {
        val user = userHelper.createUser(permissions = listOf("api_key:create", "api_key:get"))
        val cookie = userHelper.login(mockMvc = mockMvc, email = user.email)

        val createRequest = """
            {
                "name": "Permanent API Key",
                "expiresInDays": null
            }
        """.trimIndent()

        mockMvc.perform(
            post("/api-keys/")
                .cookie(cookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createRequest)
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.id", notNullValue()))
            .andExpect(jsonPath("$.name", `is`("Permanent API Key")))
            .andExpect(jsonPath("$.key", notNullValue()))
            .andExpect(jsonPath("$.expiresAt", nullValue()))
    }

    @Test
    fun `test create API key without license fails`() {
        // Delete the license
        licenseAdapter.deleteAll()

        val user = userHelper.createUser(permissions = listOf("api_key:create", "api_key:get"))
        val cookie = userHelper.login(mockMvc = mockMvc, email = user.email)

        val createRequest = """
            {
                "name": "Should Fail",
                "expiresInDays": 30
            }
        """.trimIndent()

        mockMvc.perform(
            post("/api-keys/")
                .cookie(cookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createRequest)
        )
            .andExpect(status().isPaymentRequired)
    }

    @Test
    fun `test create API key without permission fails`() {
        // User without api_key:create permission
        val user = userHelper.createUser(permissions = listOf("api_key:get"))
        val cookie = userHelper.login(mockMvc = mockMvc, email = user.email)

        val createRequest = """
            {
                "name": "Should Fail",
                "expiresInDays": 30
            }
        """.trimIndent()

        mockMvc.perform(
            post("/api-keys/")
                .cookie(cookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createRequest)
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `test list API keys`() {
        // Create user and some API keys
        val user = userHelper.createUser(permissions = listOf("api_key:create", "api_key:get"))
        val cookie = userHelper.login(mockMvc = mockMvc, email = user.email)

        // Create two API keys
        val createRequest1 = """
            {
                "name": "API Key 1",
                "expiresInDays": 30
            }
        """.trimIndent()

        val createRequest2 = """
            {
                "name": "API Key 2",
                "expiresInDays": 60
            }
        """.trimIndent()

        mockMvc.perform(
            post("/api-keys/")
                .cookie(cookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createRequest1)
        ).andExpect(status().isCreated)

        mockMvc.perform(
            post("/api-keys/")
                .cookie(cookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createRequest2)
        ).andExpect(status().isCreated)

        // List API keys
        mockMvc.perform(
            get("/api-keys/")
                .cookie(cookie)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.apiKeys", notNullValue()))
            .andExpect(jsonPath("$.apiKeys.length()", `is`(2)))
            .andExpect(jsonPath("$.apiKeys[0].name", notNullValue()))
            .andExpect(jsonPath("$.apiKeys[0].key").doesNotExist()) // Key should not be exposed
            .andExpect(jsonPath("$.apiKeys[0].user.id", `is`(user.getId())))
            .andExpect(jsonPath("$.apiKeys[0].user.email", `is`(user.email)))
            .andExpect(jsonPath("$.apiKeys[0].user.fullName", `is`(user.fullName)))
            .andExpect(jsonPath("$.apiKeys[1].user.id", `is`(user.getId())))
            .andExpect(jsonPath("$.apiKeys[1].user.email", `is`(user.email)))
            .andExpect(jsonPath("$.apiKeys[1].user.fullName", `is`(user.fullName)))
            .andExpect(jsonPath("$.apiKeys[1].name", notNullValue()))
            .andExpect(jsonPath("$.apiKeys[1].key").doesNotExist()) // Key should not be exposed
    }

    @Test
    fun `test get specific API key`() {
        val user = userHelper.createUser(permissions = listOf("api_key:create", "api_key:get"))
        val cookie = userHelper.login(mockMvc = mockMvc, email = user.email)

        // Create an API key
        val createRequest = """
            {
                "name": "Test API Key",
                "expiresInDays": 30
            }
        """.trimIndent()

        val createResult = mockMvc.perform(
            post("/api-keys/")
                .cookie(cookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createRequest)
        )
            .andExpect(status().isCreated)
            .andReturn()

        // Extract the ID from the response
        val responseBody = createResult.response.contentAsString
        val jsonNode = objectMapper.readTree(responseBody)
        val apiKeyId = jsonNode.get("id").asText()

        // Get specific API key
        mockMvc.perform(
            get("/api-keys/$apiKeyId")
                .cookie(cookie)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id", `is`(apiKeyId)))
            .andExpect(jsonPath("$.name", `is`("Test API Key")))
            .andExpect(jsonPath("$.createdAt", notNullValue()))
            .andExpect(jsonPath("$.expiresAt", notNullValue()))
            .andExpect(jsonPath("$.key").doesNotExist()) // Key should not be exposed
            .andExpect(jsonPath("$.user.id", `is`(user.getId())))
            .andExpect(jsonPath("$.user.email", `is`(user.email)))
            .andExpect(jsonPath("$.user.fullName", `is`(user.fullName)))
    }

    @Test
    fun `test delete API key`() {
        val user = userHelper.createUser(permissions = listOf("api_key:create", "api_key:edit", "api_key:get"))
        val cookie = userHelper.login(mockMvc = mockMvc, email = user.email)

        // Create an API key
        val createRequest = """
            {
                "name": "To Be Deleted",
                "expiresInDays": 30
            }
        """.trimIndent()

        val createResult = mockMvc.perform(
            post("/api-keys/")
                .cookie(cookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createRequest)
        )
            .andExpect(status().isCreated)
            .andReturn()

        // Extract the ID
        val responseBody = createResult.response.contentAsString
        val jsonNode = objectMapper.readTree(responseBody)
        val apiKeyId = jsonNode.get("id").asText()

        // Delete the API key
        mockMvc.perform(
            delete("/api-keys/$apiKeyId")
                .cookie(cookie)
        )
            .andExpect(status().isNoContent)

        // Verify it's deleted by trying to get it
        mockMvc.perform(
            get("/api-keys/$apiKeyId")
                .cookie(cookie)
        )
            .andExpect(status().isNotFound)
    }

    @Test
    fun `test delete API key without permission fails`() {
        // User with create permission but not edit
        val user = userHelper.createUser(permissions = listOf("api_key:create", "api_key:get"))
        val cookie = userHelper.login(mockMvc = mockMvc, email = user.email)

        // Create an API key
        val createRequest = """
            {
                "name": "Cannot Delete",
                "expiresInDays": 30
            }
        """.trimIndent()

        val createResult = mockMvc.perform(
            post("/api-keys/")
                .cookie(cookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createRequest)
        )
            .andExpect(status().isCreated)
            .andReturn()

        // Extract the ID
        val responseBody = createResult.response.contentAsString
        val jsonNode = objectMapper.readTree(responseBody)
        val apiKeyId = jsonNode.get("id").asText()

        // Try to delete without permission
        mockMvc.perform(
            delete("/api-keys/$apiKeyId")
                .cookie(cookie)
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `test all endpoints require enterprise license`() {
        // Delete the license
        licenseAdapter.deleteAll()

        val user = userHelper.createUser(permissions = listOf("*"))
        val cookie = userHelper.login(mockMvc = mockMvc, email = user.email)

        // Test create endpoint
        mockMvc.perform(
            post("/api-keys/")
                .cookie(cookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name": "Test", "expiresInDays": 30}""")
        )
            .andExpect(status().isPaymentRequired)

        // Test list endpoint
        mockMvc.perform(
            get("/api-keys/")
                .cookie(cookie)
        )
            .andExpect(status().isPaymentRequired)

        // Test get endpoint
        mockMvc.perform(
            get("/api-keys/test-id")
                .cookie(cookie)
        )
            .andExpect(status().isPaymentRequired)

        // Test delete endpoint
        mockMvc.perform(
            delete("/api-keys/test-id")
                .cookie(cookie)
        )
            .andExpect(status().isPaymentRequired)
    }
}