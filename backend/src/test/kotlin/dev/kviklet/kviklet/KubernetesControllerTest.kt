package dev.kviklet.kviklet

import com.ninjasquad.springmockk.MockkBean
import dev.kviklet.kviklet.db.ExecutionRequestAdapter
import dev.kviklet.kviklet.helper.ConnectionHelper
import dev.kviklet.kviklet.helper.RoleHelper
import dev.kviklet.kviklet.helper.UserHelper
import dev.kviklet.kviklet.security.Permission
import dev.kviklet.kviklet.shell.KubernetesApi
import io.kubernetes.client.openapi.models.V1Container
import io.kubernetes.client.openapi.models.V1ObjectMeta
import io.kubernetes.client.openapi.models.V1Pod
import io.kubernetes.client.openapi.models.V1PodSpec
import io.kubernetes.client.openapi.models.V1PodStatus
import io.mockk.every
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
class KubernetesControllerTest {

    @MockkBean
    private lateinit var kubernetesApi: KubernetesApi

    @Autowired
    private lateinit var executionRequestAdapter: ExecutionRequestAdapter

    @Autowired
    private lateinit var userHelper: UserHelper

    @Autowired
    private lateinit var roleHelper: RoleHelper

    @Autowired
    private lateinit var connectionHelper: ConnectionHelper

    @Autowired
    lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setup() {
        val mockPods = mutableListOf(
            V1Pod().apply {
                metadata = V1ObjectMeta().apply {
                    name = "test-pod"
                    namespace = "default"
                    uid = "12345"
                }
                status = V1PodStatus().apply {
                    phase = "Running"
                }
                spec = V1PodSpec().apply {
                    containers = listOf(V1Container().apply { name = "test-container" })
                }
            },
        )

        every { kubernetesApi.getActivePods() } returns mockPods
    }

    @AfterEach
    fun tearDown() {
        executionRequestAdapter.deleteAll()
        connectionHelper.deleteAll()
        userHelper.deleteAll()
        roleHelper.deleteAll()
    }

    @Test
    fun `get pods returns list from kubernetes API`() {
        userHelper.createUser(permissions = listOf("*"))
        val cookie = userHelper.login(mockMvc = mockMvc)
        mockMvc.perform(
            MockMvcRequestBuilders.get("/kubernetes/pods")
                .cookie(cookie),
        ).andExpect(MockMvcResultMatchers.status().isOk).andExpect(
            MockMvcResultMatchers.content().json(
                """
                {
                    "pods": [
                        {
                            "id": "12345",
                            "name": "test-pod",
                            "namespace": "default",
                            "status": "Running",
                            "containerNames": ["test-container"]
                        }
                    ]
                }
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun `get pods returns list with only edit executionrequest permissions`() {
        userHelper.createUser(permissions = listOf(Permission.EXECUTION_REQUEST_EDIT.getPermissionString()))
        val cookie = userHelper.login(mockMvc = mockMvc)
        mockMvc.perform(
            MockMvcRequestBuilders.get("/kubernetes/pods")
                .cookie(cookie),
        ).andExpect(MockMvcResultMatchers.status().isOk).andExpect(
            MockMvcResultMatchers.content().json(
                """
                {
                    "pods": [
                        {
                            "id": "12345",
                            "name": "test-pod",
                            "namespace": "default",
                            "status": "Running",
                            "containerNames": ["test-container"]
                        }
                    ]
                }
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun `get pods doesnt return list with only get executionrequest permissions`() {
        userHelper.createUser(permissions = listOf(Permission.EXECUTION_REQUEST_GET.getPermissionString()))
        val cookie = userHelper.login(mockMvc = mockMvc)
        mockMvc.perform(
            MockMvcRequestBuilders.get("/kubernetes/pods")
                .cookie(cookie),
        ).andExpect(MockMvcResultMatchers.status().isForbidden)
    }
}
