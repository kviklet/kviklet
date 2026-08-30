// This file is not MIT licensed
package dev.kviklet.kviklet.proxy.mysql

import java.nio.BufferUnderflowException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

// Decodes a COM_STMT_EXECUTE payload's binary parameter values and renders them into the prepared
// statement's placeholder text, so the audit log records the statement as it actually ran instead of
// "VALUES (?)". Interpolation is audit-only and strictly best effort: it must never block a legitimate
// client and must never put a wrong value in the log. So the decode is all-or-nothing -- anything the
// decoder cannot fully and unambiguously account for (an unknown type code, a layout mismatch, leftover
// bytes) abandons interpolation entirely (returns null) and the caller audits the placeholder text, which
// is exactly what was audited before values existed. The rendered SQL aims for readability, not for
// byte-perfect re-executability (same standard as the Postgres proxy's interpolateQuery).

// Binary-protocol type codes (the low byte of the 2-byte type field in the execute packet).
private const val TYPE_DECIMAL = 0x00
private const val TYPE_TINY = 0x01
private const val TYPE_SHORT = 0x02
private const val TYPE_LONG = 0x03
private const val TYPE_FLOAT = 0x04
private const val TYPE_DOUBLE = 0x05
private const val TYPE_NULL = 0x06
private const val TYPE_TIMESTAMP = 0x07
private const val TYPE_LONGLONG = 0x08
private const val TYPE_INT24 = 0x09
private const val TYPE_DATE = 0x0A
private const val TYPE_TIME = 0x0B
private const val TYPE_DATETIME = 0x0C
private const val TYPE_YEAR = 0x0D
private const val TYPE_VARCHAR = 0x0F
private const val TYPE_BIT = 0x10
private const val TYPE_JSON = 0xF5
private const val TYPE_NEWDECIMAL = 0xF6
private const val TYPE_ENUM = 0xF7
private const val TYPE_SET = 0xF8
private const val TYPE_TINY_BLOB = 0xF9
private const val TYPE_MEDIUM_BLOB = 0xFA
private const val TYPE_LONG_BLOB = 0xFB
private const val TYPE_BLOB = 0xFC
private const val TYPE_VAR_STRING = 0xFD
private const val TYPE_STRING = 0xFE
private const val TYPE_GEOMETRY = 0xFF

// The unsigned flag lives in the high byte of the 2-byte type field.
private const val UNSIGNED_FLAG = 0x8000

// COM_STMT_EXECUTE payload prefix: command byte + 4-byte statement id + 1-byte flags + 4-byte iteration
// count. The parameter section (when the statement has parameters) starts right after it.
private const val EXECUTE_HEADER_LENGTH = 10

// What a parameter streamed ahead of the execute via COM_STMT_SEND_LONG_DATA renders as. Its bytes are
// not in the execute packet (and are deliberately not accumulated -- they can be arbitrarily large blobs),
// so the audit log gets an explicit marker instead of a value.
const val LONG_DATA_PLACEHOLDER = "<long data>"

// paramTypes are the types the values were decoded with (resent types, or the caller's cached ones);
// the caller caches them because a re-execute with new-params-bound-flag = 0 does not resend them.
class InterpolatedExecute(val query: String, val paramTypes: IntArray?)

fun interpolateExecutePayload(
    query: String,
    paramCount: Int,
    cachedParamTypes: IntArray?,
    longDataParams: Set<Int>,
    payload: ByteArray,
): InterpolatedExecute? {
    if (paramCount == 0) return InterpolatedExecute(query, null)
    // The server derived paramCount by really parsing the statement; if this scan disagrees, its idea of
    // where the placeholders sit cannot be trusted, so do not splice values into the wrong places.
    val placeholders = findPlaceholders(query)
    if (placeholders.size != paramCount) return null
    return try {
        val buffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
        buffer.position(EXECUTE_HEADER_LENGTH)
        val nullBitmap = ByteArray((paramCount + 7) / 8)
        buffer.get(nullBitmap)
        val newParamsBound = (buffer.get().toInt() and 0xFF) == 1
        val paramTypes = if (newParamsBound) {
            IntArray(paramCount) { buffer.short.toInt() and 0xFFFF }
        } else {
            cachedParamTypes ?: return null
        }
        if (paramTypes.size != paramCount) return null
        val values = ArrayList<String>(paramCount)
        for (i in 0 until paramCount) {
            val isNull = (nullBitmap[i / 8].toInt() shr (i % 8)) and 1 == 1
            values += when {
                isNull -> "NULL"
                i in longDataParams -> LONG_DATA_PLACEHOLDER
                else -> renderValue(paramTypes[i], buffer) ?: return null
            }
        }
        // Leftover bytes mean the layout was not what the decoder assumed, so some value above was
        // probably misread from the wrong offset; none of it can be trusted.
        if (buffer.hasRemaining()) return null
        val interpolated = StringBuilder(query.length + 16 * paramCount)
        var copiedUpTo = 0
        placeholders.forEachIndexed { i, position ->
            interpolated.append(query, copiedUpTo, position).append(values[i])
            copiedUpTo = position + 1
        }
        interpolated.append(query, copiedUpTo, query.length)
        InterpolatedExecute(interpolated.toString(), paramTypes)
    } catch (e: BufferUnderflowException) {
        null
    } catch (e: IllegalArgumentException) {
        // A payload shorter than the execute header: buffer.position() rejects the offset.
        null
    }
}

// Renders one non-NULL parameter value, consuming exactly its wire bytes. Returns null for a type whose
// wire length the decoder does not know -- consuming a guessed number of bytes would misalign every value
// after it.
private fun renderValue(typeField: Int, buffer: ByteBuffer): String? {
    val unsigned = (typeField and UNSIGNED_FLAG) != 0
    return when (typeField and 0xFF) {
        TYPE_NULL -> "NULL"

        TYPE_TINY -> {
            val value = buffer.get()
            if (unsigned) (value.toInt() and 0xFF).toString() else value.toString()
        }

        TYPE_SHORT, TYPE_YEAR -> {
            val value = buffer.short
            if (unsigned) (value.toInt() and 0xFFFF).toString() else value.toString()
        }

        TYPE_LONG, TYPE_INT24 -> {
            val value = buffer.int
            if (unsigned) (value.toLong() and 0xFFFFFFFFL).toString() else value.toString()
        }

        TYPE_LONGLONG -> {
            val value = buffer.long
            if (unsigned) value.toULong().toString() else value.toString()
        }

        TYPE_FLOAT -> buffer.float.toString()

        TYPE_DOUBLE -> buffer.double.toString()

        TYPE_DATE, TYPE_DATETIME, TYPE_TIMESTAMP -> renderTemporal(buffer)

        TYPE_TIME -> renderTime(buffer)

        TYPE_DECIMAL, TYPE_NEWDECIMAL, TYPE_VARCHAR, TYPE_ENUM, TYPE_SET, TYPE_JSON,
        TYPE_TINY_BLOB, TYPE_MEDIUM_BLOB, TYPE_LONG_BLOB, TYPE_BLOB, TYPE_VAR_STRING, TYPE_STRING,
        -> renderString(buffer)

        TYPE_BIT, TYPE_GEOMETRY -> readLengthEncodedBytes(buffer)?.let { toHexLiteral(it) }

        else -> null
    }
}

// A length-encoded byte string: a length-encoded integer followed by that many bytes. Returns null on the
// NULL/ERR markers (0xFB/0xFF, which are not valid value lengths) or a length beyond the buffer.
private fun readLengthEncodedBytes(buffer: ByteBuffer): ByteArray? {
    val first = buffer.get().toInt() and 0xFF
    val length = when {
        first < 0xFB -> first.toLong()

        first == 0xFC -> buffer.short.toLong() and 0xFFFF

        first == 0xFD -> (buffer.get().toLong() and 0xFF) or
            ((buffer.get().toLong() and 0xFF) shl 8) or
            ((buffer.get().toLong() and 0xFF) shl 16)

        first == 0xFE -> buffer.long

        else -> return null
    }
    if (length < 0 || length > buffer.remaining()) return null
    val bytes = ByteArray(length.toInt())
    buffer.get(bytes)
    return bytes
}

// Text values become a quoted SQL literal; bytes that are not valid UTF-8 (binary blobs) become a hex
// literal instead of mojibake.
private fun renderString(buffer: ByteBuffer): String? {
    val bytes = readLengthEncodedBytes(buffer) ?: return null
    val decoder = Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
    return try {
        val text = decoder.decode(ByteBuffer.wrap(bytes)).toString()
        "'" + text.replace("\\", "\\\\").replace("'", "''") + "'"
    } catch (e: CharacterCodingException) {
        toHexLiteral(bytes)
    }
}

private fun toHexLiteral(bytes: ByteArray): String = if (bytes.isEmpty()) {
    "''"
} else {
    "0x" + bytes.joinToString("") { "%02x".format(it) }
}

// Binary DATE/DATETIME/TIMESTAMP: a length byte (0, 4, 7 or 11) then the packed fields; trailing
// all-zero fields are omitted on the wire, so length 0 is the zero date and length 4 a date-only value.
private fun renderTemporal(buffer: ByteBuffer): String? {
    val length = buffer.get().toInt() and 0xFF
    if (length > buffer.remaining()) return null
    if (length == 0) return "'0000-00-00'"
    if (length != 4 && length != 7 && length != 11) return null
    val year = buffer.short.toInt() and 0xFFFF
    val month = buffer.get().toInt() and 0xFF
    val day = buffer.get().toInt() and 0xFF
    if (length == 4) return "'%04d-%02d-%02d'".format(year, month, day)
    val hour = buffer.get().toInt() and 0xFF
    val minute = buffer.get().toInt() and 0xFF
    val second = buffer.get().toInt() and 0xFF
    if (length == 7) return "'%04d-%02d-%02d %02d:%02d:%02d'".format(year, month, day, hour, minute, second)
    val micros = buffer.int
    return "'%04d-%02d-%02d %02d:%02d:%02d.%06d'".format(year, month, day, hour, minute, second, micros)
}

// Binary TIME: a length byte (0, 8 or 12), then sign, days and the clock fields. A MySQL TIME is an
// interval, so it can be negative and exceed 24 hours; days are folded into the hour figure.
private fun renderTime(buffer: ByteBuffer): String? {
    val length = buffer.get().toInt() and 0xFF
    if (length > buffer.remaining()) return null
    if (length == 0) return "'00:00:00'"
    if (length != 8 && length != 12) return null
    val negative = (buffer.get().toInt() and 0xFF) == 1
    val days = buffer.int
    val hours = buffer.get().toInt() and 0xFF
    val minutes = buffer.get().toInt() and 0xFF
    val seconds = buffer.get().toInt() and 0xFF
    val sign = if (negative) "-" else ""
    val totalHours = days.toLong() * 24 + hours
    if (length == 8) return "'$sign%02d:%02d:%02d'".format(totalHours, minutes, seconds)
    val micros = buffer.int
    return "'$sign%02d:%02d:%02d.%06d'".format(totalHours, minutes, seconds, micros)
}

// Offsets of the bare `?` placeholders in the statement text. A `?` inside a string literal, a quoted
// identifier or a comment is not a parameter marker, so those regions are skipped the way the MySQL
// parser reads them (backslash and doubled-quote escapes in strings, doubled backticks in identifiers,
// `-- ` and `#` line comments, `/* */` block comments).
private fun findPlaceholders(query: String): List<Int> {
    val positions = mutableListOf<Int>()
    var i = 0
    while (i < query.length) {
        when (query[i]) {
            '?' -> {
                positions.add(i)
                i++
            }

            '\'', '"' -> i = skipQuoted(query, i, backslashEscapes = true)

            '`' -> i = skipQuoted(query, i, backslashEscapes = false)

            '#' -> i = skipToLineEnd(query, i)

            '-' -> i = if (i + 2 < query.length && query[i + 1] == '-' && query[i + 2].isWhitespace()) {
                skipToLineEnd(query, i)
            } else if (i + 2 == query.length && query[i + 1] == '-') {
                query.length
            } else {
                i + 1
            }

            '/' -> i = if (i + 1 < query.length && query[i + 1] == '*') {
                val end = query.indexOf("*/", i + 2)
                if (end == -1) query.length else end + 2
            } else {
                i + 1
            }

            else -> i++
        }
    }
    return positions
}

// Skips a quoted region starting at the opening quote; returns the index just past the closing quote.
// A doubled quote character is an escaped quote inside the region, not its end. An unterminated region
// runs to the end of the string.
private fun skipQuoted(query: String, start: Int, backslashEscapes: Boolean): Int {
    val quote = query[start]
    var i = start + 1
    while (i < query.length) {
        val c = query[i]
        when {
            backslashEscapes && c == '\\' -> i += 2
            c == quote && i + 1 < query.length && query[i + 1] == quote -> i += 2
            c == quote -> return i + 1
            else -> i++
        }
    }
    return query.length
}

private fun skipToLineEnd(query: String, start: Int): Int {
    val newline = query.indexOf('\n', start)
    return if (newline == -1) query.length else newline + 1
}
