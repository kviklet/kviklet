package dev.kviklet.kviklet.security

import com.example.executiongate.TestFixtures.createDatasourceRequest
import com.example.executiongate.db.DatasourceRepository
import com.example.executiongate.service.dto.Policy
import dev.kviklet.kviklet.controller.DatasourceController
import io.kotest.matchers.collections.shouldHaveSize
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.stream.Stream

class DatasourceSecurityTest(
    @Autowired val datasourceController: dev.kviklet.kviklet.controller.DatasourceController,
    @Autowired val datasourceRepository: DatasourceRepository,
) : SecurityTestBase() {

    @BeforeEach
    fun setUp() {
        asAdmin {
            datasourceController.createDatasource(createDatasourceRequest("prod-db1"))
            datasourceController.createDatasource(createDatasourceRequest("prod-db2"))
            datasourceController.createDatasource(createDatasourceRequest("dev-db1"))
        }
    }

    @AfterEach
    fun tearDown() {
        datasourceRepository.deleteAll()
    }

    @ParameterizedTest
    @MethodSource
    fun testGetForbidden(policies: List<Policy>) {
        mockMvc.perform(get("/datasources").withContext(policies)).andExpect(status().isForbidden)
    }

    @ParameterizedTest
    @MethodSource
    fun testDeleteSuccessful(policies: List<Policy>) {
        mockMvc.perform(delete("/datasources/prod-db1").withContext(policies)).andExpect(status().isOk)
        datasourceRepository.findAll() shouldHaveSize 2
    }

    @ParameterizedTest
    @MethodSource
    fun testDeleteForbidden(policies: List<Policy>) {
        mockMvc.perform(delete("/datasources/prod-db1").withContext(policies)).andExpect(status().isForbidden)
        datasourceRepository.findAll() shouldHaveSize 3
    }

    @ParameterizedTest
    @MethodSource
    fun testCreateSuccessful(policies: List<Policy>) {
        val request = createDatasourceRequest("dev-db2")
        mockMvc.perform(post("/datasources").content(request).withContext(policies)).andExpect(status().isOk)
        datasourceRepository.findAll() shouldHaveSize 4
    }

    @ParameterizedTest
    @MethodSource
    fun testCreateForbidden(policies: List<Policy>) {
        val request = createDatasourceRequest("dev-db2")
        mockMvc.perform(post("/datasources").content(request).withContext(policies)).andExpect(status().isForbidden)
        datasourceRepository.findAll() shouldHaveSize 3
    }

    companion object {
        @JvmStatic
        fun testGetForbidden(): Stream<List<Policy>> = Stream.of(
            listOf(allow("datasource:edit", "*"), allow("datasource:create", "*"), allow("foobar", "*")),
            listOf(allow("*", "*"), deny("datasource:get", "*")),
            listOf(deny("datasource:get", "*")),
        )

        @JvmStatic
        fun testDeleteSuccessful(): Stream<List<Policy>> = Stream.of(
            listOf(allow("*", "*")),
            listOf(allow("datasource:edit", "prod-db1"), allow("datasource:get", "prod-db1")),
            listOf(allow("datasource:edit", "prod-*"), allow("datasource:get", "prod-*")),
            listOf(allow("datasource:edit", "prod-db1"), allow("datasource:get", "*")),
            listOf(allow("datasource:edit", "prod-*"), allow("datasource:get", "*")),
            listOf(allow("datasource:edit", "*"), allow("datasource:get", "*")),
            listOf(allow("datasource:*", "*")),
            listOf(allow("datasource:*", "prod-db1")),
            listOf(allow("datasource:*", "prod-*")),
        )

        @JvmStatic
        fun testDeleteForbidden(): Stream<List<Policy>> = Stream.of(
            listOf(),
            listOf(deny("*", "*")),
            listOf(allow("datasource:edit", "prod-db1")),
            listOf(allow("datasource:edit", "prod-*")),
            listOf(allow("datasource:edit", "dev-*"), allow("datasource:get", "*")),
            listOf(allow("datasource:edit", "*"), allow("datasource:get", "*"), deny("datasource:edit", "prod-db1")),
            listOf(allow("datasource:*", "*"), deny("datasource:edit", "prod-db1")),
            listOf(allow("datasource:*", "*"), deny("datasource:get", "prod-db1")),
        )

        @JvmStatic
        fun testCreateSuccessful(): Stream<List<Policy>> = Stream.of(
            listOf(allow("datasource:create", "dev-db2"), allow("datasource:get", "dev-db2")),
            listOf(allow("datasource:create", "*"), allow("datasource:get", "dev-db2")),
            listOf(allow("datasource:create", "dev-db2"), allow("datasource:get", "*")),
            listOf(allow("datasource:*", "dev-db2")),
            listOf(allow("datasource:*", "*"), deny("datasource:create", "prod*")),
        )

        @JvmStatic
        fun testCreateForbidden(): Stream<List<Policy>> = Stream.of(
            listOf(deny("*", "*")),
            listOf(allow("datasource:create", "dev-db2"), allow("datasource:get", "prod-*")),
            listOf(allow("datasource:create", "*"), allow("datasource:get", "prod-*")),
            listOf(allow("datasource:create", "*")),
            listOf(allow("datasource:get", "*")),
            listOf(allow("datasource:create", "prod-*"), allow("datasource:get", "*")),
            listOf(allow("datasource:*", "prod-*")),
            listOf(allow("datasource:create", "*"), allow("datasource:get", "*"), deny("datasource:create", "dev*")),
        )
    }
}
