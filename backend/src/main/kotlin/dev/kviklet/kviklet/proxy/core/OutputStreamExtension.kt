// This file is not MIT licensed
package dev.kviklet.kviklet.proxy.core

import java.io.OutputStream

fun OutputStream.writeAndFlush(b: ByteArray) {
    this.write(b)
    this.flush()
}
