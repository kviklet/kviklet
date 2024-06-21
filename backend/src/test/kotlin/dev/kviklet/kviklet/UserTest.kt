package dev.kviklet.kviklet

import dev.kviklet.kviklet.helper.RoleHelper
import dev.kviklet.kviklet.helper.UserHelper
import dev.kviklet.kviklet.service.dto.Role
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.CoreMatchers.notNullValue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UserTest {

    @Autowired
    private lateinit var roleHelper: RoleHelper

    @Autowired
    private lateinit var userHelper: UserHelper

    @Autowired
    lateinit var mockMvc: MockMvc

    @AfterEach
    fun tearDown() {
        userHelper.deleteAll()
        roleHelper.deleteAll()
    }

    fun successPermissions() = listOf(
        Arguments.of(listOf("*"), listOf("*")),
        Arguments.of(listOf("user:create", "user:get"), listOf("*", "*")),
    )

    @ParameterizedTest
    @MethodSource("successPermissions")
    fun createUser(permissions: List<String>, resources: List<String>) {
        userHelper.createUser(permissions = permissions, resources = resources)
        val cookie = userHelper.login(mockMvc = mockMvc)

        mockMvc.perform(
            post("/users/").cookie(cookie).content(
                """
                {
                    "email": "someemail@email.de",
                    "password": "123456",
                    "fullName": "Some User"
                }
                """.trimIndent(),
            ).contentType("application/json"),
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.id", notNullValue()))
            .andExpect(jsonPath("$.email", `is`("someemail@email.de")))
            .andExpect(jsonPath("$.fullName", `is`("Some User")))
            .andExpect(jsonPath("$.roles.length()", `is`(1))) // Should already have the default role
            .andExpect(jsonPath("$.password").doesNotExist())
    }

    @Test
    fun `cant create user with email that already exists`() {
        val user = userHelper.createUser()
        val cookie = userHelper.login(mockMvc = mockMvc)

        mockMvc.perform(
            post("/users/").cookie(cookie).content(
                """
                {
                    "email": "${user.email}",
                    "password": "123456",
                    "fullName": "Some User"
                }
                """.trimIndent(),
            ).contentType("application/json"),
        ).andExpect(status().isBadRequest)
    }

    fun failPermissions() = listOf(
        Arguments.of(listOf("user:get"), listOf("*")),
        Arguments.of(listOf("user:edit", "user:get"), listOf("*", "*")),
    )

    @ParameterizedTest
    @MethodSource("failPermissions")
    fun createUserFails(permissions: List<String>, resources: List<String>) {
        userHelper.createUser(permissions = permissions, resources = resources)
        val cookie = userHelper.login(mockMvc = mockMvc)

        mockMvc.perform(
            post("/users/").cookie(cookie).content(
                """
                {
                    "email": "someemail@email.de",
                    "password": "123456",
                    "fullName": "Some User"
                }
                """.trimIndent(),
            ).contentType("application/json"),
        ).andExpect(status().isForbidden)
    }

    fun editPermissions() = listOf(
        Arguments.of(listOf("*"), listOf("*")),
        Arguments.of(listOf("user:edit", "user:get"), listOf("*", "*")),
    )

    @ParameterizedTest
    @MethodSource("editPermissions")
    fun editUser(permissions: List<String>, resources: List<String>) {
        val user = userHelper.createUser(permissions = permissions, resources = resources)
        val cookie = userHelper.login(mockMvc = mockMvc)

        mockMvc.perform(
            patch("/users/${user.getId()}").cookie(cookie).content(
                """
                {
                    "email": "newemail@email.de"
                }
                """.trimIndent(),
            ).contentType("application/json"),
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.id", `is`(user.getId())))
            .andExpect(jsonPath("$.email", `is`("newemail@email.de")))
            .andExpect(jsonPath("$.fullName", `is`(user.fullName)))
            .andExpect(jsonPath("$.roles.length()", `is`(2)))
            .andExpect(jsonPath("$.password").doesNotExist())
    }

    @Test
    fun `not allowed to edit other user`() {
        val testUser = userHelper.createUser(listOf("user:edit", "user:get"), listOf("*", "*"))
        val cookie = userHelper.login(mockMvc = mockMvc)
        val user = userHelper.createUser(
            permissions = listOf("*"),
            email = "secondUser@email.de",
            password = "123456",
            fullName = "Second User",
        )

        mockMvc.perform(
            patch("/users/${user.getId()}").cookie(cookie).content(
                """
                {
                    "email": "newemail@email.de"
                }
                """.trimIndent(),
            ).contentType("application/json"),
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `allowed to edit others with edit roles permission`() {
        val testUser = userHelper.createUser(listOf("user:edit", "user:get", "user:edit_roles"), listOf("*", "*", "*"))
        val cookie = userHelper.login(mockMvc = mockMvc)
        val user = userHelper.createUser(
            permissions = listOf("*"),
            email = "secondUser@email.de",
            password = "123456",
            fullName = "Second User",
        )

        mockMvc.perform(
            patch("/users/${user.getId()}").cookie(cookie).content(
                """
                {
                    "email": "newemail@email.de"
                }
                """.trimIndent(),
            ).contentType("application/json"),
        ).andExpect(status().isOk)
    }

    @Test
    fun `editing roles with edit roles permission`() {
        val testUser = userHelper.createUser(listOf("user:edit", "user:get", "user:edit_roles"), listOf("*", "*", "*"))
        val cookie = userHelper.login(mockMvc = mockMvc)
        val user = userHelper.createUser(
            permissions = listOf("*"),
            email = "secondUser@email.de",
            password = "123456",
            fullName = "Second User",
        )
        val role = roleHelper.createRole(listOf("*"), name = "bla")

        mockMvc.perform(
            patch("/users/${user.getId()}").cookie(cookie).content(
                """
                {
                    "email": "newemail@email.de",
                    "roles": ["${role.getId()}", "${Role.DEFAULT_ROLE_ID}"]
                }
                """.trimIndent(),
            ).contentType("application/json"),
        ).andExpect(status().isOk)
    }

    @Test
    fun `editing roles does not allow removing the default role`() {
        val testUser = userHelper.createUser(listOf("user:edit", "user:get", "user:edit_roles"), listOf("*", "*", "*"))
        val cookie = userHelper.login(mockMvc = mockMvc)
        val user = userHelper.createUser(
            permissions = listOf("*"),
            email = "secondUser@email.de",
            password = "123456",
            fullName = "Second User",
        )
        val role = roleHelper.createRole(listOf("*"), name = "bla")

        mockMvc.perform(
            patch("/users/${user.getId()}").cookie(cookie).content(
                """
                {
                    "email": "newemail@email.de",
                    "roles": ["${role.getId()}"]
                }
                """.trimIndent(),
            ).contentType("application/json"),
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `editing roles without edit roles permission`() {
        val testUser = userHelper.createUser(listOf("user:edit", "user:get"), listOf("*", "*"))
        val cookie = userHelper.login(mockMvc = mockMvc)
        val user = userHelper.createUser(
            permissions = listOf("*"),
            email = "secondUser@email.de",
            password = "123456",
            fullName = "Second User",
        )
        val role = roleHelper.createRole(listOf("*"), name = "bla")

        mockMvc.perform(
            patch("/users/${user.getId()}").cookie(cookie).content(
                """
                {
                    "email": "newemail@email.de",
                    "roles": ["${role.getId()}"]
                }
                """.trimIndent(),
            ).contentType("application/json"),
        ).andExpect(status().isForbidden)
    }
}
