package dev.kviklet.kviklet.proxy.postgres

import dev.kviklet.kviklet.proxy.postgres.messages.AuthenticationSASLContinue
import dev.kviklet.kviklet.proxy.postgres.messages.AuthenticationSASLFinal
import dev.kviklet.kviklet.proxy.postgres.messages.SASLInitialResponse
import dev.kviklet.kviklet.proxy.postgres.messages.SASLResponse
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.*
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
enum class AuthenticationState {
    WAITING_CLIENT_FIRST, WAITING_CLIENT_PROOF, DONE
}
/*
* Note about isUserValid:
* As the username is passed with the startup message, in the SASL flow the username is set to *(at least by the postgres driver).
* As we want to validate the user, but not provide a potential attacker a way of enumerating them(that is finding valid user by probing), the error is postpone until the point where password is sent.
* That way when an attacker tries to enumerate users, he won't know if the username or the password was incorrect.
* */
class SASLAuthHandler(private val output: OutputStream,private  val input: InputStream,private val password : String, private val isUserValid: Boolean, private val iterations : Int = 4096) {
    private val serverNonce = getRandomString()
    private val salt = Salt()
    private var state : AuthenticationState = AuthenticationState.WAITING_CLIENT_FIRST
    private var clientFirst : SASLInitialResponse? = null
    private var serverFirst : String = ""
    fun handle() {
        while (state != AuthenticationState.DONE) {
            val buff = ByteArray(8192)
            val read = input.read(buff)
            if (read > 0) {
                handleMessage(buff, read)
            }
        }
    }
    private fun handleMessage(buff : ByteArray, read : Int) {
        when (state) {
            AuthenticationState.WAITING_CLIENT_FIRST -> { handleClientFirstMessage(buff, read); }
            AuthenticationState.WAITING_CLIENT_PROOF -> { handleClientProof(buff, read);  }
            AuthenticationState.DONE -> { return }
        }
    }
    private fun handleClientFirstMessage(buff: ByteArray, read: Int) {
        clientFirst = SASLInitialResponse.fromBytes(read, buff)
        serverFirst = "r=${clientFirst!!.getClientNonce() + serverNonce},s=${salt.base64Encoded},i=${iterations}"
        sendServerFirstMessage()
    }
    private fun handleClientProof(buff: ByteArray, read: Int) {
        val clientResp = SASLResponse.fromBytes(read, buff)
        val authMsg = "${clientFirst!!.saslMessage},${serverFirst},${clientResp.getResponseWithoutProof()}"
        if(!isUserValid || !verifyClientProof(authMsg, clientResp.getProof())) {
            state = AuthenticationState.DONE
            throw Exception("Authentication failed")
        }
        sendServerFinal(authMsg)
    }

    private fun sendServerFinal(authMsg : String ) {
        val srvResp = generateServerResponse(authMsg)
        output.writeAndFlush(srvResp)
        state = AuthenticationState.DONE
    }
    private fun sendServerFirstMessage() {
        output.writeAndFlush(AuthenticationSASLContinue(serverFirst.toByteArray()))
        state = AuthenticationState.WAITING_CLIENT_PROOF
    }
    private fun verifyClientProof(authMessage: String, clientProof: String) : Boolean{
        val saltedPassword = pbkdf2(password, salt.salt, iterations)
        val clientKey =  hmacSha256(saltedPassword, "Client Key")
        val storedKey = sha256(clientKey)
        val clientSignature = hmacSha256(storedKey, authMessage)
        val expectedClientKey = xorBytes(Base64.getDecoder().decode(clientProof), clientSignature)
        val recomputedStoredKey = sha256(expectedClientKey)
        return storedKey.contentEquals(recomputedStoredKey)
    }

    private fun generateServerResponse(authMessage: String) : ByteArray {
        val saltedPassword = pbkdf2(password, salt.salt, iterations)
        val serverKey = hmacSha256(saltedPassword, "Server Key")
        val serverSignature = Base64.getEncoder().encodeToString(hmacSha256(serverKey, authMessage))
        return AuthenticationSASLFinal("v=${serverSignature}".toByteArray())
    }
}

class Salt() {
    val salt = generateRandomSalt()
    val base64Encoded: String = Base64.getEncoder().encodeToString(salt)
    private fun generateRandomSalt(size: Int = 24)  : ByteArray {
        val salt = ByteArray(size)
        SecureRandom().nextBytes(salt)
        return salt
    }

}


fun getRandomString(length: Int = 32) : String {
    val allowedChars = ('A'..'Z') + ('a'..'z') + ('0'..'9')
    return (1..length)
        .map { allowedChars.random() }
        .joinToString("")
}

fun hmacSha256(key: ByteArray, data: String): ByteArray {
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(key, "HmacSHA256"))
    return mac.doFinal(data.toByteArray(Charsets.UTF_8))
}
fun sha256(data: ByteArray): ByteArray {
    return MessageDigest.getInstance("SHA-256").digest(data)
}
fun pbkdf2(password: String, salt: ByteArray, iterations: Int): ByteArray {
    val spec = PBEKeySpec(password.toCharArray(), salt, iterations, 32 * 8)
    return SecretKeyFactory
        .getInstance("PBKDF2WithHmacSHA256")
        .generateSecret(spec)
        .encoded
}

fun xorBytes(a: ByteArray, b: ByteArray): ByteArray {
    return a.mapIndexed { i, v -> (v.toInt() xor b[i].toInt()).toByte() }.toByteArray()
}
