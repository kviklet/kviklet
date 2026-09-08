package dev.kviklet.kviklet.db.util

import org.hibernate.engine.spi.SharedSessionContractImplementor
import org.hibernate.id.IdentifierGenerator
import java.io.Serializable
import java.nio.ByteBuffer
import java.util.UUID

/** Width of the id columns; generated ids never exceed it. */
const val ID_LENGTH = 22

class IdGenerator : IdentifierGenerator {

    override fun generate(sharedSessionContractImplementor: SharedSessionContractImplementor, obj: Any): Serializable {
        // Keep an assigned id that fits the column. Anything longer is a placeholder (tests hand
        // in UUIDs) and is replaced.
        if (obj is BaseEntity && obj.id?.length in 1..ID_LENGTH) {
            return obj.id!!
        }
        return generateId()
    }

    /**
     * A random UUID in base58: 22 characters for most values, 21 for about one in 32, 20 for
     * about one in 1800, and so on down. Shorter ids are stored as they are; nothing pads them.
     */
    fun generateId(): Serializable {
        val uuid = UUID.randomUUID()
        val bb: ByteBuffer = ByteBuffer.allocate(16)
        bb.putLong(uuid.mostSignificantBits)
        bb.putLong(uuid.leastSignificantBits)
        return base58encode(bb.array())
    }

    override fun allowAssignedIdentifiers(): Boolean = true
}
