package dev.kviklet.kviklet

import org.springframework.boot.test.context.SpringBootTest

/**
 * The same flow as deployed in the Docker image, where nginx serves the backend under /api: every
 * URL the MCP client is handed must include that prefix, while the requests reach the backend
 * without it.
 */
@SpringBootTest(properties = ["app.in-docker=true"])
class McpOAuthBehindNginxTest : McpOAuthTest() {
    override val backendUrl = "http://localhost/api"
}
