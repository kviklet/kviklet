package dev.kviklet.kviklet

import com.fasterxml.jackson.databind.ObjectMapper
import dev.kviklet.kviklet.db.ApiKeyRepository
import dev.kviklet.kviklet.db.ConnectionRepository
import dev.kviklet.kviklet.db.ExecutionRequestRepository
import dev.kviklet.kviklet.db.LicenseAdapter
import dev.kviklet.kviklet.helper.RoleHelper
import dev.kviklet.kviklet.helper.UserHelper
import dev.kviklet.kviklet.service.dto.AuthenticationType
import dev.kviklet.kviklet.service.dto.DatabaseProtocol
import dev.kviklet.kviklet.service.dto.DatasourceType
import dev.kviklet.kviklet.service.dto.LicenseFile
import dev.kviklet.kviklet.service.dto.RequestType
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.CoreMatchers.notNullValue
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDateTime

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiKeyIntegrationTest {

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
    private lateinit var connectionRepository: ConnectionRepository

    @Autowired
    private lateinit var executionRequestRepository: ExecutionRequestRepository

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
        executionRequestRepository.deleteAll()
        connectionRepository.deleteAll()
        apiKeyRepository.deleteAll()  // Delete API keys before users due to foreign key constraints
        userHelper.deleteAll()
        roleHelper.deleteAll()
        licenseAdapter.deleteAll()
    }

    // Helper function to create an API key for a user
    private fun createApiKey(user: dev.kviklet.kviklet.db.User, name: String = "Test API Key", expiresInDays: Int? = 30): String {
        val cookie = userHelper.login(mockMvc = mockMvc, email = user.email)
        
        val createRequest = """
            {
                "name": "$name",
                "expiresInDays": ${expiresInDays ?: "null"}
            }
        """.trimIndent()
        
        val result = mockMvc.perform(
            post("/api-keys/")
                .cookie(cookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createRequest)
        )
            .andExpect(status().isCreated)
            .andReturn()
        
        val response = objectMapper.readTree(result.response.contentAsString)
        return response["key"]?.asText() ?: throw IllegalStateException("API key creation failed - no key returned")
    }

    // ============= Connection Management Tests =============

    @Test
    fun `test create connection via API key with proper permissions`() {
        // Create user with all permissions
        val user = userHelper.createUser(permissions = listOf("*"))
        val apiKey = createApiKey(user)

        val connectionRequest = """
            {
                "connectionType": "DATASOURCE",
                "id": "test-conn-1",
                "displayName": "Test Connection",
                "username": "testuser",
                "password": "testpass",
                "reviewConfig": {
                    "numTotalRequired": 1
                },
                "type": "POSTGRESQL",
                "hostname": "localhost",
                "port": 5432,
                "databaseName": "testdb"
            }
        """.trimIndent()

        mockMvc.perform(
            post("/connections/")
                .header("Authorization", "Bearer $apiKey")
                .contentType(MediaType.APPLICATION_JSON)
                .content(connectionRequest)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id", `is`("test-conn-1")))
            .andExpect(jsonPath("$.displayName", `is`("Test Connection")))
    }

    @Test
    fun `test create connection via API key without permissions fails`() {
        // Create user with only API key permissions (no connection create permission)
        // They'll still have default role permissions but those don't include connection:create
        val user = userHelper.createUser(permissions = listOf("api_key:create", "api_key:get"))
        val apiKey = createApiKey(user)

        val connectionRequest = """
            {
                "connectionType": "DATASOURCE",
                "id": "test-conn-2",
                "displayName": "Test Connection",
                "username": "testuser",
                "password": "testpass",
                "reviewConfig": {
                    "numTotalRequired": 1
                },
                "type": "POSTGRESQL",
                "hostname": "localhost",
                "port": 5432,
                "databaseName": "testdb"
            }
        """.trimIndent()

        mockMvc.perform(
            post("/connections/")
                .header("Authorization", "Bearer $apiKey")
                .contentType(MediaType.APPLICATION_JSON)
                .content(connectionRequest)
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `test list connections via API key`() {
        // Create user with all permissions
        val user = userHelper.createUser(permissions = listOf("*"))
        val apiKey = createApiKey(user)

        // Create a connection first (using admin permissions)
        val adminUser = userHelper.createUser(permissions = listOf("*"))
        val adminCookie = userHelper.login(mockMvc = mockMvc, email = adminUser.email)
        
        val connectionRequest = """
            {
                "connectionType": "DATASOURCE",
                "id": "test-conn-3",
                "displayName": "Test Connection",
                "username": "testuser",
                "password": "testpass",
                "reviewConfig": {
                    "numTotalRequired": 1
                },
                "type": "POSTGRESQL",
                "hostname": "localhost",
                "port": 5432,
                "databaseName": "testdb"
            }
        """.trimIndent()

        mockMvc.perform(
            post("/connections/")
                .cookie(adminCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(connectionRequest)
        )
            .andExpect(status().isOk)

        // Now list connections using API key
        mockMvc.perform(
            get("/connections/")
                .header("Authorization", "Bearer $apiKey")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].id", `is`("test-conn-3")))
    }

    @Test
    fun `test update connection via API key with proper permissions`() {
        // Create user with all permissions
        val user = userHelper.createUser(permissions = listOf("*"))
        val apiKey = createApiKey(user)

        // Create a connection first (using admin permissions)
        val adminUser = userHelper.createUser(permissions = listOf("*"))
        val adminCookie = userHelper.login(mockMvc = mockMvc, email = adminUser.email)
        
        val connectionRequest = """
            {
                "connectionType": "DATASOURCE",
                "id": "test-conn-4",
                "displayName": "Original Name",
                "username": "testuser",
                "password": "testpass",
                "reviewConfig": {
                    "numTotalRequired": 1
                },
                "type": "POSTGRESQL",
                "hostname": "localhost",
                "port": 5432,
                "databaseName": "testdb"
            }
        """.trimIndent()

        mockMvc.perform(
            post("/connections/")
                .cookie(adminCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(connectionRequest)
        )
            .andExpect(status().isOk)

        // Now update the connection using API key
        val updateRequest = """
            {
                "connectionType": "DATASOURCE",
                "displayName": "Updated Name"
            }
        """.trimIndent()

        mockMvc.perform(
            patch("/connections/test-conn-4")
                .header("Authorization", "Bearer $apiKey")
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateRequest)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.displayName", `is`("Updated Name")))
    }

    // ============= Execution Request Tests =============

    @Test
    fun `test create execution request via API key with proper permissions`() {
        // Create user with all permissions
        val user = userHelper.createUser(permissions = listOf("*"))
        val apiKey = createApiKey(user)

        // Create a connection first (using admin permissions)
        val adminUser = userHelper.createUser(permissions = listOf("*"))
        val adminCookie = userHelper.login(mockMvc = mockMvc, email = adminUser.email)
        
        val connectionRequest = """
            {
                "connectionType": "DATASOURCE",
                "id": "test-conn-5",
                "displayName": "Test Connection",
                "username": "testuser",
                "password": "testpass",
                "reviewConfig": {
                    "numTotalRequired": 1
                },
                "type": "POSTGRESQL",
                "hostname": "localhost",
                "port": 5432,
                "databaseName": "testdb"
            }
        """.trimIndent()

        mockMvc.perform(
            post("/connections/")
                .cookie(adminCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(connectionRequest)
        )
            .andExpect(status().isOk)

        // Now create execution request using API key
        val executionRequest = """
            {
                "connectionType": "DATASOURCE",
                "connectionId": "test-conn-5",
                "title": "Test Query",
                "type": "SingleExecution",
                "description": "Test execution request",
                "statement": "SELECT 1"
            }
        """.trimIndent()

        mockMvc.perform(
            post("/execution-requests/")
                .header("Authorization", "Bearer $apiKey")
                .contentType(MediaType.APPLICATION_JSON)
                .content(executionRequest)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.title", `is`("Test Query")))
            .andExpect(jsonPath("$.statement", `is`("SELECT 1")))
    }

    @Test
    fun `test create execution request via API key without permissions fails`() {
        // Create user with only API key permissions (no execution request create permission)
        // They'll still have default role permissions but those don't include execution_request:create
        val user = userHelper.createUser(permissions = listOf("api_key:create", "api_key:get"))
        val apiKey = createApiKey(user)

        val executionRequest = """
            {
                "connectionType": "DATASOURCE",
                "connectionId": "test-conn-6",
                "title": "Test Query",
                "type": "SingleExecution",
                "description": "Test execution request",
                "statement": "SELECT 1"
            }
        """.trimIndent()

        mockMvc.perform(
            post("/execution-requests/")
                .header("Authorization", "Bearer $apiKey")
                .contentType(MediaType.APPLICATION_JSON)
                .content(executionRequest)
        )
            .andExpect(status().isForbidden)
    }

    // ============= API Key Validation Tests =============

    @Test
    fun `test expired API key is rejected`() {
        // Create user with all permissions
        val user = userHelper.createUser(permissions = listOf("*"))
        
        // Create an API key with 0 days expiration (expires immediately)
        val apiKey = createApiKey(user, "Expired Key", expiresInDays = 0)

        // Try to use the expired API key
        mockMvc.perform(
            get("/connections/")
                .header("Authorization", "Bearer $apiKey")
        )
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `test invalid API key is rejected`() {
        mockMvc.perform(
            get("/connections/")
                .header("Authorization", "Bearer invalid-api-key-12345")
        )
            .andExpect(status().isUnauthorized)
    }


    // ============= Permission Inheritance Tests =============

    @Test
    fun `test API key inherits user wildcard permissions`() {
        // Create user with wildcard permissions
        val user = userHelper.createUser(permissions = listOf("*"))
        val apiKey = createApiKey(user)

        // Should be able to perform any action
        val connectionRequest = """
            {
                "connectionType": "DATASOURCE",
                "id": "test-conn-wildcard",
                "displayName": "Wildcard Test",
                "username": "testuser",
                "password": "testpass",
                "reviewConfig": {
                    "numTotalRequired": 1
                },
                "type": "POSTGRESQL",
                "hostname": "localhost",
                "port": 5432,
                "databaseName": "testdb"
            }
        """.trimIndent()

        mockMvc.perform(
            post("/connections/")
                .header("Authorization", "Bearer $apiKey")
                .contentType(MediaType.APPLICATION_JSON)
                .content(connectionRequest)
        )
            .andExpect(status().isOk)

        // Should also be able to list connections
        mockMvc.perform(
            get("/connections/")
                .header("Authorization", "Bearer $apiKey")
        )
            .andExpect(status().isOk)

        // Should be able to delete the connection
        mockMvc.perform(
            delete("/connections/test-conn-wildcard")
                .header("Authorization", "Bearer $apiKey")
        )
            .andExpect(status().isNoContent)
    }

    @Test
    fun `test API key respects permission boundaries`() {
        // Create user with limited permissions (only read, no create/edit/delete)
        // They also have api_key permissions to create the key
        val user = userHelper.createUser(permissions = listOf("datasource_connection:get", "api_key:create", "api_key:get"))
        val apiKey = createApiKey(user)

        // Should be able to list connections
        mockMvc.perform(
            get("/connections/")
                .header("Authorization", "Bearer $apiKey")
        )
            .andExpect(status().isOk)

        // Should NOT be able to create a connection
        val connectionRequest = """
            {
                "connectionType": "DATASOURCE",
                "id": "test-conn-forbidden",
                "displayName": "Should Fail",
                "username": "testuser",
                "password": "testpass",
                "reviewConfig": {
                    "numTotalRequired": 1
                },
                "type": "POSTGRESQL",
                "hostname": "localhost",
                "port": 5432,
                "databaseName": "testdb"
            }
        """.trimIndent()

        mockMvc.perform(
            post("/connections/")
                .header("Authorization", "Bearer $apiKey")
                .contentType(MediaType.APPLICATION_JSON)
                .content(connectionRequest)
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `test API key lastUsedAt timestamp gets updated`() {
        // Create user with all permissions
        val user = userHelper.createUser(permissions = listOf("*"))
        val apiKey = createApiKey(user)

        // Get the API key ID
        val cookie = userHelper.login(mockMvc = mockMvc, email = user.email)
        val apiKeysResponse = mockMvc.perform(
            get("/api-keys/")
                .cookie(cookie)
        )
            .andExpect(status().isOk)
            .andReturn()

        val apiKeysJson = objectMapper.readTree(apiKeysResponse.response.contentAsString)
        val apiKeys = apiKeysJson["apiKeys"]
        val apiKeyId = apiKeys[0]["id"].asText()

        // Check initial lastUsedAt (should be null)
        val initialApiKey = mockMvc.perform(
            get("/api-keys/$apiKeyId")
                .cookie(cookie)
        )
            .andExpect(status().isOk)
            .andReturn()

        val initialLastUsed = objectMapper.readTree(initialApiKey.response.contentAsString)["lastUsedAt"]
        assert(initialLastUsed.isNull)

        // Use the API key
        mockMvc.perform(
            get("/connections/")
                .header("Authorization", "Bearer $apiKey")
        )
            .andExpect(status().isOk)

        // Check that lastUsedAt was updated
        val updatedApiKey = mockMvc.perform(
            get("/api-keys/$apiKeyId")
                .cookie(cookie)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.lastUsedAt", notNullValue()))
            .andReturn()
    }

    @Test
    fun `test API key authentication does not create session`() {
        // Create user with all permissions
        val user = userHelper.createUser(permissions = listOf("*"))
        val apiKey = createApiKey(user)

        // Make a request with API key and verify no session cookie is returned
        val result = mockMvc.perform(
            get("/connections/")
                .header("Authorization", "Bearer $apiKey")
        )
            .andExpect(status().isOk)
            .andReturn()

        // Check that no SESSION cookie is set in the response
        val cookies = result.response.cookies
        assert(cookies.isEmpty() || !cookies.any { it.name == "SESSION" }) {
            "Expected no SESSION cookie, but found: ${cookies.map { "${it.name}=${it.value}" }}"
        }

        // Make another request with the same API key to verify it still works
        // (not relying on session)
        mockMvc.perform(
            get("/connections/")
                .header("Authorization", "Bearer $apiKey")
        )
            .andExpect(status().isOk)
            .andReturn()
            .also {
                val secondRequestCookies = it.response.cookies
                assert(secondRequestCookies.isEmpty() || !secondRequestCookies.any { cookie -> cookie.name == "SESSION" }) {
                    "Expected no SESSION cookie on second request, but found: ${secondRequestCookies.map { cookie -> "${cookie.name}=${cookie.value}" }}"
                }
            }
    }
}