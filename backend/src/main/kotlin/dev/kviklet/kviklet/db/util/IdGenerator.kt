package dev.kviklet.kviklet.db.util

import org.hibernate.engine.spi.SharedSessionContractImplementor
import org.hibernate.id.IdentifierGenerator
import java.io.Serializable
import java.nio.ByteBuffer
import java.util.UUID

class IdGenerator : IdentifierGenerator {

    override fun generate(sharedSessionContractImplementor: SharedSessionContractImplementor, obj: Any): Serializable {
        if (obj is BaseEntity && !obj.id.isNullOrBlank()) {
            return obj.id!!
        }
        return generateId()
    }

    /** A base58-encoded random UUID: 22 characters, or 21 when the leading digits are small. */
    fun generateId(): Serializable {
        val uuid = UUID.randomUUID()
        val bb: ByteBuffer = ByteBuffer.allocate(16)
        bb.putLong(uuid.mostSignificantBits)
        bb.putLong(uuid.leastSignificantBits)
        return base58encode(bb.array())
    }

    override fun allowAssignedIdentifiers(): Boolean = true
}
