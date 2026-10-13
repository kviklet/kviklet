package dev.kviklet.kviklet

import dev.kviklet.kviklet.controller.CreateDatasourceExecutionRequestRequest
import dev.kviklet.kviklet.db.UserAdapter
import dev.kviklet.kviklet.helper.ConnectionHelper
import dev.kviklet.kviklet.helper.ExecutionRequestHelper
import dev.kviklet.kviklet.helper.UserHelper
import dev.kviklet.kviklet.security.ApiKeyAuthentication
import dev.kviklet.kviklet.security.PolicyGrantedAuthority
import dev.kviklet.kviklet.security.UserDetailsWithId
import dev.kviklet.kviklet.security.currentRequestSource
import dev.kviklet.kviklet.security.mcp.McpAuthentication
import dev.kviklet.kviklet.service.ExecutionRequestService
import dev.kviklet.kviklet.service.dto.RequestSource
import dev.kviklet.kviklet.service.dto.RequestType
import dev.kviklet.kviklet.service.dto.Role
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("test")
class RequestSourceTest {

    @Autowired private lateinit var executionRequestService: ExecutionRequestService

    @Autowired private lateinit var executionRequestHelper: ExecutionRequestHelper

    @Autowired private lateinit var connectionHelper: ConnectionHelper

    @Autowired private lateinit var userHelper: UserHelper

    @Autowired private lateinit var userAdapter: UserAdapter

    private val principal = UserDetailsWithId("user", "user@example.com", "", emptyList())

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
        executionRequestHelper.deleteAll()
        connectionHelper.deleteAll()
        userHelper.deleteAll()
    }

    @Test
    fun `the source follows how the caller authenticated`() {
        authenticate(ApiKeyAuthentication(principal, emptyList()))
        assertThat(currentRequestSource()).isEqualTo(RequestSource.API_KEY)

        authenticate(McpAuthentication(principal, emptyList()))
        assertThat(currentRequestSource()).isEqualTo(RequestSource.MCP)

        authenticate(UsernamePasswordAuthenticationToken(principal, null, emptyList()))
        assertThat(currentRequestSource()).isEqualTo(RequestSource.WEB)
    }

    @Test
    fun `calls from within the application without a caller count as the web UI`() {
        SecurityContextHolder.clearContext()

        assertThat(currentRequestSource()).isEqualTo(RequestSource.WEB)
    }

    @Test
    fun `a request can be created with a source other than the caller's`() {
        val connection = connectionHelper.createDummyConnection()
        val user = userAdapter.findById(userHelper.createUser().getId()!!)
        val policies = user.roles.flatMap(Role::policies).map(::PolicyGrantedAuthority)
        authenticate(
            UsernamePasswordAuthenticationToken(
                UserDetailsWithId(user.getId()!!, user.email, "", policies),
                null,
                policies,
            ),
        )

        val request = executionRequestService.create(
            connection.id,
            CreateDatasourceExecutionRequestRequest(
                connectionId = connection.id,
                title = "From the proxy",
                type = RequestType.SingleExecution,
                description = "",
                statement = "SELECT 1",
            ),
            user.getId()!!,
            source = RequestSource.PROXY,
        )

        assertThat(request.request.source).isEqualTo(RequestSource.PROXY)
    }

    private fun authenticate(authentication: UsernamePasswordAuthenticationToken) {
        SecurityContextHolder.getContext().authentication = authentication
    }
}
