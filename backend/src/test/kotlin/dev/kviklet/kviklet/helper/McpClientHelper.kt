package dev.kviklet.kviklet.helper

import dev.kviklet.kviklet.db.User
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.util.UriComponentsBuilder
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.net.URLDecoder
import java.security.MessageDigest
import java.util.Base64

/** The result of an MCP tool call: the tool's JSON answer, or the error text when [isError]. */
data class ToolCallResult(val isError: Boolean, val text: String, val json: JsonNode?)

/**
 * Acts as an MCP client like Claude Code: logs a user in through the OAuth flow and calls tools on
 * /mcp with the resulting access token. Needs a license, since MCP is an enterprise feature.
 */
@Component
class McpClientHelper(private val userHelper: UserHelper, private val objectMapper: ObjectMapper) {

    private val redirectUri = "http://localhost:53682/callback"
    private val codeVerifier = "a-code-verifier-that-is-long-enough-for-pkce-0123456789"

    fun obtainAccessToken(mockMvc: MockMvc, user: User): String {
        val clientId = registerClient(mockMvc)
        val cookie = userHelper.login(mockMvc = mockMvc, email = user.email)

        val authorization = get(
            UriComponentsBuilder.fromPath("/oauth2/authorize")
                .queryParam("response_type", "code")
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("state", "client-state")
                .queryParam("code_challenge", codeChallenge())
                .queryParam("code_challenge_method", "S256")
                .queryParam("resource", "http://localhost/mcp")
                .encode()
                .build()
                .toUriString(),
        ).cookie(cookie)
        val consentUrl = mockMvc.perform(authorization).andExpect(status().isFound)
            .andReturn().response.getHeader(HttpHeaders.LOCATION)!!

        val consent = post("/oauth2/authorize").cookie(cookie)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .param("client_id", clientId)
            .param("state", queryParam(consentUrl, "state"))
            .param("scope", "mcp")
        val clientRedirect = mockMvc.perform(consent).andExpect(status().isOk).andReturn().response
            .contentAsString.let { objectMapper.readTree(it)["redirectUri"].asString() }

        val tokens = mockMvc.perform(
            post("/oauth2/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("grant_type", "authorization_code")
                .param("code", queryParam(clientRedirect, "code"))
                .param("redirect_uri", redirectUri)
                .param("client_id", clientId)
                .param("code_verifier", codeVerifier)
                .param("resource", "http://localhost/mcp"),
        ).andExpect(status().isOk).andReturn().response.contentAsString
        return objectMapper.readTree(tokens)["access_token"].asString()
    }

    fun callTool(
        mockMvc: MockMvc,
        token: String,
        name: String,
        arguments: Map<String, Any?> = emptyMap(),
    ): ToolCallResult {
        val call = mapOf(
            "jsonrpc" to "2.0",
            "id" to 1,
            "method" to "tools/call",
            "params" to mapOf("name" to name, "arguments" to arguments),
        )
        val response = mockMvc.perform(
            post("/mcp")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                .header(HttpHeaders.ACCEPT, "application/json, text/event-stream")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(call)),
        ).andExpect(status().isOk).andReturn().response.contentAsString
        val body = objectMapper.readTree(response)
        val result = body["result"] ?: throw AssertionError("The tool call failed: $response")
        val isError = result["isError"]?.asBoolean() ?: false
        val text = result["content"][0]["text"].asString()
        return ToolCallResult(isError, text, if (isError) null else objectMapper.readTree(text))
    }

    fun listTools(mockMvc: MockMvc, token: String): JsonNode {
        val response = mockMvc.perform(
            post("/mcp")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                .header(HttpHeaders.ACCEPT, "application/json, text/event-stream")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"jsonrpc": "2.0", "id": 1, "method": "tools/list", "params": {}}"""),
        ).andExpect(status().isOk).andReturn().response.contentAsString
        return objectMapper.readTree(response)["result"]["tools"]
    }

    private fun registerClient(mockMvc: MockMvc): String {
        val registration = objectMapper.writeValueAsString(
            mapOf(
                "client_name" to "Claude Code",
                "redirect_uris" to listOf(redirectUri),
                "grant_types" to listOf("authorization_code", "refresh_token"),
                "response_types" to listOf("code"),
                "token_endpoint_auth_method" to "none",
            ),
        )
        val response = mockMvc.perform(
            post("/oauth2/register").contentType(MediaType.APPLICATION_JSON).content(registration),
        ).andExpect(status().isCreated).andReturn().response.contentAsString
        return objectMapper.readTree(response)["client_id"].asString()
    }

    private fun codeChallenge(): String = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(MessageDigest.getInstance("SHA-256").digest(codeVerifier.toByteArray()))

    private fun queryParam(url: String, name: String): String =
        UriComponentsBuilder.fromUriString(url).build().queryParams.getFirst(name)
            ?.let { URLDecoder.decode(it, Charsets.UTF_8) }
            ?: throw AssertionError("$url has no $name parameter")
}
