// This file is not MIT licensed
package dev.kviklet.kviklet.proxy.postgres

import dev.kviklet.kviklet.proxy.core.writeAndFlush
import dev.kviklet.kviklet.proxy.postgres.messages.SASLInitialResponse
import dev.kviklet.kviklet.proxy.postgres.messages.SASLResponse
import dev.kviklet.kviklet.proxy.postgres.messages.createAuthenticationSASLContinue
import dev.kviklet.kviklet.proxy.postgres.messages.createAuthenticationSASLFinal
import dev.kviklet.kviklet.proxy.postgres.messages.errorResponse
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.*
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
// Shared thread-safe CSPRNG for nonces and salts; constructing a SecureRandom per call re-seeds needlessly.
private val secureRandom = SecureRandom()

enum class AuthenticationState {
    WAITING_CLIENT_FIRST,
    WAITING_CLIENT_PROOF,
    DONE,
}

/*
* Note about isUserValid:
* As the username is passed with the startup message, in the SASL flow the username is set to *(at least by the postgres driver).
* As we want to validate the user, but not provide a potential attacker a way of enumerating them(that is finding valid user by probing), the error is postpone until the point where password is sent.
* That way when an attacker tries to enumerate users, he won't know if the username or the password was incorrect.
* */
class SASLAuthHandler(
    private val output: OutputStream,
    private val input: InputStream,
    private val password: String,
    private val isUserValid: Boolean,
    private val iterations: Int = 4096,
) {
    private val serverNonce = getRandomString()
    private val salt = Salt()
    private var state: AuthenticationState = AuthenticationState.WAITING_CLIENT_FIRST
    private var clientFirst: SASLInitialResponse? = null
    private var serverFirst: String = ""
    fun handle() {
        while (state != AuthenticationState.DONE) {
            val buff = ByteArray(8192)
            val read = input.read(buff)
            if (read == -1) {
                // The client aborted mid-SASL. Treat EOF as terminal so the handshake thread does not
                // hot-spin on a closed stream.
                throw Exception("Client closed the connection during SASL authentication")
            }
            if (read > 0) {
                handleMessage(buff, read)
            }
        }
    }
    private fun handleMessage(buff: ByteArray, read: Int) {
        when (state) {
            AuthenticationState.WAITING_CLIENT_FIRST -> {
                handleClientFirstMessage(buff, read)
            }

            AuthenticationState.WAITING_CLIENT_PROOF -> {
                handleClientProof(buff, read)
            }

            AuthenticationState.DONE -> {
                return
            }
        }
    }
    private fun handleClientFirstMessage(buff: ByteArray, read: Int) {
        val (length, body) = messageBody(buff, read)
        clientFirst = SASLInitialResponse.fromBytes(length, body)
        serverFirst = "r=${clientFirst!!.getClientNonce() + serverNonce},s=${salt.base64Encoded},i=$iterations"
        sendServerFirstMessage()
    }

    // Extracts (declaredLength, body) from a raw SASL read, matching the contract the relay MessageFramer
    // uses: the header byte and the int32 length are stripped, and length includes the 4 length bytes.
    private fun messageBody(buff: ByteArray, read: Int): Pair<Int, ByteArray> {
        if (read < 5) throw Exception("Incomplete SASL message header")
        val length = ByteBuffer.wrap(buff, 1, 4).int
        // length counts itself but not the header byte, so it must be at least 4 and no larger than the
        // bytes actually read. Comparing against read - 1 avoids the 1 + length overflow for a huge length.
        if (length < 4 || length > read - 1) {
            throw Exception("Invalid SASL message length $length")
        }
        return length to buff.copyOfRange(5, 1 + length)
    }

    private fun handleClientProof(buff: ByteArray, read: Int) {
        val (length, body) = messageBody(buff, read)
        val clientResp = SASLResponse.fromBytes(length, body)
        // Always run the full proof verification (PBKDF2 + HMAC) before deciding, so an unknown user and a
        // known user with a wrong password take the same time and cannot be told apart by an attacker
        // probing for valid usernames. A malformed proof (bad base64, wrong length, too few fields) is just
        // an authentication failure and must still get a 28P01 error rather than a bare connection reset.
        val authMsg: String
        val proofValid: Boolean
        try {
            authMsg = "${clientFirst!!.saslMessage},$serverFirst,${clientResp.getResponseWithoutProof()}"
            proofValid = verifyClientProof(authMsg, clientResp.getProof())
        } catch (e: Exception) {
            failAuthentication()
        }
        if (!isUserValid || !proofValid) {
            failAuthentication()
        }
        sendServerFinal(authMsg)
    }

    private fun failAuthentication(): Nothing {
        state = AuthenticationState.DONE
        // Send a proper ErrorResponse so the client reports "password authentication failed" (28P01) instead
        // of a bare connection reset. The message is deliberately generic: it must not reveal whether the
        // username or the password was wrong.
        output.writeAndFlush(errorResponse("password authentication failed", "28P01"))
        throw Exception("Authentication failed")
    }

    private fun sendServerFinal(authMsg: String) {
        val srvResp = generateServerResponse(authMsg)
        output.writeAndFlush(srvResp)
        state = AuthenticationState.DONE
    }
    private fun sendServerFirstMessage() {
        output.writeAndFlush(createAuthenticationSASLContinue(serverFirst.toByteArray()))
        state = AuthenticationState.WAITING_CLIENT_PROOF
    }
    private fun verifyClientProof(authMessage: String, clientProof: String): Boolean {
        val saltedPassword = pbkdf2(password, salt.salt, iterations)
        val clientKey = hmacSha256(saltedPassword, "Client Key")
        val storedKey = sha256(clientKey)
        val clientSignature = hmacSha256(storedKey, authMessage)
        val expectedClientKey = xorBytes(Base64.getDecoder().decode(clientProof), clientSignature)
        val recomputedStoredKey = sha256(expectedClientKey)
        // Constant-time comparison so the proof check does not leak how many leading bytes matched.
        return MessageDigest.isEqual(storedKey, recomputedStoredKey)
    }

    private fun generateServerResponse(authMessage: String): ByteArray {
        val saltedPassword = pbkdf2(password, salt.salt, iterations)
        val serverKey = hmacSha256(saltedPassword, "Server Key")
        val serverSignature = Base64.getEncoder().encodeToString(hmacSha256(serverKey, authMessage))
        return createAuthenticationSASLFinal("v=$serverSignature".toByteArray())
    }
}

class Salt {
    val salt = generateRandomSalt()
    val base64Encoded: String = Base64.getEncoder().encodeToString(salt)
    private fun generateRandomSalt(size: Int = 24): ByteArray {
        val salt = ByteArray(size)
        secureRandom.nextBytes(salt)
        return salt
    }
}

// SCRAM's replay protection assumes an unpredictable server nonce, so draw from SecureRandom rather than
// Kotlin's default (non-crypto) Random.
fun getRandomString(length: Int = 32): String {
    val allowedChars = ('A'..'Z') + ('a'..'z') + ('0'..'9')
    return (1..length)
        .map { allowedChars[secureRandom.nextInt(allowedChars.size)] }
        .joinToString("")
}

fun hmacSha256(key: ByteArray, data: String): ByteArray {
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(key, "HmacSHA256"))
    return mac.doFinal(data.toByteArray(Charsets.UTF_8))
}
fun sha256(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(data)
fun pbkdf2(password: String, salt: ByteArray, iterations: Int): ByteArray {
    val spec = PBEKeySpec(password.toCharArray(), salt, iterations, 32 * 8)
    return SecretKeyFactory
        .getInstance("PBKDF2WithHmacSHA256")
        .generateSecret(spec)
        .encoded
}

fun xorBytes(a: ByteArray, b: ByteArray): ByteArray = a.mapIndexed { i, v ->
    (v.toInt() xor b[i].toInt()).toByte()
}.toByteArray()
