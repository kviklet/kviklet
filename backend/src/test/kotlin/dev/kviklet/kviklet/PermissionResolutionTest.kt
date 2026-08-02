package dev.kviklet.kviklet

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import dev.kviklet.kviklet.controller.ConnectionController
import dev.kviklet.kviklet.controller.CreateDatasourceConnectionRequest
import dev.kviklet.kviklet.controller.ExecutionRequestController
import dev.kviklet.kviklet.controller.ExecutionRequestResponse
import dev.kviklet.kviklet.controller.ReviewConfigRequest
import dev.kviklet.kviklet.db.ConnectionRepository
import dev.kviklet.kviklet.db.ExecutionRequestRepository
import dev.kviklet.kviklet.db.User
import dev.kviklet.kviklet.helper.UserHelper
import dev.kviklet.kviklet.security.Permission
import dev.kviklet.kviklet.security.UserDetailsWithId
import dev.kviklet.kviklet.service.dto.DatasourceType
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldNotContain
import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * Covers the permissions the API reports to the frontend: the unscoped set on `/status` and the
 * object-scoped sets on the connection and execution request responses. The separation exists
 * because the unscoped set deliberately collapses resource scoping and cannot see the `auth()`
 * rules — [connectionListReportsPermissionsPerConnection] and [requestPermissionsFoldInTheAuthHook]
 * are the tests that pin that down.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PermissionResolutionTest {

    @Autowired
    private lateinit var userHelper: UserHelper

    @Autowired
    private lateinit var connectionController: ConnectionController

    @Autowired
    private lateinit var executionRequestController: ExecutionRequestController

    @Autowired
    private lateinit var connectionRepository: ConnectionRepository

    @Autowired
    private lateinit var executionRequestRepository: ExecutionRequestRepository

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @AfterEach
    fun tearDown() {
        executionRequestRepository.deleteAllInBatch()
        connectionRepository.deleteAllInBatch()
        userHelper.deleteAll()
    }

    @Test
    fun `status lists every permission for an admin`() {
        userHelper.createUser(permissions = listOf("*"))
        val cookie = userHelper.login(mockMvc = mockMvc)

        statusPermissions(cookie) shouldContainExactlyInAnyOrder Permission.entries.map { it.getPermissionString() }
    }

    @Test
    fun `status lists only the default role grants for a user without extra policies`() {
        userHelper.createUser(permissions = emptyList())
        val cookie = userHelper.login(mockMvc = mockMvc)

        statusPermissions(cookie) shouldContainExactlyInAnyOrder listOf(
            Permission.DATASOURCE_CONNECTION_GET.getPermissionString(),
            Permission.EXECUTION_REQUEST_GET.getPermissionString(),
            Permission.USER_GET.getPermissionString(),
        )
    }

    @Test
    fun `status omits a permission whose required parent is missing`() {
        userHelper.createUser(permissions = listOf(Permission.API_KEY_CREATE.getPermissionString()))
        val cookie = userHelper.login(mockMvc = mockMvc)

        // api_key:create requires api_key:get, which no role grants here.
        statusPermissions(cookie) shouldNotContain Permission.API_KEY_CREATE.getPermissionString()
    }

    @Test
    fun `status reports a resource scoped permission as held`() {
        userHelper.createUser(
            permissions = listOf(Permission.DATASOURCE_CONNECTION_EDIT.getPermissionString()),
            resources = listOf("conn-a"),
        )
        val cookie = userHelper.login(mockMvc = mockMvc)

        // "Allowed on at least one resource" — which is exactly why this answer must not be used to
        // decide whether the user may edit some particular connection.
        statusPermissions(cookie) shouldContain Permission.DATASOURCE_CONNECTION_EDIT.getPermissionString()
    }

    @Test
    fun connectionListReportsPermissionsPerConnection() {
        createConnection("conn-a")
        createConnection("conn-b")
        userHelper.createUser(
            permissions = listOf(Permission.DATASOURCE_CONNECTION_EDIT.getPermissionString()),
            resources = listOf("conn-a"),
        )
        val cookie = userHelper.login(mockMvc = mockMvc)

        val connections = mockMvc.perform(get("/connections/").cookie(cookie))
            .andExpect(status().isOk)
            .andReturn().parse<List<ConnectionPermissions>>()
            .associate { it.id to it.permissions }

        val edit = Permission.DATASOURCE_CONNECTION_EDIT.getPermissionString()
        connections.getValue("conn-a") shouldContain edit
        connections.getValue("conn-b") shouldNotContain edit
    }

    @Test
    fun requestPermissionsFoldInTheAuthHook() {
        val author = userHelper.createUser(permissions = listOf("*"))
        val reviewer = userHelper.createUser(permissions = listOf("*"))
        createConnection("conn-a", reviewsRequired = 1)
        val request = createRequest("conn-a", author)

        val requestId = request.id.toString()
        val authorPermissions = requestPermissions(requestId, userHelper.login(author.email, mockMvc = mockMvc))
        val reviewerPermissions = requestPermissions(requestId, userHelper.login(reviewer.email, mockMvc = mockMvc))

        val edit = Permission.EXECUTION_REQUEST_EDIT.getPermissionString()
        val execute = Permission.EXECUTION_REQUEST_EXECUTE.getPermissionString()
        val review = Permission.EXECUTION_REQUEST_REVIEW.getPermissionString()

        // Editing is author-only, so the reviewer does not get it despite a wildcard policy...
        authorPermissions shouldContain edit
        reviewerPermissions shouldNotContain edit
        reviewerPermissions shouldContain review
        // ...and nobody may execute a request that is still awaiting approval.
        authorPermissions shouldNotContain execute
        reviewerPermissions shouldNotContain execute
    }

    private fun statusPermissions(cookie: Cookie): List<String> = mockMvc.perform(get("/status").cookie(cookie))
        .andExpect(status().isOk)
        .andReturn().parse<PermissionsOnly>().permissions

    private fun requestPermissions(requestId: String, cookie: Cookie): List<String> = mockMvc
        .perform(get("/execution-requests/$requestId").cookie(cookie))
        .andExpect(status().isOk)
        .andReturn().parse<PermissionsOnly>().permissions

    private fun createConnection(id: String, reviewsRequired: Int = 0) = connectionController.createConnection(
        CreateDatasourceConnectionRequest(
            id = id,
            displayName = id,
            username = "username",
            password = "password",
            description = "description",
            reviewConfig = ReviewConfigRequest(reviewsRequired),
            type = DatasourceType.MYSQL,
            hostname = "localhost",
            port = 3306,
        ),
    )

    private fun createRequest(connectionId: String, author: User): ExecutionRequestResponse =
        executionRequestController.create(
            TestFixtures.createExecutionRequestRequest(connectionId),
            UserDetailsWithId(
                id = author.getId()!!,
                email = author.email,
                password = author.password,
                authorities = emptyList(),
            ),
        )

    private inline fun <reified T> MvcResult.parse(): T =
        objectMapper.readValue(response.contentAsString, object : TypeReference<T>() {})

    /** Only the field under test — everything else on the response is ignored. */
    data class PermissionsOnly(val permissions: List<String>)

    data class ConnectionPermissions(val id: String, val permissions: List<String>)
}
