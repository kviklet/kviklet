package dev.kviklet.kviklet

import dev.kviklet.kviklet.db.RoleAdapter
import dev.kviklet.kviklet.db.User
import dev.kviklet.kviklet.db.UserAdapter
import dev.kviklet.kviklet.db.UserId
import dev.kviklet.kviklet.helper.UserHelper
import dev.kviklet.kviklet.security.Permission
import dev.kviklet.kviklet.security.UserService
import dev.kviklet.kviklet.service.RoleService
import dev.kviklet.kviklet.service.dto.Policy
import dev.kviklet.kviklet.service.dto.PolicyEffect
import dev.kviklet.kviklet.service.dto.RoleId
import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PoliciesTest {
    @Autowired
    private lateinit var userHelper: UserHelper

    @Autowired
    private lateinit var roleAdapter: RoleAdapter

    @Autowired
    private lateinit var userAdapter: UserAdapter

    @Autowired
    private lateinit var roleService: RoleService

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var userService: UserService

    @AfterEach
    fun tearDown() {
        userAdapter.deleteAll()
        roleAdapter.deleteAll()
    }

    fun createUser(permissions: List<String>, resources: List<String>? = null): User {
        val user = userService.createUser(
            email = "user@example.com",
            password = "123456",
            fullName = "Some User",
        )
        val role = roleService.createRole("USER", "the users role")
        val policies = permissions.mapIndexed { index, it ->
            Policy(
                action = it,
                effect = PolicyEffect.ALLOW,
                resource = resources?.get(index) ?: "*",
            )
        }.toSet()
        roleService.updateRole(
            id = RoleId(role.getId()!!),
            policies = policies,
        )
        val updatedUser = userService.updateUserWithRoles(UserId(user.getId()!!), roles = listOf(role.getId()!!))
        return updatedUser
    }

    fun login(email: String = "user@example.com", password: String = "123456"): Cookie {
        val loginResponse = mockMvc.perform(
            post("/login")
                .content(
                    """
                        {
                            "email": "$email",
                            "password": "$password"
                        }
                    """.trimIndent(),
                )
                .contentType("application/json"),
        )
            .andExpect(status().isOk).andReturn()
        val cookie = loginResponse.response.cookies.find { it.name == "SESSION" }!!
        return cookie
    }

    @Test
    fun getRolePolicies() {
        val user = userHelper.createUser(permissions = listOf("*"))
        val cookie = login()

        mockMvc.perform(get("/roles/").cookie(cookie))
            .andExpect(status().isOk)
            .andExpect(
                content().json(
                    """
                    {
                        "roles": [
                            {
                                "id": "${user.roles.first().getId()}",
                                "name": "USER",
                                "description": "the users role",
                                "policies": [
                                    {
                                        "id": "${user.roles.first().policies.first().id}",
                                        "action": "*",
                                        "effect": "ALLOW",
                                        "resource": "*"
                                    }
                                ]
                            }
                        ]
                    }
                    """.trimIndent(),
                ),
            )
    }

    @Test
    fun canOnlyGetRoleWithCorrectPermissions() {
        val role = roleService.createRole("Some-Role", "the users role")
        val user = userHelper.createUser(
            permissions = listOf(Permission.ROLE_GET.getPermissionString()),
            resources = listOf(role.getId()!!),
        )
        val cookie = login()

        mockMvc.perform(get("/roles/").cookie(cookie))
            .andExpect(status().isOk)
            .andExpect(
                content().json(
                    """
                {
                    "roles": [
                        {
                            "id": "${role.getId()}",
                            "name": "Some-Role",
                            "description": "the users role",
                            "policies": []
                        }
                    ]
                }
                    """.trimIndent(),
                ),
            )
    }

    @Test
    fun getRolePoliciesSpecificPermissions() {
        val user = userHelper.createUser(permissions = listOf(Permission.ROLE_GET.getPermissionString()))
        val cookie = login()

        mockMvc.perform(get("/roles/").cookie(cookie))
            .andExpect(status().isOk)
            .andExpect(
                content().json(
                    """
                    {
                        "roles": [
                            {
                                "id": "${user.roles.first().getId()}",
                                "name": "USER",
                                "description": "the users role",
                                "policies": [
                                    {
                                        "id": "${user.roles.first().policies.first().id}",
                                        "action": "role:get",
                                        "effect": "ALLOW",
                                        "resource": "*"
                                    }
                                ]
                            }
                        ]
                    }
                    """.trimIndent(),
                ),
            )
    }

    @Test
    fun getRolesWrongPermissions() {
        userHelper.createUser(permissions = listOf(Permission.EXECUTION_REQUEST_GET.getPermissionString()))
        val cookie = login()

        mockMvc.perform(get("/roles/").cookie(cookie))
            .andExpect(status().isForbidden)
    }

    @Test
    fun getRolesNoLogin() {
        val user = userHelper.createUser(permissions = listOf(Permission.EXECUTION_REQUEST_GET.getPermissionString()))

        mockMvc.perform(get("/roles/"))
            .andExpect(status().isUnauthorized)
    }
}
