// This file is not MIT licensed
package dev.kviklet.kviklet.proxy.core

// A single live relay between an authenticated client and its upstream database, owned by a ProxySession.
// The protocol-specific relay (Postgres Connection, MySQL MySqlConnection) implements this; the generic
// ProxyServer only ever drives it through these two calls, so it never depends on wire-protocol details.
interface ProxyConnection {
    // Blocking relay in both directions until the client or server disconnects, the session is torn down, or
    // the connection aborts. Returns when the relay has fully stopped.
    fun startHandling()

    // Stops the relay and closes both sockets. Idempotent: safe to call from the relay's own teardown and
    // again from a concurrent session expiry or server shutdown.
    fun close()
}
