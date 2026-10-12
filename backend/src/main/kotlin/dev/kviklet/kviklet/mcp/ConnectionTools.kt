// This file is not MIT licensed
package dev.kviklet.kviklet.mcp

import dev.kviklet.kviklet.controller.CreateDatasourceConnectionRequest
import dev.kviklet.kviklet.controller.CreateKubernetesConnectionRequest
import dev.kviklet.kviklet.controller.ReviewConfigRequest
import dev.kviklet.kviklet.controller.RoleRequirementRequest
import dev.kviklet.kviklet.controller.UpdateDatasourceConnectionRequest
import dev.kviklet.kviklet.controller.UpdateKubernetesConnectionRequest
import dev.kviklet.kviklet.security.Permission
import dev.kviklet.kviklet.service.BaseUrlResolver
import dev.kviklet.kviklet.service.ConnectionService
import dev.kviklet.kviklet.service.dto.AuthenticationDetails
import dev.kviklet.kviklet.service.dto.AuthenticationType
import dev.kviklet.kviklet.service.dto.ConnectionId
import dev.kviklet.kviklet.service.dto.ConnectionWithPermissions
import dev.kviklet.kviklet.service.dto.DatabaseProtocol
import dev.kviklet.kviklet.service.dto.DatasourceConnection
import dev.kviklet.kviklet.service.dto.DatasourceType
import dev.kviklet.kviklet.service.dto.KubernetesConnection
import dev.kviklet.kviklet.service.dto.ReviewConfig
import jakarta.validation.Validator
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.ai.mcp.annotation.McpTool.McpAnnotations
import org.springframework.ai.mcp.annotation.McpToolParam
import org.springframework.stereotype.Component

private const val PASSWORD_HINT = "The password passes through the model provider, so tell the user and suggest " +
    "leaving it out and setting it in Kviklet instead."

/** A connection's settings. Fields that don't apply to its kind are null; the password is never shown. */
data class ConnectionDetail(
    val id: String,
    val displayName: String,
    val description: String,
    val kind: ConnectionKind,
    val category: String?,
    val requiredReviews: Int,
    val roleRequirements: List<RoleRequirementRequest>,
    val maxExecutions: Int?,
    val storeResults: Boolean,
    val temporaryAccessEnabled: Boolean,
    val engine: DatasourceType?,
    val protocol: DatabaseProtocol?,
    val hostname: String?,
    val port: Int?,
    val databaseName: String?,
    val authenticationType: AuthenticationType?,
    val username: String?,
    val hasPassword: Boolean?,
    val roleArn: String?,
    val additionalJDBCOptions: String?,
    val maxTemporaryAccessMinutes: Long?,
    val dumpsEnabled: Boolean?,
    val explainEnabled: Boolean?,
    val dryRunEnabled: Boolean?,
    val dryRunRequiresApproval: Boolean?,
    val kubernetesExecInitialWaitTimeoutSeconds: Long?,
    val kubernetesExecTimeoutMinutes: Long?,
    val canEditConnection: Boolean,
    val url: String?,
) {
    companion object {
        fun from(dto: ConnectionWithPermissions, baseUrl: String?): ConnectionDetail {
            val connection = dto.connection
            val database = connection as? DatasourceConnection
            val kubernetes = connection as? KubernetesConnection
            return ConnectionDetail(
                id = connection.getId(),
                displayName = connection.displayName,
                description = connection.description,
                kind = connection.kind(),
                category = connection.category,
                requiredReviews = connection.reviewConfig.numTotalRequired,
                roleRequirements = connection.reviewConfig.roleRequirements.orEmpty().map {
                    RoleRequirementRequest(it.roleId, it.numRequired)
                },
                maxExecutions = connection.maxExecutions,
                storeResults = database?.storeResults ?: kubernetes!!.storeResults,
                temporaryAccessEnabled = database?.temporaryAccessEnabled ?: kubernetes!!.temporaryAccessEnabled,
                engine = database?.type,
                protocol = database?.protocol,
                hostname = database?.hostname,
                port = database?.port,
                databaseName = database?.databaseName,
                authenticationType = database?.authenticationType,
                username = database?.auth?.username,
                hasPassword = database?.let {
                    (it.auth as? AuthenticationDetails.UserPassword)?.password?.isNotEmpty()
                },
                roleArn = (database?.auth as? AuthenticationDetails.AwsIam)?.roleArn,
                additionalJDBCOptions = database?.additionalOptions,
                maxTemporaryAccessMinutes = database?.maxTemporaryAccessDuration,
                dumpsEnabled = database?.dumpsEnabled,
                explainEnabled = database?.explainEnabled,
                dryRunEnabled = database?.dryRunEnabled,
                dryRunRequiresApproval = database?.dryRunRequiresApproval,
                kubernetesExecInitialWaitTimeoutSeconds = kubernetes?.kubernetesExecInitialWaitTimeoutSeconds,
                kubernetesExecTimeoutMinutes = kubernetes?.kubernetesExecTimeoutMinutes,
                canEditConnection = Permission.DATASOURCE_CONNECTION_EDIT in dto.permissions,
                url = baseUrl?.let { "$it/settings/connections/${connection.getId()}" },
            )
        }
    }
}

/** Setting up connections to databases and Kubernetes clusters. */
@Component
class ConnectionTools(
    private val connectionService: ConnectionService,
    private val baseUrlResolver: BaseUrlResolver,
    private val validator: Validator,
) {

    @McpTool(
        name = "get_connection",
        description = "Gets a connection's settings.",
        annotations = McpAnnotations(readOnlyHint = true, destructiveHint = false),
    )
    fun getConnection(@McpToolParam(description = "The connection id") id: String): ConnectionDetail =
        detail(connectionService.getConnectionWithPermissions(ConnectionId(id)))

    @McpTool(
        name = "list_connection_categories",
        description = "Lists the categories connections are grouped by.",
        annotations = McpAnnotations(readOnlyHint = true, destructiveHint = false),
    )
    fun listConnectionCategories(): List<String> = connectionService.listCategories()

    @McpTool(
        name = "create_database_connection",
        description = "Creates a connection to a database. Requests on it need requiredReviews approvals.",
        annotations = McpAnnotations(destructiveHint = false),
    )
    fun createDatabaseConnection(
        @McpToolParam(description = "Letters, digits and dashes, e.g. orders-prod") id: String,
        @McpToolParam(description = "The name shown in Kviklet") displayName: String,
        @McpToolParam(required = false) description: String?,
        @McpToolParam(required = false, description = "A category to group connections by") category: String?,
        engine: DatasourceType,
        @McpToolParam(required = false, description = "Defaults to the engine's protocol") protocol: DatabaseProtocol?,
        hostname: String,
        port: Int,
        @McpToolParam(required = false) databaseName: String?,
        username: String,
        @McpToolParam(required = false, description = "For USER_PASSWORD authentication. $PASSWORD_HINT")
        password: String?,
        @McpToolParam(required = false, description = "USER_PASSWORD by default")
        authenticationType: AuthenticationType?,
        @McpToolParam(required = false, description = "For AWS_IAM authentication") roleArn: String?,
        @McpToolParam(description = "Approvals a request needs") requiredReviews: Int,
        @McpToolParam(required = false, description = "Approvals needed from specific roles, an enterprise feature")
        roleRequirements: List<RoleRequirementRequest>?,
        @McpToolParam(required = false, description = "How often a request may run, 1 by default, 0 for unlimited")
        maxExecutions: Int?,
        @McpToolParam(required = false, description = "Appended to the JDBC URL, e.g. ?sslmode=require")
        additionalJDBCOptions: String?,
        @McpToolParam(required = false, description = "True by default") temporaryAccessEnabled: Boolean?,
        @McpToolParam(required = false, description = "Unlimited by default") maxTemporaryAccessMinutes: Long?,
        @McpToolParam(required = false) dumpsEnabled: Boolean?,
        @McpToolParam(required = false) explainEnabled: Boolean?,
        @McpToolParam(required = false) storeResults: Boolean?,
        @McpToolParam(required = false, description = "Not for MongoDB") dryRunEnabled: Boolean?,
        @McpToolParam(required = false, description = "True by default") dryRunRequiresApproval: Boolean?,
    ): ConnectionDetail {
        val request = CreateDatasourceConnectionRequest(
            id = id,
            displayName = displayName,
            databaseName = databaseName,
            maxExecutions = maxExecutions ?: 1,
            username = username,
            password = password,
            description = description ?: "",
            reviewConfig = ReviewConfigRequest(requiredReviews, roleRequirements),
            type = engine,
            protocol = protocol,
            hostname = hostname,
            port = port,
            additionalJDBCOptions = additionalJDBCOptions ?: "",
            dumpsEnabled = dumpsEnabled ?: false,
            authenticationType = authenticationType ?: AuthenticationType.USER_PASSWORD,
            temporaryAccessEnabled = temporaryAccessEnabled ?: true,
            explainEnabled = explainEnabled ?: false,
            storeResults = storeResults ?: false,
            roleArn = roleArn,
            maxTemporaryAccessDuration = maxTemporaryAccessMinutes,
            category = category,
            dryRunEnabled = dryRunEnabled ?: false,
            dryRunRequiresApproval = dryRunRequiresApproval ?: true,
        )
        validator.check(request)
        return detail(
            connectionService.createDatasourceConnection(
                connectionId = ConnectionId(request.id),
                displayName = request.displayName,
                databaseName = request.databaseName,
                maxExecutions = request.maxExecutions,
                username = request.username,
                password = request.password,
                authenticationType = request.authenticationType,
                description = request.description,
                reviewConfig = request.reviewConfig.toReviewConfig(),
                port = request.port,
                hostname = request.hostname,
                type = request.type,
                protocol = request.protocol ?: request.type.toProtocol(),
                additionalJDBCOptions = request.additionalJDBCOptions,
                dumpsEnabled = request.dumpsEnabled,
                temporaryAccessEnabled = request.temporaryAccessEnabled,
                explainEnabled = request.explainEnabled,
                storeResults = request.storeResults,
                roleArn = request.roleArn,
                maxTemporaryAccessDuration = request.maxTemporaryAccessDuration,
                category = request.category,
                dryRunEnabled = request.dryRunEnabled,
                dryRunRequiresApproval = request.dryRunRequiresApproval,
            ),
        )
    }

    @McpTool(
        name = "update_database_connection",
        description = "Changes a database connection. Pass only the fields to change. Changing the reviews or " +
            "maxExecutions updates the status of its open requests.",
        annotations = McpAnnotations(destructiveHint = true),
    )
    fun updateDatabaseConnection(
        @McpToolParam(description = "The connection id") id: String,
        @McpToolParam(required = false) displayName: String?,
        @McpToolParam(required = false) description: String?,
        @McpToolParam(required = false, description = "An empty string removes the category") category: String?,
        @McpToolParam(required = false, description = "Without a protocol, the new engine's one is used")
        engine: DatasourceType?,
        @McpToolParam(required = false) protocol: DatabaseProtocol?,
        @McpToolParam(required = false) hostname: String?,
        @McpToolParam(required = false) port: Int?,
        @McpToolParam(required = false) databaseName: String?,
        @McpToolParam(required = false) username: String?,
        @McpToolParam(required = false, description = PASSWORD_HINT) password: String?,
        @McpToolParam(required = false) authenticationType: AuthenticationType?,
        @McpToolParam(required = false) roleArn: String?,
        @McpToolParam(required = false, description = "Approvals a request needs") requiredReviews: Int?,
        @McpToolParam(required = false, description = "Replaces the role requirements, an empty list removes them")
        roleRequirements: List<RoleRequirementRequest>?,
        @McpToolParam(required = false, description = "How often a request may run, 0 for unlimited")
        maxExecutions: Int?,
        @McpToolParam(required = false) additionalJDBCOptions: String?,
        @McpToolParam(required = false) temporaryAccessEnabled: Boolean?,
        @McpToolParam(required = false, description = "0 for unlimited") maxTemporaryAccessMinutes: Long?,
        @McpToolParam(required = false) dumpsEnabled: Boolean?,
        @McpToolParam(required = false) explainEnabled: Boolean?,
        @McpToolParam(required = false) storeResults: Boolean?,
        @McpToolParam(required = false) dryRunEnabled: Boolean?,
        @McpToolParam(required = false) dryRunRequiresApproval: Boolean?,
    ): ConnectionDetail {
        val current = connectionService.getDatasourceConnection(ConnectionId(id))
        if (current !is DatasourceConnection) {
            throw IllegalArgumentException("$id is a Kubernetes connection, use update_kubernetes_connection")
        }
        val request = UpdateDatasourceConnectionRequest(
            displayName = displayName,
            description = description,
            type = engine,
            protocol = protocol,
            maxExecutions = maxExecutions,
            hostname = hostname,
            port = port,
            databaseName = databaseName,
            username = username,
            roleArn = roleArn,
            password = password,
            reviewConfig = reviewConfig(current.reviewConfig, requiredReviews, roleRequirements),
            additionalJDBCOptions = additionalJDBCOptions,
            authenticationType = authenticationType,
            dumpsEnabled = dumpsEnabled,
            temporaryAccessEnabled = temporaryAccessEnabled,
            explainEnabled = explainEnabled,
            storeResults = storeResults,
            maxTemporaryAccessDuration = maxTemporaryAccessMinutes?.takeIf { it != 0L },
            clearMaxTempDuration = maxTemporaryAccessMinutes == 0L,
            category = category?.takeIf { it.isNotEmpty() },
            clearCategory = category?.isEmpty() == true,
            dryRunEnabled = dryRunEnabled,
            dryRunRequiresApproval = dryRunRequiresApproval,
        )
        validator.check(request)
        return detail(connectionService.updateConnection(ConnectionId(id), request))
    }

    @McpTool(
        name = "create_kubernetes_connection",
        description = "Creates a connection to the Kubernetes cluster Kviklet runs in, for running commands in " +
            "its pods. Requests on it need requiredReviews approvals.",
        annotations = McpAnnotations(destructiveHint = false),
    )
    fun createKubernetesConnection(
        @McpToolParam(description = "Letters, digits and dashes, e.g. cluster-prod") id: String,
        @McpToolParam(description = "The name shown in Kviklet") displayName: String,
        @McpToolParam(required = false) description: String?,
        @McpToolParam(required = false, description = "A category to group connections by") category: String?,
        @McpToolParam(description = "Approvals a request needs") requiredReviews: Int,
        @McpToolParam(required = false, description = "Approvals needed from specific roles, an enterprise feature")
        roleRequirements: List<RoleRequirementRequest>?,
        @McpToolParam(required = false, description = "How often a request may run, 1 by default, 0 for unlimited")
        maxExecutions: Int?,
        @McpToolParam(required = false) storeResults: Boolean?,
        @McpToolParam(required = false, description = "How long to wait for a command's first output, 5 by default")
        kubernetesExecInitialWaitTimeoutSeconds: Long?,
        @McpToolParam(required = false, description = "When a command is stopped, 60 by default")
        kubernetesExecTimeoutMinutes: Long?,
    ): ConnectionDetail {
        val request = CreateKubernetesConnectionRequest(
            id = id,
            displayName = displayName,
            description = description ?: "",
            reviewConfig = ReviewConfigRequest(requiredReviews, roleRequirements),
            maxExecutions = maxExecutions ?: 1,
            storeResults = storeResults ?: false,
            category = category,
            kubernetesExecInitialWaitTimeoutSeconds = kubernetesExecInitialWaitTimeoutSeconds,
            kubernetesExecTimeoutMinutes = kubernetesExecTimeoutMinutes,
        )
        validator.check(request)
        return detail(
            connectionService.createKubernetesConnection(
                connectionId = ConnectionId(request.id),
                displayName = request.displayName,
                description = request.description,
                reviewConfig = request.reviewConfig.toReviewConfig(),
                maxExecutions = request.maxExecutions,
                storeResults = request.storeResults,
                category = request.category,
                kubernetesExecInitialWaitTimeoutSeconds = request.kubernetesExecInitialWaitTimeoutSeconds,
                kubernetesExecTimeoutMinutes = request.kubernetesExecTimeoutMinutes,
            ),
        )
    }

    @McpTool(
        name = "update_kubernetes_connection",
        description = "Changes a Kubernetes connection. Pass only the fields to change. Changing the reviews or " +
            "maxExecutions updates the status of its open requests.",
        annotations = McpAnnotations(destructiveHint = true),
    )
    fun updateKubernetesConnection(
        @McpToolParam(description = "The connection id") id: String,
        @McpToolParam(required = false) displayName: String?,
        @McpToolParam(required = false) description: String?,
        @McpToolParam(required = false, description = "An empty string removes the category") category: String?,
        @McpToolParam(required = false, description = "Approvals a request needs") requiredReviews: Int?,
        @McpToolParam(required = false, description = "Replaces the role requirements, an empty list removes them")
        roleRequirements: List<RoleRequirementRequest>?,
        @McpToolParam(required = false, description = "How often a request may run, 0 for unlimited")
        maxExecutions: Int?,
        @McpToolParam(required = false) storeResults: Boolean?,
        @McpToolParam(required = false) kubernetesExecInitialWaitTimeoutSeconds: Long?,
        @McpToolParam(required = false) kubernetesExecTimeoutMinutes: Long?,
    ): ConnectionDetail {
        val current = connectionService.getDatasourceConnection(ConnectionId(id))
        if (current !is KubernetesConnection) {
            throw IllegalArgumentException("$id is a database connection, use update_database_connection")
        }
        val request = UpdateKubernetesConnectionRequest(
            displayName = displayName,
            description = description,
            reviewConfig = reviewConfig(current.reviewConfig, requiredReviews, roleRequirements),
            maxExecutions = maxExecutions,
            storeResults = storeResults,
            category = category?.takeIf { it.isNotEmpty() },
            clearCategory = category?.isEmpty() == true,
            kubernetesExecInitialWaitTimeoutSeconds = kubernetesExecInitialWaitTimeoutSeconds,
            kubernetesExecTimeoutMinutes = kubernetesExecTimeoutMinutes,
        )
        validator.check(request)
        return detail(connectionService.updateConnection(ConnectionId(id), request))
    }

    // The review config is replaced as a whole, so a change to one of its parts keeps the other.
    private fun reviewConfig(
        current: ReviewConfig,
        requiredReviews: Int?,
        roleRequirements: List<RoleRequirementRequest>?,
    ): ReviewConfigRequest? {
        if (requiredReviews == null && roleRequirements == null) return null
        return ReviewConfigRequest(
            numTotalRequired = requiredReviews ?: current.numTotalRequired,
            roleRequirements = roleRequirements
                ?: current.roleRequirements?.map { RoleRequirementRequest(it.roleId, it.numRequired) },
        )
    }

    private fun detail(connection: ConnectionWithPermissions) =
        ConnectionDetail.from(connection, baseUrlResolver.resolve())
}

/** Runs the Bean Validation a controller's `@Valid` would, as a one-line tool error. */
internal fun Validator.check(request: Any) {
    val violations = validate(request)
    if (violations.isNotEmpty()) {
        throw IllegalArgumentException(
            violations.sortedBy { it.propertyPath.toString() }
                .joinToString("; ") { "${it.propertyPath}: ${it.message}" },
        )
    }
}
