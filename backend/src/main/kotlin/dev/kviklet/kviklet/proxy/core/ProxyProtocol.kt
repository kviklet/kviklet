// This file is not MIT licensed
package dev.kviklet.kviklet.proxy.core

import java.net.Socket

// The (possibly TLS-upgraded) client socket once the client has successfully authenticated, together with
// the session its username routed to and the raw accepted socket underneath. rawClientSocket is the original
// TCP socket: for a TLS client [socket] is an SSLSocket layered over it, and only closing the raw socket
// reliably unblocks the relay threads on teardown, so the relay is built against both.
class AuthenticatedClient(val socket: Socket, val rawClientSocket: Socket, val session: ProxySession)

// The wire-protocol-specific half of the proxy. Everything a ProxyServer needs to serve one client after it
// has been accepted: run the handshake (TLS + authentication + username routing), then open the upstream and
// build the relay. Keeping this behind an interface is what lets one generic listener/lifecycle serve any
// database protocol -- a Postgres and a MySQL implementation plug into the same ProxyServer.
interface ProxyProtocol {
    // Runs TLS negotiation and client authentication, routing to a session by the username the client sends in
    // its startup/handshake message. No upstream connection is opened here, so an unauthenticated, aborting or
    // idle client can never leak a target connection: the caller sets the socket's timeout as the handshake
    // budget before calling this, and every read below must honour it.
    //
    // [resolve] returns the session for a username, or null if there is none. An unknown username must not be
    // short-circuited: the full authentication exchange still runs so it fails identically to a wrong password
    // and an attacker cannot enumerate valid usernames. Returns null when the client goes away before
    // authenticating (immediate close, or a cancel/ping that never authenticates and has nothing to relay);
    // throws on an authentication failure.
    fun handshake(clientSocket: Socket, resolve: (String) -> ProxySession?): AuthenticatedClient?

    // Opens the upstream database connection for the now-authenticated client's session, performs any
    // post-authentication finalization the protocol requires, and wraps both sockets in a ready-to-run relay.
    // Throws (after closing anything it opened) if the upstream cannot be established.
    fun connect(authenticatedClient: AuthenticatedClient): ProxyConnection
}
