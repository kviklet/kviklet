// This file is not MIT licensed
package dev.kviklet.kviklet.proxy

import dev.kviklet.kviklet.proxy.postgres.messages.BindMessage
import dev.kviklet.kviklet.proxy.postgres.messages.Statement
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer

class PostgresProxyMessagesTest {

    // Builds the content of a Bind message (without the 'B' header and the length int),
    // as laid out in the Postgres wire protocol: portal name first, statement name second.
    private fun bindMessageContent(
        portal: String,
        statement: String,
        parameters: List<ByteArray?> = emptyList(),
        parameterFormatCodes: List<Int> = emptyList(),
    ): ByteArray {
        val portalBytes = portal.toByteArray() + byteArrayOf(0)
        val statementBytes = statement.toByteArray() + byteArrayOf(0)
        val paramsSize = parameters.sumOf { 4 + (it?.size ?: 0) }
        val buffer = ByteBuffer.allocate(
            portalBytes.size + statementBytes.size + 2 + parameterFormatCodes.size * 2 + 2 + paramsSize + 2,
        )
        buffer.put(portalBytes)
        buffer.put(statementBytes)
        buffer.putShort(parameterFormatCodes.size.toShort())
        parameterFormatCodes.forEach { buffer.putShort(it.toShort()) }
        buffer.putShort(parameters.size.toShort())
        parameters.forEach { param ->
            if (param == null) {
                buffer.putInt(-1)
            } else {
                buffer.putInt(param.size)
                buffer.put(param)
            }
        }
        buffer.putShort(0) // result format codes
        return buffer.array()
    }

    private fun bindMessageFromContent(content: ByteArray): BindMessage =
        BindMessage.fromBytes(content.size + 4, content)

    @Test
    fun `bind message reads the portal name first and the statement name second`() {
        val content = bindMessageContent(portal = "myportal", statement = "S_1")
        val message = bindMessageFromContent(content)
        assertEquals("S_1", message.statementName)
        assertEquals("myportal", message.portalName)
    }

    @Test
    fun `bind message parses NULL parameters instead of throwing`() {
        val content = bindMessageContent(portal = "", statement = "", parameters = listOf(null))
        val message = bindMessageFromContent(content)
        assertEquals(1, message.parameters.size)
        assertNull(message.parameters[0])
    }

    @Test
    fun `interpolation substitutes parameters beyond number nine correctly`() {
        val query = "SELECT " + (1..10).joinToString(", ") { "\$$it" }
        val statement = Statement(
            query,
            parameterFormatCodes = emptyList(),
            parameterTypes = List(10) { 25 },
            boundParams = (1..10).map { "v$it".toByteArray() },
        )
        val interpolated = statement.interpolateQuery()
        assertTrue(interpolated.endsWith("'v10'"), "Expected \$10 to become 'v10' but got: $interpolated")
        assertFalse(interpolated.contains("'v1'0"), "\$10 was corrupted by the \$1 substitution: $interpolated")
    }

    @Test
    fun `interpolation renders null parameters as unquoted NULL`() {
        val statement = Statement(
            "SELECT \$1",
            parameterFormatCodes = emptyList(),
            parameterTypes = listOf(25),
            boundParams = listOf(null),
        )
        assertEquals("SELECT NULL", statement.interpolateQuery())
    }

    @Test
    fun `interpolation escapes single quotes in parameter values`() {
        val statement = Statement(
            "SELECT \$1",
            parameterFormatCodes = emptyList(),
            parameterTypes = listOf(25),
            boundParams = listOf("O'Brien".toByteArray()),
        )
        assertEquals("SELECT 'O''Brien'", statement.interpolateQuery())
    }

    @Test
    fun `interpolation does not resubstitute placeholders contained in parameter values`() {
        val statement = Statement(
            "SELECT \$1, \$2",
            parameterFormatCodes = emptyList(),
            parameterTypes = listOf(25, 25),
            boundParams = listOf("costs \$2".toByteArray(), "x".toByteArray()),
        )
        assertEquals("SELECT 'costs \$2', 'x'", statement.interpolateQuery())
    }

    @Test
    fun `interpolation audits text format integers as their text value`() {
        val statement = Statement(
            "SELECT \$1",
            parameterFormatCodes = emptyList(),
            parameterTypes = listOf(23),
            boundParams = listOf("1234".toByteArray()),
        )
        val interpolated = statement.interpolateQuery()
        assertTrue(interpolated.contains("1234"), "Expected the text value 1234 but got: $interpolated")
        assertFalse(interpolated.contains("825373492"), "Text bytes were decoded as a binary int: $interpolated")
    }

    @Test
    fun `interpolation decodes binary format integers per their type`() {
        val statement = Statement(
            "SELECT \$1",
            parameterFormatCodes = listOf(1),
            parameterTypes = listOf(23),
            boundParams = listOf(ByteBuffer.allocate(4).putInt(1234).array()),
        )
        assertTrue(statement.interpolateQuery().contains("1234"))
    }

    @Test
    fun `interpolation does not throw when bind supplies more parameters than declared types`() {
        val statement = Statement(
            "SELECT \$1",
            parameterFormatCodes = emptyList(),
            parameterTypes = emptyList(),
            boundParams = listOf("a".toByteArray()),
        )
        assertEquals("SELECT 'a'", statement.interpolateQuery())
    }
}
