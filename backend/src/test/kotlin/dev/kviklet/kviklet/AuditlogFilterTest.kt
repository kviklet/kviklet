package dev.kviklet.kviklet

import dev.kviklet.kviklet.db.LicenseAdapter
import dev.kviklet.kviklet.db.User
import dev.kviklet.kviklet.helper.ConnectionHelper
import dev.kviklet.kviklet.helper.ExecutionRequestHelper
import dev.kviklet.kviklet.helper.RoleHelper
import dev.kviklet.kviklet.helper.UserHelper
import dev.kviklet.kviklet.service.dto.Connection
import dev.kviklet.kviklet.service.dto.ExecutionRequestDetails
import dev.kviklet.kviklet.service.dto.LicenseFile
import jakarta.servlet.http.Cookie
import org.hamcrest.Matchers.hasSize
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.time.Instant
import java.time.LocalDateTime

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuditlogFilterTest {

    @Autowired private lateinit var mockMvc: MockMvc

    @Autowired private lateinit var userHelper: UserHelper

    @Autowired private lateinit var roleHelper: RoleHelper

    @Autowired private lateinit var connectionHelper: ConnectionHelper

    @Autowired private lateinit var executionRequestHelper: ExecutionRequestHelper

    @Autowired private lateinit var licenseAdapter: LicenseAdapter

    private lateinit var testUser: User
    private lateinit var testReviewer: User
    private lateinit var testConnection: Connection

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

        testUser = userHelper.createUser(permissions = listOf("*"))
        testReviewer = userHelper.createUser(permissions = listOf("*"))
        testConnection = connectionHelper.createPostgresConnection(db)
    }

    @AfterEach
    fun tearDown() {
        executionRequestHelper.deleteAll()
        connectionHelper.deleteAll()
        userHelper.deleteAll()
        roleHelper.deleteAll()
        licenseAdapter.deleteAll()
    }

    /**
     * Creates an approved request and executes it via the HTTP endpoint, producing an EXECUTE event.
     * Returns the execution request details (containing the createdAt of the request itself,
     * but the execute event's createdAt is created at execution time).
     */
    private fun createAndExecute(sql: String = "SELECT 1;"): ExecutionRequestDetails {
        val request = executionRequestHelper.createApprovedRequest(
            dbcontainer = db,
            author = testUser,
            approver = testReviewer,
            sql = sql,
            connection = testConnection,
        )
        val userCookie = userHelper.login(email = testUser.email, mockMvc = mockMvc)
        mockMvc.perform(
            post("/execution-requests/${request.getId()}/execute")
                .cookie(userCookie)
                .contentType("application/json"),
        ).andExpect(status().isOk)
        return request
    }

    @Nested
    inner class DateRangeFilterTests {

        @Test
        fun `from only returns executions on or after the given timestamp`() {
            val userCookie = userHelper.login(email = testUser.email, mockMvc = mockMvc)

            createAndExecute("SELECT 1;")
            Thread.sleep(15)
            createAndExecute("SELECT 2;")
            Thread.sleep(15)
            createAndExecute("SELECT 3;")

            val allExecutions = mockMvc.perform(
                get("/executions/").cookie(userCookie),
            ).andExpect(status().isOk)
                .andReturn()

            // Executions are sorted desc; index 0 is newest (3rd), index 1 is 2nd, index 2 is 1st
            // Use the 2nd execution's time as the "from" boundary — should return 2nd and 3rd
            val secondExecutionTime = com.jayway.jsonpath.JsonPath.read<String>(
                allExecutions.response.contentAsString,
                "$.executions[1].executionTime",
            )

            // The field is already an ISO instant string (e.g. 2026-05-09T19:56:20.143985Z)
            mockMvc.perform(
                get("/executions/")
                    .param("from", secondExecutionTime)
                    .cookie(userCookie),
            ).andExpect(status().isOk)
                .andExpect(jsonPath("$.executions", hasSize<Collection<*>>(2)))
        }

        @Test
        fun `to only returns executions on or before the given timestamp`() {
            val userCookie = userHelper.login(email = testUser.email, mockMvc = mockMvc)

            createAndExecute("SELECT 1;")
            Thread.sleep(15)
            createAndExecute("SELECT 2;")
            Thread.sleep(15)
            createAndExecute("SELECT 3;")

            val allExecutions = mockMvc.perform(
                get("/executions/").cookie(userCookie),
            ).andExpect(status().isOk)
                .andReturn()

            // Desc order: index 0=3rd, index 1=2nd, index 2=1st
            // Use the 2nd execution's time as the "to" boundary — should return 1st and 2nd
            val secondExecutionTime = com.jayway.jsonpath.JsonPath.read<String>(
                allExecutions.response.contentAsString,
                "$.executions[1].executionTime",
            )

            mockMvc.perform(
                get("/executions/")
                    .param("to", secondExecutionTime)
                    .cookie(userCookie),
            ).andExpect(status().isOk)
                .andExpect(jsonPath("$.executions", hasSize<Collection<*>>(2)))
        }

        @Test
        fun `from and to together define an inclusive window and boundary records are included`() {
            val userCookie = userHelper.login(email = testUser.email, mockMvc = mockMvc)

            createAndExecute("SELECT 1;")
            Thread.sleep(15)
            createAndExecute("SELECT 2;")
            Thread.sleep(15)
            createAndExecute("SELECT 3;")
            Thread.sleep(15)
            createAndExecute("SELECT 4;")
            Thread.sleep(15)
            createAndExecute("SELECT 5;")

            val allExecutions = mockMvc.perform(
                get("/executions/").cookie(userCookie),
            ).andExpect(status().isOk)
                .andReturn()

            // Desc order: index 0=5th, 1=4th, 2=3rd, 3=2nd, 4=1st
            // from=2nd, to=4th → should return 2nd, 3rd, 4th (3 records)
            val fourthExecutionTime = com.jayway.jsonpath.JsonPath.read<String>(
                allExecutions.response.contentAsString,
                "$.executions[1].executionTime",
            )
            val secondExecutionTime = com.jayway.jsonpath.JsonPath.read<String>(
                allExecutions.response.contentAsString,
                "$.executions[3].executionTime",
            )

            // Should return 2nd, 3rd, 4th executions (inclusive bounds)
            mockMvc.perform(
                get("/executions/")
                    .param("from", secondExecutionTime)
                    .param("to", fourthExecutionTime)
                    .cookie(userCookie),
            ).andExpect(status().isOk)
                .andExpect(jsonPath("$.executions", hasSize<Collection<*>>(3)))
        }

        @Test
        fun `boundary records at exact from and to timestamps are included`() {
            val userCookie = userHelper.login(email = testUser.email, mockMvc = mockMvc)

            createAndExecute("SELECT 1;")
            Thread.sleep(15)
            createAndExecute("SELECT 2;")

            val allExecutions = mockMvc.perform(
                get("/executions/").cookie(userCookie),
            ).andExpect(status().isOk)
                .andReturn()

            // Desc order: index 0=2nd, index 1=1st
            val firstExecutionTime = com.jayway.jsonpath.JsonPath.read<String>(
                allExecutions.response.contentAsString,
                "$.executions[1].executionTime",
            )
            val secondExecutionTime = com.jayway.jsonpath.JsonPath.read<String>(
                allExecutions.response.contentAsString,
                "$.executions[0].executionTime",
            )

            // Use both boundary timestamps as from/to — both records must be included (goe/loe inclusive)
            mockMvc.perform(
                get("/executions/")
                    .param("from", firstExecutionTime)
                    .param("to", secondExecutionTime)
                    .cookie(userCookie),
            ).andExpect(status().isOk)
                .andExpect(jsonPath("$.executions", hasSize<Collection<*>>(2)))
        }

        @Test
        fun `no filter returns all executions`() {
            val userCookie = userHelper.login(email = testUser.email, mockMvc = mockMvc)

            createAndExecute("SELECT 1;")
            Thread.sleep(15)
            createAndExecute("SELECT 2;")
            Thread.sleep(15)
            createAndExecute("SELECT 3;")

            mockMvc.perform(
                get("/executions/").cookie(userCookie),
            ).andExpect(status().isOk)
                .andExpect(jsonPath("$.executions", hasSize<Collection<*>>(3)))
        }

        @Test
        fun `results are sorted descending by executionTime`() {
            val userCookie = userHelper.login(email = testUser.email, mockMvc = mockMvc)

            createAndExecute("SELECT 1;")
            Thread.sleep(15)
            createAndExecute("SELECT 2;")
            Thread.sleep(15)
            createAndExecute("SELECT 3;")

            val result = mockMvc.perform(
                get("/executions/").cookie(userCookie),
            ).andExpect(status().isOk)
                .andExpect(jsonPath("$.executions", hasSize<Collection<*>>(3)))
                .andReturn()

            val times = com.jayway.jsonpath.JsonPath.read<List<String>>(
                result.response.contentAsString,
                "$.executions[*].executionTime",
            )

            // Verify descending order: each time must be >= the next (as ISO instant strings)
            for (i in 0 until times.size - 1) {
                val t1 = Instant.parse(times[i])
                val t2 = Instant.parse(times[i + 1])
                assert(!t1.isBefore(t2)) {
                    "Expected executions sorted desc by time, but got $t1 before $t2 at index $i"
                }
            }
        }
    }
}
