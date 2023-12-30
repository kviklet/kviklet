package dev.kviklet.kviklet.helper

import dev.kviklet.kviklet.db.DatasourceConnectionAdapter
import dev.kviklet.kviklet.db.ExecutionRequestAdapter
import dev.kviklet.kviklet.db.ReviewConfig
import dev.kviklet.kviklet.db.ReviewPayload
import dev.kviklet.kviklet.db.User
import dev.kviklet.kviklet.db.UserId
import dev.kviklet.kviklet.security.UserService
import dev.kviklet.kviklet.service.RoleService
import dev.kviklet.kviklet.service.dto.AuthenticationType
import dev.kviklet.kviklet.service.dto.DatasourceConnection
import dev.kviklet.kviklet.service.dto.DatasourceConnectionId
import dev.kviklet.kviklet.service.dto.DatasourceType
import dev.kviklet.kviklet.service.dto.ExecutionRequestDetails
import dev.kviklet.kviklet.service.dto.ExecutionRequestId
import dev.kviklet.kviklet.service.dto.Policy
import dev.kviklet.kviklet.service.dto.PolicyEffect
import dev.kviklet.kviklet.service.dto.RequestType
import dev.kviklet.kviklet.service.dto.ReviewAction
import dev.kviklet.kviklet.service.dto.Role
import dev.kviklet.kviklet.service.dto.RoleId
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.containers.JdbcDatabaseContainer

@Component
class UserHelper(private val userService: UserService, private val roleHelper: RoleHelper) {
    var userCount = 1

    @Transactional
    fun createUser(
        permissions: List<String>,
        resources: List<String>? = null,
        email: String? = null,
        password: String = "123456",
        fullName: String? = null,
    ): User {
        val userEmail = email ?: "user-$userCount@example.com"
        val userFullName = fullName ?: "User $userCount"
        val user = userService.createUser(
            email = userEmail,
            password = password,
            fullName = userFullName,
        )
        val role = roleHelper.createRole(permissions, resources, "$fullName Role", "$fullName users role")
        val updatedUser = userService.updateUserWithRoles(UserId(user.getId()!!), roles = listOf(role.getId()!!))
        userCount++
        return updatedUser
    }
}

@Component
class RoleHelper(private val roleService: RoleService) {
    @Transactional
    fun createRole(
        permissions: List<String>,
        resources: List<String>? = null,
        name: String = "Test Role",
        description: String = "This is a test role",
    ): Role {
        val role = roleService.createRole(name, description)
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
        return role
    }
}

@Component
class ConnectionHelper(
    private val connectionAdapter: DatasourceConnectionAdapter,
) {
    @Transactional
    fun createPostgresConnection(container: JdbcDatabaseContainer<*>): DatasourceConnection {
        return connectionAdapter.createDatasourceConnection(
            DatasourceConnectionId("ds-conn-test"),
            "Test Connection",
            AuthenticationType.USER_PASSWORD,
            container.databaseName,
            container.username,
            container.password,
            "A test connection",
            ReviewConfig(
                numTotalRequired = 1,
            ),

            container.getMappedPort(5432),
            container.host,
            DatasourceType.POSTGRESQL,
        )
    }
}

@Component
class ExecutionRequestHelper(
    private val executionRequestAdapter: ExecutionRequestAdapter,
    private val connectionHelper: ConnectionHelper,
) {
    @Transactional
    fun createApprovedRequest(
        dbcontainer: JdbcDatabaseContainer<*>,
        author: User,
        approver: User,
    ): ExecutionRequestDetails {
        val connection = connectionHelper.createPostgresConnection(dbcontainer)
        val executionRequest = executionRequestAdapter.createExecutionRequest(
            connectionId = connection.id,
            title = "Test Execution",
            type = RequestType.SingleQuery,
            description = "A test execution request",
            statement = "SELECT 1;",
            readOnly = true,
            executionStatus = "PENDING",
            authorId = author.getId()!!,
        )
        executionRequestAdapter.addEvent(
            ExecutionRequestId(executionRequest.getId()!!),
            approver.getId()!!,
            ReviewPayload(
                action = ReviewAction.APPROVE,
                comment = "lgtm",
            ),
        )

        return executionRequestAdapter.getExecutionRequestDetails(
            ExecutionRequestId(executionRequest.getId()!!),
        )
    }
}
