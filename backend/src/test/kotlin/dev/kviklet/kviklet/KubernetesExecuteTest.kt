package dev.kviklet.kviklet

import com.ninjasquad.springmockk.MockkBean
import dev.kviklet.kviklet.db.ExecutionRequestAdapter
import dev.kviklet.kviklet.helper.ConnectionHelper
import dev.kviklet.kviklet.helper.ExecutionRequestHelper
import dev.kviklet.kviklet.helper.RoleHelper
import dev.kviklet.kviklet.helper.UserHelper
import dev.kviklet.kviklet.shell.KubernetesApi
import dev.kviklet.kviklet.shell.KubernetesResult
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
class KubernetesExecuteTest {

    @MockkBean
    private lateinit var kubernetesApi: KubernetesApi

    @Autowired
    private lateinit var roleHelper: RoleHelper

    @Autowired
    private lateinit var executionRequestAdapter: ExecutionRequestAdapter

    @Autowired
    private lateinit var userHelper: UserHelper

    @Autowired
    private lateinit var connectionHelper: ConnectionHelper

    @Autowired
    private lateinit var executionRequestHelper: ExecutionRequestHelper

    @Autowired
    lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setup() {
        val mockExecuteResponse = KubernetesResult(
            errors = listOf("some-error"),
            messages = listOf("some-message"),
            finished = true,
            exitCode = 0,
        )

        every {
            kubernetesApi.executeCommandOnPod(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
            )
        } returns mockExecuteResponse
    }

    @AfterEach
    fun tearDown() {
        executionRequestAdapter.deleteAll()
        connectionHelper.deleteAll()
        userHelper.deleteAll()
        roleHelper.deleteAll()
    }

    @Test
    fun `test executing a kubernetes execution request`() {
        val user = userHelper.createUser(permissions = listOf("*"))
        val approver = userHelper.createUser(permissions = listOf("*"))
        val cookie = userHelper.login(mockMvc = mockMvc)
        val executionRequest = executionRequestHelper.createApprovedKubernetesExecutionRequest(
            user,
            approver,
        )

        mockMvc.perform(
            MockMvcRequestBuilders.post("/execution-requests/${executionRequest.getId()}/execute")
                .cookie(cookie),
        ).andExpect(MockMvcResultMatchers.status().isOk).andExpect(
            MockMvcResultMatchers.jsonPath("$.errors").value("some-error"),
        ).andExpect(
            MockMvcResultMatchers.jsonPath("$.messages").value("some-message"),
        ).andExpect(
            MockMvcResultMatchers.jsonPath("$.finished").value(true),
        ).andExpect(
            MockMvcResultMatchers.jsonPath("$.exitCode").value(0),
        )
    }
}
