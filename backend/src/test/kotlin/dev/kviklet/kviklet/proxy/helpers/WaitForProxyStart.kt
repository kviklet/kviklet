package dev.kviklet.kviklet.proxy.helpers

import dev.kviklet.kviklet.proxy.postgres.PostgresProxy

fun waitForProxyStart(proxy: PostgresProxy) {
    var sleepCycle = 0
    while (!proxy.isRunning && sleepCycle < 10) {
        Thread.sleep(1000); sleepCycle++
    }
    if (sleepCycle >= 10) {
        throw Exception("Failed to start server")
    }
}