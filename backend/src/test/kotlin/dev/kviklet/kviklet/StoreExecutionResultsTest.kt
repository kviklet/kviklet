package dev.kviklet.kviklet

import dev.kviklet.kviklet.db.User
import dev.kviklet.kviklet.helper.ConnectionHelper
import dev.kviklet.kviklet.helper.ExecutionRequestHelper
import dev.kviklet.kviklet.helper.RoleHelper
import dev.kviklet.kviklet.helper.UserHelper
import dev.kviklet.kviklet.service.dto.Connection
import jakarta.servlet.http.Cookie
import org.hamcrest.Matchers.hasSize
import org.hamcrest.Matchers.nullValue
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StoreExecutionResultsTest {

    @Autowired private lateinit var mockMvc: MockMvc

    @Autowired private lateinit var userHelper: UserHelper

    @Autowired private lateinit var roleHelper: RoleHelper

    @Autowired private lateinit var connectionHelper: ConnectionHelper

    @Autowired private lateinit var executionRequestHelper: ExecutionRequestHelper

    private lateinit var testUser: User
    private lateinit var testReviewer: User

    companion object {
        val db: PostgreSQLContainer<*> = PostgreSQLContainer(DockerImageName.parse("postgres:11.1"))
            .withUsername("root")
            .withPassword("root")
            .withReuse(true)
            .withDatabaseName("test_db")

        init {
            db.start()
        }
    }

    @BeforeEach
    fun setup() {
        val initScript = this::class.java.classLoader.getResource("psql_init.sql")!!
        ScriptUtils.executeSqlScript(db.createConnection(""), FileUrlResource(initScript))

        testUser = userHelper.createUser(permissions = listOf("*"))
        testReviewer = userHelper.createUser(permissions = listOf("*"))
    }

    @AfterEach
    fun tearDown() {
        executionRequestHelper.deleteAll()
        connectionHelper.deleteAll()
        userHelper.deleteAll()
        roleHelper.deleteAll()
    }

    @Nested
    inner class ConnectionConfigurationTests {

        @Test
        fun `create datasource connection with storeResults=true`() {
            val cookie = userHelper.login(email = testUser.email, mockMvc = mockMvc)

            mockMvc.perform(
                post("/connections/")
                    .cookie(cookie)
                    .content(
                        """
                        {
                            "id": "ds-store-results-true",
                            "displayName": "Test Connection with Store Results",
                            "databaseName": "${db.databaseName}",
                            "username": "${db.username}",
                            "password": "${db.password}",
                            "description": "A test connection with storeResults enabled",
                            "reviewConfig": {"numTotalRequired": 1},
                            "port": ${db.getMappedPort(5432)},
                            "hostname": "${db.host}",
                            "type": "POSTGRESQL",
                            "connectionType": "DATASOURCE",
                            "storeResults": true
                        }
                        """.trimIndent(),
                    )
                    .contentType("application/json"),
            ).andExpect(status().isOk)
                .andExpect(jsonPath("$.storeResults").value(true))

            // Verify it persists
            mockMvc.perform(
                get("/connections/ds-store-results-true").cookie(cookie),
            ).andExpect(status().isOk)
                .andExpect(jsonPath("$.storeResults").value(true))
        }

        @Test
        fun `create datasource connection defaults storeResults to false`() {
            val cookie = userHelper.login(email = testUser.email, mockMvc = mockMvc)

            mockMvc.perform(
                post("/connections/")
                    .cookie(cookie)
                    .content(
                        """
                        {
                            "id": "ds-store-results-default",
                            "displayName": "Test Connection Default Store Results",
                            "databaseName": "${db.databaseName}",
                            "username": "${db.username}",
                            "password": "${db.password}",
                            "description": "A test connection with default storeResults",
                            "reviewConfig": {"numTotalRequired": 1},
                            "port": ${db.getMappedPort(5432)},
                            "hostname": "${db.host}",
                            "type": "POSTGRESQL",
                            "connectionType": "DATASOURCE"
                        }
                        """.trimIndent(),
                    )
                    .contentType("application/json"),
            ).andExpect(status().isOk)
                .andExpect(jsonPath("$.storeResults").value(false))
        }

        @Test
        fun `update connection to enable storeResults`() {
            val cookie = userHelper.login(email = testUser.email, mockMvc = mockMvc)
            val connection = connectionHelper.createPostgresConnection(db, storeResults = false)

            // Verify initially false
            mockMvc.perform(
                get("/connections/${connection.id}").cookie(cookie),
            ).andExpect(status().isOk)
                .andExpect(jsonPath("$.storeResults").value(false))

            // Update to enable
            mockMvc.perform(
                patch("/connections/${connection.id}")
                    .cookie(cookie)
                    .content("""{"connectionType": "DATASOURCE", "storeResults": true}""")
                    .contentType("application/json"),
            ).andExpect(status().isOk)
                .andExpect(jsonPath("$.storeResults").value(true))

            // Verify it persists
            mockMvc.perform(
                get("/connections/${connection.id}").cookie(cookie),
            ).andExpect(status().isOk)
                .andExpect(jsonPath("$.storeResults").value(true))
        }

        @Test
        fun `update connection to disable storeResults`() {
            val cookie = userHelper.login(email = testUser.email, mockMvc = mockMvc)
            val connection = connectionHelper.createPostgresConnection(db, storeResults = true)

            // Verify initially true
            mockMvc.perform(
                get("/connections/${connection.id}").cookie(cookie),
            ).andExpect(status().isOk)
                .andExpect(jsonPath("$.storeResults").value(true))

            // Update to disable
            mockMvc.perform(
                patch("/connections/${connection.id}")
                    .cookie(cookie)
                    .content("""{"connectionType": "DATASOURCE", "storeResults": false}""")
                    .contentType("application/json"),
            ).andExpect(status().isOk)
                .andExpect(jsonPath("$.storeResults").value(false))

            // Verify it persists
            mockMvc.perform(
                get("/connections/${connection.id}").cookie(cookie),
            ).andExpect(status().isOk)
                .andExpect(jsonPath("$.storeResults").value(false))
        }
    }

    @Nested
    inner class DatasourceExecutionResultStorageTests {

        @Test
        fun `execute query on connection with storeResults=true stores results`() {
            val connectionWithStorage = connectionHelper.createPostgresConnection(db, storeResults = true)
            val executionRequest = executionRequestHelper.createApprovedRequest(
                author = testUser,
                approver = testReviewer,
                connection = connectionWithStorage,
                sql = "SELECT * FROM foo.simple_table",
            )

            val userCookie = userHelper.login(email = testUser.email, mockMvc = mockMvc)

            // Execute the query
            mockMvc.perform(
                post("/execution-requests/${executionRequest.getId()}/execute")
                    .cookie(userCookie)
                    .contentType("application/json"),
            ).andExpect(status().isOk)

            // Verify stored results in execution request details
            mockMvc.perform(
                get("/execution-requests/${executionRequest.getId()}").cookie(userCookie),
            ).andExpect(status().isOk)
                .andExpect(jsonPath("$.events[-1].type").value("EXECUTE"))
                .andExpect(jsonPath("$.events[-1].results[0].type").value("QUERY"))
                .andExpect(jsonPath("$.events[-1].results[0].storedRows").isArray)
                .andExpect(jsonPath("$.events[-1].results[0].storedRowCount").isNumber)
                .andExpect(jsonPath("$.events[-1].results[0].columns").isArray)
                .andExpect(jsonPath("$.events[-1].results[0].rowCount").value(2))
                .andExpect(jsonPath("$.events[-1].results[0].storedRowCount").value(2))
        }

        @Test
        fun `execute query on connection with storeResults=false does not store results`() {
            val connectionWithoutStorage = connectionHelper.createPostgresConnection(db, storeResults = false)
            val executionRequest = executionRequestHelper.createApprovedRequest(
                author = testUser,
                approver = testReviewer,
                connection = connectionWithoutStorage,
                sql = "SELECT * FROM foo.simple_table",
            )

            val userCookie = userHelper.login(email = testUser.email, mockMvc = mockMvc)

            // Execute the query
            mockMvc.perform(
                post("/execution-requests/${executionRequest.getId()}/execute")
                    .cookie(userCookie)
                    .contentType("application/json"),
            ).andExpect(status().isOk)

            // Verify no stored results in execution request details
            mockMvc.perform(
                get("/execution-requests/${executionRequest.getId()}").cookie(userCookie),
            ).andExpect(status().isOk)
                .andExpect(jsonPath("$.events[-1].type").value("EXECUTE"))
                .andExpect(jsonPath("$.events[-1].results[0].type").value("QUERY"))
                .andExpect(jsonPath("$.events[-1].results[0].storedRows").doesNotExist())
                .andExpect(jsonPath("$.events[-1].results[0].storedRowCount").doesNotExist())
                .andExpect(jsonPath("$.events[-1].results[0].columns").doesNotExist())
                // rowCount should still be recorded
                .andExpect(jsonPath("$.events[-1].results[0].rowCount").value(2))
        }

        @Test
        fun `execute UPDATE statement on connection with storeResults=true`() {
            val connectionWithStorage = connectionHelper.createPostgresConnection(db, storeResults = true)
            val executionRequest = executionRequestHelper.createApprovedRequest(
                author = testUser,
                approver = testReviewer,
                connection = connectionWithStorage,
                sql = "UPDATE foo.simple_table SET col2 = 'updated' WHERE col1 = 1",
            )

            val userCookie = userHelper.login(email = testUser.email, mockMvc = mockMvc)

            // Execute the UPDATE
            mockMvc.perform(
                post("/execution-requests/${executionRequest.getId()}/execute")
                    .cookie(userCookie)
                    .contentType("application/json"),
            ).andExpect(status().isOk)
                .andExpect(jsonPath("$.results[0].type").value("UPDATE_COUNT"))
                .andExpect(jsonPath("$.results[0].rowsUpdated").value(1))

            // Verify UPDATE result type has rowsUpdated but no storedRows
            mockMvc.perform(
                get("/execution-requests/${executionRequest.getId()}").cookie(userCookie),
            ).andExpect(status().isOk)
                .andExpect(jsonPath("$.events[-1].type").value("EXECUTE"))
                .andExpect(jsonPath("$.events[-1].results[0].type").value("UPDATE"))
                .andExpect(jsonPath("$.events[-1].results[0].rowsUpdated").value(1))
                // UPDATE results should never have storedRows
                .andExpect(jsonPath("$.events[-1].results[0].storedRows").doesNotExist())
        }
    }

    @Nested
    inner class RowLimitTests {

        @Test
        fun `query returning more than 500 rows only stores 500`() {
            val connectionWithStorage = connectionHelper.createPostgresConnection(db, storeResults = true)
            val executionRequest = executionRequestHelper.createApprovedRequest(
                author = testUser,
                approver = testReviewer,
                connection = connectionWithStorage,
                sql = "SELECT generate_series(1, 600) as num",
            )

            val userCookie = userHelper.login(email = testUser.email, mockMvc = mockMvc)

            // Execute the query
            mockMvc.perform(
                post("/execution-requests/${executionRequest.getId()}/execute")
                    .cookie(userCookie)
                    .contentType("application/json"),
            ).andExpect(status().isOk)

            // Verify row limits in execution request details
            mockMvc.perform(
                get("/execution-requests/${executionRequest.getId()}").cookie(userCookie),
            ).andExpect(status().isOk)
                .andExpect(jsonPath("$.events[-1].type").value("EXECUTE"))
                .andExpect(jsonPath("$.events[-1].results[0].type").value("QUERY"))
                // rowCount should be the actual count (600)
                .andExpect(jsonPath("$.events[-1].results[0].rowCount").value(600))
                // storedRowCount should be limited to 500
                .andExpect(jsonPath("$.events[-1].results[0].storedRowCount").value(500))
                // storedRows array should have 500 elements
                .andExpect(jsonPath("$.events[-1].results[0].storedRows", hasSize<Collection<*>>(500)))
        }

        @Test
        fun `query returning exactly 500 rows stores all 500`() {
            val connectionWithStorage = connectionHelper.createPostgresConnection(db, storeResults = true)
            val executionRequest = executionRequestHelper.createApprovedRequest(
                author = testUser,
                approver = testReviewer,
                connection = connectionWithStorage,
                sql = "SELECT generate_series(1, 500) as num",
            )

            val userCookie = userHelper.login(email = testUser.email, mockMvc = mockMvc)

            // Execute the query
            mockMvc.perform(
                post("/execution-requests/${executionRequest.getId()}/execute")
                    .cookie(userCookie)
                    .contentType("application/json"),
            ).andExpect(status().isOk)

            // Verify all rows are stored
            mockMvc.perform(
                get("/execution-requests/${executionRequest.getId()}").cookie(userCookie),
            ).andExpect(status().isOk)
                .andExpect(jsonPath("$.events[-1].results[0].rowCount").value(500))
                .andExpect(jsonPath("$.events[-1].results[0].storedRowCount").value(500))
                .andExpect(jsonPath("$.events[-1].results[0].storedRows", hasSize<Collection<*>>(500)))
        }

        @Test
        fun `query returning fewer than 500 rows stores all rows`() {
            val connectionWithStorage = connectionHelper.createPostgresConnection(db, storeResults = true)
            val executionRequest = executionRequestHelper.createApprovedRequest(
                author = testUser,
                approver = testReviewer,
                connection = connectionWithStorage,
                sql = "SELECT generate_series(1, 10) as num",
            )

            val userCookie = userHelper.login(email = testUser.email, mockMvc = mockMvc)

            // Execute the query
            mockMvc.perform(
                post("/execution-requests/${executionRequest.getId()}/execute")
                    .cookie(userCookie)
                    .contentType("application/json"),
            ).andExpect(status().isOk)

            // Verify all rows are stored
            mockMvc.perform(
                get("/execution-requests/${executionRequest.getId()}").cookie(userCookie),
            ).andExpect(status().isOk)
                .andExpect(jsonPath("$.events[-1].results[0].rowCount").value(10))
                .andExpect(jsonPath("$.events[-1].results[0].storedRowCount").value(10))
                .andExpect(jsonPath("$.events[-1].results[0].storedRows", hasSize<Collection<*>>(10)))
        }
    }

    @Nested
    inner class CSVDownloadResultStorageTests {

        @Test
        fun `CSV download on connection with storeResults=true stores results`() {
            val connectionWithStorage = connectionHelper.createPostgresConnection(db, storeResults = true)
            val executionRequest = executionRequestHelper.createApprovedRequest(
                author = testUser,
                approver = testReviewer,
                connection = connectionWithStorage,
                sql = "SELECT * FROM foo.simple_table",
            )

            val userCookie = userHelper.login(email = testUser.email, mockMvc = mockMvc)

            // Download CSV
            mockMvc.perform(
                get("/execution-requests/${executionRequest.getId()}/download")
                    .cookie(userCookie)
                    .contentType("application/json"),
            ).andExpect(status().isOk)

            // Verify stored results in execution request details
            mockMvc.perform(
                get("/execution-requests/${executionRequest.getId()}").cookie(userCookie),
            ).andExpect(status().isOk)
                .andExpect(jsonPath("$.events[-1].type").value("EXECUTE"))
                .andExpect(jsonPath("$.events[-1].isDownload").value(true))
                .andExpect(jsonPath("$.events[-1].results[0].type").value("QUERY"))
                .andExpect(jsonPath("$.events[-1].results[0].storedRows").isArray)
                .andExpect(jsonPath("$.events[-1].results[0].storedRowCount").isNumber)
                .andExpect(jsonPath("$.events[-1].results[0].columns").isArray)
        }

        @Test
        fun `CSV download on connection with storeResults=false does not store results`() {
            val connectionWithoutStorage = connectionHelper.createPostgresConnection(db, storeResults = false)
            val executionRequest = executionRequestHelper.createApprovedRequest(
                author = testUser,
                approver = testReviewer,
                connection = connectionWithoutStorage,
                sql = "SELECT * FROM foo.simple_table",
            )

            val userCookie = userHelper.login(email = testUser.email, mockMvc = mockMvc)

            // Download CSV
            mockMvc.perform(
                get("/execution-requests/${executionRequest.getId()}/download")
                    .cookie(userCookie)
                    .contentType("application/json"),
            ).andExpect(status().isOk)

            // Verify no stored results in execution request details
            // When storeResults=false, the results array is empty
            mockMvc.perform(
                get("/execution-requests/${executionRequest.getId()}").cookie(userCookie),
            ).andExpect(status().isOk)
                .andExpect(jsonPath("$.events[-1].type").value("EXECUTE"))
                .andExpect(jsonPath("$.events[-1].isDownload").value(true))
                .andExpect(jsonPath("$.events[-1].results", hasSize<Collection<*>>(0)))
        }
    }
}
