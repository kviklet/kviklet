package dev.kviklet.kviklet.proxy.postgres

import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.nio.ByteBuffer
import javax.net.ssl.SSLSocket

fun setupClient(
    client: Socket,
    tlsCert: TLSCertificate?,
    params: Map<String, String>,
    username: String,
    password: String
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

        waitForStartupMessage(input)
        val salt = sendAuthRequest(output)
        waitUntilAuthenticated(input, salt, username, password)
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
        clientSocket, null,
        clientSocket.getPort(), false
    ) as SSLSocket
    sslSocket.useClientMode = false
    return sslSocket
}

fun waitForStartupMessage(input: InputStream) {
    while (true) { // wait for startup message
        val buff = ByteArray(8192)
        val read = input.read(buff)
        if (read > 0 && isStartupMessage(buff)) {
            break
        }
    }
}

fun sendAuthRequest(output: OutputStream): Int {
    val salt = (0..10000).random()
    val authRequest = createAuthenticationMD5PasswordMessage(salt)
    output.writeAndFlush(authRequest)
    return salt
}

fun waitUntilAuthenticated(input: InputStream, salt: Int, username: String, password: String) {
    while (true) {
        val buff = ByteArray(8192)
        val read = input.read(buff)
        if (read > 0) {
            val parsed = ParsedMessage.fromBytes(ByteBuffer.wrap(buff))
            if (parsed is HashedPasswordMessage) {
                confirmPasswordMessage(parsed, username, password, ByteBuffer.allocate(4).putInt(salt).array())
                break
            }
        }
    }
}

fun sendParameters(output: OutputStream, params: Map<String, String>) {
    for (param in params) {
        output.write(paramMessage(param.key, param.value))
    }
    output.flush()
}