package dev.kviklet.kviklet.service.notifications
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import org.springframework.web.client.exchange

@Service
class TeamsApiClient(private val restTemplate: RestTemplate, private val objectMapper: ObjectMapper) {

    fun sendMessage(webhookUrl: String, title: String, message: String) {
        val formattedMessage = formatMessageWithLinksAndLineBreaks(message)
        val headers = HttpHeaders().apply {
            add("Content-Type", "application/json")
        }
        val notification = TeamsNotification(
            summary = title,
            sections = listOf(
                Section(title, formattedMessage),
            ),
        )

        val body = objectMapper.writeValueAsString(notification)
        val request = HttpEntity(body, headers)

        restTemplate.exchange<String>(webhookUrl, HttpMethod.POST, request)
        return
    }

    private fun formatMessageWithLinksAndLineBreaks(message: String): String {
        val urlRegex = Regex("(http[s]?://[^\\s]+)")
        val formattedMessage = urlRegex.replace(message) { matchResult ->
            val url = matchResult.value
            "[$url]($url)"
        }
        return formattedMessage.replace("\n", "  \n")
    }
}

data class TeamsNotification(val summary: String, val sections: List<Section>)

data class Section(val title: String, val text: String, val markdown: Boolean = true)
