package dev.kviklet.kviklet.controller

import dev.kviklet.kviklet.service.BaseUrlResolver
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
                    val targetPath = request.servletPath
                    if (targetPath.startsWith("/health")) {
                        // Skip setting serverUrl for health check endpoint as this is not a user-facing URL
                        // and likely wont contain the correct server URL.
                        return true
                    }

                    serverUrl = BaseUrlResolver.originOf(request)
                }
            }
        }
        return true
    }
}
