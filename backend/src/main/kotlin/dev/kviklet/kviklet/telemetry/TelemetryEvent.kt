package dev.kviklet.kviklet.telemetry

import dev.kviklet.kviklet.service.dto.AuthenticationType
import dev.kviklet.kviklet.service.dto.ConnectionType
import dev.kviklet.kviklet.service.dto.DatasourceType
import dev.kviklet.kviklet.service.dto.RequestType
import dev.kviklet.kviklet.service.dto.ReviewAction

/**
 * Everything Kviklet ever reports to PostHog. Each event is a data class whose constructor parameters
 * become the event's properties, and [TelemetryEventTest] pins down what those parameters may be:
 * enums, numbers, booleans, and a short allowlist of named strings. There is deliberately no free-form
 * property map, so a query, a result, or an error message cannot end up in an event without someone
 * adding a typed field for it, which a reviewer will see in the diff.
 *
 * Property names are the constructor parameter names in snake_case.
 */
sealed class TelemetryEvent(val name: String)

enum class LoginMethod { PASSWORD, LDAP, OIDC, SAML }

/** How the acting user reached Kviklet: the web frontend (any browser session) or an API key. */
enum class TelemetryClient { WEB, API_KEY }

enum class ExecutionMode { EXECUTE, DRY_RUN, DOWNLOAD, EXPLAIN, DUMP }

/** The database Kviklet itself stores its data in, derived from the JDBC URL. */
enum class MetadataDatabase { POSTGRESQL, H2, OTHER }

data class RequestCreated(
    val requestType: RequestType,
    val connectionType: ConnectionType,
    val datasourceType: DatasourceType?,
    val requiredReviews: Int,
) : TelemetryEvent("request_created")

data class ReviewSubmitted(val reviewAction: ReviewAction) : TelemetryEvent("review_submitted")

object RequestClosed : TelemetryEvent("request_closed")

data class RequestExecuted(
    val requestType: RequestType,
    val connectionType: ConnectionType,
    val datasourceType: DatasourceType?,
    val executionMode: ExecutionMode,
    val succeeded: Boolean,
    /** The database vendor's numeric error code, never its message. */
    val errorCode: Int?,
) : TelemetryEvent("request_executed")

data class ProxySessionStarted(val datasourceType: DatasourceType) : TelemetryEvent("proxy_session_started")

data class LiveSessionStarted(val datasourceType: DatasourceType?) : TelemetryEvent("live_session_started")

data class ConnectionCreated(
    val connectionType: ConnectionType,
    val datasourceType: DatasourceType?,
    val authenticationType: AuthenticationType?,
    val requiredReviews: Int,
    val temporaryAccessEnabled: Boolean,
    val dumpsEnabled: Boolean,
    val dryRunEnabled: Boolean,
) : TelemetryEvent("connection_created")

object UserCreated : TelemetryEvent("user_created")

data class UserLoggedIn(val loginMethod: LoginMethod) : TelemetryEvent("user_logged_in")

object RoleCreated : TelemetryEvent("role_created")

object LicenseUploaded : TelemetryEvent("license_uploaded")

/**
 * An unexpected server error. Only class names and the route *pattern* (e.g. `/requests/{id}`), never the
 * exception message: database errors embed query fragments and row values in their messages.
 */
data class ServerError(
    val exceptionClass: String,
    val rootCauseClass: String,
    val httpMethod: String,
    val route: String,
) : TelemetryEvent("server_error")

data class InstanceHeartbeat(
    val version: String,
    val gitCommit: String,
    val metadataDatabase: MetadataDatabase,
    val inDocker: Boolean,
    val usersActive: Long,
    val usersTotal: Long,
    val connectionsPostgresql: Int,
    val connectionsMysql: Int,
    val connectionsMariadb: Int,
    val connectionsMssql: Int,
    val connectionsMongodb: Int,
    val connectionsKubernetes: Int,
    val requestsLast24h: Long,
    val executionsLast24h: Long,
    val oidcProvider: String?,
    val ldapEnabled: Boolean,
    val samlEnabled: Boolean,
    val licenseValid: Boolean,
    val proxyEnabled: Boolean,
    val encryptionEnabled: Boolean,
) : TelemetryEvent("instance_heartbeat")
