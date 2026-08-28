// This file is not MIT licensed
package dev.kviklet.kviklet.proxy.mysql

import dev.kviklet.kviklet.service.dto.AuthenticationDetails
import dev.kviklet.kviklet.service.dto.DatasourceType
import java.net.Socket
import java.sql.Connection
import java.sql.DriverManager
import java.util.Properties
import org.mariadb.jdbc.Connection as MariaDbConnection

// The authenticated upstream: the raw socket the relay pumps bytes through, plus the JDBC connection it
// was extracted from. The JDBC connection MUST stay referenced (and be closed on teardown) for as long as
// the socket is in use -- an abandoned driver connection object is exactly the resource leak that got the
// previous driver-extraction attempt removed, and some drivers reap the network resources of connections
// that are garbage-collected without close().
class TargetMySqlConnection(val socket: Socket, val jdbcConnection: Connection)

// Opens and authenticates the upstream connection for a proxied session by letting the JDBC driver do the
// full handshake, then extracting the underlying socket for the byte relay -- the same approach the
// Postgres proxy takes with pgjdbc, so auth-plugin negotiation (native, caching_sha2 incl. RSA full auth,
// AuthSwitch) is maintained by the driver instead of hand-rolled crypto.
//
// The MariaDB driver is used for BOTH MySQL and MariaDB targets, deliberately: the relay is a transparent
// byte pump after the handshakes, so the capabilities the upstream negotiates must produce the same wire
// format the proxy's own client-facing handshake advertised (fixed minimal capabilities, EOF-style result
// sets). Connector/J enables CLIENT_DEPRECATE_EOF unconditionally -- which changes result-set framing and
// would desync every downstream client -- while the MariaDB driver exposes toggles for every
// format-changing capability; those are pinned off below. It speaks to MySQL servers natively.
class TargetMySqlSocketFactory(
    private val datasourceType: DatasourceType,
    private val authenticationDetails: AuthenticationDetails.UserPassword,
    private val databaseName: String,
    private val targetHost: String,
    private val targetPort: Int,
) {
    fun createTargetMySqlConnection(): TargetMySqlConnection {
        val props = Properties()
        props.setProperty("user", authenticationDetails.username)
        props.setProperty("password", authenticationDetails.password)
        // The relay hands the client's plaintext bytes straight to this socket, so it must be the plain
        // TCP socket, never a TLS-wrapped one.
        props.setProperty("sslMode", "disable")
        // caching_sha2_password full auth over a plaintext connection needs the server's RSA public key.
        props.setProperty("allowPublicKeyRetrieval", "true")
        // Pin every capability that changes the response wire format to what the proxy's client-facing
        // handshake advertises (no DEPRECATE_EOF, no MariaDB extended metadata), so any downstream client
        // parses the relayed responses correctly. These are the driver's non-mapped options; their
        // defaults are all true.
        props.setProperty("deprecateEof", "false")
        props.setProperty("extendedTypeInfo", "false")
        props.setProperty("enableBulkUnitResult", "false")
        props.setProperty("enableSkipMeta", "false")
        props.setProperty("useServerPrepStmts", "false")

        val database = if (databaseName.isNotEmpty()) "/$databaseName" else ""
        val conn = try {
            DriverManager.getConnection("jdbc:mariadb://$targetHost:$targetPort$database", props)
        } catch (e: Exception) {
            throw IllegalStateException("Could not open the $datasourceType upstream connection", e)
        }
        try {
            return TargetMySqlConnection(extractSocket(conn), conn)
        } catch (e: Exception) {
            runCatching { conn.close() }
            throw IllegalStateException(
                "Could not extract the upstream socket from the $datasourceType driver connection",
                e,
            )
        }
    }

    // The driver has no public accessor for its socket, so it is read reflectively from the client
    // implementation, walking up the class hierarchy so a driver refactor that moves the field to a
    // superclass keeps working. Extraction is safe at this point: the protocol is synchronous and the
    // driver has fully consumed the responses to its own setup commands, so no stray bytes sit in its
    // buffers.
    private fun extractSocket(conn: Connection): Socket {
        val client = conn.unwrap(MariaDbConnection::class.java).client
        var currentClass: Class<*>? = client.javaClass
        while (currentClass != null) {
            try {
                val socketField = currentClass.getDeclaredField("socket")
                socketField.isAccessible = true
                return socketField.get(client) as Socket
            } catch (e: NoSuchFieldException) {
                currentClass = currentClass.superclass
            }
        }
        throw NoSuchFieldException("Could not find the 'socket' field on the driver client class ${client.javaClass}")
    }
}
