package dev.kviklet.kviklet.proxy.postgres

import java.io.OutputStream

fun OutputStream.writeAndFlush(b: ByteArray, off: Int, len: Int) {
    this.write(b, off, len)
    this.flush()
}

fun OutputStream.writeAndFlush(b: ByteArray) {
    this.write(b)
    this.flush()
}