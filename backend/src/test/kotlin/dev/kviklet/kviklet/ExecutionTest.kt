package dev.kviklet.kviklet

import dev.kviklet.kviklet.db.DatasourceConnectionAdapter
import dev.kviklet.kviklet.db.ReviewConfig
import dev.kviklet.kviklet.db.RoleAdapter
import dev.kviklet.kviklet.db.UserAdapter
import dev.kviklet.kviklet.helper.UserHelper
import dev.kviklet.kviklet.security.UserService
import dev.kviklet.kviklet.service.RoleService
import dev.kviklet.kviklet.service.dto.AuthenticationType
import dev.kviklet.kviklet.service.dto.DatasourceConnectionId
import dev.kviklet.kviklet.service.dto.DatasourceType
import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ExecutionTest {

    @Autowired
    private lateinit var datasourceConnectionAdapter: DatasourceConnectionAdapter

    @Autowired
    private lateinit var roleAdapter: RoleAdapter

    @Autowired
    private lateinit var userAdapter: UserAdapter

    @Autowired
    private lateinit var roleService: RoleService

    @Autowired
    private lateinit var userHelper: UserHelper

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var userService: UserService

    @AfterEach
    fun tearDown() {
        userAdapter.deleteAll()
        roleAdapter.deleteAll()
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
    fun createExecutionRequest() {
        val user = userHelper.createUser(permissions = listOf("*"))
        val connection = datasourceConnectionAdapter.createDatasourceConnection(
            DatasourceConnectionId("ds-conn-test"),
            "Test Connection",
            AuthenticationType.USER_PASSWORD,
            "test",
            "username",
            "password",
            "A test connection",
            ReviewConfig(
                numTotalRequired = 1,
            ),

            3306,
            "postgres",
            DatasourceType.POSTGRESQL,
        )
        val cookie = login()

        mockMvc.perform(
            post("/execution-requests/").cookie(cookie).content(
                """
                {
                    "datasourceConnectionId": "${connection.id}",
                    "title": "Test Execution",
                    "type": "SingleQuery",
                    "statement": "SELECT * FROM test",
                    "description": "A test execution request",
                    "readOnly": true
                }
                """.trimIndent(),
            ).contentType("application/json"),
        ).andExpect(status().isOk)
    }
}
