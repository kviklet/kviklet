package dev.kviklet.kviklet.service

import dev.kviklet.kviklet.controller.ServerUrlInterceptor
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.net.URI
import java.net.URISyntaxException

/**
 * The one answer to "where does Kviklet live": notification links, the proxy host clients connect
 * to, and the redirects back to the frontend after an SSO round trip all go through here.
 *
 * An explicitly configured `kviklet.baseUrl` always wins. Without it, Kviklet falls back to the
 * origin it is reached on: the request at hand when there is one, otherwise the first origin
 * observed by [ServerUrlInterceptor]. Deployed, the frontend is served from that same origin. In
 * local development the Vite dev server runs on its own port, so the `local` profile sets the
 * property.
 *
 * The configured value must be an absolute http(s) URL such as `https://kviklet.example.com`. Anything
 * else, e.g. a bare host name, would produce broken links and redirects, so it is logged and ignored.
 */
@Component
class BaseUrlResolver(@Value("\${kviklet.baseUrl:#{null}}") configuredBaseUrl: String?) {

    private val configuredBaseUrl = configuredBaseUrl?.trim()?.trimEnd('/')?.takeIf { it.isNotEmpty() }?.takeIf {
        isAbsoluteHttpUrl(it).also { valid ->
            if (!valid) {
                logger.error(
                    "Ignoring kviklet.baseUrl '{}': it must be an absolute URL like https://kviklet.example.com. " +
                        "Falling back to the URL Kviklet is reached on.",
                    it,
                )
            }
        }
    }

    /** For links produced outside a request, e.g. notifications. Null until anything has hit the server. */
    fun resolve(): String? = configuredBaseUrl ?: ServerUrlInterceptor.getServerUrl()

    /** For redirects answering [request]. */
    fun resolve(request: HttpServletRequest): String = configuredBaseUrl ?: originOf(request)

    companion object {
        private val logger = LoggerFactory.getLogger(BaseUrlResolver::class.java)

        private fun isAbsoluteHttpUrl(url: String): Boolean = try {
            val uri = URI(url)
            uri.scheme in setOf("http", "https") && !uri.host.isNullOrEmpty()
        } catch (e: URISyntaxException) {
            false
        }

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
