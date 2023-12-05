package dev.kviklet.kviklet.security

import dev.kviklet.kviklet.TestFixtures.createDatasourceConnectionRequest
import dev.kviklet.kviklet.TestFixtures.createDatasourceRequest
import dev.kviklet.kviklet.controller.DatasourceConnectionResponse
import dev.kviklet.kviklet.controller.DatasourceController
import dev.kviklet.kviklet.controller.ListDatasourceResponse
import dev.kviklet.kviklet.controller.UpdateDataSourceConnectionRequest
import dev.kviklet.kviklet.db.DatasourceConnectionRepository
import dev.kviklet.kviklet.db.DatasourceRepository
import dev.kviklet.kviklet.service.dto.DatasourceConnectionId
import dev.kviklet.kviklet.service.dto.DatasourceId
import dev.kviklet.kviklet.service.dto.Policy
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.Optional
import java.util.stream.Stream

class DatasourceConnectionSecurityTest(
    @Autowired val datasourceController: dev.kviklet.kviklet.controller.DatasourceController,
    @Autowired val datasourceRepository: DatasourceRepository,
    @Autowired val datasourceConnectionRepository: DatasourceConnectionRepository,
) : SecurityTestBase() {

    @BeforeEach
    fun setUp() {
        asAdmin {
            datasourceController.createDatasource(createDatasourceRequest("db1"))
            datasourceController.createDatasourceConnection(
                DatasourceId("db1"),
                createDatasourceConnectionRequest("db1-conn1"),
            )
            datasourceController.createDatasourceConnection(
                DatasourceId("db1"),
                createDatasourceConnectionRequest("db1-conn2"),
            )
            datasourceController.createDatasource(createDatasourceRequest("db2"))
            datasourceController.createDatasourceConnection(
                DatasourceId("db2"),
                createDatasourceConnectionRequest("db2-conn1"),
            )
            datasourceController.createDatasourceConnection(
                DatasourceId("db2"),
                createDatasourceConnectionRequest("db2-conn2"),
            )
        }
    }

    @AfterEach
    fun tearDown() {
        datasourceRepository.deleteAll()
        datasourceConnectionRepository.deleteAll()
    }

    @ParameterizedTest
    @MethodSource
    fun testGetSuccessful(policies: List<Policy>, length: Int) {
        val response = mockMvc.perform(get("/datasources").withContext(policies)).andExpect(status().isOk)
            .andReturn().parse<dev.kviklet.kviklet.controller.ListDatasourceResponse>()

        response.databases.flatMap { it.datasourceConnections } shouldHaveSize length
    }

    @ParameterizedTest
    @MethodSource
    fun testGetForbidden(policies: List<Policy>) {
        mockMvc.perform(get("/datasources").withContext(policies)).andExpect(status().isForbidden)
    }

    @ParameterizedTest
    @MethodSource
    fun testUpdateSuccessful(policies: List<Policy>) {
        val request = dev.kviklet.kviklet.controller.UpdateDataSourceConnectionRequest(
            displayName = "new name",
        )

        val response = mockMvc.perform(
            patch("/datasources/db1/connections/db1-conn1").content(request).withContext(policies),
        )
            .andExpect(status().isOk).andReturn().parse<dev.kviklet.kviklet.controller.DatasourceConnectionResponse>()

        response.id shouldBe DatasourceConnectionId("db1-conn1")
        response.displayName shouldBe "new name"

        datasourceConnectionRepository.findById("db1-conn1").get().displayName shouldBe "new name"
    }

    @ParameterizedTest
    @MethodSource
    fun testUpdateForbidden(policies: List<Policy>) {
        val request = dev.kviklet.kviklet.controller.UpdateDataSourceConnectionRequest(
            displayName = "new name",
        )

        mockMvc.perform(patch("/datasources/db1/connections/db1-conn1").content(request).withContext(policies))
            .andExpect(status().isForbidden)

        datasourceConnectionRepository.findById("db1-conn1").get().displayName shouldBe "display name"
    }

    @ParameterizedTest
    @MethodSource
    fun testCreateDatasourceConnectionSuccessful(policies: List<Policy>) {
        val request = createDatasourceConnectionRequest("db1-conn3", displayName = "new name")
        mockMvc.perform(post("/datasources/db1/connections").content(request).withContext(policies))
            .andExpect(status().isOk)

        datasourceConnectionRepository.findById("db1-conn3").get().displayName shouldBe "new name"
    }

    @ParameterizedTest
    @MethodSource
    fun testCreateDatasourceConnectionForbidden(policies: List<Policy>) {
        val request = createDatasourceConnectionRequest("db1-conn3")
        mockMvc.perform(post("/datasources/db1/connections").content(request).withContext(policies))
            .andExpect(status().isForbidden)

        datasourceConnectionRepository.findById("db1-conn3") shouldBe Optional.empty()
    }

    companion object {
        @JvmStatic
        fun testCreateDatasourceConnectionSuccessful(): Stream<List<Policy>> = Stream.of(
            listOf(
                allow("*", "*"),
            ),
            listOf(
                allow("datasource_connection:create", "db1-conn3"),
                allow("datasource_connection:get", "db1-conn3"),
                allow("datasource:get", "db1"),
            ),
            listOf(
                allow("datasource_connection:create", "db1-*"),
                allow("datasource_connection:get", "db1-*"),
                allow("datasource:get", "db1"),
            ),
        )

        @JvmStatic
        fun testCreateDatasourceConnectionForbidden(): Stream<List<Policy>> = Stream.of(
            listOf(
                allow("datasource_connection:create", "db1-conn3"),
                allow("datasource:get", "db1"),
            ),
            listOf(
                allow("datasource_connection:create", "db1-conn3"),
                allow("datasource_connection:get", "db1-conn3"),
            ),
            listOf(
                allow("datasource_connection:get", "db1-conn3"),
                allow("datasource:get", "db1"),
            ),
        )

        @JvmStatic
        fun testUpdateSuccessful(): Stream<List<Policy>> = Stream.of(
            listOf(
                allow("datasource_connection:edit", "db1-conn1"),
                allow("datasource_connection:get", "db1-conn1"),
                allow("datasource:get", "db1"),
            ),
            listOf(allow("datasource_connection:*", "*"), allow("datasource:*", "*")),
        )

        @JvmStatic
        fun testUpdateForbidden(): Stream<List<Policy>> = Stream.of(
            listOf(
                allow("datasource_connection:get", "db1-conn1"),
                allow("datasource:get", "db1"),
            ),
            listOf(
                allow("datasource_connection:edit", "db1-conn1"),
                allow("datasource:get", "db1"),
            ),
            listOf(
                allow("datasource_connection:edit", "db1-conn1"),
                allow("datasource_connection:get", "db1-conn1"),
            ),
            listOf(
                allow("datasource_connection:edit", "db1-conn2"),
                allow("datasource_connection:get", "db1-conn1"),
                allow("datasource:get", "db1"),
            ),
            listOf(
                allow("datasource_connection:edit", "db1-conn1"),
                allow("datasource_connection:get", "db1-conn2"),
                allow("datasource:get", "db1"),
            ),
            listOf(
                allow("datasource_connection:edit", "db1-conn1"),
                allow("datasource_connection:get", "db1-conn1"),
                allow("datasource:get", "db2"),
            ),
        )

        @JvmStatic
        fun testGetSuccessful(): Stream<Arguments> = Stream.of(
            Arguments.of(listOf(allow("datasource_connection:get", "*"), allow("datasource:get", "foo")), 0),
            Arguments.of(listOf(allow("datasource_connection:get", "*"), allow("datasource:get", "db1")), 2),
            Arguments.of(listOf(allow("datasource_connection:get", "db2-*"), allow("datasource:get", "db1")), 0),
            Arguments.of(listOf(allow("datasource_connection:get", "db1-*"), allow("datasource:get", "db1")), 2),
            Arguments.of(listOf(allow("datasource_connection:get", "db1-conn1"), allow("datasource:get", "db1")), 1),
            Arguments.of(
                listOf(
                    allow("datasource_connection:get", "db1-conn1"),
                    allow("datasource_connection:get", "db1-conn2"),
                    allow("datasource:get", "db1"),
                ),
                2,
            ),
            Arguments.of(listOf(allow("datasource_connection:get", "*"), allow("datasource:get", "*")), 4),
            Arguments.of(listOf(allow("datasource_connection:get", "db*"), allow("datasource:get", "db*")), 4),
        )

        @JvmStatic
        fun testGetForbidden(): Stream<List<Policy>> = Stream.of(
            listOf(allow("datasource_connection:get", "*")),
            listOf(allow("datasource:get", "*")),
        )
    }
}
