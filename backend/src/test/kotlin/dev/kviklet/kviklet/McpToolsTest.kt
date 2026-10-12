package dev.kviklet.kviklet

import com.ninjasquad.springmockk.MockkBean
import dev.kviklet.kviklet.db.ExecutionRequestAdapter
import dev.kviklet.kviklet.db.LicenseAdapter
import dev.kviklet.kviklet.db.User
import dev.kviklet.kviklet.helper.ConnectionHelper
import dev.kviklet.kviklet.helper.ExecutionRequestHelper
import dev.kviklet.kviklet.helper.McpClientHelper
import dev.kviklet.kviklet.helper.RoleHelper
import dev.kviklet.kviklet.helper.ToolCallResult
import dev.kviklet.kviklet.helper.UserHelper
import dev.kviklet.kviklet.service.dto.LicenseFile
import dev.kviklet.kviklet.service.dto.RequestType
import dev.kviklet.kviklet.shell.KubernetesApi
import dev.kviklet.kviklet.shell.KubernetesResult
import io.kubernetes.client.openapi.models.V1Container
import io.kubernetes.client.openapi.models.V1ObjectMeta
import io.kubernetes.client.openapi.models.V1Pod
import io.kubernetes.client.openapi.models.V1PodSpec
import io.kubernetes.client.openapi.models.V1PodStatus
import io.mockk.every
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import tools.jackson.databind.JsonNode
import java.time.LocalDateTime

/** Every MCP tool, called the way an agent calls it: through /mcp with an OAuth access token. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class McpToolsTest {

    @Autowired private lateinit var mockMvc: MockMvc

    @Autowired private lateinit var mcp: McpClientHelper

    @Autowired private lateinit var userHelper: UserHelper

    @Autowired private lateinit var roleHelper: RoleHelper

    @Autowired private lateinit var connectionHelper: ConnectionHelper

    @Autowired private lateinit var executionRequestHelper: ExecutionRequestHelper

    @Autowired private lateinit var executionRequestAdapter: ExecutionRequestAdapter

    @Autowired private lateinit var licenseAdapter: LicenseAdapter

    @Autowired private lateinit var jdbcTemplate: JdbcTemplate

    @MockkBean(relaxed = true)
    private lateinit var kubernetesApi: KubernetesApi

    // The test license allows two users: the agent's user and a colleague who reviews.
    private lateinit var author: User
    private lateinit var reviewer: User

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
    fun setUp() {
        author = userHelper.createUser(permissions = listOf("*"))
        reviewer = userHelper.createUser(permissions = listOf("*"))
        installLicense()
    }

    @AfterEach
    fun tearDown() {
        jdbcTemplate.update("DELETE FROM oauth2_authorization")
        jdbcTemplate.update("DELETE FROM oauth2_registered_client")
        executionRequestAdapter.deleteAll()
        connectionHelper.deleteAll()
        userHelper.deleteAll()
        roleHelper.deleteAll()
        licenseAdapter.deleteAll()
    }

    @Test
    fun `tools are listed with hints that tell clients which ones to confirm`() {
        val tools = mcp.listTools(mockMvc, token(author)).associateBy { it["name"].asString() }

        assertThat(tools.keys).contains(
            "list_connections", "list_requests", "get_request", "create_query_request",
            "create_database_access_request", "create_pod_command_request", "create_pod_access_request",
            "update_request", "review_request", "close_request", "execute_request", "run_with_access",
            "start_proxy", "list_pods",
        )
        fun hint(tool: String, name: String) = tools.getValue(tool)["annotations"][name].asBoolean()
        assertThat(hint("get_request", "readOnlyHint")).isTrue()
        assertThat(hint("create_query_request", "destructiveHint")).isFalse()
        listOf("review_request", "close_request", "execute_request", "run_with_access", "start_proxy").forEach {
            assertThat(hint(it, "destructiveHint")).describedAs(it).isTrue()
        }
    }

    @Test
    fun `list_connections shows the connections and whether the user can open requests on them`() {
        val connection = connectionHelper.createPostgresConnection(db)

        val result = call(author, "list_connections")

        val listed = result.json!!.single()
        assertThat(listed["id"].asString()).isEqualTo(connection.getId())
        assertThat(listed["kind"].asString()).isEqualTo("DATABASE")
        assertThat(listed["engine"].asString()).isEqualTo("POSTGRESQL")
        assertThat(listed["canCreateRequest"].asBoolean()).isTrue()
    }

    @Test
    fun `an agent creates a query request that is marked as made via MCP`() {
        val connection = connectionHelper.createPostgresConnection(db)

        val created = call(
            author,
            "create_query_request",
            mapOf(
                "connectionId" to connection.getId(),
                "title" to "Count users",
                "description" to "Support ticket",
                "statement" to "SELECT 1",
            ),
        ).json!!

        assertThat(created["statement"].asString()).isEqualTo("SELECT 1")
        assertThat(created["source"].asString()).isEqualTo("MCP")
        assertThat(created["reviewStatus"].asString()).isEqualTo("AWAITING_APPROVAL")
        assertThat(created["canReview"].asBoolean()).isFalse()
        assertThat(created["url"].asString()).endsWith("/requests/${created["id"].asString()}")

        val fetched = call(author, "get_request", mapOf("id" to created["id"].asString())).json!!
        assertThat(fetched["title"].asString()).isEqualTo("Count users")
        assertThat(fetched["approvals"]["required"].asInt()).isEqualTo(1)
    }

    @Test
    fun `a create tool refuses a connection of the other kind and names the right tool`() {
        val connection = connectionHelper.createKubernetesConnection()

        val result = call(
            author,
            "create_query_request",
            mapOf(
                "connectionId" to connection.getId(),
                "title" to "t",
                "description" to "d",
                "statement" to "SELECT 1",
            ),
        )

        assertThat(result.isError).isTrue()
        assertThat(result.text).contains("create_pod_command_request")
    }

    @Test
    fun `an agent asks for temporary database access`() {
        val connection = connectionHelper.createPostgresConnection(db)

        val created = call(
            author,
            "create_database_access_request",
            mapOf(
                "connectionId" to connection.getId(),
                "title" to "Debugging",
                "description" to "Incident",
                "durationMinutes" to 30,
            ),
        ).json!!

        assertThat(created["type"].asString()).isEqualTo("TemporaryAccess")
        assertThat(created["durationMinutes"].asInt()).isEqualTo(30)
    }

    @Test
    fun `an agent asks to run a command in a pod and for access to a pod`() {
        val connection = connectionHelper.createKubernetesConnection()
        val pod = mapOf("connectionId" to connection.getId(), "namespace" to "default", "podName" to "api-1")

        val command = call(
            author,
            "create_pod_command_request",
            pod + mapOf("title" to "Logs", "description" to "d", "command" to "ls /var/log"),
        ).json!!
        val access = call(
            author,
            "create_pod_access_request",
            pod + mapOf("title" to "Shell", "description" to "d", "durationMinutes" to 15),
        ).json!!

        assertThat(command["command"].asString()).isEqualTo("ls /var/log")
        assertThat(command["pod"]["name"].asString()).isEqualTo("api-1")
        assertThat(access["type"].asString()).isEqualTo("TemporaryAccess")
        assertThat(access["connectionKind"].asString()).isEqualTo("KUBERNETES")
    }

    @Test
    fun `list_requests filters and pages`() {
        val connection = connectionHelper.createPostgresConnection(db)
        repeat(3) { executionRequestHelper.createExecutionRequest(author = author, connection = connection) }

        val first = call(author, "list_requests", mapOf("limit" to 2, "reviewStatuses" to listOf("AWAITING_APPROVAL")))
            .json!!
        assertThat(first["requests"].size()).isEqualTo(2)
        assertThat(first["requests"][0]["source"].asString()).isEqualTo("WEB")

        val second = call(author, "list_requests", mapOf("limit" to 2, "cursor" to first["nextCursor"].asString()))
            .json!!
        assertThat(second["requests"].size()).isEqualTo(1)
        assertThat(second["nextCursor"].isNull).isTrue()

        val none = call(author, "list_requests", mapOf("reviewStatuses" to listOf("APPROVED"))).json!!
        assertThat(none["requests"].size()).isEqualTo(0)
    }

    @Test
    fun `update_request changes only the given fields and resets approvals when the statement changes`() {
        val request = executionRequestHelper.createApprovedRequest(db, author, reviewer, sql = "SELECT 1")
        val id = request.getId()

        val retitled = call(author, "update_request", mapOf("id" to id, "title" to "New title")).json!!
        assertThat(retitled["title"].asString()).isEqualTo("New title")
        assertThat(retitled["statement"].asString()).isEqualTo("SELECT 1")
        assertThat(retitled["reviewStatus"].asString()).isEqualTo("APPROVED")

        val edited = call(author, "update_request", mapOf("id" to id, "statement" to "SELECT 2")).json!!
        assertThat(edited["statement"].asString()).isEqualTo("SELECT 2")
        assertThat(edited["title"].asString()).isEqualTo("New title")
        assertThat(edited["reviewStatus"].asString()).isEqualTo("AWAITING_APPROVAL")
    }

    @Test
    fun `moving a pod command to another pod resets its approvals`() {
        val request = executionRequestHelper.createApprovedKubernetesExecutionRequest(author, reviewer)

        val moved = call(author, "update_request", mapOf("id" to request.getId(), "podName" to "other-pod")).json!!

        assertThat(moved["pod"]["name"].asString()).isEqualTo("other-pod")
        assertThat(moved["reviewStatus"].asString()).isEqualTo("AWAITING_APPROVAL")
    }

    @Test
    fun `only someone else can review a request`() {
        val request = executionRequestHelper.createExecutionRequest(db, author = author)
        val id = request.getId()

        val own = call(author, "review_request", mapOf("id" to id, "action" to "APPROVE", "comment" to "lgtm"))
        assertThat(own.isError).isTrue()
        assertThat(own.text).contains("can't review their own request")

        val listed = call(reviewer, "get_request", mapOf("id" to id)).json!!
        assertThat(listed["canReview"].asBoolean()).isTrue()

        val review = call(reviewer, "review_request", mapOf("id" to id, "action" to "APPROVE", "comment" to "lgtm"))
            .json!!
        assertThat(review["reviewStatus"].asString()).isEqualTo("APPROVED")
        assertThat(review["approvals"]["current"].asInt()).isEqualTo(1)
    }

    @Test
    fun `close_request withdraws a request`() {
        val request = executionRequestHelper.createExecutionRequest(db, author = author)

        val closed = call(author, "close_request", mapOf("id" to request.getId(), "comment" to "not needed")).json!!

        assertThat(closed["reviewStatus"].asString()).isEqualTo("REJECTED")
    }

    @Test
    fun `execute_request runs an approved request and cuts large results`() {
        val request = executionRequestHelper.createApprovedRequest(
            db,
            author,
            reviewer,
            sql = "SELECT g AS n, repeat('x', 1500) AS long FROM generate_series(1, 250) g",
        )

        val result = call(author, "execute_request", mapOf("id" to request.getId())).json!!

        val rows = result["results"][0]["rows"]
        assertThat(result["kind"].asString()).isEqualTo("DATABASE")
        assertThat(strings(rows["columns"])).containsExactly("n", "long")
        assertThat(rows["rowCount"].asInt()).isEqualTo(250)
        assertThat(rows["rows"].size()).isEqualTo(200)
        assertThat(rows["rows"][0][1].asString()).hasSize(1000)
        assertThat(rows["truncated"].asBoolean()).isTrue()
    }

    @Test
    fun `execute_request refuses unapproved and temporary access requests`() {
        val unapproved = executionRequestHelper.createExecutionRequest(db, author = author)
        val access = executionRequestHelper.createApprovedRequest(
            db,
            author,
            reviewer,
            requestType = RequestType.TemporaryAccess,
        )

        val notApproved = call(author, "execute_request", mapOf("id" to unapproved.getId()))
        val wrongTool = call(author, "execute_request", mapOf("id" to access.getId()))

        assertThat(notApproved.isError).isTrue()
        assertThat(wrongTool.isError).isTrue()
        assertThat(wrongTool.text).contains("run_with_access")
    }

    @Test
    fun `run_with_access runs statements on an approved temporary access request only`() {
        val access = executionRequestHelper.createApprovedRequest(
            db,
            author,
            reviewer,
            requestType = RequestType.TemporaryAccess,
        )
        val single = executionRequestHelper.createApprovedRequest(db, author, reviewer)

        val result = call(author, "run_with_access", mapOf("id" to access.getId(), "statement" to "SELECT 42 AS x"))
            .json!!
        val refused = call(author, "run_with_access", mapOf("id" to single.getId(), "statement" to "SELECT 1"))

        assertThat(result["results"][0]["rows"]["rows"][0][0].asString()).isEqualTo("42")
        assertThat(refused.isError).isTrue()
        assertThat(refused.text).contains("execute_request")
    }

    @Test
    fun `pod commands run through execute_request and long output is cut`() {
        every { kubernetesApi.executeCommandOnPod(any(), any(), any(), any(), any(), any(), any()) } returns
            KubernetesResult(errors = emptyList(), messages = listOf("y".repeat(25_000)), finished = true, exitCode = 0)
        val request = executionRequestHelper.createApprovedKubernetesExecutionRequest(author, reviewer)

        val result = call(author, "execute_request", mapOf("id" to request.getId())).json!!

        assertThat(result["kind"].asString()).isEqualTo("KUBERNETES")
        assertThat(result["stdout"].asString()).hasSize(20_000)
        assertThat(result["truncated"].asBoolean()).isTrue()
        verify {
            kubernetesApi.executeCommandOnPod(
                "default",
                "test-pod",
                "test-container",
                "echo 'Hello, World!'",
                any(),
                any(),
                any(),
            )
        }
    }

    @Test
    fun `start_proxy is refused while the proxy is disabled`() {
        val access = executionRequestHelper.createApprovedRequest(
            db,
            author,
            reviewer,
            requestType = RequestType.TemporaryAccess,
        )

        val result = call(author, "start_proxy", mapOf("id" to access.getId()))

        assertThat(result.isError).isTrue()
        assertThat(result.text).contains("proxy is disabled")
    }

    @Test
    fun `list_pods lists the cluster's pods, optionally of one namespace`() {
        every { kubernetesApi.getActivePods() } returns listOf(pod("default", "api-1"), pod("jobs", "worker-1"))

        val all = call(author, "list_pods").json!!
        val jobs = call(author, "list_pods", mapOf("namespace" to "jobs")).json!!

        assertThat(all.size()).isEqualTo(2)
        assertThat(jobs.single()["name"].asString()).isEqualTo("worker-1")
        assertThat(strings(jobs.single()["containers"])).containsExactly("main")
    }

    @Test
    fun `a user without permissions on a connection can't see or use its requests`() {
        val connection = connectionHelper.createPostgresConnection(db)
        val request = executionRequestHelper.createExecutionRequest(author = author, connection = connection)
        // A third user needs a seat beyond the test license's two, which is only checked when users are created.
        licenseAdapter.deleteAll()
        val outsider = userHelper.createUser(
            permissions = listOf("datasource_connection:get", "execution_request:get"),
            resources = listOf("other-connection", "other-connection"),
        )
        installLicense()
        roleHelper.removeDefaultRolePermissions()

        val listed = call(outsider, "list_requests").json!!
        val fetched = call(outsider, "get_request", mapOf("id" to request.getId()))

        assertThat(listed["requests"].size()).isEqualTo(0)
        assertThat(fetched.isError).isTrue()
    }

    private fun call(user: User, tool: String, arguments: Map<String, Any?> = emptyMap()): ToolCallResult =
        mcp.callTool(mockMvc, token(user), tool, arguments)

    private val tokens = mutableMapOf<String, String>()

    private fun token(user: User): String = tokens.getOrPut(user.getId()!!) { mcp.obtainAccessToken(mockMvc, user) }

    private fun strings(node: JsonNode): List<String> = (0 until node.size()).map { node[it].asString() }

    private fun pod(namespace: String, name: String) = V1Pod()
        .metadata(V1ObjectMeta().namespace(namespace).name(name).uid("$namespace-$name"))
        .spec(V1PodSpec().containers(listOf(V1Container().name("main"))))
        .status(V1PodStatus().phase("Running"))

    private fun installLicense() {
        // Same test license as in McpOAuthTest
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
