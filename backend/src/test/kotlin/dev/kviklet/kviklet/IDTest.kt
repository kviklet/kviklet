package dev.kviklet.kviklet

import dev.kviklet.kviklet.db.util.IdGenerator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class IDTest {

    @Test
    fun `generated ids are base58 without padding`() {
        repeat(1000) {
            val id = IdGenerator().generateId() as String
            assertTrue(id.length in 21..22, "unexpected length ${id.length} for '$id'")
            assertEquals(id.trim(), id)
        }
    }
}
