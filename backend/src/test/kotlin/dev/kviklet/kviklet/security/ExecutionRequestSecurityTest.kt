package dev.kviklet.kviklet.security

import dev.kviklet.kviklet.TestFixtures.createDatasourceConnectionRequest
import dev.kviklet.kviklet.TestFixtures.createDatasourceRequest
import dev.kviklet.kviklet.TestFixtures.createExecutionRequestRequest
import dev.kviklet.kviklet.TestFixtures.updateExecutionRequestRequest
import dev.kviklet.kviklet.controller.DatasourceConnectionController
import dev.kviklet.kviklet.controller.DatasourceController
import dev.kviklet.kviklet.controller.ExecutionRequestController
import dev.kviklet.kviklet.controller.ExecutionRequestDetailResponse
import dev.kviklet.kviklet.controller.ExecutionRequestResponse
import dev.kviklet.kviklet.db.DatasourceConnectionRepository
import dev.kviklet.kviklet.db.DatasourceRepository
import dev.kviklet.kviklet.db.ExecutionRequestRepository
import dev.kviklet.kviklet.service.dto.DatasourceId
import dev.kviklet.kviklet.service.dto.Policy
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.stream.Stream

class ExecutionRequestSecurityTest(
    @Autowired val datasourceController: DatasourceController,
    @Autowired val datasourceConnectionController: DatasourceConnectionController,
    @Autowired val executionRequestController: ExecutionRequestController,
    @Autowired val executionRequestRepository: ExecutionRequestRepository,
    @Autowired val datasourceRepository: DatasourceRepository,
    @Autowired val datasourceConnectionRepository: DatasourceConnectionRepository,
) : SecurityTestBase() {

    private lateinit var executionRequest: ExecutionRequestResponse

    @BeforeEach
    fun setUp() {
        asAdmin {
            datasourceController.createDatasource(createDatasourceRequest("db1"))
            datasourceConnectionController.createDatasourceConnection(
                DatasourceId("db1").toString(),
                createDatasourceConnectionRequest("db1-conn1"),
            )
            executionRequest = executionRequestController
                .create(createExecutionRequestRequest("db1-conn1"), testUserDetails)
        }
    }

    @AfterEach
    fun tearDown() {
        datasourceRepository.deleteAll()
        datasourceConnectionRepository.deleteAll()
        executionRequestRepository.deleteAll()
    }

    @ParameterizedTest
    @MethodSource
    fun testUpdateSuccessful(policies: List<Policy>, userId: String?) {
        val request = updateExecutionRequestRequest("select 2")
        val userDetails = UserDetailsWithId(
            id = userId ?: testUserDetails.id,
            email = "user2@example.com",
            password = "foobar",
            authorities = emptyList(),
        )

        val response = mockMvc.perform(
            patch("/execution-requests/${executionRequest.id}").content(request).withContext(policies, userDetails),
        ).andExpect(status().isOk)
            .andReturn().parse<ExecutionRequestDetailResponse>()

        response.statement shouldBe "select 2"
        executionRequestRepository.findById(executionRequest.id.toString()).get().statement shouldBe "select 2"
    }

    @ParameterizedTest
    @MethodSource
    fun testUpdateForbidden(policies: List<Policy>, userId: String?) {
        val request = updateExecutionRequestRequest("select 2")
        val userDetails = UserDetailsWithId(
            id = userId ?: testUserDetails.id,
            email = "user2@example.com",
            password = "foobar",
            authorities = emptyList(),
        )

        mockMvc.perform(
            patch("/execution-requests/${executionRequest.id}").content(request).withContext(policies, userDetails),
        ).andExpect(status().isForbidden)

        executionRequestRepository.findById(executionRequest.id.toString()).get().statement shouldBe "select 1"
    }

    companion object {
        @JvmStatic
        fun testUpdateSuccessful(): Stream<Arguments> = Stream.of(
            Arguments.of(listOf(allow("*", "*")), null),
            Arguments.of(listOf(allow("datasource_connection:get", "*"), allow("datasource:get", "*")), null),
            Arguments.of(listOf(allow("datasource_connection:get", "db1-conn1"), allow("datasource:get", "db1")), null),
        )

        @JvmStatic
        fun testUpdateForbidden(): Stream<Arguments> = Stream.of(
            Arguments.of(listOf(allow("*", "*")), "userid2"),
            Arguments.of(listOf(allow("datasource_connection:get", "db2-conn1"), allow("datasource:get", "db2")), null),
            Arguments.of(listOf(allow("datasource_connection:get", "*"), allow("datasource:get", "db2")), null),
            Arguments.of(listOf(allow("datasource_connection:get", "db2-conn1"), allow("datasource:get", "*")), null),
        )
    }
}
