package dev.kviklet.kviklet.service.dto

/**
 * Where a request or event was made: the web UI, an API key, an MCP client acting for the user, or a
 * database client connected through the proxy.
 */
enum class RequestSource {
    WEB,
    API_KEY,
    MCP,
    PROXY,
}
