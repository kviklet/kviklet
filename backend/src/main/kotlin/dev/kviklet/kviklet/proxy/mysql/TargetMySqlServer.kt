// This file is not MIT licensed
package dev.kviklet.kviklet.proxy.mysql

import dev.kviklet.kviklet.proxy.core.parseAdditionalOptions
import dev.kviklet.kviklet.service.dto.AuthenticationDetails
import dev.kviklet.kviklet.service.dto.DatasourceType
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.sql.Connection
import java.sql.DriverManager
import java.util.Properties
import org.mariadb.jdbc.Connection as MariaDbConnection

// The authenticated upstream: the driver's own I/O streams the relay pumps bytes through, the raw TCP
// socket underneath them (closed on teardown -- which, when upstream TLS is on, hard-kills the TLS layer
// from below exactly like the relays already do client-side), plus the JDBC connection everything was
// extracted from. The JDBC connection MUST stay referenced (and be closed on teardown) for as long as the
// streams are in use -- an abandoned driver connection object is exactly the resource leak that got the
// previous driver-extraction attempt removed, and some drivers reap the network resources of connections
// that are garbage-collected without close().
class TargetMySqlConnection(
    val serverInput: InputStream,
    val serverOutput: OutputStream,
    val rawSocket: Socket,
    val jdbcConnection: Connection,
)

// Opens and authenticates the upstream connection for a proxied session by letting the JDBC driver do the
// full handshake, then extracting the driver's I/O endpoints for the byte relay -- the same approach the
// Postgres proxy takes with pgjdbc, so auth-plugin negotiation (native, caching_sha2 incl. RSA full auth,
// AuthSwitch) and the upstream TLS handshake are maintained by the driver instead of hand-rolled crypto.
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
    private val additionalOptions: String = "",
) {
    fun createTargetMySqlConnection(): TargetMySqlConnection {
        val props = Properties()
        props.setProperty("user", authenticationDetails.username)
        props.setProperty("password", authenticationDetails.password)
        val sslMode = upstreamSslMode(additionalOptions)
        props.setProperty("sslMode", sslMode)
        if (sslMode == SSL_MODE_DISABLE) {
            // caching_sha2_password full auth over a plaintext connection needs the server's RSA public
            // key. Over TLS the plugin just sends the password (the channel is already confidential), so
            // the retrieval -- itself MITM-exposed on plaintext -- is only enabled when TLS is off.
            props.setProperty("allowPublicKeyRetrieval", "true")
        } else {
            serverSslCert(additionalOptions)?.let { props.setProperty("serverSslCert", it) }
        }
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
            return extractRelayEndpoints(conn)
        } catch (e: Exception) {
            runCatching { conn.close() }
            throw IllegalStateException(
                "Could not extract the upstream relay endpoints from the $datasourceType driver connection",
                e,
            )
        }
    }

    // The relay pumps the driver's own stream objects, not the socket's: with upstream TLS the driver keeps
    // the SSLSocket only in a local variable during its handshake -- the `socket` field keeps pointing at
    // the raw TCP socket underneath, whose streams would carry ciphertext -- and rebuilds its I/O on the
    // SSLSocket's streams (PacketWriter.out / PacketReader.inputStream). Those stream objects are therefore
    // the only reachable endpoints that do the crypto. The plain path uses the same extraction so both
    // modes share one code path, and reusing the driver's buffered streams also preserves any bytes its
    // read-ahead already prefetched, which grabbing the socket's raw stream would lose. Extraction is safe
    // at this point: the protocol is synchronous and the driver has fully consumed the responses to its own
    // setup commands, so nothing the client needs sits unread in the driver's buffers.
    //
    // The driver has no public accessors for any of this, so the fields are read reflectively, walking up
    // each class hierarchy so a driver refactor that moves a field to a superclass keeps working.
    private fun extractRelayEndpoints(conn: Connection): TargetMySqlConnection {
        val client = conn.unwrap(MariaDbConnection::class.java).client
        val serverOutput = readField(readField(client, "writer"), "out") as OutputStream
        val serverInput = readField(readField(client, "reader"), "inputStream") as InputStream
        val rawSocket = readField(client, "socket") as Socket
        return TargetMySqlConnection(serverInput, serverOutput, rawSocket, conn)
    }

    private fun readField(target: Any, fieldName: String): Any {
        var currentClass: Class<*>? = target.javaClass
        while (currentClass != null) {
            try {
                val field = currentClass.getDeclaredField(fieldName)
                field.isAccessible = true
                return field.get(target)
                    ?: throw IllegalStateException("Field '$fieldName' on ${target.javaClass} is null")
            } catch (e: NoSuchFieldException) {
                currentClass = currentClass.superclass
            }
        }
        throw NoSuchFieldException("Could not find the '$fieldName' field on ${target.javaClass}")
    }
}

private const val SSL_MODE_DISABLE = "disable"

// The sslMode for the upstream leg, from the connection's additionalOptions. Both drivers' spellings are
// accepted -- a MySQL connection's options carry MySQL Connector/J values (REQUIRED, VERIFY_IDENTITY),
// a MariaDB connection's carry MariaDB driver values (trust, verify-full) -- and are normalized to the
// MariaDB driver's, since that is the driver dialing the upstream for both flavors. Fail closed on a value
// this mapping does not know: silently ignoring it would downgrade a connection that asked for TLS to
// plaintext. PREFERRED maps to trust for the same reason -- the MariaDB driver has no opportunistic mode,
// and requiring TLS honors the intent while refusing the silent downgrade half of "preferred".
private fun upstreamSslMode(additionalOptions: String): String {
    val configured = parseAdditionalOptions(additionalOptions)
        .entries.firstOrNull { it.key.equals("sslMode", ignoreCase = true) }
        ?.value ?: return SSL_MODE_DISABLE
    return when (configured.lowercase().replace('_', '-')) {
        "disable", "disabled" -> SSL_MODE_DISABLE

        "trust", "required", "preferred" -> "trust"

        "verify-ca" -> "verify-ca"

        "verify-full", "verify-identity" -> "verify-full"

        else -> throw IllegalArgumentException(
            "Unsupported sslMode '$configured' in the connection's additional options; " +
                "the proxied session is refused rather than falling back to plaintext",
        )
    }
}

private fun serverSslCert(additionalOptions: String): String? = parseAdditionalOptions(additionalOptions)
    .entries.firstOrNull { it.key.equals("serverSslCert", ignoreCase = true) }
    ?.value
