package dev.kviklet.kviklet

import dev.kviklet.kviklet.db.ConnectionAdapter
import dev.kviklet.kviklet.db.User
import dev.kviklet.kviklet.helper.ConnectionHelper
import dev.kviklet.kviklet.helper.ExecutionRequestHelper
import dev.kviklet.kviklet.helper.RoleHelper
import dev.kviklet.kviklet.helper.UserHelper
import dev.kviklet.kviklet.service.dto.AuthenticationType
import dev.kviklet.kviklet.service.dto.Connection
import dev.kviklet.kviklet.service.dto.ConnectionId
import dev.kviklet.kviklet.service.dto.DatabaseProtocol
import dev.kviklet.kviklet.service.dto.DatasourceType
import dev.kviklet.kviklet.service.dto.ReviewConfig
import jakarta.servlet.http.Cookie
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.io.FileUrlResource
import org.springframework.jdbc.datasource.init.ScriptUtils
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.containers.JdbcDatabaseContainer
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DryRunIntegrationTest {

    @Autowired private lateinit var mockMvc: MockMvc

    @Autowired private lateinit var userHelper: UserHelper

    @Autowired private lateinit var roleHelper: RoleHelper

    @Autowired private lateinit var connectionHelper: ConnectionHelper

    @Autowired private lateinit var executionRequestHelper: ExecutionRequestHelper

    @Autowired private lateinit var connectionAdapter: ConnectionAdapter

    private lateinit var testUser: User
    private lateinit var testReviewer: User
    private var connectionCount = 1

    companion object {
        val postgresDb: PostgreSQLContainer<*> = PostgreSQLContainer(DockerImageName.parse("postgres:11.1"))
            .withUsername("root")
            .withPassword("root")
            .withReuse(true)
            .withDatabaseName("test_db")

        val mysqlDb: MySQLContainer<*> = MySQLContainer(DockerImageName.parse("mysql:8.2"))
            .withUsername("root")
            .withPassword("")
            .withReuse(true)
            .withDatabaseName("")

        init {
            postgresDb.start()
            mysqlDb.start()
        }
    }

    @BeforeEach
    fun setup() {
        val initScript = this::class.java.classLoader.getResource("psql_init.sql")!!
        ScriptUtils.executeSqlScript(postgresDb.createConnection(""), FileUrlResource(initScript))

        testUser = userHelper.createUser(permissions = listOf("*"))
        testReviewer = userHelper.createUser(permissions = listOf("*"))
    }

    @AfterEach
    fun tearDown() {
        executionRequestHelper.deleteAll()
        connectionHelper.deleteAll()
        userHelper.deleteAll()
        roleHelper.deleteAll()
        connectionCount = 1
    }

    @Nested
    inner class HappyPathTests {

        @Test
        fun `testDryRunWithApprovalRequired_Approved_Succeeds`() {
            // Create PostgreSQL connection with dryRunEnabled=true, dryRunRequiresApproval=true
            val connection = createPostgresConnection(
                container = postgresDb,
                explainEnabled = false,
                storeResults = false,
                maxExecutions = 1,
                dryRunEnabled = true,
                dryRunRequiresApproval = true,
            )

            // Create execution request with SELECT 1 statement
            val executionRequest = executionRequestHelper.createExecutionRequest(
                dbcontainer = postgresDb,
                author = testUser,
                statement = "SELECT 1",
                connection = connection,
            )

            // Approve the request
            val reviewerCookie = userHelper.login(email = testReviewer.email, mockMvc = mockMvc)
            approveRequest(executionRequest.getId(), "Approved for dry run", reviewerCookie)

            // Execute with dryRun=true
            val userCookie = userHelper.login(email = testUser.email, mockMvc = mockMvc)
            mockMvc.perform(
                post("/execution-requests/${executionRequest.getId()}/execute")
                    .cookie(userCookie)
                    .content("""{"dryRun": true}""")
                    .contentType("application/json"),
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.results[0].columns[0].label").value("?column?"))
                .andExpect(jsonPath("$.results[0].data[0]['?column?']").value("1"))

            // Verify the event was created with isDryRun=true
            mockMvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                    "/execution-requests/${executionRequest.getId()}",
                ).cookie(userCookie),
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.events[1].type").value("EXECUTE"))
                .andExpect(jsonPath("$.events[1].isDryRun").value(true))
        }

        @Test
        fun `testDryRunWithoutApprovalRequired_NotApproved_Succeeds`() {
            // Create connection with dryRunEnabled=true, dryRunRequiresApproval=false
            val connection = createPostgresConnection(
                container = postgresDb,
                explainEnabled = false,
                storeResults = false,
                maxExecutions = 1,
                dryRunEnabled = true,
                dryRunRequiresApproval = false,
            )

            // Create execution request (NOT approved)
            val executionRequest = executionRequestHelper.createExecutionRequest(
                dbcontainer = postgresDb,
                author = testUser,
                statement = "SELECT 1",
                connection = connection,
            )

            // Execute with dryRun=true (should succeed even without approval)
            val userCookie = userHelper.login(email = testUser.email, mockMvc = mockMvc)
            mockMvc.perform(
                post("/execution-requests/${executionRequest.getId()}/execute")
                    .cookie(userCookie)
                    .content("""{"dryRun": true}""")
                    .contentType("application/json"),
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.results[0].columns[0].label").value("?column?"))
                .andExpect(jsonPath("$.results[0].data[0]['?column?']").value("1"))

            // Verify the event was created with isDryRun=true
            mockMvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                    "/execution-requests/${executionRequest.getId()}",
                ).cookie(userCookie),
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.events[0].type").value("EXECUTE"))
                .andExpect(jsonPath("$.events[0].isDryRun").value(true))
        }

        @Test
        fun `testDryRunDoesNotCountTowardExecutionLimit`() {
            // Create connection with maxExecutions=1, dryRunEnabled=true
            val connection = createPostgresConnection(
                container = postgresDb,
                explainEnabled = false,
                storeResults = false,
                maxExecutions = 1,
                dryRunEnabled = true,
                dryRunRequiresApproval = true,
            )

            // Create and approve execution request
            val executionRequest = executionRequestHelper.createApprovedRequest(
                dbcontainer = postgresDb,
                author = testUser,
                approver = testReviewer,
                sql = "SELECT 1",
                connection = connection,
            )

            val userCookie = userHelper.login(email = testUser.email, mockMvc = mockMvc)

            // Execute with dryRun=true → succeeds
            mockMvc.perform(
                post("/execution-requests/${executionRequest.getId()}/execute")
                    .cookie(userCookie)
                    .content("""{"dryRun": true}""")
                    .contentType("application/json"),
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.results[0].data[0]['?column?']").value("1"))

            // Verify execution status is still EXECUTABLE
            mockMvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                    "/execution-requests/${executionRequest.getId()}",
                ).cookie(userCookie),
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.executionStatus").value("EXECUTABLE"))

            // Execute with dryRun=false (real execute) → succeeds
            mockMvc.perform(
                post("/execution-requests/${executionRequest.getId()}/execute")
                    .cookie(userCookie)
                    .contentType("application/json"),
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.results[0].data[0]['?column?']").value("1"))

            // Verify execution status is now EXECUTED
            mockMvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                    "/execution-requests/${executionRequest.getId()}",
                ).cookie(userCookie),
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.executionStatus").value("EXECUTED"))
        }
    }

    @Nested
    inner class EdgeCaseAndErrorTests {

        @Test
        fun `testDryRunWithDryRunDisabled_Fails`() {
            // Create connection with dryRunEnabled=false
            val connection = createPostgresConnection(
                container = postgresDb,
                explainEnabled = false,
                storeResults = false,
                maxExecutions = 1,
                dryRunEnabled = false,
                dryRunRequiresApproval = true,
            )

            // Create and approve execution request
            val executionRequest = executionRequestHelper.createApprovedRequest(
                dbcontainer = postgresDb,
                author = testUser,
                approver = testReviewer,
                sql = "SELECT 1",
                connection = connection,
            )

            // Execute with dryRun=true
            val userCookie = userHelper.login(email = testUser.email, mockMvc = mockMvc)
            mockMvc.perform(
                post("/execution-requests/${executionRequest.getId()}/execute")
                    .cookie(userCookie)
                    .content("""{"dryRun": true}""")
                    .contentType("application/json"),
            )
                .andExpect(status().isBadRequest)
        }

        @Test
        fun `testDryRunWithApprovalRequired_NotApproved_Fails`() {
            // Create connection with dryRunEnabled=true, dryRunRequiresApproval=true
            val connection = createPostgresConnection(
                container = postgresDb,
                explainEnabled = false,
                storeResults = false,
                maxExecutions = 1,
                dryRunEnabled = true,
                dryRunRequiresApproval = true,
            )

            // Create execution request (NOT approved)
            val executionRequest = executionRequestHelper.createExecutionRequest(
                dbcontainer = postgresDb,
                author = testUser,
                statement = "SELECT 1",
                connection = connection,
            )

            // Execute with dryRun=true
            val userCookie = userHelper.login(email = testUser.email, mockMvc = mockMvc)
            mockMvc.perform(
                post("/execution-requests/${executionRequest.getId()}/execute")
                    .cookie(userCookie)
                    .content("""{"dryRun": true}""")
                    .contentType("application/json"),
            )
                .andExpect(status().isBadRequest)
        }

        @Test
        fun `testDryRunOnTemporaryAccessRequest_Fails`() {
            // Create connection with dryRunEnabled=true, temporaryAccessEnabled=true
            val connection = createPostgresConnection(
                container = postgresDb,
                explainEnabled = false,
                storeResults = false,
                maxExecutions = 1,
                dryRunEnabled = true,
                dryRunRequiresApproval = true,
            )

            // Create TemporaryAccess execution request
            val executionRequest = executionRequestHelper.createExecutionRequest(
                dbcontainer = postgresDb,
                author = testUser,
                statement = "SELECT 1",
                connection = connection,
                requestType = dev.kviklet.kviklet.service.dto.RequestType.TemporaryAccess,
            )

            // Approve the request
            val reviewerCookie = userHelper.login(email = testReviewer.email, mockMvc = mockMvc)
            approveRequest(executionRequest.getId(), "Approved", reviewerCookie)

            // Execute with dryRun=true
            val userCookie = userHelper.login(email = testUser.email, mockMvc = mockMvc)
            mockMvc.perform(
                post("/execution-requests/${executionRequest.getId()}/execute")
                    .cookie(userCookie)
                    .content("""{"dryRun": true}""")
                    .contentType("application/json"),
            )
                .andExpect(status().isBadRequest)
        }

        @Test
        fun `testDryRunWithCommitInSql_Fails`() {
            // Create connection with dryRunEnabled=true
            val connection = createPostgresConnection(
                container = postgresDb,
                explainEnabled = false,
                storeResults = false,
                maxExecutions = 1,
                dryRunEnabled = true,
                dryRunRequiresApproval = true,
            )

            // Create execution request with SQL containing COMMIT
            val executionRequest = executionRequestHelper.createExecutionRequest(
                dbcontainer = postgresDb,
                author = testUser,
                statement = "SELECT 1; COMMIT;",
                connection = connection,
            )

            // Approve the request
            val reviewerCookie = userHelper.login(email = testReviewer.email, mockMvc = mockMvc)
            approveRequest(executionRequest.getId(), "Approved", reviewerCookie)

            // Execute with dryRun=true
            val userCookie = userHelper.login(email = testUser.email, mockMvc = mockMvc)
            mockMvc.perform(
                post("/execution-requests/${executionRequest.getId()}/execute")
                    .cookie(userCookie)
                    .content("""{"dryRun": true}""")
                    .contentType("application/json"),
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.message", containsString("COMMIT")))
        }

        @Test
        fun `testDryRunWithDdlOnMysql_Fails`() {
            // Initialize MySQL database
            val initScript = this::class.java.classLoader.getResource("mysql_init.sql")!!
            ScriptUtils.executeSqlScript(mysqlDb.createConnection(""), FileUrlResource(initScript))

            // Create MySQL connection with dryRunEnabled=true
            val connection = createMysqlConnection(
                container = mysqlDb,
                dryRunEnabled = true,
                dryRunRequiresApproval = true,
            )

            // Create execution request with DDL SQL (CREATE TABLE)
            val executionRequest = executionRequestHelper.createExecutionRequest(
                author = testUser,
                statement = "CREATE TABLE test_table (id INT PRIMARY KEY, name VARCHAR(255))",
                connection = connection,
            )

            // Approve the request
            val reviewerCookie = userHelper.login(email = testReviewer.email, mockMvc = mockMvc)
            approveRequest(executionRequest.getId(), "Approved", reviewerCookie)

            // Execute with dryRun=true
            val userCookie = userHelper.login(email = testUser.email, mockMvc = mockMvc)
            mockMvc.perform(
                post("/execution-requests/${executionRequest.getId()}/execute")
                    .cookie(userCookie)
                    .content("""{"dryRun": true}""")
                    .contentType("application/json"),
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.message", containsString("Only SELECT, INSERT, UPDATE, and DELETE")))
        }
    }

    @Nested
    inner class ConnectionValidationTests {

        @Test
        fun `testCreateMongoConnectionWithDryRunEnabled_Fails`() {
            val userCookie = userHelper.login(email = testUser.email, mockMvc = mockMvc)

            // Attempt to POST create MongoDB connection with dryRunEnabled=true
            mockMvc.perform(
                post("/connections/")
                    .cookie(userCookie)
                    .content(
                        """
                        {
                            "connectionType": "DATASOURCE",
                            "id": "mongo-dry-run-test",
                            "displayName": "MongoDB with Dry Run",
                            "username": "root",
                            "password": "root",
                            "type": "MONGODB",
                            "protocol": "MONGODB",
                            "hostname": "localhost",
                            "port": 27017,
                            "dryRunEnabled": true,
                            "reviewConfig": {
                                "numTotalRequired": 1
                            }
                        }
                        """.trimIndent(),
                    )
                    .contentType("application/json"),
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.message", containsString("MongoDB")))
        }

        @Test
        fun `testUpdateMongoConnectionWithDryRunEnabled_Fails`() {
            val userCookie = userHelper.login(email = testUser.email, mockMvc = mockMvc)

            // Create MongoDB connection with dryRunEnabled=false (should succeed)
            mockMvc.perform(
                post("/connections/")
                    .cookie(userCookie)
                    .content(
                        """
                        {
                            "connectionType": "DATASOURCE",
                            "id": "mongo-update-dry-run-test",
                            "displayName": "MongoDB for Update Test",
                            "username": "root",
                            "password": "root",
                            "type": "MONGODB",
                            "protocol": "MONGODB",
                            "hostname": "localhost",
                            "port": 27017,
                            "dryRunEnabled": false,
                            "reviewConfig": {
                                "numTotalRequired": 1
                            }
                        }
                        """.trimIndent(),
                    )
                    .contentType("application/json"),
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.dryRunEnabled").value(false))

            // Attempt to PATCH update MongoDB connection with dryRunEnabled=true (should fail)
            mockMvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(
                    "/connections/mongo-update-dry-run-test",
                )
                    .cookie(userCookie)
                    .content(
                        """
                        {
                            "connectionType": "DATASOURCE",
                            "dryRunEnabled": true
                        }
                        """.trimIndent(),
                    )
                    .contentType("application/json"),
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.message", containsString("MongoDB")))
        }
    }

    // Helper functions
    private fun approveRequest(executionRequestId: String, comment: String, cookie: Cookie) {
        mockMvc.perform(
            post("/execution-requests/$executionRequestId/reviews")
                .cookie(cookie)
                .content(
                    """
                    {
                        "comment": "$comment",
                        "action": "APPROVE"
                    }
                    """.trimIndent(),
                )
                .contentType("application/json"),
        )
            .andExpect(status().isOk)
    }

    private fun createPostgresConnection(
        container: JdbcDatabaseContainer<*>,
        explainEnabled: Boolean,
        storeResults: Boolean,
        maxExecutions: Int,
        dryRunEnabled: Boolean,
        dryRunRequiresApproval: Boolean,
    ): Connection {
        val connection = connectionAdapter.createDatasourceConnection(
            ConnectionId("ds-conn-test-$connectionCount"),
            "Test Connection $connectionCount",
            AuthenticationType.USER_PASSWORD,
            container.databaseName,
            maxExecutions,
            container.username,
            container.password,
            "A test connection",
            ReviewConfig(
                numTotalRequired = 1,
            ),
            container.getMappedPort(5432),
            container.host,
            DatasourceType.POSTGRESQL,
            DatabaseProtocol.POSTGRESQL,
            additionalJDBCOptions = "",
            dumpsEnabled = false,
            temporaryAccessEnabled = true,
            explainEnabled = explainEnabled,
            storeResults = storeResults,
            dryRunEnabled = dryRunEnabled,
            dryRunRequiresApproval = dryRunRequiresApproval,
        )
        connectionCount++
        return connection
    }

    private fun createMysqlConnection(
        container: JdbcDatabaseContainer<*>,
        dryRunEnabled: Boolean,
        dryRunRequiresApproval: Boolean,
    ): Connection {
        val connection = connectionAdapter.createDatasourceConnection(
            ConnectionId("ds-conn-test-$connectionCount"),
            "Test Connection $connectionCount",
            AuthenticationType.USER_PASSWORD,
            container.databaseName,
            1,
            container.username,
            container.password,
            "A test connection",
            ReviewConfig(
                numTotalRequired = 1,
            ),
            container.getMappedPort(3306),
            container.host,
            DatasourceType.MYSQL,
            DatabaseProtocol.MYSQL,
            additionalJDBCOptions = "",
            dumpsEnabled = false,
            temporaryAccessEnabled = true,
            explainEnabled = false,
            storeResults = false,
            dryRunEnabled = dryRunEnabled,
            dryRunRequiresApproval = dryRunRequiresApproval,
        )
        connectionCount++
        return connection
    }
}
