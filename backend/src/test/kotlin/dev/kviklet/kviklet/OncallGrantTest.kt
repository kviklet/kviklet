package dev.kviklet.kviklet

import dev.kviklet.kviklet.db.OncallGrantAdapter
import dev.kviklet.kviklet.helper.ConnectionHelper
import dev.kviklet.kviklet.helper.ExecutionRequestHelper
import dev.kviklet.kviklet.helper.RoleHelper
import dev.kviklet.kviklet.helper.UserHelper
import dev.kviklet.kviklet.security.Permission
import dev.kviklet.kviklet.service.dto.OncallGrant
import dev.kviklet.kviklet.service.dto.OncallGrantKind
import dev.kviklet.kviklet.service.dto.utcTimeNow
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.hasItems
import org.hamcrest.Matchers.hasSize
import org.hamcrest.Matchers.not
import org.hamcrest.Matchers.nullValue
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OncallGrantTest {
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
    private lateinit var oncallGrantAdapter: OncallGrantAdapter

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @AfterEach
    fun tearDown() {
        executionRequestHelper.deleteAll()
        connectionHelper.deleteAll()
        userHelper.deleteAll()
        roleHelper.deleteAll()
    }

    @Test
    fun `oncall grant unlocks every connection only while it is active`() {
        roleHelper.removeDefaultRolePermissions()
        val admin = userHelper.createUser(permissions = listOf("*"))
        val connA = connectionHelper.createDummyConnection()
        val connB = connectionHelper.createDummyConnection()
        val operator = userHelper.createUser(
            permissions = listOf(
                Permission.DATASOURCE_CONNECTION_GET.getPermissionString(),
                Permission.EXECUTION_REQUEST_GET.getPermissionString(),
                Permission.EXECUTION_REQUEST_EDIT.getPermissionString(),
            ),
            resources = listOf(connA.id.toString(), connA.id.toString(), connA.id.toString()),
        )
        val adminCookie = userHelper.login(email = admin.email, mockMvc = mockMvc)
        val operatorCookie = userHelper.login(email = operator.email, mockMvc = mockMvc)

        mockMvc.perform(get("/connections/").cookie(operatorCookie))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$", hasSize<Collection<*>>(1)))
            .andExpect(jsonPath("$[0].id", equalTo(connA.id.toString())))

        createRequest(connB.id.toString(), operatorCookie).andExpect(status().isForbidden)

        mockMvc.perform(
            post("/users/${operator.getId()}/oncall-grant")
                .cookie(adminCookie)
                .contentType("application/json")
                .content(
                    """
                    {
                        "kind": "ONCALL",
                        "durationMinutes": 60,
                        "reason": "weekend shift"
                    }
                    """.trimIndent(),
                ),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.kind", equalTo("ONCALL")))
            .andExpect(jsonPath("$.bypassApproval", equalTo(false)))

        mockMvc.perform(get("/connections/").cookie(operatorCookie))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$", hasSize<Collection<*>>(2)))
            .andExpect(jsonPath("$[*].id", hasItems(connA.id.toString(), connB.id.toString())))

        createRequest(connB.id.toString(), operatorCookie).andExpect(status().isOk)

        val connC = connectionHelper.createDummyConnection()
        mockMvc.perform(get("/connections/").cookie(operatorCookie))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$", hasSize<Collection<*>>(3)))
            .andExpect(jsonPath("$[*].id", hasItems(connC.id.toString())))

        mockMvc.perform(get("/status").cookie(operatorCookie))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.activeOncallGrant.kind", equalTo("ONCALL")))
            .andExpect(
                jsonPath(
                    "$.permissions",
                    hasItems(
                        Permission.EXECUTION_REQUEST_EXECUTE.getPermissionString(),
                        Permission.EXECUTION_REQUEST_REVIEW.getPermissionString(),
                    ),
                ),
            )

        mockMvc.perform(delete("/users/${operator.getId()}/oncall-grant").cookie(adminCookie))
            .andExpect(status().isNoContent)

        mockMvc.perform(get("/connections/").cookie(operatorCookie))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$", hasSize<Collection<*>>(1)))
            .andExpect(jsonPath("$[0].id", equalTo(connA.id.toString())))

        mockMvc.perform(get("/status").cookie(operatorCookie))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.activeOncallGrant").value(nullValue()))
            .andExpect(
                jsonPath(
                    "$.permissions",
                    not(hasItems(Permission.EXECUTION_REQUEST_EXECUTE.getPermissionString())),
                ),
            )
    }

    @Test
    fun `outage grant bypasses approval until it is revoked`() {
        roleHelper.removeDefaultRolePermissions()
        val admin = userHelper.createUser(permissions = listOf("*"))
        val connA = connectionHelper.createDummyConnection()
        val operator = userHelper.createUser(
            permissions = listOf(
                Permission.DATASOURCE_CONNECTION_GET.getPermissionString(),
                Permission.EXECUTION_REQUEST_GET.getPermissionString(),
                Permission.EXECUTION_REQUEST_EDIT.getPermissionString(),
            ),
            resources = listOf(connA.id.toString(), connA.id.toString(), connA.id.toString()),
        )
        val adminCookie = userHelper.login(email = admin.email, mockMvc = mockMvc)
        val operatorCookie = userHelper.login(email = operator.email, mockMvc = mockMvc)

        val created = createRequest(connA.id.toString(), operatorCookie)
            .andExpect(status().isOk)
            .andReturn()
        val requestId = objectMapper.readTree(created.response.contentAsString).get("id").asText()

        mockMvc.perform(get("/execution-requests/$requestId").cookie(operatorCookie))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.reviewStatus", equalTo("AWAITING_APPROVAL")))

        mockMvc.perform(
            post("/users/${operator.getId()}/oncall-grant")
                .cookie(adminCookie)
                .contentType("application/json")
                .content(
                    """
                    {
                        "kind": "OUTAGE",
                        "durationMinutes": 60
                    }
                    """.trimIndent(),
                ),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.bypassApproval", equalTo(true)))

        mockMvc.perform(get("/execution-requests/$requestId").cookie(operatorCookie))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.reviewStatus", equalTo("APPROVED")))
            .andExpect(jsonPath("$.approvalProgress.bypassed", equalTo(true)))
            .andExpect(jsonPath("$.approvalProgress.bypassedByRoleNames[0]", equalTo("Outage")))

        mockMvc.perform(delete("/users/${operator.getId()}/oncall-grant").cookie(adminCookie))
            .andExpect(status().isNoContent)

        mockMvc.perform(get("/execution-requests/$requestId").cookie(operatorCookie))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.reviewStatus", equalTo("AWAITING_APPROVAL")))
    }

    @Test
    fun `expired grant does not grant all-connection access`() {
        roleHelper.removeDefaultRolePermissions()
        userHelper.createUser(permissions = listOf("*"))
        val connA = connectionHelper.createDummyConnection()
        val connB = connectionHelper.createDummyConnection()
        val operator = userHelper.createUser(
            permissions = listOf(Permission.DATASOURCE_CONNECTION_GET.getPermissionString()),
            resources = listOf(connA.id.toString()),
        )
        val operatorCookie = userHelper.login(email = operator.email, mockMvc = mockMvc)

        oncallGrantAdapter.save(
            OncallGrant(
                userId = operator.getId()!!,
                kind = OncallGrantKind.ONCALL,
                startsAt = utcTimeNow().minusHours(2),
                endsAt = utcTimeNow().minusHours(1),
                bypassApproval = false,
                grantedByUserId = operator.getId()!!,
                approvedAt = utcTimeNow().minusHours(2),
                approvedByUserId = operator.getId()!!,
                durationMinutes = 60,
            ),
        )

        mockMvc.perform(get("/connections/").cookie(operatorCookie))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$", hasSize<Collection<*>>(1)))
            .andExpect(jsonPath("$[0].id", equalTo(connA.id.toString())))
            .andExpect(jsonPath("$[*].id", not(hasItems(connB.id.toString()))))
    }

    @Test
    fun `starting a new grant revokes the previous one`() {
        val admin = userHelper.createUser(permissions = listOf("*"))
        val operator = userHelper.createUser(permissions = emptyList())
        val adminCookie = userHelper.login(email = admin.email, mockMvc = mockMvc)

        mockMvc.perform(
            post("/users/${operator.getId()}/oncall-grant")
                .cookie(adminCookie)
                .contentType("application/json")
                .content("""{"kind": "ONCALL", "durationMinutes": 60}"""),
        ).andExpect(status().isOk)

        mockMvc.perform(
            post("/users/${operator.getId()}/oncall-grant")
                .cookie(adminCookie)
                .contentType("application/json")
                .content("""{"kind": "OUTAGE", "durationMinutes": 120}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.kind", equalTo("OUTAGE")))
            .andExpect(jsonPath("$.bypassApproval", equalTo(true)))

        mockMvc.perform(get("/users/").cookie(adminCookie))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.users[?(@.id == '${operator.getId()}')].activeOncallGrant.kind", hasSize<Collection<*>>(1)))
            .andExpect(
                jsonPath("$.users[?(@.id == '${operator.getId()}')].activeOncallGrant.kind")
                    .value(hasItems("OUTAGE")),
            )
    }

    @Test
    fun `duration below 15 minutes is rejected`() {
        val admin = userHelper.createUser(permissions = listOf("*"))
        val operator = userHelper.createUser(permissions = emptyList())
        val adminCookie = userHelper.login(email = admin.email, mockMvc = mockMvc)

        mockMvc.perform(
            post("/users/${operator.getId()}/oncall-grant")
                .cookie(adminCookie)
                .contentType("application/json")
                .content("""{"kind": "ONCALL", "durationMinutes": 5}"""),
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `user without edit roles cannot start a grant`() {
        userHelper.createUser(permissions = listOf("*"))
        val operator = userHelper.createUser(permissions = emptyList())
        val operatorCookie = userHelper.login(email = operator.email, mockMvc = mockMvc)

        mockMvc.perform(
            post("/users/${operator.getId()}/oncall-grant")
                .cookie(operatorCookie)
                .contentType("application/json")
                .content("""{"kind": "ONCALL", "durationMinutes": 60}"""),
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `duration of 10 days is accepted and 11 days is rejected`() {
        val admin = userHelper.createUser(permissions = listOf("*"))
        val operator = userHelper.createUser(permissions = emptyList())
        val adminCookie = userHelper.login(email = admin.email, mockMvc = mockMvc)

        mockMvc.perform(
            post("/users/${operator.getId()}/oncall-grant")
                .cookie(adminCookie)
                .contentType("application/json")
                .content("""{"kind": "ONCALL", "durationMinutes": 14400}"""),
        ).andExpect(status().isOk)

        mockMvc.perform(
            post("/users/${operator.getId()}/oncall-grant")
                .cookie(adminCookie)
                .contentType("application/json")
                .content("""{"kind": "ONCALL", "durationMinutes": 15840}"""),
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `manager with bypassApproval can start a grant for any user`() {
        val manager = userHelper.createUser(permissions = emptyList(), bypassApproval = true)
        val operator = userHelper.createUser(permissions = emptyList())
        val managerCookie = userHelper.login(email = manager.email, mockMvc = mockMvc)

        mockMvc.perform(get("/status").cookie(managerCookie))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.canManageOncall", equalTo(true)))

        mockMvc.perform(
            post("/users/${operator.getId()}/oncall-grant")
                .cookie(managerCookie)
                .contentType("application/json")
                .content("""{"kind": "OUTAGE", "durationMinutes": 1440}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status", equalTo("ACTIVE")))
            .andExpect(jsonPath("$.kind", equalTo("OUTAGE")))
    }

    @Test
    fun `manager can approve a pending oncall request from any user`() {
        val manager = userHelper.createUser(permissions = emptyList(), bypassApproval = true)
        val operator = userHelper.createUser(permissions = emptyList())
        val managerCookie = userHelper.login(email = manager.email, mockMvc = mockMvc)
        val operatorCookie = userHelper.login(email = operator.email, mockMvc = mockMvc)

        mockMvc.perform(
            post("/users/${operator.getId()}/oncall-grant")
                .cookie(operatorCookie)
                .contentType("application/json")
                .content("""{"kind": "ONCALL", "durationMinutes": 60}"""),
        ).andExpect(status().isForbidden)

        mockMvc.perform(
            post("/users/${operator.getId()}/oncall-grant/request")
                .cookie(operatorCookie)
                .contentType("application/json")
                .content("""{"kind": "ONCALL", "durationMinutes": 4320, "reason": "weekend cover"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status", equalTo("PENDING")))

        mockMvc.perform(get("/users/").cookie(managerCookie))
            .andExpect(status().isOk)
            .andExpect(
                jsonPath("$.users[?(@.id == '${operator.getId()}')].pendingOncallGrant.status")
                    .value(hasItems("PENDING")),
            )

        mockMvc.perform(
            post("/users/${operator.getId()}/oncall-grant/approve").cookie(managerCookie),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status", equalTo("ACTIVE")))
            .andExpect(jsonPath("$.durationMinutes", equalTo(4320)))
    }

    @Test
    fun `user cannot request oncall access for another user`() {
        val operator = userHelper.createUser(permissions = emptyList())
        val other = userHelper.createUser(permissions = emptyList())
        val operatorCookie = userHelper.login(email = operator.email, mockMvc = mockMvc)

        mockMvc.perform(
            post("/users/${other.getId()}/oncall-grant/request")
                .cookie(operatorCookie)
                .contentType("application/json")
                .content("""{"kind": "ONCALL", "durationMinutes": 60}"""),
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `status includes pending oncall grant for the requester`() {
        val operator = userHelper.createUser(permissions = emptyList())
        val operatorCookie = userHelper.login(email = operator.email, mockMvc = mockMvc)

        mockMvc.perform(
            post("/users/${operator.getId()}/oncall-grant/request")
                .cookie(operatorCookie)
                .contentType("application/json")
                .content("""{"kind": "OUTAGE", "durationMinutes": 1440}"""),
        ).andExpect(status().isOk)

        mockMvc.perform(get("/status").cookie(operatorCookie))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.pendingOncallGrant.status", equalTo("PENDING")))
            .andExpect(jsonPath("$.pendingOncallGrant.kind", equalTo("OUTAGE")))
            .andExpect(jsonPath("$.canManageOncall", equalTo(false)))
    }

    private fun createRequest(connectionId: String, cookie: jakarta.servlet.http.Cookie) = mockMvc.perform(
        post("/execution-requests/")
            .cookie(cookie)
            .contentType("application/json")
            .content(
                """
                {
                    "connectionId": "$connectionId",
                    "title": "Test Execution",
                    "type": "SingleExecution",
                    "statement": "SELECT 1",
                    "description": "A test execution request",
                    "connectionType": "DATASOURCE"
                }
                """.trimIndent(),
            ),
    )
}
