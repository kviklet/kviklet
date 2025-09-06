package dev.kviklet.kviklet

import dev.kviklet.kviklet.db.ErrorResultLogPayload
import dev.kviklet.kviklet.db.ExecutePayload
import dev.kviklet.kviklet.db.ExecutionRequestAdapter
import dev.kviklet.kviklet.db.LicenseAdapter
import dev.kviklet.kviklet.db.QueryResultLogPayload
import dev.kviklet.kviklet.db.UpdateResultLogPayload
import dev.kviklet.kviklet.helper.ConnectionHelper
import dev.kviklet.kviklet.helper.ExecutionRequestHelper
import dev.kviklet.kviklet.helper.RoleHelper
import dev.kviklet.kviklet.helper.UserHelper
import dev.kviklet.kviklet.service.dto.ExecutionRequestId
import dev.kviklet.kviklet.service.dto.LicenseFile
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.`is`
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDateTime

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ExecutionsControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var userHelper: UserHelper

    @Autowired
    private lateinit var roleHelper: RoleHelper

    @Autowired
    private lateinit var connectionHelper: ConnectionHelper

    @Autowired
    private lateinit var executionRequestHelper: ExecutionRequestHelper

    @Autowired
    private lateinit var licenseAdapter: LicenseAdapter

    @Autowired
    private lateinit var executionRequestAdapter: ExecutionRequestAdapter

    @BeforeEach
    fun setUp() {
        // Setup test license for enterprise features (same as used in ApiKeyControllerTest)
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
        executionRequestHelper.deleteAll()
        connectionHelper.deleteAll()
        userHelper.deleteAll()
        roleHelper.deleteAll()
        licenseAdapter.deleteAll()
    }

    @Test
    fun `test get executions list`() {
        // Create test data
        val user = userHelper.createUser(permissions = listOf("execution_request:get"))
        val cookie = userHelper.login(mockMvc = mockMvc, email = user.email)

        val connection = connectionHelper.createDummyConnection()
        val executionRequest = executionRequestHelper.createExecutionRequest(
            author = user,
            connection = connection,
            statement = "SELECT * FROM users",
        )

        // Add an execution event to the request
        executionRequestAdapter.addEvent(
            ExecutionRequestId(executionRequest.getId()),
            user.getId()!!,
            ExecutePayload(
                query = "SELECT * FROM users",
                results = listOf(
                    QueryResultLogPayload(columnCount = 5, rowCount = 10),
                ),
            ),
        )

        // Test getting executions list
        mockMvc.perform(
            get("/executions/")
                .cookie(cookie)
                .contentType(MediaType.APPLICATION_JSON),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.executions[0].name", `is`(user.fullName ?: "")))
            .andExpect(jsonPath("$.executions[0].statement", `is`("SELECT * FROM users")))
            .andExpect(jsonPath("$.executions[0].connectionId", `is`(connection.getId())))
    }

    @Test
    fun `test export executions with valid license`() {
        // Create test data
        val user = userHelper.createUser(
            permissions = listOf("execution_request:get"),
            fullName = "Test User",
        )
        val cookie = userHelper.login(mockMvc = mockMvc, email = user.email)

        val connection = connectionHelper.createDummyConnection()
        val executionRequest1 = executionRequestHelper.createExecutionRequest(
            author = user,
            connection = connection,
            statement = "SELECT COUNT(*) FROM orders",
        )
        val executionRequest2 = executionRequestHelper.createExecutionRequest(
            author = user,
            connection = connection,
            statement = "UPDATE users SET active = true WHERE id = 1",
        )

        // Add execution events
        executionRequestAdapter.addEvent(
            ExecutionRequestId(executionRequest1.getId()),
            user.getId()!!,
            ExecutePayload(
                query = "SELECT COUNT(*) FROM orders",
                results = listOf(
                    QueryResultLogPayload(columnCount = 1, rowCount = 1),
                ),
            ),
        )

        executionRequestAdapter.addEvent(
            ExecutionRequestId(executionRequest2.getId()),
            user.getId()!!,
            ExecutePayload(
                query = "UPDATE users SET active = true WHERE id = 1",
                results = listOf(
                    UpdateResultLogPayload(rowsUpdated = 1),
                ),
            ),
        )

        // Test export endpoint
        mockMvc.perform(
            get("/executions/export")
                .cookie(cookie),
        )
            .andExpect(status().isOk)
            .andExpect(header().string("Content-Type", "text/plain"))
            .andExpect(
                header().string("Content-Disposition", containsString("attachment; filename=\"auditlog-export-")),
            )
            .andExpect(header().string("Content-Disposition", containsString(".txt\"")))
            .andExpect(content().string(containsString("=== KVIKLET AUDIT LOG EXPORT ===")))
            .andExpect(content().string(containsString("Total Executions: 2")))
            .andExpect(content().string(containsString("User: Test User")))
            .andExpect(content().string(containsString("Connection: ${connection.getId()}")))
            .andExpect(content().string(containsString("Statement: SELECT COUNT(*) FROM orders")))
            .andExpect(content().string(containsString("Statement: UPDATE users SET active = true WHERE id = 1")))
            .andExpect(content().string(containsString("SUCCESS: 1 rows returned (1 columns)")))
            .andExpect(content().string(containsString("SUCCESS: 1 rows updated")))
    }

    @Test
    fun `test export executions without license returns payment required`() {
        // Delete the license to test without enterprise features
        licenseAdapter.deleteAll()

        val user = userHelper.createUser(permissions = listOf("execution_request:get"))
        val cookie = userHelper.login(mockMvc = mockMvc, email = user.email)

        // Test export endpoint without license (returns 402 Payment Required)
        mockMvc.perform(
            get("/executions/export")
                .cookie(cookie),
        )
            .andExpect(status().isPaymentRequired)
    }

    @Test
    fun `test export executions without permission returns forbidden`() {
        // Remove default role permissions first
        roleHelper.removeDefaultRolePermissions()

        // Create user without execution_request:get permission
        val user = userHelper.createUser(permissions = listOf())
        val cookie = userHelper.login(mockMvc = mockMvc, email = user.email)

        // Test export endpoint without permission
        mockMvc.perform(
            get("/executions/export")
                .cookie(cookie),
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `test export with execution results`() {
        // Create test data with different result types
        val user = userHelper.createUser(
            permissions = listOf("execution_request:get", "execution_request:execute"),
            fullName = "Admin User",
        )
        val cookie = userHelper.login(mockMvc = mockMvc, email = user.email)

        val connection = connectionHelper.createDummyConnection()

        // Create request with query result
        val queryRequest = executionRequestHelper.createExecutionRequest(
            author = user,
            connection = connection,
            statement = "SELECT * FROM products",
        )
        executionRequestAdapter.addEvent(
            ExecutionRequestId(queryRequest.getId()),
            user.getId()!!,
            ExecutePayload(
                query = "SELECT * FROM products",
                results = listOf(
                    QueryResultLogPayload(columnCount = 5, rowCount = 100),
                ),
            ),
        )

        // Create request with update result
        val updateRequest = executionRequestHelper.createExecutionRequest(
            author = user,
            connection = connection,
            statement = "UPDATE products SET price = price * 1.1",
        )
        executionRequestAdapter.addEvent(
            ExecutionRequestId(updateRequest.getId()),
            user.getId()!!,
            ExecutePayload(
                query = "UPDATE products SET price = price * 1.1",
                results = listOf(
                    UpdateResultLogPayload(rowsUpdated = 50),
                ),
            ),
        )

        // Create request with error result
        val errorRequest = executionRequestHelper.createExecutionRequest(
            author = user,
            connection = connection,
            statement = "SELECT * FROM non_existent_table",
        )
        executionRequestAdapter.addEvent(
            ExecutionRequestId(errorRequest.getId()),
            user.getId()!!,
            ExecutePayload(
                query = "SELECT * FROM non_existent_table",
                results = listOf(
                    ErrorResultLogPayload(errorCode = 42, message = "Table 'non_existent_table' does not exist"),
                ),
            ),
        )

        // Test export endpoint includes all result types
        mockMvc.perform(
            get("/executions/export")
                .cookie(cookie),
        )
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("SUCCESS: 100 rows returned (5 columns)")))
            .andExpect(content().string(containsString("SUCCESS: 50 rows updated")))
            .andExpect(content().string(containsString("ERROR (Code 42): Table 'non_existent_table' does not exist")))
    }

    @Test
    fun `test export empty executions`() {
        val user = userHelper.createUser(permissions = listOf("execution_request:get"))
        val cookie = userHelper.login(mockMvc = mockMvc, email = user.email)

        // Test export with no executions
        mockMvc.perform(
            get("/executions/export")
                .cookie(cookie),
        )
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("=== KVIKLET AUDIT LOG EXPORT ===")))
            .andExpect(content().string(containsString("Total Executions: 0")))
    }
}
