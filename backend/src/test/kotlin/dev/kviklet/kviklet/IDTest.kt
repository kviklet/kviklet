package dev.kviklet.kviklet

import dev.kviklet.kviklet.db.util.ID_LENGTH
import dev.kviklet.kviklet.db.util.IdGenerator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class IDTest {

    @Test
    fun `generated ids fit the column and carry no whitespace`() {
        repeat(20_000) {
            val id = IdGenerator().generateId() as String
            assertTrue(id.length in 1..ID_LENGTH, "unexpected length ${id.length} for '$id'")
            assertEquals(id.trim(), id)
        }
    }
}
