package dev.kviklet.kviklet

import dev.kviklet.kviklet.db.User
import dev.kviklet.kviklet.db.UserId
import dev.kviklet.kviklet.helper.RoleHelper
import dev.kviklet.kviklet.helper.UserHelper
import dev.kviklet.kviklet.security.Permission
import dev.kviklet.kviklet.service.RoleService
import dev.kviklet.kviklet.service.UserService
import dev.kviklet.kviklet.service.dto.Policy
import dev.kviklet.kviklet.service.dto.PolicyEffect
import dev.kviklet.kviklet.service.dto.RoleId
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
    private lateinit var roleHelper: RoleHelper

    @Autowired
    private lateinit var roleService: RoleService

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var userService: UserService

    @AfterEach
    fun tearDown() {
        userHelper.deleteAll()
        roleHelper.deleteAll()
    }

    fun createUser(permissions: List<String>, resources: List<String>? = null): User {
        val user = userService.createUser(
            email = "user@example.com",
            password = "123456",
            fullName = "User 1",
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

    @Test
    fun getRolePolicies() {
        val user = userHelper.createUser(permissions = listOf("*"))
        val cookie = userHelper.login(mockMvc = mockMvc)

        mockMvc.perform(get("/roles/").cookie(cookie))
            .andExpect(status().isOk)
            .andExpect(
                content().json(
                    """
                    {
                        "roles": [
                            {
                                "name": "Default Role"
                            },
                            {
                                "name": "User 1 Role",
                                "description": "User 1 users role",
                                "policies": [
                                    {
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
    fun canGetOnlySpecificRoleWithCorrectPermission() {
        val role = roleService.createRole("Some-Role", "the users role")
        val user = userHelper.createUser(
            permissions = listOf(Permission.ROLE_GET.getPermissionString()),
            resources = listOf(role.getId()!!),
        )
        val cookie = userHelper.login(mockMvc = mockMvc)

        mockMvc.perform(get("/roles/").cookie(cookie))
            .andExpect(status().isOk)
            .andExpect(
                content().json(
                    """
                {
                    "roles": [
                        {
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
        val cookie = userHelper.login(mockMvc = mockMvc)

        mockMvc.perform(get("/roles/").cookie(cookie))
            .andExpect(status().isOk)
            .andExpect(
                content().json(
                    """
                    {
                        "roles": [
                            {
                                "name": "Default Role"
                            },
                            {
                                "name": "User 1 Role",
                                "description": "User 1 users role",
                                "policies": [
                                    {
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
        val cookie = userHelper.login(mockMvc = mockMvc)

        mockMvc.perform(get("/roles/").cookie(cookie))
            .andExpect(status().isForbidden)
    }

    @Test
    fun getRolesNoLogin() {
        val user = userHelper.createUser(permissions = listOf(Permission.EXECUTION_REQUEST_GET.getPermissionString()))

        mockMvc.perform(get("/roles/"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun createRole() {
        val user = userHelper.createUser(
            permissions = listOf(
                Permission.ROLE_GET.getPermissionString(),
                Permission.ROLE_EDIT.getPermissionString(),
            ),
        )
        val cookie = userHelper.login(mockMvc = mockMvc)
        mockMvc.perform(
            post("/roles/")
                .cookie(cookie)
                .contentType("application/json")
                .content(
                    """
                        {
                            "name": "Role 1",
                            "description": "Role 1 description",
                            "policies": [
                                {
                                    "action": "role:get",
                                    "effect": "ALLOW",
                                    "resource": "*"
                                }
                            ]
                        }
                    """.trimIndent(),
                ),
        )

        mockMvc.perform(get("/roles/").cookie(cookie))
            .andExpect(status().isOk)
            .andExpect(
                content().json(
                    """
                    {
                      "roles": [
                        {
                          "id": "7WoJJYKT2hhrLp49YrT2yr",
                          "name": "Default Role",
                          "description": "This is the default role and gives permission to read connections and requests",
                          "policies": [
                            {
                              "action": "datasource_connection:get",
                              "effect": "ALLOW",
                              "resource": "*"
                            },
                            {
                              "action": "execution_request:get",
                              "effect": "ALLOW",
                              "resource": "*"
                            }
                          ],
                          "isDefault": true
                        },
                        {
                          "name": "User 1 Role",
                          "description": "User 1 users role",
                          "isDefault": false
                        },
                        {
                          "name": "Role 1",
                          "description": "Role 1 description",
                          "policies": [
                            {
                              "action": "role:get",
                              "effect": "ALLOW",
                              "resource": "*"
                            }
                          ],
                          "isDefault": false
                        }
                      ]
                    }
                    """.trimIndent(),
                ),
            )
    }
}
