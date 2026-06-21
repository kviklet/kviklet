package dev.kviklet.kviklet.service.notifications

import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity
import org.springframework.web.client.RestTemplate
import java.net.URI

class TeamsApiClientTest {

    private val restTemplate = mockk<RestTemplate>()
    private val objectMapper = ObjectMapper()
    private val client = TeamsApiClient(restTemplate, objectMapper)

    @Test
    fun `sends a well-formed adaptive card to the webhook`() {
        // A Power Automate "Workflows" style URL: percent-encoded path params plus a SAS `sig` query param.
        val webhookUrl = "https://example.webhook/triggers/manual/paths/invoke" +
            "?api-version=1&sp=%2Ftriggers%2Fmanual%2Frun&sig=abc123signature"
        val uriSlot = slot<URI>()
        val bodySlot = slot<HttpEntity<*>>()
        every {
            restTemplate.exchange(capture(uriSlot), HttpMethod.POST, capture(bodySlot), String::class.java)
        } returns ResponseEntity.ok("")

        client.sendMessage(
            webhookUrl = webhookUrl,
            title = "New Request: \"Deploy\"",
            message = "Created by Alice.\nGo to https://kviklet.example.com/requests/42 to review it.",
        )

        // The webhook URL must reach RestTemplate intact, query string (and SAS signature) included,
        // otherwise the request fails to authenticate (HTTP 401).
        assertEquals(webhookUrl, uriSlot.captured.toString())
        assertTrue(uriSlot.captured.rawQuery.contains("sig=abc123signature"), "SAS signature must survive")

        val json = objectMapper.readTree(bodySlot.captured.body as String)

        // It's an Adaptive Card, not the legacy MessageCard.
        assertEquals("AdaptiveCard", json["type"].asText())
        assertEquals("http://adaptivecards.io/schemas/adaptive-card.json", json["\$schema"].asText())

        // Title is the first block, rendered as a bold heading.
        val titleBlock = json["body"][0]
        assertEquals("New Request: \"Deploy\"", titleBlock["text"].asText())
        assertEquals("Bolder", titleBlock["weight"].asText())

        // Message lines are present and URLs are linkified.
        val bodyText = json["body"].joinToString(" ") { it["text"].asText() }
        assertTrue(bodyText.contains("Created by Alice."), "message lines should be present: $bodyText")
        assertTrue(
            bodyText.contains("[https://kviklet.example.com/requests/42](https://kviklet.example.com/requests/42)"),
            "url should be linkified: $bodyText",
        )

        // A single "Open in Kviklet" button links to the request.
        val action = json["actions"][0]
        assertEquals("Action.OpenUrl", action["type"].asText())
        assertEquals("Open in Kviklet", action["title"].asText())
        assertEquals("https://kviklet.example.com/requests/42", action["url"].asText())

        verify {
            restTemplate.exchange(
                URI.create(webhookUrl),
                HttpMethod.POST,
                any<HttpEntity<*>>(),
                String::class.java,
            )
        }
    }
}
