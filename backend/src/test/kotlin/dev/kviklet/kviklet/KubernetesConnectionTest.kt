package dev.kviklet.kviklet

import dev.kviklet.kviklet.db.ExecutionRequestAdapter
import dev.kviklet.kviklet.helper.ConnectionHelper
import dev.kviklet.kviklet.helper.RoleHelper
import dev.kviklet.kviklet.helper.UserHelper
import dev.kviklet.kviklet.security.Permission
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class KubernetesConnectionTest {

    @Autowired
    private lateinit var roleHelper: RoleHelper

    @Autowired
    private lateinit var executionRequestAdapter: ExecutionRequestAdapter

    @Autowired
    private lateinit var userHelper: UserHelper

    @Autowired
    private lateinit var connectionHelper: ConnectionHelper

    @Autowired
    lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setup() {
    }

    @AfterEach
    fun tearDown() {
        executionRequestAdapter.deleteAll()
        connectionHelper.deleteAll()
        userHelper.deleteAll()
        roleHelper.deleteAll()
    }

    @Test
    fun `create Kubernetes Connection`() {
        userHelper.createUser(permissions = listOf("*"))
        val cookie = userHelper.login(mockMvc = mockMvc)
        mockMvc.perform(
            MockMvcRequestBuilders.post("/connections/").cookie(cookie).content(
                """
                {
                    "connectionType": "KUBERNETES",
                    "id": "kubernetes-connection",
                    "displayName": "Test Kubernetes Connect",
                    "description": "A generic Kubernetes Connection",
                    "reviewConfig": {
                        "numTotalRequired": 1
                    }
                }
                """.trimIndent(),
            ).contentType("application/json"),
        ).andExpect(MockMvcResultMatchers.status().isOk)
    }

    @Test
    fun `create Kubernetes Connection with only connection permissions`() {
        userHelper.createUser(
            permissions = listOf(
                Permission.DATASOURCE_CONNECTION_GET.getPermissionString(),
                Permission.DATASOURCE_CONNECTION_CREATE.getPermissionString(),
            ),
        )
        val cookie = userHelper.login(mockMvc = mockMvc)
        mockMvc.perform(
            MockMvcRequestBuilders.post("/connections/").cookie(cookie).content(
                """
                {
                    "connectionType": "KUBERNETES",
                    "id": "kubernetes-connection",
                    "displayName": "Test Kubernetes Connect",
                    "description": "A generic Kubernetes Connection",
                    "reviewConfig": {
                        "numTotalRequired": 1
                    }
                }
                """.trimIndent(),
            ).contentType("application/json"),
        ).andExpect(MockMvcResultMatchers.status().isOk)
    }

    @Test
    fun `create Kubernetes Connection with wrong permissions fails`() {
        userHelper.createUser(
            permissions = listOf(
                Permission.DATASOURCE_CONNECTION_GET.getPermissionString(),
            ),
        )
        val cookie = userHelper.login(mockMvc = mockMvc)
        mockMvc.perform(
            MockMvcRequestBuilders.post("/connections/").cookie(cookie).content(
                """
                {
                    "connectionType": "KUBERNETES",
                    "id": "kubernetes-connection",
                    "displayName": "Test Kubernetes Connect",
                    "description": "A generic Kubernetes Connection",
                    "reviewConfig": {
                        "numTotalRequired": 1
                    }
                }
                """.trimIndent(),
            ).contentType("application/json"),
        ).andExpect(MockMvcResultMatchers.status().isForbidden)
    }
}
