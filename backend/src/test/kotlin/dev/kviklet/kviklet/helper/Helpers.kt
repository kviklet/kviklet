package dev.kviklet.kviklet.helper

import dev.kviklet.kviklet.db.ConnectionAdapter
import dev.kviklet.kviklet.db.ExecutionRequestAdapter
import dev.kviklet.kviklet.db.LiveSessionAdapter
import dev.kviklet.kviklet.db.ReviewConfig
import dev.kviklet.kviklet.db.ReviewPayload
import dev.kviklet.kviklet.db.User
import dev.kviklet.kviklet.db.UserAdapter
import dev.kviklet.kviklet.db.UserId
import dev.kviklet.kviklet.service.RoleService
import dev.kviklet.kviklet.service.UserService
import dev.kviklet.kviklet.service.dto.AuthenticationType
import dev.kviklet.kviklet.service.dto.Connection
import dev.kviklet.kviklet.service.dto.ConnectionId
import dev.kviklet.kviklet.service.dto.DatabaseProtocol
import dev.kviklet.kviklet.service.dto.DatasourceType
import dev.kviklet.kviklet.service.dto.ExecutionRequestDetails
import dev.kviklet.kviklet.service.dto.ExecutionRequestId
import dev.kviklet.kviklet.service.dto.LiveSession
import dev.kviklet.kviklet.service.dto.LiveSessionId
import dev.kviklet.kviklet.service.dto.Policy
import dev.kviklet.kviklet.service.dto.PolicyEffect
import dev.kviklet.kviklet.service.dto.RequestType
import dev.kviklet.kviklet.service.dto.ReviewAction
import dev.kviklet.kviklet.service.dto.Role
import dev.kviklet.kviklet.service.dto.RoleId
import jakarta.annotation.PostConstruct
import jakarta.servlet.http.Cookie
import org.springframework.stereotype.Component
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.containers.JdbcDatabaseContainer
import org.testcontainers.containers.MongoDBContainer

@Component
class UserHelper(
    private val userService: UserService,
    private val roleHelper: RoleHelper,
    private val userAdapter: UserAdapter,
) {
    var userCount = 1

    @Transactional
    fun createUser(
        permissions: List<String> = listOf("*"),
        resources: List<String>? = null,
        email: String? = null,
        password: String = "123456",
        fullName: String? = null,
        policies: Set<Policy> = emptySet(),
    ): User {
        val userEmail = email ?: "user-$userCount@example.com"
        val userFullName = fullName ?: "User $userCount"
        val user = userService.createUser(
            email = userEmail,
            password = password,
            fullName = userFullName,
        )
        val role = roleHelper.createRole(
            permissions,
            resources,
            "$userFullName Role",
            "$userFullName users role",
            policies,
        )
        val updatedUser = userService.updateUserWithRoles(
            UserId(user.getId()!!),
            roles = listOf(role.getId()!!, Role.DEFAULT_ROLE_ID.toString()),
        )
        userCount++
        return updatedUser
    }

    @Transactional
    fun createUsersBatch(count: Int = 10) {
        for (i in 1..count) {
            createUser(listOf("*"))
        }
    }

    fun deleteAll() {
        userAdapter.deleteAll()
        userCount = 1
    }

    fun login(email: String = "user-1@example.com", password: String = "123456", mockMvc: MockMvc): Cookie {
        val loginResponse = mockMvc.perform(
            MockMvcRequestBuilders.post("/login")
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
            .andExpect(MockMvcResultMatchers.status().isOk).andReturn()
        val cookie = loginResponse.response.cookies.find { it.name == "SESSION" }!!
        return cookie
    }
}

@Component
class RoleHelper(private val roleService: RoleService) {
    @Transactional
    fun createRole(
        permissions: List<String> = listOf("*"),
        resources: List<String>? = null,
        name: String = "Test Role",
        description: String = "This is a test role",
        policies: Set<Policy> = emptySet(),
    ): Role {
        val role = roleService.createRole(name, description)
        if (policies.isNotEmpty()) {
            roleService.updateRole(
                id = RoleId(role.getId()!!),
                policies = policies,
            )
            return role
        }
        val mappedPolicies = permissions.mapIndexed { index, it ->
            Policy(
                action = it,
                effect = PolicyEffect.ALLOW,
                resource = resources?.get(index) ?: "*",
            )
        }.toSet()
        roleService.updateRole(
            id = RoleId(role.getId()!!),
            policies = mappedPolicies,
        )
        return role
    }

    @Transactional
    fun deleteAll() {
        roleService.getAllRoles().forEach {
            if (!it.isDefault) {
                roleService.deleteRole(RoleId(it.getId()!!))
            }
        }
        roleService.updateRole(
            id = Role.DEFAULT_ROLE_ID,
            policies = Role.DEFAULT_ROLE_POLICIES,
        )
    }

    @Transactional
    fun removeDefaultRolePermissions() {
        val defaultRole = roleService.getRole(Role.DEFAULT_ROLE_ID)
        roleService.updateRole(
            id = RoleId(defaultRole.getId()!!),
            policies = emptySet(),
        )
    }
}

@Component
class ConnectionHelper(private val connectionAdapter: ConnectionAdapter) {

    var connectionCount = 1

    /*
     * Create a dummy connection for testing purposes
     * This connection cannot execute requests
     * @return Connection
     */
    @Transactional
    fun createDummyConnection(): Connection {
        val connection = connectionAdapter.createDatasourceConnection(
            ConnectionId("ds-conn-test-$connectionCount"),
            "Test Connection $connectionCount",
            AuthenticationType.USER_PASSWORD,
            "test",
            1,
            "test",
            "test",
            "A test connection",
            ReviewConfig(
                numTotalRequired = 1,
            ),
            1,
            "localhost",
            DatasourceType.POSTGRESQL,
            DatabaseProtocol.POSTGRESQL,
            additionalJDBCOptions = "",
            dumpsEnabled = false,
            temporaryAccessEnabled = true,
            explainEnabled = false,
        )
        connectionCount++
        return connection
    }

    @Transactional
    fun createPostgresConnection(
        container: JdbcDatabaseContainer<*>,
        explainEnabled: Boolean = true,
        maxExecutions: Int = 1,
    ): Connection {
        val connection = connectionAdapter.createDatasourceConnection(
            ConnectionId("ds-conn-test-$connectionCount"),
            "Test Connection $connectionCount",
            AuthenticationType.USER_PASSWORD,
            container.databaseName,
            maxExecutions,
            container.username,
            container.password,
            "A test connection",
            ReviewConfig(
                numTotalRequired = 1,
            ),
            container.getMappedPort(5432),
            container.host,
            DatasourceType.POSTGRESQL,
            DatabaseProtocol.POSTGRESQL,
            additionalJDBCOptions = "",
            dumpsEnabled = false,
            temporaryAccessEnabled = true,
            explainEnabled = explainEnabled,
        )
        connectionCount++
        return connection
    }

    @Transactional
    fun createMongoDBConnection(container: MongoDBContainer, databaseName: String = "db"): Connection {
        val connection = connectionAdapter.createDatasourceConnection(
            ConnectionId("ds-conn-test-$connectionCount"),
            "Test Connection $connectionCount",
            AuthenticationType.USER_PASSWORD,
            databaseName,
            1,
            "",
            "",
            "A test connection",
            ReviewConfig(
                numTotalRequired = 1,
            ),
            container.getMappedPort(27017),
            container.host,
            DatasourceType.MONGODB,
            DatabaseProtocol.MONGODB,
            additionalJDBCOptions = "",
            dumpsEnabled = false,
            temporaryAccessEnabled = true,
            explainEnabled = false,
        )
        connectionCount++
        return connection
    }

    @Transactional
    fun createKubernetesConnection(): Connection {
        val connection = connectionAdapter.createKubernetesConnection(
            ConnectionId("k8s-conn-test-$connectionCount"),
            "Test Kubernetes Connection $connectionCount",
            "A test kubernetes connection",
            ReviewConfig(
                numTotalRequired = 1,
            ),
            1,
        )
        connectionCount++
        return connection
    }

    @Transactional
    fun deleteAll() {
        connectionAdapter.deleteAll()
    }
}

@Component
class ExecutionRequestHelper(
    private val executionRequestAdapter: ExecutionRequestAdapter,
    private val connectionHelper: ConnectionHelper,
) {

    @Transactional
    fun createExecutionRequest(
        dbcontainer: JdbcDatabaseContainer<*>? = null,
        author: User,
        statement: String? = "SELECT 1;",
        connection: Connection? = null,
        description: String = "A test execution request",
        requestType: RequestType = RequestType.SingleExecution,
    ): ExecutionRequestDetails {
        val requestConnection = connection ?: connectionHelper.createPostgresConnection(dbcontainer!!)
        return executionRequestAdapter.createExecutionRequest(
            connectionId = requestConnection.id,
            title = "Test Execution",
            type = requestType,
            description = description,
            statement = statement,
            executionStatus = "PENDING",
            authorId = author.getId()!!,
        )
    }

    @Transactional
    fun createApprovedRequest(
        dbcontainer: JdbcDatabaseContainer<*>? = null,
        author: User,
        approver: User,
        sql: String = "SELECT 1;",
        connection: Connection? = null,
        requestType: RequestType = RequestType.SingleExecution,
    ): ExecutionRequestDetails {
        val executionConnection = connection ?: connectionHelper.createPostgresConnection(dbcontainer!!)
        val executionRequest = executionRequestAdapter.createExecutionRequest(
            connectionId = executionConnection.id,
            title = "Test Execution",
            type = requestType,
            description = "A test execution request",
            statement = sql,
            executionStatus = "PENDING",
            authorId = author.getId()!!,
        )
        executionRequestAdapter.addEvent(
            ExecutionRequestId(executionRequest.getId()),
            approver.getId()!!,
            ReviewPayload(
                action = ReviewAction.APPROVE,
                comment = "lgtm",
            ),
        )

        return executionRequestAdapter.getExecutionRequestDetails(
            ExecutionRequestId(executionRequest.getId()),
        )
    }

    @Transactional
    fun createApprovedKubernetesExecutionRequest(author: User, approver: User): ExecutionRequestDetails {
        val connection = connectionHelper.createKubernetesConnection()
        val executionRequestDetails = executionRequestAdapter.createExecutionRequest(
            connectionId = connection.id,
            title = "Test Kubernetes Execution",
            type = RequestType.SingleExecution,
            description = "A test kubernetes execution request",
            executionStatus = "PENDING",
            authorId = author.getId()!!,
            namespace = "default",
            podName = "test-pod",
            containerName = "test-container",
            command = "echo 'Hello, World!'",
        )

        executionRequestAdapter.addEvent(
            ExecutionRequestId(executionRequestDetails.getId()),
            approver.getId()!!,
            ReviewPayload(
                action = ReviewAction.APPROVE,
                comment = "lgtm",
            ),
        )
        return executionRequestAdapter.getExecutionRequestDetails(
            ExecutionRequestId(executionRequestDetails.getId()),
        )
    }

    @Transactional
    fun deleteAll() {
        executionRequestAdapter.deleteAll()
    }
}

@Component
class LiveSessionHelper(
    private val liveSessionAdapter: LiveSessionAdapter,
    private val executionRequestHelper: ExecutionRequestHelper,
    private val userHelper: UserHelper,
) {
    private lateinit var genericApprover: User

    @PostConstruct
    fun init() {
        genericApprover = userHelper.createUser()
    }

    @Transactional
    fun createLiveSession(
        executionRequest: ExecutionRequestDetails? = null,
        author: User? = null,
        dbContainer: JdbcDatabaseContainer<*>? = null,
        initialContent: String = "",
    ): LiveSession {
        val request = executionRequest ?: executionRequestHelper.createApprovedRequest(
            dbcontainer = dbContainer ?: throw IllegalArgumentException(
                "dbContainer must be provided if executionRequest is null",
            ),
            approver = genericApprover,
            requestType = RequestType.TemporaryAccess,
            author = author ?: throw IllegalArgumentException(
                "Author must be provided if executionRequest is null",
            ),
        )

        return liveSessionAdapter.createLiveSession(
            executionRequestId = request.request.id!!,
            consoleContent = initialContent,
        )
    }

    @Transactional
    fun updateLiveSession(liveSessionId: LiveSessionId, consoleContent: String): LiveSession =
        liveSessionAdapter.updateLiveSession(
            id = liveSessionId,
            consoleContent = consoleContent,
        )

    @Transactional
    fun findLiveSessionByExecutionRequestId(executionRequestId: ExecutionRequestId): LiveSession? =
        liveSessionAdapter.findByExecutionRequestId(executionRequestId)

    @Transactional
    fun deleteAll() {
        liveSessionAdapter.deleteAll()
    }
}
