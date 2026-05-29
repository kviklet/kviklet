package dev.kviklet.kviklet.proxy

import dev.kviklet.kviklet.proxy.mysql.MySqlClientPacketParser
import dev.kviklet.kviklet.proxy.mysql.buildInitialHandshake
import dev.kviklet.kviklet.proxy.mysql.buildErrPacket
import dev.kviklet.kviklet.proxy.mysql.buildOkPacket
import dev.kviklet.kviklet.proxy.mysql.verifyPassword
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream

class MySqlProxyUnitTest {

    @Test
    fun `test verifyPassword with correct and incorrect credentials`() {
        val scramble = byteArrayOf(
            1, 2, 3, 4, 5, 6, 7, 8, 9, 10,
            11, 12, 13, 14, 15, 16, 17, 18, 19, 20
        )
        val password = "secretPassword123"
        
        // Let's compute the expected client hash manually using the formula:
        // SHA1(password) XOR SHA1(scramble + SHA1(SHA1(password)))
        val sha1 = java.security.MessageDigest.getInstance("SHA-1")
        val sha1Password = sha1.digest(password.toByteArray(Charsets.UTF_8))
        val sha1Sha1Password = sha1.digest(sha1Password)
        
        val concat = ByteArray(scramble.size + sha1Sha1Password.size)
        System.arraycopy(scramble, 0, concat, 0, scramble.size)
        System.arraycopy(sha1Sha1Password, 0, concat, scramble.size, sha1Sha1Password.size)
        
        val sha1Concat = sha1.digest(concat)
        val clientHash = ByteArray(sha1Password.size)
        for (i in sha1Password.indices) {
            clientHash[i] = (sha1Password[i].toInt() xor sha1Concat[i].toInt()).toByte()
        }

        // Must verify successfully
        assertTrue(verifyPassword(scramble, password, clientHash))

        // Must fail with incorrect password
        assertFalse(verifyPassword(scramble, "wrongPassword", clientHash))

        // Must fail with modified client hash
        clientHash[0] = (clientHash[0] + 1).toByte()
        assertFalse(verifyPassword(scramble, password, clientHash))
    }

    @Test
    fun `test MySqlClientPacketParser parses COM_QUERY successfully`() {
        var parsedQuery = ""
        val parser = MySqlClientPacketParser { query ->
            parsedQuery = query
        }

        val sql = "SELECT * FROM users WHERE id = 1"
        val sqlBytes = sql.toByteArray(Charsets.UTF_8)
        
        val bos = ByteArrayOutputStream()
        // MySQL Packet Header: 3 bytes length, 1 byte sequence id
        val length = sqlBytes.size + 1 // +1 for the command byte
        bos.write(length and 0xFF)
        bos.write((length ushr 8) and 0xFF)
        bos.write((length ushr 16) and 0xFF)
        bos.write(0) // Sequence ID
        
        bos.write(0x03) // COM_QUERY command byte
        bos.write(sqlBytes)

        parser.addBytes(bos.toByteArray())

        assertEquals(sql, parsedQuery)
    }

    @Test
    fun `test MySqlClientPacketParser parses COM_STMT_PREPARE successfully`() {
        var parsedQuery = ""
        val parser = MySqlClientPacketParser { query ->
            parsedQuery = query
        }

        val sql = "INSERT INTO logs (message) VALUES (?)"
        val sqlBytes = sql.toByteArray(Charsets.UTF_8)
        
        val bos = ByteArrayOutputStream()
        val length = sqlBytes.size + 1
        bos.write(length and 0xFF)
        bos.write((length ushr 8) and 0xFF)
        bos.write((length ushr 16) and 0xFF)
        bos.write(0) // Sequence ID
        
        bos.write(0x16) // COM_STMT_PREPARE command byte
        bos.write(sqlBytes)

        parser.addBytes(bos.toByteArray())

        assertEquals(sql, parsedQuery)
    }

    @Test
    fun `test buildInitialHandshake structure`() {
        val salt = ByteArray(20) { it.toByte() }
        val handshake = buildInitialHandshake(1234, salt)
        
        // Protocol version must be 10
        assertEquals(10.toByte(), handshake[0])
        
        // Server version should start after protocol byte
        val versionStr = String(handshake, 1, 14, Charsets.US_ASCII)
        assertEquals("8.0.35-kviklet", versionStr)
    }

    @Test
    fun `test buildOkPacket and buildErrPacket structure`() {
        val ok = buildOkPacket()
        assertEquals(0x00.toByte(), ok[0]) // OK header

        val err = buildErrPacket(1045, "28000", "Access denied")
        assertEquals(0xFF.toByte(), err[0]) // ERR header
        assertEquals(1045 and 0xFF, err[1].toInt() and 0xFF)
        assertEquals('#'.code.toByte(), err[3])
    }
}
