package dev.kviklet.kviklet.proxy

import dev.kviklet.kviklet.db.ExecutePayload
import dev.kviklet.kviklet.service.EventService
import dev.kviklet.kviklet.service.dto.ExecutionRequest
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.security.KeyFactory
import java.security.KeyStore
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.*
import java.security.spec.PKCS8EncodedKeySpec
import java.util.*
import javax.net.ServerSocketFactory
import javax.net.ssl.*


class Connection(
    private var clientSocket: Socket,
    private var targetSocket: Socket,
    private val eventService: EventService,
    private val executionRequest: ExecutionRequest,
    private val userId: String,
) {
    private var clientInput: InputStream = clientSocket.getInputStream()
    private var clientOutput: OutputStream = clientSocket.getOutputStream()
    private var targetInput: InputStream = targetSocket.getInputStream()
    private var targetOutput: OutputStream = targetSocket.getOutputStream()
    private var setSSL: Boolean = false
    private var params: Map<String, String> = emptyMap()

    private var md5Salt = byteArrayOf()

    private val boundStatements: MutableMap<String, Statement> = mutableMapOf()

    private var proxyUsername = "postgres"
    private var proxyPassword = "postgres"

    fun startHandling(
        proxyUsername: String,
        proxyPassword: String,
        params: Map<String, String>,
    ) {
        this.proxyUsername = proxyUsername
        this.proxyPassword = proxyPassword
        this.clientInput = clientSocket.getInputStream()
        this.clientOutput = clientSocket.getOutputStream()
        this.targetInput = targetSocket.getInputStream()
        this.targetOutput = targetSocket.getOutputStream()
        this.params = params

        this.startMessageExchange()
    }

    private fun startMessageExchange() {
        val clientBuffer = ByteArray(8192)
        var lastClientMessage: MessageOrBytes? = null

        while (true) {
            // client-to-server via proxy. Either we must respond or the forward or the server so that it can respond
            if (clientInput.available() > 0) {
                lastClientMessage = handleClientData(clientBuffer, lastClientMessage)
            }


            val (serverData, bytesReadFromServer) = fetchServerData()
            if (bytesReadFromServer > 0) {
                // Data is available, prepend the read byte to the buffer
                val availableBytes = targetInput.available()
                var dataBuffer = ByteArray(availableBytes + 1)
                System.arraycopy(serverData, 0, dataBuffer, 0, 1)
                if (availableBytes > 0) {
                    targetInput.read(dataBuffer, 1, availableBytes)
                }

                // Handle the data as before
                val responseData = parseResponse(dataBuffer)
                if (responseData.second == "SSL") { // intercept TLS
                    dataBuffer = "S".toByteArray()
                }
                clientOutput.write(dataBuffer, 0, dataBuffer.size)
                clientOutput.flush()
            }

            if (lastClientMessage?.message?.isTermination() == true) {
                break
                // TODO: Handle socket and connection cleanup
            }
        }
    }

    private fun handleClientData(clientBuffer: ByteArray, lastClientMessage: MessageOrBytes?): MessageOrBytes? {
        val bytesRead = clientInput.read(clientBuffer)
        val data = clientBuffer.copyOf(bytesRead)
        var currentLastMessage: MessageOrBytes? = lastClientMessage

        for (clientMessage in parseDataToMessages(data)) {
            val newData = clientMessage.message?.toByteArray() ?: clientMessage.bytes!!
            if (proxyHasResponse(clientMessage)) {
                // respond to client
                clientOutput.write(
                    clientMessage.response!!,
                    0,
                    clientMessage.response.size
                ) // non-null assert by proxyHasResponse method
                clientOutput.flush()
                /*if (setSSL) {
                    enableSSL();
                    this.setSSL = false
                }*/
            } else {
                // pass to server
                targetOutput.write(newData, 0, newData.size)
                targetOutput.flush()
                currentLastMessage = clientMessage
            }
        }
        return currentLastMessage
    }

    private fun fetchServerData(): Pair<ByteArray, Int> {
        val singleByte = ByteArray(1)
        val bytesRead: Int = try {
            targetInput.read(singleByte, 0, 1)
        } catch (e: SocketTimeoutException) {
            0
        }
        return Pair(singleByte, bytesRead)
    }

    private fun startupHandler(byteArray: ByteArray): List<MessageOrBytes> {
        if (isSSLRequest(byteArray)) {
            // return N for no SSL
            val nByteArray = "S".toByteArray()
            setSSL = true
            return listOf(MessageOrBytes(null, byteArray, nByteArray))
        }
        // The message is not SSL, send auth request
        val salt = (0..10000).random()
        md5Salt = ByteBuffer.allocate(4).putInt(salt).array()
        val okByteArray = createAuthenticationMD5PasswordMessage(salt)
        val authMessage = MessageOrBytes(null, byteArray, okByteArray)
        return listOf(authMessage)
    }


    private fun parseDataToMessages(byteArray: ByteArray): List<MessageOrBytes> {
        // Check for SSL Request and startup message
        val buffer = ByteBuffer.wrap(byteArray)
        if (isSSLRequest(byteArray) || isStartupMessage(byteArray)) {
            return startupHandler(byteArray)
        }

        var messages = mutableListOf<MessageOrBytes>()
        while (buffer.remaining() > 0) {
            val parsedMessage = ParsedMessage.fromBytes(buffer)
            when (parsedMessage) {
                is HashedPasswordMessage -> {
                    messages = connectionSetupHandler(byteArray, parsedMessage, messages)
                    continue
                }
                is ParseMessage -> handleParseMassage(parsedMessage)
                is BindMessage -> handleBindMessage(parsedMessage)
                is ExecuteMessage -> handleExecute(parsedMessage)
            }
            messages.add(MessageOrBytes(parsedMessage, null))
        }
        return messages
    }


    private fun confirmPasswordMessage(message: HashedPasswordMessage) {
        val password = message.message
        val expectedMessage = HashedPasswordMessage.passwordContent(this.proxyUsername, this.proxyPassword, md5Salt)
        if (!password.toByteArray().contentEquals(expectedMessage)) {
            throw Exception("Password does not match")
        }
    }

    private fun connectionSetupHandler(
        originalMessage: ByteArray,
        parsedMessage: HashedPasswordMessage,
        messages: MutableList<MessageOrBytes>
    ): MutableList<MessageOrBytes> {
        confirmPasswordMessage(parsedMessage)
        messages.add(
            MessageOrBytes(
                null,
                parsedMessage.originalContent,
                authenticationOk(),
            ),
        )

        for (param in params) {
            messages.add(
                MessageOrBytes(
                    null,
                    parsedMessage.originalContent,
                    paramMessage(param.key, param.value),
                ),
            )
        }
        messages.add(
            MessageOrBytes(
                null,
                parsedMessage.originalContent,
                backendKeyData(),
            ),
        )
        messages.add(MessageOrBytes(null, originalMessage, readyForQuery()))
        return messages
    }

    private fun handleExecute(parsedMessage: ExecuteMessage) {
        val statement = boundStatements[parsedMessage.statementName]!!
        val executePayload = ExecutePayload(query = statement.interpolateQuery())
        eventService.saveEvent(executionRequest.id!!, userId, executePayload)
    }
    private fun handleParseMassage(parsedMessage: ParseMessage) {
        boundStatements[parsedMessage.statementName] = Statement(
            parsedMessage.query,
            parameterTypes = parsedMessage.parameterTypes
        )
    }
    private fun handleBindMessage(parsedMessage: BindMessage) {
        val statement = boundStatements[parsedMessage.statementName]!!
        boundStatements[parsedMessage.statementName] = Statement(
            statement.query,
            parsedMessage.parameterFormatCodes,
            statement.parameterTypes,
            parsedMessage.parameters
        )
    }
    private fun parseResponse(byteArray: ByteArray): Pair<ByteArray, String> = byteArray to "Not handled"
    private fun enableSSL() {
        val cert = TLSCertificate()

        val sslSocket = cert.sslContext.socketFactory.createSocket(
            clientSocket, null,
            clientSocket.getPort(), false
        ) as SSLSocket
        sslSocket.useClientMode = false

        clientInput = sslSocket.inputStream
        clientOutput = sslSocket.outputStream

        /*val sslContext: SSLContext = SSLContext.getInstance("TLS")
        sslContext.init(null, arrayOf<TrustManager>(PassthroughTrustManager()), SecureRandom())
        val sslSocketFactory = sslContext.getSocketFactory();
        val sslSocket = sslSocketFactory.createSocket(
            clientSocket, clientSocket.inetAddress.hostAddress, clientSocket.port, true) as SSLSocket
        sslSocket.startHandshake()
        clientSocket = sslSocket
        clientInput = clientSocket.inputStream
        clientOutput = clientSocket.outputStream*/

    }
}


class TLSCertificate() {
    val privKey = """MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQDOIyXVB4f0rBjysnbvXtVRXTO0GIojh52O7CnSRig8Ni7L46LFYGO+Ehj01Iwae0G9o5fw4tpgm3DPcQcx2I+vD2Z5YbCxCWkeZFW+M65D6diDEO/bH4rjWoP5KUU5r3VfA9qiMN3X6T7/nmIgTFi++TCE2earc3yltRTy8R9zPMnX0NyJrLdGhekEqwRmMigpAGVDOVSdXOAYeLWDMbuFBCQEZgdCW6vKhrcxjv2yuJ74AR/gf4dk50XgSmEKO/YmZHuCQifu683A93B3DuF9P9oEhYsblMAnfRmkWY7D9GY/2LPWhcvyTG49lFV1fZTdOwcQt2LYs3FeH4j7hdUxAgMBAAECggEAHEuu0cMq4mcNNaNRuCHoXjbQ9hO4QpBHDGtWgkqnEzzMx6gDm9xTVK/fRRw37xqkN4fRP3ukRkaQAameNzVm47zVcCv8uRB1oXpcWrN1ZFUhJzyX8BgwVG0EWJtVqUlwbw50YHccvJqDz0rKZWyVcgF6q4HNrBM6NPTaX07B5mteS5LYeoGmdn+bIPdIike2dgPLx2h8MiwH19r+if+qVESyo2cbuKjncCXFG7nslO3t0dqybcleeKknqO5jZrlYsd+7xcdf1t2u05cSd9qqPq1Y0OR9VerJMTZKGUBbrKVsDtfXLHy+cBo479xtGUMmSwRPRnRNfXdPi76DwxsyEQKBgQDOWwVLiTqK18upenFC/HUkokg7Zts9JU/1jq8fFn3GRB8R7Movg7zFf3LRrlGmL3MpVaC9XJpmGX571oEPAprozXZxYHGbqyzviyPimDuXqVZEBwWvIVf8WH69KTLGbRCLzunCMPKYYk0GDHLMFgRpVL4p88hTl/Ft3YKNzgXrvwKBgQD/uq9zBsKCQUBKrckFx4aVLWnpkge1C63tC7LeG7VQi7UuF1TWugy9vY3/po3PSvf6XUSaRwCrT4SN4KLWREdCEnU4h/Fl9F0ntQhP3VA2Uy8mQsilYp1REdNajx2JP/q2dr3TljXkiqEDgiNUn1Lvtiw1QFixor3F8rlzyig7DwKBgAiQfofECkn46tr92fWNxM7gbV8Jxc+j3M20PlBr/oxcB24XBc0zCoKn53wMYBcloQH2K9WwIjhaloVNQc39rbA71s6d0hlD4XmPrM2aw95niM0J/ZJnL9+pTJlNPG4/2I/05n7IyUjJy6iUm68cutIkUkArfgT6KWsF5oU8J8LBAoGBAPcRETMrg76+dfPwhLfNtkvoDVx5FnMm7omHdO87i+heodP+/JtcMrUaLtegvX9Zqc08UOxQzuezspg0QH6Mht/h31iXlnTvKxUSxQ4L/tQNeA8aFKocZWsOssjaXindI0cn32xNwpGkEb3G/IVkTIeF1J46JbaxSXG2eM/Srx2nAoGAC/rjYVJ+eDAKz867hTNu9fw+vdX1Yl/ph59oY851QeKxL7KMTb0FDLbrvfhBCiLAn4Xk0NUwhgRhCP80UDv/OirQUFE5QvnbBZ0BZeZGcz2lW62jzpvng5N6NwK2Nxm+qEdtLGJjjhfvy9iTX7p2CJC2+PPy2deEXgREkGk/jpo="""
    val cert = """MIIC9DCCAdygAwIBAgIGAZViVxKQMA0GCSqGSIb3DQEBCwUAMDsxDTALBgNVBAMMBHRlc3QxCzAJBgNVBAYTAlVTMR0wGwYJKoZIhvcNAQkBFg50ZXN0QHRlc3QudGVzdDAeFw0yNTAzMDQxODA3MDhaFw0yNjAzMDQxODA3MDhaMDsxDTALBgNVBAMMBHRlc3QxCzAJBgNVBAYTAlVTMR0wGwYJKoZIhvcNAQkBFg50ZXN0QHRlc3QudGVzdDCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAM4jJdUHh/SsGPKydu9e1VFdM7QYiiOHnY7sKdJGKDw2LsvjosVgY74SGPTUjBp7Qb2jl/Di2mCbcM9xBzHYj68PZnlhsLEJaR5kVb4zrkPp2IMQ79sfiuNag/kpRTmvdV8D2qIw3dfpPv+eYiBMWL75MITZ5qtzfKW1FPLxH3M8ydfQ3Imst0aF6QSrBGYyKCkAZUM5VJ1c4Bh4tYMxu4UEJARmB0Jbq8qGtzGO/bK4nvgBH+B/h2TnReBKYQo79iZke4JCJ+7rzcD3cHcO4X0/2gSFixuUwCd9GaRZjsP0Zj/Ys9aFy/JMbj2UVXV9lN07BxC3YtizcV4fiPuF1TECAwEAATANBgkqhkiG9w0BAQsFAAOCAQEAH0zmELPuP1nwSwSbY6rmQsR6gMHpwGYKHdo51194bkZgwVDwV1VcU74EaCyKGXHR9Jko24+raxuMJQHyxNv/JtvNWuz/jmNVDPSrtKOaQ3z9xfM+Lql8dIk1X/7xJgOES3IQQ32tSfXCCDaRs/nFL/HOsybqRftmxerci1fwiDdA/fgtc3GDY11vY2Yj9+oIWQLxwxc8UsGRibSzIFZTPD9Pn6SiriiZFX9ETYm90JaGuiJU5ZP/zmQys8GRq3jbskmo6aCNA1quJ9Gb7lO+vXcknA4Y+13/rMJ0Ao/kaOlhwXLPDla42taOY4qhcAm2CCzQqBd5lyaay33ZnWHPFw=="""
    var sslContext : SSLContext
    init {
        val certFactory = CertificateFactory.getInstance("X.509")
        var certificate : Certificate
        ByteArrayInputStream(
            Base64.getDecoder().decode(cert)
        ).use { certStream ->
            certificate = certFactory.generateCertificate(certStream)
        }
        val keyFactory = KeyFactory.getInstance("RSA")
        val privateKey: PrivateKey
        ByteArrayInputStream(
            Base64.getDecoder().decode(privKey)
        ).use { keyStream ->
            val keySpec = PKCS8EncodedKeySpec(keyStream.readAllBytes())
            privateKey = keyFactory.generatePrivate(keySpec)
        }
        val keyStore = KeyStore.getInstance("PKCS12")
        keyStore.load(null, null)
        keyStore.setKeyEntry("server", privateKey, CharArray(0), arrayOf(certificate))
        val kmf = KeyManagerFactory.getInstance("SunX509")
        kmf.init(keyStore, CharArray(0))
        this.sslContext = SSLContext.getInstance("TLS")
        sslContext.init(kmf.keyManagers, null, SecureRandom())
    }
}
fun proxyHasResponse(clientMessage: MessageOrBytes): Boolean {
    return clientMessage.response != null
}
