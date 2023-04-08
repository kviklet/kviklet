package com.example.executiongate.db.util

import com.example.executiongate.db.util.base58encode
import org.hibernate.engine.spi.SharedSessionContractImplementor
import org.hibernate.id.IdentifierGenerator
import java.io.Serializable
import java.nio.ByteBuffer
import java.util.UUID


class IdGenerator : IdentifierGenerator {

    override fun generate(
        sharedSessionContractImplementor: SharedSessionContractImplementor,
        obj: Any
    ): Serializable {
        val uuid = UUID.randomUUID()
        val bb: ByteBuffer = ByteBuffer.allocate(16)
        bb.putLong(uuid.mostSignificantBits)
        bb.putLong(uuid.leastSignificantBits)
        return base58encode(bb.array())
    }

    companion object {
        const val generatorName = "myGenerator"
    }
}
