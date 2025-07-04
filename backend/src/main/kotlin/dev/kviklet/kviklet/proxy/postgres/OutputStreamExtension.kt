package dev.kviklet.kviklet.proxy.postgres

import java.io.OutputStream

fun OutputStream.writeAndFlush(b: ByteArray) {
    this.write(b)
    this.flush()
}
