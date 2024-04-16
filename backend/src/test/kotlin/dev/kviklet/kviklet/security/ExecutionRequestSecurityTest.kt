package dev.kviklet.kviklet.security

import dev.kviklet.kviklet.TestFixtures.createDatasourceConnectionRequest
import dev.kviklet.kviklet.TestFixtures.createExecutionRequestRequest
import dev.kviklet.kviklet.TestFixtures.updateExecutionRequestRequest
import dev.kviklet.kviklet.controller.ConnectionController
import dev.kviklet.kviklet.controller.ExecutionRequestController
import dev.kviklet.kviklet.controller.ExecutionRequestDetailResponse
import dev.kviklet.kviklet.controller.ExecutionRequestResponse
import dev.kviklet.kviklet.db.ConnectionRepository
import dev.kviklet.kviklet.db.ExecutionRequestRepository
import dev.kviklet.kviklet.db.UserEntity
import dev.kviklet.kviklet.service.dto.Policy
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.lang.reflect.Field
import java.util.stream.Stream

@ActiveProfiles("test")
class ExecutionRequestSecurityTest(
    @Autowired val connectionController: ConnectionController,
    @Autowired val executionRequestController: ExecutionRequestController,
    @Autowired val executionRequestRepository: ExecutionRequestRepository,
    @Autowired val connectionRepository: ConnectionRepository,
) : SecurityTestBase() {

    private lateinit var executionRequest: ExecutionRequestResponse

    @BeforeEach
    fun setUp() {
        asAdmin {
            connectionController.createConnection(
                createDatasourceConnectionRequest("db1-conn1"),
            )
            executionRequest = executionRequestController
                .create(createExecutionRequestRequest("db1-conn1"), testUserDetails)
        }
    }

    @AfterEach
    fun tearDown() {
        connectionRepository.deleteAllInBatch()
        executionRequestRepository.deleteAllInBatch()
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

    fun setField(obj: Any, fieldName: String, value: Any) {
        val field: Field = obj.javaClass.superclass.getDeclaredField(fieldName)
        field.isAccessible = true
        field.set(obj, value)
    }

    @ParameterizedTest
    @MethodSource
    fun testUpdateForbidden(policies: List<Policy>, userId: String?) {
        val user = UserEntity(
            email = "user2@example.com",
            fullName = "User 2",
            password = passwordEncoder.encode("foobar"),
        )
        val savedUser = userRepository.saveAndFlush(user)
        val request = updateExecutionRequestRequest("select 2")
        val userDetails = UserDetailsWithId(
            id = savedUser.id!!,
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
            Arguments.of(
                listOf(
                    allow("datasource_connection:get", "*"),
                    allow("execution_request:edit", "*"),
                    allow("execution_request:get", "*"),
                ),
                null,
            ),
            Arguments.of(
                listOf(
                    allow("datasource_connection:get", "db1-conn1"),
                    allow("execution_request:edit", "*"),
                    allow("execution_request:get", "*"),
                ),
                null,
            ),
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
