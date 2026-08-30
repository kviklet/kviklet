// This file is not MIT licensed
package dev.kviklet.kviklet.proxy

import dev.kviklet.kviklet.proxy.mysql.FailClosedException
import dev.kviklet.kviklet.proxy.mysql.MySqlClientPacketParser
import dev.kviklet.kviklet.proxy.mysql.MySqlServerPacketParser
import dev.kviklet.kviklet.proxy.mysql.buildErrPacket
import dev.kviklet.kviklet.proxy.mysql.buildInitialHandshake
import dev.kviklet.kviklet.proxy.mysql.buildOkPacket
import dev.kviklet.kviklet.proxy.mysql.verifyPassword
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream

class MySqlProxyUnitTest {

    @Test
    fun `test verifyPassword with correct and incorrect credentials`() {
        val scramble = byteArrayOf(
            1, 2, 3, 4, 5, 6, 7, 8, 9, 10,
            11, 12, 13, 14, 15, 16, 17, 18, 19, 20,
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

        assertTrue(verifyPassword(scramble, password, clientHash))

        assertFalse(verifyPassword(scramble, "wrongPassword", clientHash))

        clientHash[0] = (clientHash[0] + 1).toByte()
        assertFalse(verifyPassword(scramble, password, clientHash))
    }

    @Test
    fun `test MySqlClientPacketParser parses COM_QUERY successfully`() {
        var parsedQuery = ""
        val parser = MySqlClientPacketParser(
            onQuery = { parsedQuery = it },
            onPrepare = {},
            onExecute = { _, _ -> },
            onQuit = {},
        )

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
        val parser = MySqlClientPacketParser(
            onQuery = {},
            onPrepare = { parsedQuery = it },
            onExecute = { _, _ -> },
            onQuit = {},
        )

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
    fun `test MySqlClientPacketParser parses COM_STMT_EXECUTE successfully`() {
        var executedStmtId = 0
        val parser = MySqlClientPacketParser(
            onQuery = {},
            onPrepare = {},
            onExecute = { stmtId, _ -> executedStmtId = stmtId },
            onQuit = {},
        )

        val bos = ByteArrayOutputStream()
        val length = 5 // 1 cmd + 4 stmtId
        bos.write(length and 0xFF)
        bos.write((length ushr 8) and 0xFF)
        bos.write((length ushr 16) and 0xFF)
        bos.write(0) // Sequence ID

        bos.write(0x17) // COM_STMT_EXECUTE command byte
        bos.write(42 and 0xFF) // stmtId byte 1
        bos.write(0)
        bos.write(0)
        bos.write(0)

        parser.addBytes(bos.toByteArray())

        assertEquals(42, executedStmtId)
    }

    @Test
    fun `test MySqlClientPacketParser parses COM_STMT_SEND_LONG_DATA successfully`() {
        var longDataStmtId = 0
        var longDataParamIndex = -1
        val parser = MySqlClientPacketParser(
            onQuery = {},
            onPrepare = {},
            onExecute = { _, _ -> },
            onQuit = {},
            onLongData = { stmtId, paramIndex ->
                longDataStmtId = stmtId
                longDataParamIndex = paramIndex
            },
        )

        // cmd + stmt id 7 + param index 3 + payload bytes
        val packet = mysqlPacket(0, byteArrayOf(0x18, 7, 0, 0, 0, 3, 0) + "chunk".toByteArray(Charsets.UTF_8))
        parser.addBytes(packet)

        assertEquals(7, longDataStmtId)
        assertEquals(3, longDataParamIndex)
    }

    @Test
    fun `test MySqlClientPacketParser fails closed on a truncated COM_STMT_SEND_LONG_DATA`() {
        val parser = MySqlClientPacketParser(
            onQuery = {},
            onPrepare = {},
            onExecute = { _, _ -> },
            onQuit = {},
        )

        // Too short to carry the statement id and the parameter index
        val packet = mysqlPacket(0, byteArrayOf(0x18, 7, 0, 0, 0))
        val exception = assertThrows(FailClosedException::class.java) { parser.addBytes(packet) }
        assertTrue(exception.message!!.contains("COM_STMT_SEND_LONG_DATA"))
    }

    @Test
    fun `test MySqlClientPacketParser parses COM_STMT_RESET successfully`() {
        var resetStmtId = 0
        val parser = MySqlClientPacketParser(
            onQuery = {},
            onPrepare = {},
            onExecute = { _, _ -> },
            onQuit = {},
            onStmtReset = { resetStmtId = it },
        )

        parser.addBytes(mysqlPacket(0, byteArrayOf(0x1A, 5, 0, 0, 0)))

        assertEquals(5, resetStmtId)
    }

    @Test
    fun `test MySqlClientPacketParser parses COM_QUIT successfully`() {
        var quitCalled = false
        val parser = MySqlClientPacketParser(
            onQuery = {},
            onPrepare = {},
            onExecute = { _, _ -> },
            onQuit = { quitCalled = true },
        )

        val bos = ByteArrayOutputStream()
        val length = 1
        bos.write(length and 0xFF)
        bos.write((length ushr 8) and 0xFF)
        bos.write((length ushr 16) and 0xFF)
        bos.write(0) // Sequence ID

        bos.write(0x01) // COM_QUIT command byte

        parser.addBytes(bos.toByteArray())

        assertTrue(quitCalled)
    }

    @Test
    fun `test MySqlServerPacketParser parses STMT_PREPARE_OK successfully`() {
        var returnedStmtId = 0
        var returnedParamCount = -1
        val parser = MySqlServerPacketParser({ stmtId, paramCount ->
            returnedStmtId = stmtId
            returnedParamCount = paramCount
        })

        val bos = ByteArrayOutputStream()
        val length = 12 // 1 status + 4 stmt_id + 2 columns + 2 params + 1 filler + 2 warnings
        bos.write(length and 0xFF)
        bos.write((length ushr 8) and 0xFF)
        bos.write((length ushr 16) and 0xFF)
        bos.write(1) // Sequence ID

        bos.write(0x00) // status
        bos.write(99 and 0xFF) // stmtId byte 1
        bos.write(0)
        bos.write(0)
        bos.write(0)

        bos.write(0) // columns (2 bytes)
        bos.write(0)
        bos.write(3) // params (2 bytes)
        bos.write(0)
        bos.write(0) // filler
        bos.write(0) // warnings (2 bytes)
        bos.write(0)

        parser.addBytes(bos.toByteArray())

        assertEquals(99, returnedStmtId)
        assertEquals(3, returnedParamCount)
    }

    @Test
    fun `test MySqlClientPacketParser parses a packet delivered across two reads`() {
        var parsedQuery = ""
        val parser = MySqlClientPacketParser(
            onQuery = { parsedQuery = it },
            onPrepare = {},
            onExecute = { _, _ -> },
            onQuit = {},
        )

        val sql = "SELECT * FROM users"
        val packet = mysqlPacket(0, byteArrayOf(0x03) + sql.toByteArray(Charsets.UTF_8))

        // TCP can split a packet anywhere; nothing may be parsed until it is complete
        parser.addBytes(packet.copyOfRange(0, 7))
        assertEquals("", parsedQuery)
        parser.addBytes(packet.copyOfRange(7, packet.size))
        assertEquals(sql, parsedQuery)
    }

    @Test
    fun `test MySqlClientPacketParser reassembles a split 16MB+ COM_QUERY`() {
        var parsedQuery = ""
        val parser = MySqlClientPacketParser(
            onQuery = { parsedQuery = it },
            onPrepare = {},
            onExecute = { _, _ -> },
            onQuit = {},
        )

        // A payload of exactly 0xFFFFFF continues in the next packet. Build a query spanning two packets:
        // the first carries the command byte plus 0xFFFFFF-1 filler chars, the second the final "END".
        val firstPayload = ByteArray(0xFFFFFF)
        firstPayload[0] = 0x03 // COM_QUERY
        for (i in 1 until firstPayload.size) firstPayload[i] = 'a'.code.toByte()
        val bytes = mysqlPacket(0, firstPayload) + mysqlPacket(1, "END".toByteArray(Charsets.UTF_8))

        parser.addBytes(bytes)

        assertEquals(0xFFFFFF - 1 + 3, parsedQuery.length)
        assertTrue(parsedQuery.endsWith("aaaEND"))
    }

    @Test
    fun `test MySqlClientPacketParser fails closed on COM_CHANGE_USER`() {
        val parser = MySqlClientPacketParser(
            onQuery = {},
            onPrepare = {},
            onExecute = { _, _ -> },
            onQuit = {},
        )

        val packet = mysqlPacket(0, byteArrayOf(0x11) + "someuser".toByteArray(Charsets.UTF_8))
        val exception = assertThrows(FailClosedException::class.java) { parser.addBytes(packet) }
        assertTrue(exception.message!!.contains("COM_CHANGE_USER"))
    }

    @Test
    fun `test MySqlClientPacketParser fails closed on an unlisted command`() {
        val parser = MySqlClientPacketParser(
            onQuery = {},
            onPrepare = {},
            onExecute = { _, _ -> },
            onQuit = {},
        )

        // COM_BINLOG_DUMP (0x12) streams row changes with no auditable SQL and is not on the allowlist
        val packet = mysqlPacket(0, byteArrayOf(0x12, 0x00, 0x00, 0x00, 0x00))
        val exception = assertThrows(FailClosedException::class.java) { parser.addBytes(packet) }
        assertTrue(exception.message!!.contains("COM_BINLOG_DUMP"))
    }

    @Test
    fun `test MySqlClientPacketParser audits COM_INIT_DB as a USE statement`() {
        var parsedQuery = ""
        val parser = MySqlClientPacketParser(
            onQuery = { parsedQuery = it },
            onPrepare = {},
            onExecute = { _, _ -> },
            onQuit = {},
        )

        val packet = mysqlPacket(0, byteArrayOf(0x02) + "reporting".toByteArray(Charsets.UTF_8))
        parser.addBytes(packet)
        assertEquals("USE `reporting`", parsedQuery)
    }

    @Test
    fun `test MySqlClientPacketParser fails closed on a truncated COM_STMT_EXECUTE`() {
        val parser = MySqlClientPacketParser(
            onQuery = {},
            onPrepare = {},
            onExecute = { _, _ -> },
            onQuit = {},
        )

        // COM_STMT_EXECUTE needs at least the command byte plus a 4-byte statement id
        val packet = mysqlPacket(0, byteArrayOf(0x17, 0x01, 0x00))
        val exception = assertThrows(FailClosedException::class.java) { parser.addBytes(packet) }
        assertTrue(exception.message!!.contains("COM_STMT_EXECUTE"))
    }

    @Test
    fun `test MySqlServerPacketParser streams past an oversized split payload and still parses a prepare-ok`() {
        var returnedStmtId = 0
        val parser = MySqlServerPacketParser({ stmtId, _ -> returnedStmtId = stmtId })

        // A split logical payload (a 0xFFFFFF continuation packet plus a small final packet) is larger than
        // any control packet the server parser inspects, so it must be streamed past without buffering
        val continuation = mysqlPacket(0, ByteArray(0xFFFFFF))
        val finalPiece = mysqlPacket(1, ByteArray(8))
        val prepareOk = ByteArray(12).also { it[1] = 55 } // 0x00 status, stmt id 55
        parser.addBytes(continuation + finalPiece + mysqlPacket(2, prepareOk))

        assertEquals(55, returnedStmtId)
    }

    @Test
    fun `test MySqlServerPacketParser streams past a large single packet and still parses a prepare-ok`() {
        var returnedStmtId = 0
        val parser = MySqlServerPacketParser({ stmtId, _ -> returnedStmtId = stmtId })

        // A single result packet larger than the control-packet cap, but not split, is also streamed past
        val bigRow = mysqlPacket(0, ByteArray(5000))
        val prepareOk = ByteArray(12).also { it[1] = 7 }
        parser.addBytes(bigRow + mysqlPacket(1, prepareOk))

        assertEquals(7, returnedStmtId)
    }

    @Test
    fun `test buildInitialHandshake structure`() {
        val salt = ByteArray(20) { it.toByte() }
        val handshake = buildInitialHandshake(1234, salt, false)

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

    // One wire packet: 3-byte little-endian payload length, 1-byte sequence id, payload
    private fun mysqlPacket(sequenceId: Int, payload: ByteArray): ByteArray {
        val header = byteArrayOf(
            (payload.size and 0xFF).toByte(),
            ((payload.size ushr 8) and 0xFF).toByte(),
            ((payload.size ushr 16) and 0xFF).toByte(),
            (sequenceId and 0xFF).toByte(),
        )
        return header + payload
    }
}
