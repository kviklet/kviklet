package dev.kviklet.kviklet.helper

import dev.kviklet.kviklet.db.ReviewConfig
import dev.kviklet.kviklet.db.User
import dev.kviklet.kviklet.db.UserId
import dev.kviklet.kviklet.service.dto.AuthenticationType
import dev.kviklet.kviklet.service.dto.ConnectionId
import dev.kviklet.kviklet.service.dto.DatabaseProtocol
import dev.kviklet.kviklet.service.dto.DatasourceConnection
import dev.kviklet.kviklet.service.dto.DatasourceExecutionRequest
import dev.kviklet.kviklet.service.dto.DatasourceType
import dev.kviklet.kviklet.service.dto.Event
import dev.kviklet.kviklet.service.dto.EventId
import dev.kviklet.kviklet.service.dto.ExecutionRequest
import dev.kviklet.kviklet.service.dto.ExecutionRequestDetails
import dev.kviklet.kviklet.service.dto.ExecutionRequestId
import dev.kviklet.kviklet.service.dto.Policy
import dev.kviklet.kviklet.service.dto.PolicyEffect
import dev.kviklet.kviklet.service.dto.RequestType
import dev.kviklet.kviklet.service.dto.ReviewAction
import dev.kviklet.kviklet.service.dto.ReviewEvent
import dev.kviklet.kviklet.service.dto.Role
import dev.kviklet.kviklet.service.dto.RoleId
import dev.kviklet.kviklet.service.dto.utcTimeNow
import java.time.LocalDateTime
import kotlin.reflect.KClass

open class Factory {
    companion object {
        private val idMap = mutableMapOf<KClass<out Factory>, IncrementingId>()

        fun nextId(forClass: KClass<out Factory>): Int = idMap.getOrPut(forClass) { IncrementingId() }.next()
    }

    protected fun nextId(): Int = nextId(this::class)
}

class IncrementingId {
    private var id = 0

    fun next(): Int = id++
}

class ExecutionRequestDetailsFactory : Factory() {
    private val executionRequestFactory = ExecutionRequestFactory()
    private val eventFactory = EventFactory()
    fun createExecutionRequestDetails(
        request: ExecutionRequest? = null,
        events: MutableSet<Event>? = null,
    ): ExecutionRequestDetails {
        val requestToCreate = request ?: executionRequestFactory.createDatasourceExecutionRequest()
        return ExecutionRequestDetails(
            request = requestToCreate,
            events = events ?: mutableSetOf(eventFactory.createReviewApprovedEvent(request = requestToCreate)),
        )
    }
}

class EventFactory : Factory() {
    private val executionRequestFactory = ExecutionRequestFactory()
    private val userFactory = UserFactory()

    fun createReviewApprovedEvent(
        id: EventId = EventId("test-event " + nextId()),
        request: ExecutionRequest? = null,
        createdAt: LocalDateTime = utcTimeNow(),
        author: User? = null,
        comment: String = "approved",
    ): ReviewEvent = ReviewEvent(
        eventId = id,
        request = request ?: executionRequestFactory.createDatasourceExecutionRequest(),
        author = author ?: userFactory.createUser(),
        createdAt = createdAt,
        comment = comment,
        action = ReviewAction.APPROVE,
    )

    fun createReviewRejectedEvent(
        id: EventId = EventId("test-event " + nextId()),
        request: ExecutionRequest? = null,
        createdAt: LocalDateTime = utcTimeNow(),
        author: User? = null,
        comment: String = "rejected",
    ): ReviewEvent = ReviewEvent(
        eventId = id,
        request = request ?: executionRequestFactory.createDatasourceExecutionRequest(),
        author = author ?: userFactory.createUser(),
        createdAt = createdAt,
        comment = comment,
        action = ReviewAction.REJECT,
    )

    fun createReviewRequestedChangeEvent(
        id: EventId = EventId("test-event " + nextId()),
        request: ExecutionRequest? = null,
        createdAt: LocalDateTime = utcTimeNow(),
        author: User? = null,
        comment: String = "requested change",
    ): ReviewEvent = ReviewEvent(
        eventId = id,
        request = request ?: executionRequestFactory.createDatasourceExecutionRequest(),
        author = author ?: userFactory.createUser(),
        createdAt = createdAt,
        comment = comment,
        action = ReviewAction.REQUEST_CHANGE,
    )
}

class ExecutionRequestFactory : Factory() {
    private val connectionFactory = ConnectionFactory()
    private val userFactory = UserFactory()

    fun createDatasourceExecutionRequest(
        id: ExecutionRequestId? = ExecutionRequestId("test-request " + nextId()),
        connection: DatasourceConnection? = null,
        title: String = "Test Request",
        type: RequestType = RequestType.SingleExecution,
        description: String? = "A test request",
        statement: String = "SELECT 1;",
        executionStatus: String = "PENDING",
        createdAt: LocalDateTime = utcTimeNow(),
        author: User? = null,
    ): DatasourceExecutionRequest = DatasourceExecutionRequest(
        id,
        connection ?: connectionFactory.createDatasourceConnection(),
        title,
        type,
        description,
        statement,
        executionStatus,
        createdAt,
        author ?: userFactory.createUser(),
        null
    )
}

class ConnectionFactory : Factory() {
    fun createDatasourceConnection(
        id: ConnectionId = ConnectionId("test-connection " + nextId()),
        displayName: String = "Test Connection",
        description: String = "A test connection",
        reviewConfig: ReviewConfig = ReviewConfig(numTotalRequired = 1, fourEyesRequired = false),
        maxExecutions: Int? = 1,
        databaseName: String? = null,
        authenticationType: AuthenticationType = AuthenticationType.USER_PASSWORD,
        username: String = "root",
        password: String = "root",
        port: Int = 3306,
        hostname: String = "localhost",
        type: DatasourceType = DatasourceType.MYSQL,
        protocol: DatabaseProtocol = DatabaseProtocol.MYSQL,
        additionalJDBCOptions: String = "",
    ): DatasourceConnection = DatasourceConnection(
        id,
        displayName,
        description,
        reviewConfig,
        maxExecutions,
        databaseName,
        authenticationType,
        username,
        password,
        port,
        hostname,
        type,
        protocol,
        additionalJDBCOptions,
    )
}

class UserFactory : Factory() {
    private val roleFactory = RoleFactory()

    fun createUser(
        id: UserId = UserId("test-user " + nextId()),
        fullName: String? = "Test User " + nextId(),
        password: String? = "password",
        subject: String? = null,
        ldapIdentifier: String? = null,
        email: String = "test" + nextId() + "@user.com",
        roles: Set<Role>? = null,
    ): User = User(
        id,
        fullName,
        password,
        subject,
        ldapIdentifier,
        email,
        roles ?: setOf(roleFactory.createRole()),
    )
}

class RoleFactory : Factory() {

    private val policyFactory = PolicyFactory()

    fun createRole(
        id: RoleId = RoleId("test-role " + nextId()),
        name: String = "Test Role " + nextId(),
        description: String = "A test role",
        policies: Set<Policy>? = null,
    ): Role = Role(
        id = id,
        name = name,
        description = description,
        policies = policies ?: setOf(policyFactory.createPolicy()),
    )
}

class PolicyFactory : Factory() {
    fun createPolicy(
        id: String = "test-policy " + nextId(),
        action: String = "*:*",
        effect: PolicyEffect = PolicyEffect.ALLOW,
        resource: String = "*",
    ): Policy = Policy(id, action, effect, resource)
}
