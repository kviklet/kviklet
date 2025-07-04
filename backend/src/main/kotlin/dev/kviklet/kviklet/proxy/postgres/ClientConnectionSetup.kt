package dev.kviklet.kviklet.proxy.postgres

import dev.kviklet.kviklet.proxy.postgres.messages.authenticationOk
import dev.kviklet.kviklet.proxy.postgres.messages.backendKeyData
import dev.kviklet.kviklet.proxy.postgres.messages.createAuthenticationSASLStartMessage
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

fun setupClient(
    client: Socket,
    tlsCert: TLSCertificate?,
    params: Map<String, String>,
    username: String,
    password: String,
): Socket {
    val buffer = ByteArray(1024)
    var input = client.getInputStream()
    var output = client.getOutputStream()
    var finalSocket: Socket = client

    while (true) {
        val bytesRead = input.read(buffer)
        val data = buffer.copyOf(bytesRead)

        if (isSSLRequest(data)) {
            finalSocket = handleSSLRequest(client, tlsCert)
            input = finalSocket.getInputStream()
            output = finalSocket.getOutputStream()
        }

        val isUserValid = waitForStartupMessageWithValidUser(input, username)
        sendAuthRequest(output)
        waitUntilAuthenticated(input, output, password, isUserValid)
        output.writeAndFlush(authenticationOk())
        sendParameters(output, params)
        output.writeAndFlush(backendKeyData())
        output.writeAndFlush(readyForQuery())

        return finalSocket
    }
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

/* This method checks if a message is startup message and then if the user is valid. However, if a user is invalid, the error is not being delivered straight away.
*   Rather than that, the error is passed to the SASL flow, which cancel the authentication once a password is sent. This is to prevent enumerating available users.
* */
fun waitForStartupMessageWithValidUser(input: InputStream, username: String): Boolean {
    while (true) { // wait for startup message
        val buff = ByteArray(8192)
        val read = input.read(buff)
        if (read > 0 && isStartupMessage(buff)) {
            return !startupMessageContainsValidUser(buff, read, username)
        }
    }
}
fun sendAuthRequest(output: OutputStream) {
    val authRequest = createAuthenticationSASLStartMessage()
    output.writeAndFlush(authRequest)
}
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
