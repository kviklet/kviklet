package dev.kviklet.kviklet.service.notifications
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import java.net.URI

@Service
class TeamsApiClient(private val restTemplate: RestTemplate, private val objectMapper: ObjectMapper) {

    fun sendMessage(webhookUrl: String, title: String, message: String) {
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        val card = buildAdaptiveCard(title, message)
        val request = HttpEntity(objectMapper.writeValueAsString(card), headers)

        // Pass a parsed URI (not a String) so RestTemplate does not treat the webhook URL as a URI
        // template. Power Automate "Workflows" URLs carry percent-encoded query params (e.g. the SAS
        // `sig`); template expansion would mangle or drop them and the request would fail to authenticate.
        restTemplate.exchange(URI.create(webhookUrl), HttpMethod.POST, request, String::class.java)
    }

    private fun buildAdaptiveCard(title: String, message: String): AdaptiveCard {
        val body = mutableListOf(
            AdaptiveCardTextBlock(text = title, weight = "Bolder", size = "Medium"),
        )
        message.split("\n")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .forEach { line ->
                body.add(AdaptiveCardTextBlock(text = linkify(line), spacing = "Small"))
            }

        val actions = urlRegex.find(message)?.value?.let { url ->
            listOf(AdaptiveCardAction(title = "Open in Kviklet", url = url))
        } ?: emptyList()

        return AdaptiveCard(body = body, actions = actions)
    }

    private fun linkify(text: String): String = urlRegex.replace(text) { "[${it.value}](${it.value})" }

    companion object {
        private val urlRegex = Regex("https?://\\S+")
    }
}

@JsonInclude(JsonInclude.Include.NON_NULL)
data class AdaptiveCard(
    val body: List<AdaptiveCardTextBlock>,
    val actions: List<AdaptiveCardAction> = emptyList(),
    val type: String = "AdaptiveCard",
    @get:JsonProperty("\$schema")
    val schema: String = "http://adaptivecards.io/schemas/adaptive-card.json",
    val version: String = "1.4",
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class AdaptiveCardTextBlock(
    val text: String,
    val type: String = "TextBlock",
    val weight: String? = null,
    val size: String? = null,
    val spacing: String? = null,
    val wrap: Boolean = true,
)

data class AdaptiveCardAction(val title: String, val url: String, val type: String = "Action.OpenUrl")
