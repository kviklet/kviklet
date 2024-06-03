package dev.kviklet.kviklet

import dev.kviklet.kviklet.db.util.IdGenerator
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class IDTest {

    @Test
    fun `generate id`() {
        val id = IdGenerator().generateId()
        assertTrue(id is String)
        id as String
        assertTrue(id.length == 22)
    }
}
