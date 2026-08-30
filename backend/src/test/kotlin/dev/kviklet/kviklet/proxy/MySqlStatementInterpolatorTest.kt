// This file is not MIT licensed
package dev.kviklet.kviklet.proxy

import dev.kviklet.kviklet.proxy.mysql.LONG_DATA_PLACEHOLDER
import dev.kviklet.kviklet.proxy.mysql.interpolateExecutePayload
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream

// Unit tests for the COM_STMT_EXECUTE parameter decoder, driving interpolateExecutePayload with
// hand-built binary payloads. Type codes are the MySQL binary-protocol values (LONG = 0x03,
// VAR_STRING = 0xFD, ...); 0x8000 on a type is the unsigned flag.
class MySqlStatementInterpolatorTest {

    @Test
    fun `interpolates integer and string parameters`() {
        val payload = executePayload(
            paramCount = 2,
            types = listOf(0x03, 0xFD), // LONG, VAR_STRING
            values = listOf(int4(42), lenenc("o'brien")),
        )
        val result = interpolateExecutePayload(
            "INSERT INTO t (a, b) VALUES (?, ?)",
            2,
            null,
            emptySet(),
            payload,
        )
        assertNotNull(result)
        assertEquals("INSERT INTO t (a, b) VALUES (42, 'o''brien')", result!!.query)
        assertArrayEquals(intArrayOf(0x03, 0xFD), result.paramTypes)
    }

    @Test
    fun `renders a NULL parameter from the null bitmap`() {
        // The NULL parameter has a type entry but no value bytes.
        val payload = executePayload(
            paramCount = 2,
            types = listOf(0xFD, 0x03),
            values = listOf(int4(7)),
            nullParams = setOf(0),
        )
        val result = interpolateExecutePayload("INSERT INTO t VALUES (?, ?)", 2, null, emptySet(), payload)
        assertEquals("INSERT INTO t VALUES (NULL, 7)", result!!.query)
    }

    @Test
    fun `a re-execute without resent types uses the cached types`() {
        val payload = executePayload(
            paramCount = 1,
            types = null, // new-params-bound-flag = 0
            values = listOf(int4(9)),
        )
        val result = interpolateExecutePayload(
            "UPDATE t SET a = ?",
            1,
            intArrayOf(0x03),
            emptySet(),
            payload,
        )
        assertEquals("UPDATE t SET a = 9", result!!.query)
    }

    @Test
    fun `a re-execute without resent types and no cache falls back`() {
        val payload = executePayload(paramCount = 1, types = null, values = listOf(int4(9)))
        assertNull(interpolateExecutePayload("UPDATE t SET a = ?", 1, null, emptySet(), payload))
    }

    @Test
    fun `an unknown parameter type falls back`() {
        val payload = executePayload(paramCount = 1, types = listOf(0x77), values = listOf(int4(1)))
        assertNull(interpolateExecutePayload("SELECT ?", 1, null, emptySet(), payload))
    }

    @Test
    fun `leftover undecoded bytes fall back`() {
        val payload = executePayload(
            paramCount = 1,
            types = listOf(0x03),
            values = listOf(int4(1), byteArrayOf(0x00)), // one stray byte after the value
        )
        assertNull(interpolateExecutePayload("SELECT ?", 1, null, emptySet(), payload))
    }

    @Test
    fun `a truncated value falls back`() {
        val payload = executePayload(
            paramCount = 1,
            types = listOf(0x08), // LONGLONG needs 8 bytes
            values = listOf(int4(1)),
        )
        assertNull(interpolateExecutePayload("SELECT ?", 1, null, emptySet(), payload))
    }

    @Test
    fun `placeholders inside literals and comments are not replaced`() {
        val query = "INSERT INTO t (a, b) VALUES ('?', ?) -- trailing ?\n/* also ? */ # and ?"
        val payload = executePayload(paramCount = 1, types = listOf(0x03), values = listOf(int4(5)))
        val result = interpolateExecutePayload(query, 1, null, emptySet(), payload)
        assertEquals("INSERT INTO t (a, b) VALUES ('?', 5) -- trailing ?\n/* also ? */ # and ?", result!!.query)
    }

    @Test
    fun `a placeholder count mismatch falls back`() {
        // The server says two parameters but the text only has one bare placeholder: the scan cannot be
        // trusted to splice values into the right places.
        val payload = executePayload(
            paramCount = 2,
            types = listOf(0x03, 0x03),
            values = listOf(int4(1), int4(2)),
        )
        assertNull(interpolateExecutePayload("SELECT ? + 1", 2, null, emptySet(), payload))
    }

    @Test
    fun `a long data parameter renders as a marker and its value is not expected in the payload`() {
        val payload = executePayload(
            paramCount = 2,
            types = listOf(0xFB, 0x03), // LONG_BLOB, LONG
            values = listOf(int4(3)), // only the second parameter has value bytes
        )
        val result = interpolateExecutePayload(
            "INSERT INTO t (blob_col, id) VALUES (?, ?)",
            2,
            null,
            setOf(0),
            payload,
        )
        assertEquals("INSERT INTO t (blob_col, id) VALUES ($LONG_DATA_PLACEHOLDER, 3)", result!!.query)
    }

    @Test
    fun `an unsigned longlong renders its unsigned value`() {
        val payload = executePayload(
            paramCount = 1,
            types = listOf(0x8008), // LONGLONG with the unsigned flag
            values = listOf(int8(-1L)),
        )
        val result = interpolateExecutePayload("SELECT ?", 1, null, emptySet(), payload)
        assertEquals("SELECT 18446744073709551615", result!!.query)
    }

    @Test
    fun `datetime and date parameters render as quoted literals`() {
        val datetime = byteArrayOf(7, 0xE9.toByte(), 0x07, 8, 30, 12, 34, 56) // 2025-08-30 12:34:56
        val date = byteArrayOf(4, 0xE9.toByte(), 0x07, 1, 2) // 2025-01-02
        val payload = executePayload(
            paramCount = 2,
            types = listOf(0x0C, 0x0A), // DATETIME, DATE
            values = listOf(datetime, date),
        )
        val result = interpolateExecutePayload("SELECT ?, ?", 2, null, emptySet(), payload)
        assertEquals("SELECT '2025-08-30 12:34:56', '2025-01-02'", result!!.query)
    }

    @Test
    fun `bytes that are not valid UTF-8 render as a hex literal`() {
        val payload = executePayload(
            paramCount = 1,
            types = listOf(0xFC), // BLOB
            values = listOf(lenenc(byteArrayOf(0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte()))),
        )
        val result = interpolateExecutePayload("INSERT INTO t VALUES (?)", 1, null, emptySet(), payload)
        assertEquals("INSERT INTO t VALUES (0xdeadbeef)", result!!.query)
    }

    @Test
    fun `backslashes in a string value are escaped`() {
        val payload = executePayload(
            paramCount = 1,
            types = listOf(0xFD),
            values = listOf(lenenc("""C:\temp""")),
        )
        val result = interpolateExecutePayload("SELECT ?", 1, null, emptySet(), payload)
        assertEquals("""SELECT 'C:\\temp'""", result!!.query)
    }

    @Test
    fun `a statement without parameters is returned unchanged`() {
        val result = interpolateExecutePayload("SELECT 1", 0, null, emptySet(), executePayload(0, null, emptyList()))
        assertEquals("SELECT 1", result!!.query)
    }

    // --- payload builders -------------------------------------------------------------------------------

    // One COM_STMT_EXECUTE payload: cmd + stmt id + flags + iteration count, then (with parameters) the
    // null bitmap, the new-params-bound flag (1 when types are given, 0 otherwise), the type array and
    // the value bytes.
    private fun executePayload(
        paramCount: Int,
        types: List<Int>?,
        values: List<ByteArray>,
        nullParams: Set<Int> = emptySet(),
    ): ByteArray {
        val bos = ByteArrayOutputStream()
        bos.write(0x17)
        bos.write(byteArrayOf(1, 0, 0, 0)) // statement id
        bos.write(0) // flags
        bos.write(byteArrayOf(1, 0, 0, 0)) // iteration count
        if (paramCount > 0) {
            val bitmap = ByteArray((paramCount + 7) / 8)
            for (p in nullParams) bitmap[p / 8] = (bitmap[p / 8].toInt() or (1 shl (p % 8))).toByte()
            bos.write(bitmap)
            bos.write(if (types != null) 1 else 0)
            types?.forEach { type ->
                bos.write(type and 0xFF)
                bos.write((type ushr 8) and 0xFF)
            }
            values.forEach { bos.write(it) }
        }
        return bos.toByteArray()
    }

    private fun int4(value: Int): ByteArray = ByteArray(4) { ((value ushr (it * 8)) and 0xFF).toByte() }

    private fun int8(value: Long): ByteArray = ByteArray(8) { ((value ushr (it * 8)) and 0xFF).toByte() }

    private fun lenenc(value: String): ByteArray = lenenc(value.toByteArray(Charsets.UTF_8))

    private fun lenenc(bytes: ByteArray): ByteArray {
        check(bytes.size < 0xFB) { "test helper only builds one-byte length encodings" }
        return byteArrayOf(bytes.size.toByte()) + bytes
    }
}
