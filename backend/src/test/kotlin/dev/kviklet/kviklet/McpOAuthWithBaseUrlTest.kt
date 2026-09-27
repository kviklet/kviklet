package dev.kviklet.kviklet

import org.springframework.boot.test.context.SpringBootTest

/**
 * The deployed flow with `kviklet.baseUrl` configured: requests reach the backend as plain
 * http://localhost, as they would through a proxy that passes on neither the scheme nor the port,
 * yet every URL the MCP client is handed is built from the configured base URL.
 */
@SpringBootTest(properties = ["app.in-docker=true", "kviklet.baseUrl=https://kviklet.example.com:8443"])
class McpOAuthWithBaseUrlTest : McpOAuthTest() {
    override val backendUrl = "https://kviklet.example.com:8443/api"
    override val frontendUrl = "https://kviklet.example.com:8443"
}
