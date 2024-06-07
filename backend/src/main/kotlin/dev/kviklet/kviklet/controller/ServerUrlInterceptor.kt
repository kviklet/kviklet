package dev.kviklet.kviklet.controller

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor

@Component
class ServerUrlInterceptor : HandlerInterceptor {

    companion object {
        @Volatile
        private var serverUrl: String? = null

        fun getServerUrl(): String? = serverUrl
    }

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        if (serverUrl == null) {
            synchronized(this) {
                if (serverUrl == null) {
                    val scheme = request.scheme // http or https
                    val serverName = request.serverName // hostname or IP address
                    val serverPort = request.serverPort // port number

                    serverUrl = if (serverPort == 80 || serverPort == 443) {
                        "$scheme://$serverName"
                    } else {
                        "$scheme://$serverName:$serverPort"
                    }
                }
            }
        }
        return true
    }
}
