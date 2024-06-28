package dev.kviklet.kviklet.security

import com.fasterxml.jackson.databind.ObjectMapper
import dev.kviklet.kviklet.TestFixtures.createDatasourceConnectionRequest
import dev.kviklet.kviklet.TestFixtures.createExecutionRequestRequest
import dev.kviklet.kviklet.TestFixtures.updateExecutionRequestRequest
import dev.kviklet.kviklet.controller.ConnectionController
import dev.kviklet.kviklet.controller.DatasourceExecutionRequestDetailResponse
import dev.kviklet.kviklet.controller.ExecutionRequestController
import dev.kviklet.kviklet.controller.ExecutionRequestResponse
import dev.kviklet.kviklet.db.ConnectionRepository
import dev.kviklet.kviklet.db.ExecutionRequestRepository
import dev.kviklet.kviklet.helper.UserHelper
import dev.kviklet.kviklet.service.dto.Policy
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.lang.reflect.Field
import java.util.stream.Stream

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class ExecutionRequestSecurityTest(
    @Autowired val connectionController: ConnectionController,
    @Autowired val executionRequestController: ExecutionRequestController,
    @Autowired val executionRequestRepository: ExecutionRequestRepository,
    @Autowired val connectionRepository: ConnectionRepository,
) {

    private lateinit var executionRequest: ExecutionRequestResponse

    @Autowired
    private lateinit var userHelper: UserHelper

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    fun setupConnectionAndRequest(userDetailsWithId: UserDetailsWithId) {
        connectionController.createConnection(
            createDatasourceConnectionRequest("db1-conn1"),
        )
        executionRequest = executionRequestController
            .create(createExecutionRequestRequest("db1-conn1"), userDetailsWithId)
    }

    @AfterEach
    fun tearDown() {
        connectionRepository.deleteAllInBatch()
        executionRequestRepository.deleteAllInBatch()
        userHelper.deleteAll()
    }

    @ParameterizedTest
    @MethodSource
    fun testUpdateSuccessful(policies: List<Policy>) {
        val testUser = userHelper.createUser(policies = policies.toSet())
        val userDetails = UserDetailsWithId(
            id = testUser.getId()!!,
            email = testUser.email,
            password = testUser.password,
            authorities = emptyList(),
        )
        val cookie = userHelper.login(mockMvc = mockMvc)
        setupConnectionAndRequest(userDetails)
        val request = updateExecutionRequestRequest("select 2")

        val response = mockMvc.perform(
            patch("/execution-requests/${executionRequest.id}").content(request).cookie(cookie),
        ).andExpect(status().isOk)
            .andReturn().parse<DatasourceExecutionRequestDetailResponse>()

        response.statement shouldBe "select 2"
        executionRequestRepository.findById(executionRequest.id.toString()).get().statement shouldBe "select 2"
    }

    fun setField(obj: Any, fieldName: String, value: Any) {
        val field: Field = obj.javaClass.superclass.getDeclaredField(fieldName)
        field.isAccessible = true
        field.set(obj, value)
    }

    @ParameterizedTest
    @MethodSource
    fun testUpdateForbidden(policies: List<Policy>) {
        val testUser = userHelper.createUser(policies = policies.toSet())
        val userDetails = UserDetailsWithId(
            id = testUser.getId()!!,
            email = testUser.email,
            password = testUser.password,
            authorities = emptyList(),
        )
        setupConnectionAndRequest(userDetails)
        val cookie = userHelper.login(mockMvc = mockMvc)
        val request = updateExecutionRequestRequest("select 2")

        mockMvc.perform(
            patch("/execution-requests/${executionRequest.id}").content(request).cookie(cookie),
        ).andExpect(status().isForbidden)

        executionRequestRepository.findById(executionRequest.id.toString()).get().statement shouldBe "select 1"
    }

    companion object {
        @JvmStatic
        fun testUpdateSuccessful(): Stream<Arguments> = Stream.of(
            Arguments.of(listOf(allow("*", "*"))),
            Arguments.of(
                listOf(
                    allow("datasource_connection:get", "*"),
                    allow("execution_request:edit", "*"),
                    allow("execution_request:get", "*"),
                ),
            ),
            Arguments.of(
                listOf(
                    allow("datasource_connection:get", "db1-conn1"),
                    allow("execution_request:edit", "*"),
                    allow("execution_request:get", "*"),
                ),
            ),
        )

        @JvmStatic
        fun testUpdateForbidden(): Stream<Arguments> = Stream.of(
            Arguments.of(listOf(allow("datasource_connection:get", "db2-conn1"), allow("datasource:get", "db2"))),
            Arguments.of(listOf(allow("datasource_connection:get", "*"), allow("datasource:get", "db2"))),
            Arguments.of(listOf(allow("datasource_connection:get", "db2-conn1"), allow("datasource:get", "*"))),
        )
    }

    final inline fun <reified T> MvcResult.parse(): T = objectMapper.readValue(response.contentAsString, T::class.java)

    final inline fun <reified T> MockHttpServletRequestBuilder.content(obj: T) =
        this.contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(obj))
}
