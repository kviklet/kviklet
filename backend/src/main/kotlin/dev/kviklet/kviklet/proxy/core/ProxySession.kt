// This file is not MIT licensed
package dev.kviklet.kviklet.proxy.core

import dev.kviklet.kviklet.service.dto.AuthenticationDetails
import dev.kviklet.kviklet.service.dto.DatasourceType
import dev.kviklet.kviklet.service.dto.ExecutionRequest
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ScheduledFuture

// A single proxied access session: the temp credentials a client authenticates with, the upstream it is
// routed to, and the audit context. Sessions are keyed in the server registry by [username], which the
// client delivers in its startup/handshake message before any upstream work happens -- that is exactly what
// lets one listener serve many concurrent requests without a port per request.
//
// Protocol-agnostic on purpose: it carries the raw upstream coordinates rather than a protocol-specific
// target factory, so the same ProxyServer/session machinery serves any wire protocol. The ProxyProtocol
// builds its own upstream connection from these coordinates when a client authenticates.
class ProxySession(
    val username: String,
    val password: String,
    val executionRequest: ExecutionRequest,
    val userId: String,
    val targetHost: String,
    val targetPort: Int,
    val databaseName: String,
    // The upstream's datasource flavor. Kept neutral (an enum, not a protocol object) so the session stays
    // protocol-agnostic: some wire protocols serve more than one flavor (MySQL and MariaDB share one
    // listener), and the ProxyProtocol reads this to build the right upstream connection.
    val datasourceType: DatasourceType,
    val authenticationDetails: AuthenticationDetails.UserPassword,
) {
    // Live relay connections for this session, closed when the session expires so an in-flight client cannot
    // keep relaying past the access window. CopyOnWriteArrayList: added by handler threads, iterated by the
    // expiry task / shutdown.
    val connections = CopyOnWriteArrayList<ProxyConnection>()

    // Flipped to false on expiry or server shutdown. Read during auth routing and connection registration
    // (both under the server monitor) so a connection that authenticated just as the window closed is refused
    // instead of left relaying.
    @Volatile
    var active = true
        internal set

    // The scheduled expiry, cancelled if the session is torn down early (replaced or on shutdown).
    internal var expiryFuture: ScheduledFuture<*>? = null

    // When the access window closes (null = never). Recorded at registration so a repeated proxy call,
    // which reuses the live session, can report the original expiry rather than recomputing a later one.
    @Volatile
    var expiresAt: Instant? = null
        internal set
}
