package dev.kviklet.kviklet.security

import jakarta.servlet.http.HttpServletRequest

/**
 * Where the frontend lives, for redirects after SSO round trips. Deployed, the frontend is served
 * on the same origin; on the default ports there is nothing to add. In local development the
 * backend runs on its own port and the Vite dev server on 5173.
 */
fun frontendBaseUrl(request: HttpServletRequest): String {
    val scheme = request.scheme
    val serverName = request.serverName
    val serverPort = request.serverPort

    return "$scheme://$serverName${if (serverPort != 80 && serverPort != 443) ":5173" else ""}"
}
