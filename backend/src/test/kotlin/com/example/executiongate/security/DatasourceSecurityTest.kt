package com.example.executiongate.security

import com.example.executiongate.controller.CreateDatasourceRequest
import com.example.executiongate.controller.DatasourceController
import com.example.executiongate.controller.ListDatasourceResponse
import com.example.executiongate.db.DatasourceRepository
import com.example.executiongate.service.dto.DatasourceType
import com.example.executiongate.service.dto.Policy
import io.kotest.matchers.collections.shouldHaveSize
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.stream.Stream

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DatasourceSecurityTest(
    @Autowired val datasourceController: DatasourceController,
    @Autowired val datasourceRepository: DatasourceRepository,
) : SecurityTestBase() {

    @BeforeEach
    fun setUp() {
        setAuthentication(listOf(allow("*", "*")))
        datasourceController.createDatasource(
            CreateDatasourceRequest(
                id = "prod-db1",
                displayName = "prod db1",
                datasourceType = DatasourceType.MYSQL,
                hostname = "localhost",
                port = 3306,
            ),
        )
        datasourceController.createDatasource(
            CreateDatasourceRequest(
                id = "prod-db2",
                displayName = "prod db2",
                datasourceType = DatasourceType.MYSQL,
                hostname = "localhost",
                port = 3306,
            ),
        )
        datasourceController.createDatasource(
            CreateDatasourceRequest(
                id = "dev-db1",
                displayName = "dev db1",
                datasourceType = DatasourceType.MYSQL,
                hostname = "localhost",
                port = 3306,
            ),
        )
        SecurityContextHolder.clearContext()
    }

    @AfterEach
    fun tearDown() {
        datasourceRepository.deleteAll()
    }

    @ParameterizedTest
    @MethodSource
    fun testGetSuccessful(policies: List<Policy>, length: Int) {
        val response = mockMvc.perform(get("/datasources").withContext(policies)).andExpect(status().isOk)
            .andReturn().parse<ListDatasourceResponse>()
        response.databases shouldHaveSize length
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
        val request = CreateDatasourceRequest(
            id = "dev-db2",
            displayName = "dev db2",
            datasourceType = DatasourceType.MYSQL,
            hostname = "localhost",
            port = 3306,
        )
        mockMvc.perform(post("/datasources").content(request).withContext(policies)).andExpect(status().isOk)
        datasourceRepository.findAll() shouldHaveSize 4
    }

    @ParameterizedTest
    @MethodSource
    fun testCreateForbidden(policies: List<Policy>) {
        val request = CreateDatasourceRequest(
            id = "dev-db2",
            displayName = "dev db2",
            datasourceType = DatasourceType.MYSQL,
            hostname = "localhost",
            port = 3306,
        )
        mockMvc.perform(post("/datasources").content(request).withContext(policies)).andExpect(status().isForbidden)
        datasourceRepository.findAll() shouldHaveSize 3
    }

    companion object {
        @JvmStatic
        fun testGetSuccessful(): Stream<Arguments> = Stream.of(
            Arguments.of(listOf(allow("*", "*")), 3),
            Arguments.of(listOf(allow("datasource:*", "*")), 3),
            Arguments.of(listOf(allow("datasource:get", "*")), 3),
            Arguments.of(listOf(allow("datasource:get", "prod-*")), 2),
            Arguments.of(listOf(allow("datasource:get", "foobar")), 0),
            Arguments.of(listOf(allow("datasource:get", "dev-db1")), 1),
            Arguments.of(listOf(allow("datasource:get", "*"), deny("datasource:get", "prod-db*")), 1),
        )

        @JvmStatic
        fun testGetForbidden(): Stream<List<Policy>> = Stream.of(
            listOf(allow("datasource:edit", "*"), allow("datasource:create", "*"), allow("foobar", "*")),
            listOf(allow("*", "*"), deny("datasource:get", "*")),
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
