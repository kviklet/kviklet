package dev.kviklet.kviklet.service.dto

/** Where a request or event was made: the web UI, an API key or an MCP client acting for the user. */
enum class RequestSource {
    WEB,
    API_KEY,
    MCP,
}
