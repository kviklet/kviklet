package dev.kviklet.kviklet

import dev.kviklet.kviklet.db.DatasourceConnectionAdapter
import dev.kviklet.kviklet.db.ExecutionRequestAdapter
import dev.kviklet.kviklet.db.ReviewConfig
import dev.kviklet.kviklet.db.RoleAdapter
import dev.kviklet.kviklet.db.UserAdapter
import dev.kviklet.kviklet.helper.ExecutionRequestHelper
import dev.kviklet.kviklet.helper.UserHelper
import dev.kviklet.kviklet.service.dto.AuthenticationType
import dev.kviklet.kviklet.service.dto.DatasourceConnectionId
import dev.kviklet.kviklet.service.dto.DatasourceType
import dev.kviklet.kviklet.service.dto.RequestType
import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.io.FileUrlResource
import org.springframework.jdbc.datasource.init.ScriptUtils
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.containers.JdbcDatabaseContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.time.format.DateTimeFormatter

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
    private lateinit var executionRequestAdapter: ExecutionRequestAdapter

    @Autowired
    private lateinit var userHelper: UserHelper

    @Autowired
    private lateinit var executionRequestHelper: ExecutionRequestHelper

    @Autowired
    lateinit var mockMvc: MockMvc

    companion object {
        val db: PostgreSQLContainer<*> = PostgreSQLContainer(DockerImageName.parse("postgres:11.1"))
            .withUsername("root")
            .withPassword("root")
            .withReuse(true)
            .withDatabaseName("")

        init {
            db.start()
        }
    }

    val initScript: String = "psql_init.sql"

    fun getDb(): JdbcDatabaseContainer<*> = db

    @BeforeEach
    fun setup() {
        val initScript = this::class.java.classLoader.getResource(initScript)!!
        ScriptUtils.executeSqlScript(getDb().createConnection(""), FileUrlResource(initScript))
    }

    @AfterEach
    fun tearDown() {
        executionRequestAdapter.deleteAll()
        userAdapter.deleteAll()
        roleAdapter.deleteAll()
    }

    fun login(email: String = "user-1@example.com", password: String = "123456"): Cookie {
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
        userHelper.createUser(permissions = listOf("*"))
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

    @Test
    fun addComment() {
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
        val executionRequest = executionRequestAdapter.createExecutionRequest(
            connectionId = connection.id,
            title = "Test Execution",
            type = RequestType.SingleQuery,
            description = "A test execution request",
            statement = "SELECT * FROM test",
            readOnly = true,
            executionStatus = "PENDING",
            authorId = user.getId()!!,
        )
        val cookie = login()

        mockMvc.perform(
            post("/execution-requests/${executionRequest.getId()}/comments").cookie(cookie).content(
                """
                {
                    "comment": "Test Comment"
                }
                """.trimIndent(),
            ).contentType("application/json"),
        ).andExpect(status().isOk)

        val refreshedExecutionRequest = executionRequestAdapter.getExecutionRequestDetails(
            executionRequest.request.id!!,
        )

        mockMvc.perform(
            get("/execution-requests/${executionRequest.getId()}").cookie(cookie),
        ).andExpect(status().isOk)
            .andExpect(
                content().json(
                    """
                    {
                            "id": "${executionRequest.getId()}",
                            "title": "Test Execution",
                            "type": "SingleQuery",
                            "description": "A test execution request",
                            "statement": "SELECT * FROM test",
                            "readOnly": true,
                            "executionStatus": "PENDING",
                            "createdAt": "${refreshedExecutionRequest.request.createdAt.format(
                        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS"),
                    )}",
                            "author": {
                                "id": "${user.getId()}",
                                "email": "${user.email}",
                                "roles": [
                                    {
                                        "id": "${user.roles.first().getId()}",
                                        "name": "Some User Role",
                                        "description": "Some User users role",
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
                            },
                            "connection": {
                                "id": "ds-conn-test",
                                "authenticationType": "USER_PASSWORD",
                                "displayName": "Test Connection",
                                "databaseName": "test",
                                "username": "username",
                                "description": "A test connection",
                                "reviewConfig": {
                                  "numTotalRequired": 1
                                }
                              },
                            "events": [
                            {
                                "id": "${refreshedExecutionRequest.events.first().getId()}",
                                "type": "COMMENT",
                                "createdAt": "${refreshedExecutionRequest.events.first().createdAt.format(
                        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS"),
                    )}",
                                "author": {
                                    "id": "${user.getId()}",
                                    "email": ${user.email},
                                    "roles": [
                                        {
                                            "id": "${user.roles.first().getId()}",
                                            "name": "Some User Role",
                                            "description": "Some User users role",
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
                                },
                                "comment": "Test Comment"
                            }
                        ]
                      }
                   }    
                    """.trimIndent(),
                ),
            )
    }

    @Test
    fun `execute simple query`() {
        val user = userHelper.createUser(permissions = listOf("*"))
        val approver = userHelper.createUser(permissions = listOf("*"))
        // Creates a new execution request with SELECT 1; as the statement
        val executionRequest = executionRequestHelper.createApprovedRequest(getDb(), user, approver)
        val cookie = login()

        mockMvc.perform(
            post("/execution-requests/${executionRequest.getId()}/execute").cookie(cookie).contentType(
                "application/json",
            ),
        ).andExpect(status().isOk).andExpect(
            content().json(
                """
                {
                  "results": [
                    {
                      "columns": [
                        {
                          "label": "?column?",
                          "typeName": "int4",
                          "typeClass": "java.lang.Integer"
                        }
                      ],
                      "data": [
                        {
                          "?column?": "1"
                        }
                      ],
                      "type": "RECORDS"
                    }
                  ]
                }
                """.trimIndent(),
            ),
        )
    }
}
