package dev.kviklet.kviklet

import dev.kviklet.kviklet.db.ConnectionRepository
import dev.kviklet.kviklet.helper.UserHelper
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ConnectionCategoryTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val connectionRepository: ConnectionRepository,
) {
    @Autowired
    private lateinit var userHelper: UserHelper

    @AfterEach
    fun tearDown() {
        connectionRepository.deleteAllInBatch()
        userHelper.deleteAll()
    }

    @Test
    fun `create connection with category and verify it is returned`() {
        userHelper.createUser(listOf("*"))
        val cookie = userHelper.login(mockMvc = mockMvc)

        val createJson = """
            {
                "connectionType": "DATASOURCE",
                "id": "category-test-conn",
                "displayName": "Category Test Connection",
                "username": "root",
                "password": "root",
                "type": "MYSQL",
                "hostname": "localhost",
                "port": 3306,
                "category": "Production",
                "reviewConfig": {
                    "numTotalRequired": 1
                }
            }
        """.trimIndent()

        mockMvc.perform(
            post("/connections/")
                .cookie(cookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createJson),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.category").value("Production"))

        // Verify persistence by fetching the connection
        mockMvc.perform(
            get("/connections/category-test-conn")
                .cookie(cookie),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.category").value("Production"))
    }

    @Test
    fun `update connection category via PATCH`() {
        userHelper.createUser(listOf("*"))
        val cookie = userHelper.login(mockMvc = mockMvc)

        // Create connection with category "Development"
        val createJson = """
            {
                "connectionType": "DATASOURCE",
                "id": "update-category-conn",
                "displayName": "Update Category Test",
                "username": "root",
                "password": "root",
                "type": "MYSQL",
                "hostname": "localhost",
                "port": 3306,
                "category": "Development",
                "reviewConfig": {
                    "numTotalRequired": 1
                }
            }
        """.trimIndent()

        mockMvc.perform(
            post("/connections/")
                .cookie(cookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createJson),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.category").value("Development"))

        // Update category to "Production"
        val updateJson = """
            {
                "connectionType": "DATASOURCE",
                "category": "Production"
            }
        """.trimIndent()

        mockMvc.perform(
            patch("/connections/update-category-conn")
                .cookie(cookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateJson),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.category").value("Production"))
    }

    @Test
    fun `GET connections categories returns distinct sorted categories`() {
        userHelper.createUser(listOf("*"))
        val cookie = userHelper.login(mockMvc = mockMvc)

        // Create connection with category "Production"
        val createJson1 = """
            {
                "connectionType": "DATASOURCE",
                "id": "conn-prod-1",
                "displayName": "Production Connection 1",
                "username": "root",
                "password": "root",
                "type": "MYSQL",
                "hostname": "localhost",
                "port": 3306,
                "category": "Production",
                "reviewConfig": {
                    "numTotalRequired": 1
                }
            }
        """.trimIndent()

        // Create connection with category "Development"
        val createJson2 = """
            {
                "connectionType": "DATASOURCE",
                "id": "conn-dev-1",
                "displayName": "Development Connection 1",
                "username": "root",
                "password": "root",
                "type": "MYSQL",
                "hostname": "localhost",
                "port": 3306,
                "category": "Development",
                "reviewConfig": {
                    "numTotalRequired": 1
                }
            }
        """.trimIndent()

        // Create another connection with category "Production" (duplicate)
        val createJson3 = """
            {
                "connectionType": "DATASOURCE",
                "id": "conn-prod-2",
                "displayName": "Production Connection 2",
                "username": "root",
                "password": "root",
                "type": "MYSQL",
                "hostname": "localhost",
                "port": 3306,
                "category": "Production",
                "reviewConfig": {
                    "numTotalRequired": 1
                }
            }
        """.trimIndent()

        // Create connection with no category (null)
        val createJson4 = """
            {
                "connectionType": "DATASOURCE",
                "id": "conn-no-category",
                "displayName": "No Category Connection",
                "username": "root",
                "password": "root",
                "type": "MYSQL",
                "hostname": "localhost",
                "port": 3306,
                "reviewConfig": {
                    "numTotalRequired": 1
                }
            }
        """.trimIndent()

        // Create all connections
        mockMvc.perform(
            post("/connections/")
                .cookie(cookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createJson1),
        ).andExpect(status().isOk)

        mockMvc.perform(
            post("/connections/")
                .cookie(cookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createJson2),
        ).andExpect(status().isOk)

        mockMvc.perform(
            post("/connections/")
                .cookie(cookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createJson3),
        ).andExpect(status().isOk)

        mockMvc.perform(
            post("/connections/")
                .cookie(cookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createJson4),
        ).andExpect(status().isOk)

        // Get categories and verify sorted, distinct, nulls excluded
        mockMvc.perform(
            get("/connections/categories")
                .cookie(cookie),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isArray)
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0]").value("Development"))
            .andExpect(jsonPath("$[1]").value("Production"))
    }
}
