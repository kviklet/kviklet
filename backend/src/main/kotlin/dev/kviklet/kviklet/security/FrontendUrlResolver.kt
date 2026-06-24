package dev.kviklet.kviklet.security

import jakarta.servlet.http.HttpServletRequest
import org.springframework.stereotype.Component

@Component
class FrontendUrlResolver {
    fun resolve(request: HttpServletRequest): String {
        val scheme = request.scheme
        val serverName = request.serverName
        val serverPort = request.serverPort

        return "$scheme://$serverName${if (serverPort != 80 && serverPort != 443) ":5173" else ""}"
    }
}
