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
            val socket = handleSSLRequest(client, tlsCert)
            input = socket.getInputStream()
            output = socket.getOutputStream()
            finalSocket = socket
        }

        waitForStartupMessage(input)

        val salt = sendAuthRequest(output)
        waitUntilAuthenticated(input, salt, username, password)

        output.write(authenticationOk())
        output.flush()

        sendParameters(output, params)

        output.write(backendKeyData())
        output.flush()

        output.write(readyForQuery())
        output.flush()

        return finalSocket;
    }
}

fun handleSSLRequest(client: Socket, cert: TLSCertificate?): Socket {
    if (cert == null) { // TLS is not supported
        val nByteArray = "N".toByteArray()
        val output = client.getOutputStream()
        output.write(nByteArray)
        output.flush()
        return client
    }
    // TLS is supported, we promote the socket
    val nByteArray = "S".toByteArray()
    val output = client.getOutputStream()
    output.write(nByteArray)
    output.flush()
    return enableSSL(client, cert)
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
            break;
        }
    }
}

fun sendAuthRequest(output: OutputStream): Int {
    val salt = (0..10000).random()
    val authRequest = createAuthenticationMD5PasswordMessage(salt)
    output.write(authRequest)
    output.flush()
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
                break;
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
