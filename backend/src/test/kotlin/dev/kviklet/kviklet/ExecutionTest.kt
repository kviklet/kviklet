package dev.kviklet.kviklet

import dev.kviklet.kviklet.db.ConnectionAdapter
import dev.kviklet.kviklet.db.ExecutionRequestAdapter
import dev.kviklet.kviklet.db.ReviewConfig
import dev.kviklet.kviklet.db.RoleAdapter
import dev.kviklet.kviklet.helper.ExecutionRequestHelper
import dev.kviklet.kviklet.helper.UserHelper
import dev.kviklet.kviklet.service.dto.AuthenticationType
import dev.kviklet.kviklet.service.dto.ConnectionId
import dev.kviklet.kviklet.service.dto.DatasourceType
import dev.kviklet.kviklet.service.dto.RequestType
import org.hamcrest.CoreMatchers.notNullValue
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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.containers.JdbcDatabaseContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ExecutionTest {

    @Autowired
    private lateinit var datasourceConnectionAdapter: ConnectionAdapter

    @Autowired
    private lateinit var roleAdapter: RoleAdapter

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
        userHelper.deleteAll()
        roleAdapter.deleteAll()
    }

    @Test
    fun createExecutionRequest() {
        val connection = datasourceConnectionAdapter.createDatasourceConnection(
            ConnectionId("ds-conn-test"),
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
            additionalJDBCOptions = "",
        )
        userHelper.createUser(permissions = listOf("*"))
        val cookie = userHelper.login(mockMvc = mockMvc)

        mockMvc.perform(
            post("/execution-requests/").cookie(cookie).content(
                """
                {
                    "connectionId": "${connection.id}",
                    "title": "Test Execution",
                    "type": "SingleExecution",
                    "statement": "SELECT * FROM test",
                    "description": "A test execution request",
                    "readOnly": true,
                    "connectionType": "DATASOURCE"
                }
                """.trimIndent(),
            ).contentType("application/json"),
        ).andExpect(status().isOk)
    }

    @Test
    fun addComment() {
        val user = userHelper.createUser(permissions = listOf("*"))
        val connection = datasourceConnectionAdapter.createDatasourceConnection(
            ConnectionId("ds-conn-test"),
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
            additionalJDBCOptions = "",
        )
        val executionRequest = executionRequestAdapter.createExecutionRequest(
            connectionId = connection.id,
            title = "Test Execution",
            type = RequestType.SingleExecution,
            description = "A test execution request",
            statement = "SELECT * FROM test",
            readOnly = true,
            executionStatus = "PENDING",
            authorId = user.getId()!!,
        )
        val cookie = userHelper.login(mockMvc = mockMvc)

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
                            "type": "SingleExecution",
                            "description": "A test execution request",
                            "statement": "SELECT * FROM test",
                            "readOnly": true,
                            "reviewStatus": "AWAITING_APPROVAL",
                            "executionStatus": "NOT_EXECUTED",
                            "author": {
                                "id": "${user.getId()}",
                                "email": "${user.email}",
                                "roles": [
                                    {
                                        "id": "${user.roles.first().getId()}",
                                        "name": "User 1 Role",
                                        "description": "User 1 users role",
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
                                "author": {
                                    "id": "${user.getId()}",
                                    "email": ${user.email},
                                    "roles": [
                                        {
                                            "id": "${user.roles.first().getId()}",
                                            "name": "User 1 Role",
                                            "description": "User 1 users role",
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
        val cookie = userHelper.login(mockMvc = mockMvc)

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

        mockMvc.perform(
            get("/executions/").cookie(cookie).contentType(
                "application/json",
            ),
        ).andExpect(status().isOk).andExpect(
            content().json(
                """
                {
                  "executions": [
                    {
                      "requestId": "${executionRequest.getId()}",
                      "name": "${user.fullName}",
                      "statement": "SELECT 1;",
                      "connectionId": "${executionRequest.request.connection.id}"
                      }
                  ]
                }
                """.trimIndent(),
            ),
        )
            .andExpect(jsonPath("$.executions[0].executionTime", notNullValue()))

        mockMvc.perform(
            get("/execution-requests/${executionRequest.getId()}").cookie(cookie).contentType(
                "application/json",
            ),
        ).andExpect(status().isOk).andExpect(
            content().json(
                """
                   {
                    "id": "${executionRequest.getId()}",
                    "executionStatus": "EXECUTED",
                    "reviewStatus": "APPROVED",
                    "events": [
                    {
                        "type": "REVIEW"
                    },
                    {
                        "type": "EXECUTE"
                    }
                    ]
              }
                """.trimIndent(),
            ),
        )
    }
}
