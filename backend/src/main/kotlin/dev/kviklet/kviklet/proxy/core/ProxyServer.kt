// This file is not MIT licensed
package dev.kviklet.kviklet.proxy.core

import dev.kviklet.kviklet.service.dto.ExecutionRequestId
import org.slf4j.LoggerFactory
import java.net.ServerSocket
import java.net.Socket
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

// A long-lived proxy server: one listener on a single stable port that routes every authenticated client to
// its session by the temp username. The wire protocol (Postgres, MySQL, ...) is supplied as a [ProxyProtocol]
// strategy, so this class owns only the protocol-independent lifecycle: the listener, the session registry,
// back-pressure, expiry and shutdown. Shutting down a *session* (its access window expiring) does not shut
// down the *server*.
class ProxyServer(
    // The stable listener port. A value <= 0 disables the listener entirely (used in tests and any build where
    // the proxy is not wired up); start() then becomes a no-op.
    val listenPort: Int,
    private val protocol: ProxyProtocol,
    // Bounds every read during the handshake, so an idle or aborting client cannot pin a slot (or a core)
    // forever. Set on the accepted socket before the protocol handshake runs; the handshake honours it as its
    // total budget.
    private val handshakeTimeoutMs: Int = 10_000,
    // Relay caps, applied only to authenticated connections (reserveConnectionSlot). The per-session cap is
    // the pre-single-port budget each request used to get on its own port, so one client's JDBC pool can no
    // longer starve everyone else; the global cap is a total-resource ceiling across all sessions.
    private val maxConnectionsPerSession: Int = 15,
    private val maxConnections: Int = 100,
) {
    private val threadPool = Executors.newCachedThreadPool()

    // One shared daemon-threaded scheduler for every session's expiry, instead of a non-daemon Timer thread
    // per session (which was wasteful at scale and could hold JVM shutdown open). Daemon so it never keeps the
    // JVM alive; single thread is plenty since each task only prunes a session and closes its sockets.
    private val expiryScheduler = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "proxy-expiry").apply { isDaemon = true }
    }

    // Registry keyed by temp username. ConcurrentHashMap because sessions are added by the request thread, read
    // by connection-handler threads during auth routing, and removed by expiry tasks.
    private val sessions = ConcurrentHashMap<String, ProxySession>()

    // Every live relay connection across all sessions, iterated on shutdown. Concurrent by nature: handler
    // threads add/remove while shutdown iterates.
    private val clientConnections = CopyOnWriteArrayList<ProxyConnection>()

    private lateinit var serverSocket: ServerSocket

    // Bounds concurrent in-flight handshakes (pre-auth), independent of the relay caps: unauthenticated sockets
    // get their own small budget so a connect flood cannot spawn unbounded handshake threads, yet cannot
    // consume relay slots. Paired with the handshake deadline in the protocol, which makes each pending slot
    // turn over within handshakeTimeoutMs so a slow client cannot hold one for long.
    private val maxPendingHandshakes = 50
    private val activeHandshakes = AtomicInteger(0)

    private val acceptFailureBackoffMs = 1000L

    // Concurrent in-flight (pre-auth) handshakes, exposed for tests and monitoring. Distinct from
    // currentConnections: an unauthenticated client (idle, aborting, cancelling) occupies one of these, never a
    // relay slot.
    val pendingHandshakes: Int get() = activeHandshakes.get()

    // Volatile so tests and monitoring see slot releases done by the connection handler threads. Counts only
    // authenticated, actively relaying connections (incremented in registerConnection, not on accept).
    @Volatile
    var currentConnections = 0
        private set

    // Read by the accept loop and by handleClient on pool threads, written by start/shutdownServer, so it needs
    // a happens-before edge for those threads to see the state change promptly.
    @Volatile
    var isRunning: Boolean = false
        private set

    companion object {
        private val logger = LoggerFactory.getLogger(ProxyServer::class.java)
    }

    // Binds the listener and starts accepting on a background thread. Bind failure is logged and swallowed: the
    // application must still boot (the proxy feature is simply unavailable -- e.g. the port is already taken in
    // local dev) rather than crash in a bare thread's default handler. A disabled port is a no-op.
    fun start() {
        if (listenPort <= 0) {
            logger.info("Proxy listener disabled (port $listenPort); proxy access is unavailable")
            return
        }
        try {
            serverSocket = ServerSocket(listenPort)
        } catch (e: Exception) {
            logger.error("Proxy could not bind port $listenPort; proxy access will be unavailable", e)
            return
        }
        isRunning = true
        Thread { acceptLoop() }.start()
        logger.info("Proxy listening on port $listenPort")
    }

    // Registers the session and schedules its expiry (expiresAt == null never expires). Called from the
    // service layer when an approved request starts proxying. Returns the canonical session for the request:
    // if a live session already exists for the same execution request, that one is returned unchanged (with
    // its original expiry, since the access window is anchored at the first execution) and the new one is
    // discarded -- so repeated proxy calls on one request hand out the same credentials instead of
    // accumulating registry entries, which nothing else would ever remove for a never-expiring session.
    // Synchronized against registerConnection/shutdown so a replaced session is torn down cleanly.
    @Synchronized
    fun registerSession(session: ProxySession, expiresAt: Instant?): ProxySession {
        val requestId = session.executionRequest.id
        if (requestId != null) {
            sessions.values.firstOrNull { it.active && it.executionRequest.id == requestId }?.let { return it }
        }
        // Replace any prior session for the same username, tearing the old one down so a re-registered
        // username never leaves the previous access window running.
        sessions.put(session.username, session)?.let { expire(it) }
        session.expiresAt = expiresAt
        if (expiresAt != null) {
            val delayMs = (expiresAt.toEpochMilli() - System.currentTimeMillis()).coerceAtLeast(0)
            // Bind the task to this exact session, so a fired-but-stale task (from a session already replaced
            // under the same username) removes only its own entry and never the current one.
            session.expiryFuture = expiryScheduler.schedule(
                { expireSession(session.username, session) },
                delayMs,
                TimeUnit.MILLISECONDS,
            )
        }
        return session
    }

    // Stops the whole server: no more sessions, every live relay closed, listener closed. Synchronized against
    // registerConnection/registerSession so a session is either registered before this iterates (and closed
    // here) or refused afterwards. shutdownNow does not wait on workers, so holding the monitor here cannot
    // deadlock against a worker blocked entering registerConnection.
    @Synchronized
    fun shutdownServer() {
        isRunning = false
        sessions.values.forEach {
            it.active = false
            it.expiryFuture?.cancel(false)
        }
        sessions.clear()
        expiryScheduler.shutdownNow()
        clientConnections.forEach { it.close() }
        threadPool.shutdownNow()
        if (::serverSocket.isInitialized) {
            serverSocket.close()
        }
    }

    // Ends every live session of one execution request ahead of its scheduled expiry (or, for a session
    // without a duration, at all): the request was rejected or closed, so its access window is over now. Same
    // teardown as a timed expiry -- registry entry gone, relay connections closed -- so a client that
    // reconnects with the old credentials is refused. Synchronized like registerSession so a session being
    // registered concurrently is either torn down here or registered after the terminal event was handled
    // (in which case the service layer already refused the proxy call on the review status).
    @Synchronized
    fun expireSessionsForRequest(requestId: ExecutionRequestId) {
        sessions.entries
            .filter { (_, session) -> session.executionRequest.id == requestId }
            .forEach { (username, session) -> expireSession(username, session) }
    }

    // Only sessions that are still active resolve; expiry removes them from the map and flips active, so a
    // client that arrives after its window closed finds nothing and is rejected.
    private fun resolveSession(username: String): ProxySession? = sessions[username]?.takeIf { it.active }

    // Identity remove: only drop the map entry if it still points at this session. A stale task from a session
    // already replaced under the same username thus no-ops instead of evicting the live session.
    private fun expireSession(username: String, session: ProxySession) {
        if (sessions.remove(username, session)) {
            expire(session)
        }
    }

    // Synchronized against registerConnection so it cannot miss a connection that is being added at the same
    // moment: the connection is either added before this iterates (and closed here) or refused because active
    // is already false.
    @Synchronized
    private fun expire(session: ProxySession) {
        session.active = false
        session.expiryFuture?.cancel(false)
        session.connections.forEach { it.close() }
    }

    private fun acceptLoop() {
        while (isRunning) {
            if (activeHandshakes.get() >= maxPendingHandshakes || currentConnections >= maxConnections) {
                Thread.sleep(100)
                continue
            }
            val clientSocket = acceptClientConnection() ?: continue
            handleClientConnection(clientSocket)
        }
    }

    // A failing accept is almost always persistent (out of file descriptors, listener broken), so retrying
    // immediately would turn the accept loop into a 100% CPU spin until the condition clears. Back off before
    // the next attempt instead. During shutdown the closed listener also throws here, but isRunning is already
    // false by then, so the loop exits without logging or sleeping.
    private fun acceptClientConnection(): Socket? = try {
        serverSocket.accept()
    } catch (e: Exception) {
        if (isRunning) {
            logger.warn("Failed to accept a proxy connection, retrying in ${acceptFailureBackoffMs}ms", e)
            Thread.sleep(acceptFailureBackoffMs)
        }
        null
    }

    private fun handleClientConnection(clientSocket: Socket) {
        // Count the handshake slot on the accept thread, in lockstep with the accept-loop gate, so a burst of
        // accepts cannot overshoot maxPendingHandshakes before the pool tasks start. handleClient releases it
        // the moment authentication finishes; the relay that follows holds a relay slot, not a handshake one.
        activeHandshakes.incrementAndGet()
        try {
            threadPool.submit {
                try {
                    handleClient(clientSocket)
                } catch (e: Exception) {
                    logger.warn("Error handling proxy client connection", e)
                } finally {
                    if (!clientSocket.isClosed) {
                        clientSocket.close()
                    }
                }
            }
        } catch (e: RejectedExecutionException) {
            // The pool was shut down between accept and submit; release the slot we just took and drop it.
            activeHandshakes.decrementAndGet()
            runCatching { clientSocket.close() }
        }
    }

    private fun handleClient(clientSocket: Socket) {
        // Authenticate first, routing by the username in the startup message. No upstream connection exists yet,
        // so an unauthenticated, aborting or idle client cannot leak a target DB connection. The handshake slot
        // is released as soon as this returns (success, refusal or throw) so the following relay never occupies
        // one -- an unauthenticated client can hold a handshake slot for at most the handshake budget, and only
        // relay slots (per-session capped) survive past authentication.
        val authenticatedClient = try {
            // The handshake budget bounds the whole handshake, not just each read: the protocol shrinks the
            // socket timeout to the remaining budget before every read, before any credentialed work.
            clientSocket.soTimeout = handshakeTimeoutMs
            protocol.handshake(clientSocket) { username -> resolveSession(username) }
        } finally {
            activeHandshakes.decrementAndGet()
        } ?: return // client went away or asked to cancel

        val session = authenticatedClient.session

        // The access window may have closed (server shutdown or session expiry) while the client was still
        // handshaking; do not open a credentialed upstream connection. attachConnection re-checks this
        // atomically below to also cover a close that fires during the upstream connect itself.
        if (!isRunning || !session.active) return

        // Reserve the relay slot BEFORE the protocol opens the upstream and finishes the client-facing
        // startup: a cap refusal can then be delivered as a proper protocol error while the client is still
        // in its connect phase, instead of a silent TCP close right after a successful startup (which pools
        // surface as "connection reset" on first use). It also avoids opening an upstream connection only
        // to tear it straight down again.
        when (reserveConnectionSlot(session)) {
            SlotReservation.SESSION_GONE -> return

            SlotReservation.OVER_CAPACITY -> {
                runCatching { protocol.refuseOverCapacity(authenticatedClient) }
                return
            }

            SlotReservation.RESERVED -> {}
        }

        // Only now does the protocol open the upstream connection and build the relay. It closes anything
        // it opened if this throws; the reservation must be handed back too.
        val clientConnection = try {
            protocol.connect(authenticatedClient)
        } catch (e: Exception) {
            releaseReservedSlot(session)
            throw e
        }

        // Attach atomically against shutdown and expiry: if either fired after the reservation (e.g. during
        // the upstream connect), this returns false and we close the just-opened session rather than leaving
        // it relaying past the access window.
        if (!attachConnection(session, clientConnection)) {
            clientConnection.close()
            return
        }
        try {
            clientConnection.startHandling()
        } finally {
            releaseConnection(session, clientConnection)
        }
    }

    private enum class SlotReservation { RESERVED, OVER_CAPACITY, SESSION_GONE }

    // Claims a relay slot against both caps before the upstream connect. Counting authenticated clients
    // here -- not on accept -- is what keeps unauthenticated sockets off the relay budget; the per-session
    // cap keeps one client's connection pool from starving other sessions. The reservation counts toward
    // currentConnections immediately so the accept-loop gate and the global cap see in-flight connects.
    @Synchronized
    private fun reserveConnectionSlot(session: ProxySession): SlotReservation {
        if (!isRunning || !session.active) {
            return SlotReservation.SESSION_GONE
        }
        if (currentConnections >= maxConnections) {
            logger.warn("Global proxy connection cap ($maxConnections) reached, refusing a connection")
            return SlotReservation.OVER_CAPACITY
        }
        if (session.connections.size + session.reservedConnections >= maxConnectionsPerSession) {
            logger.warn(
                "Per-session proxy connection cap ($maxConnectionsPerSession) reached for ${session.username}, " +
                    "refusing a connection",
            )
            return SlotReservation.OVER_CAPACITY
        }
        session.reservedConnections++
        currentConnections++
        return SlotReservation.RESERVED
    }

    @Synchronized
    private fun releaseReservedSlot(session: ProxySession) {
        session.reservedConnections--
        currentConnections--
    }

    // Converts a reservation into a registered relay, adding it to both the session and the global tracking
    // list -- but only if the server is still running and the session still active. Returns false otherwise
    // (handing the reservation back) so the caller tears the new relay down; nothing else would close it.
    @Synchronized
    private fun attachConnection(session: ProxySession, connection: ProxyConnection): Boolean {
        if (!isRunning || !session.active) {
            releaseReservedSlot(session)
            return false
        }
        session.reservedConnections--
        clientConnections.add(connection)
        session.connections.add(connection)
        return true
    }

    // Mirrors registerConnection: drops the relay from both lists and frees its slot. Guarded by the removal so
    // a double release (e.g. relay end racing session expiry, which also closes connections) cannot decrement
    // the counter twice.
    @Synchronized
    private fun releaseConnection(session: ProxySession, connection: ProxyConnection) {
        if (session.connections.remove(connection)) {
            clientConnections.remove(connection)
            currentConnections--
        }
    }
}
