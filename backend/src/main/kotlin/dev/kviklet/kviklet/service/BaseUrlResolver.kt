package dev.kviklet.kviklet.service

import dev.kviklet.kviklet.controller.ServerUrlInterceptor
import jakarta.servlet.http.HttpServletRequest
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * The one answer to "where does Kviklet live": notification links, the proxy host clients connect
 * to, and the redirects back to the frontend after an SSO round trip all go through here.
 *
 * An explicitly configured `kviklet.baseUrl` always wins. Without it, Kviklet falls back to the
 * origin it is reached on: the request at hand when there is one, otherwise the first origin
 * observed by [ServerUrlInterceptor]. Deployed, the frontend is served from that same origin. In
 * local development the Vite dev server runs on its own port, so the `local` profile sets the
 * property.
 */
@Component
class BaseUrlResolver(@Value("\${kviklet.baseUrl:#{null}}") configuredBaseUrl: String?) {

    private val configuredBaseUrl = configuredBaseUrl?.trim()?.trimEnd('/')?.takeIf { it.isNotEmpty() }

    /** For links produced outside a request, e.g. notifications. Null until anything has hit the server. */
    fun resolve(): String? = configuredBaseUrl ?: ServerUrlInterceptor.getServerUrl()

    /** For redirects answering [request]. */
    fun resolve(request: HttpServletRequest): String = configuredBaseUrl ?: originOf(request)

    companion object {
        /** `scheme://host[:port]` of [request], omitting the port when it is the scheme's default. */
        fun originOf(request: HttpServletRequest): String {
            val scheme = request.scheme
            val serverName = request.serverName
            val serverPort = request.serverPort
            return if (serverPort == 80 || serverPort == 443) {
                "$scheme://$serverName"
            } else {
                "$scheme://$serverName:$serverPort"
            }
        }
    }
}
