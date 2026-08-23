// This file is not MIT licensed
package dev.kviklet.kviklet.proxy.postgres

import dev.kviklet.kviklet.proxy.postgres.messages.authenticationOk
import dev.kviklet.kviklet.proxy.postgres.messages.backendKeyData
import dev.kviklet.kviklet.proxy.postgres.messages.createAuthenticationSASLStartMessage
import dev.kviklet.kviklet.proxy.postgres.messages.gssEncNotSupportedMessage
import dev.kviklet.kviklet.proxy.postgres.messages.isCancelRequest
import dev.kviklet.kviklet.proxy.postgres.messages.isGSSENCRequest
import dev.kviklet.kviklet.proxy.postgres.messages.isSSLRequest
import dev.kviklet.kviklet.proxy.postgres.messages.isStartupMessage
import dev.kviklet.kviklet.proxy.postgres.messages.paramMessage
import dev.kviklet.kviklet.proxy.postgres.messages.readyForQuery
import dev.kviklet.kviklet.proxy.postgres.messages.startupMessageContainsValidUser
import dev.kviklet.kviklet.proxy.postgres.messages.tlsNotSupportedMessage
import dev.kviklet.kviklet.proxy.postgres.messages.tlsSupportedMessage
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import javax.net.ssl.SSLSocket

// The (possibly TLS-upgraded) client socket once the client has successfully authenticated.
class AuthenticatedClient(val socket: Socket)

// Runs SSL negotiation and client authentication. No upstream database connection is opened here, so
// an unauthenticated, aborting or idle client can never leak a target connection or pin a slot: the
// caller's handshake deadline (soTimeout) bounds every read below, and EOF ends the handshake.
//
// Returns null when the client goes away before authenticating (immediate close, or a CancelRequest
// that never authenticates and has nothing to relay).
fun authenticateClient(
    client: Socket,
    tlsCert: TLSCertificate?,
    username: String,
    password: String,
): AuthenticatedClient? {
    val handshakeTimeout = client.soTimeout
    var socket = client
    var input = socket.getInputStream()
    var output = socket.getOutputStream()

    while (true) {
        // A fresh, full-size buffer per read: the detectors below inspect the first 8 bytes, so the
        // buffer must never carry stale bytes from a previous read past a short one.
        val buffer = ByteArray(8192)
        val bytesRead = input.read(buffer)
        if (bytesRead == -1) {
            // The client closed the connection during the handshake, there is nothing to authenticate.
            return null
        }

        when {
            isSSLRequest(buffer) -> {
                socket = handleSSLRequest(socket, tlsCert)
                socket.soTimeout = handshakeTimeout
                input = socket.getInputStream()
                output = socket.getOutputStream()
            }

            isGSSENCRequest(buffer) -> {
                // We do not offer GSSAPI encryption, decline it and let the client fall back to a
                // plain StartupMessage instead of discarding the request and deadlocking.
                output.writeAndFlush(gssEncNotSupportedMessage())
            }

            isCancelRequest(buffer) -> {
                // A CancelRequest opens a throwaway connection carrying no startup message and never
                // authenticates. The proxy hands out zeroed backend key data, so there is nothing to
                // cancel: drop it rather than block forever waiting for a startup message.
                return null
            }

            isStartupMessage(buffer) -> {
                val isUserValid = !startupMessageContainsValidUser(buffer, bytesRead, username)
                sendAuthRequest(output)
                waitUntilAuthenticated(input, output, password, isUserValid)
                return AuthenticatedClient(socket)
            }

            else -> throw Exception("Unexpected message during the client handshake, aborting the connection")
        }
    }
}

// Sent after the client has authenticated and the upstream connection has been opened: tells the
// client authentication succeeded and forwards the upstream server's runtime parameters so the client
// sees the real backend configuration.
fun finishClientStartup(socket: Socket, params: Map<String, String>) {
    val output = socket.getOutputStream()
    output.writeAndFlush(authenticationOk())
    sendParameters(output, params)
    output.writeAndFlush(backendKeyData())
    output.writeAndFlush(readyForQuery())
}

fun handleSSLRequest(client: Socket, cert: TLSCertificate?): Socket {
    val response = if (cert == null) tlsNotSupportedMessage() else tlsSupportedMessage()
    client.getOutputStream().writeAndFlush(response)
    return if (cert == null) client else enableSSL(client, cert)
}

fun enableSSL(clientSocket: Socket, cert: TLSCertificate): Socket {
    val sslSocket = cert.sslContext.socketFactory.createSocket(
        clientSocket,
        null,
        clientSocket.getPort(),
        false,
    ) as SSLSocket
    sslSocket.useClientMode = false
    return sslSocket
}

fun sendAuthRequest(output: OutputStream) {
    val authRequest = createAuthenticationSASLStartMessage()
    output.writeAndFlush(authRequest)
}

/* If the user is invalid the error is not delivered straight away. Instead it is passed to the SASL
 * flow, which cancels authentication once a password is sent, so an attacker cannot tell a wrong
 * username from a wrong password and enumerate valid users.
 */
fun waitUntilAuthenticated(input: InputStream, output: OutputStream, password: String, isUserValid: Boolean) {
    val handler = SASLAuthHandler(output, input, password, isUserValid)
    handler.handle()
}

fun sendParameters(output: OutputStream, params: Map<String, String>) {
    for (param in params) {
        output.write(paramMessage(param.key, param.value))
    }
    output.flush()
}
