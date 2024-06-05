package dev.kviklet.kviklet.service.notifications
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import org.springframework.web.client.exchange

@Service
class TeamsApiClient(
    private val restTemplate: RestTemplate,
    private val objectMapper: ObjectMapper,
) {

    fun sendMessage(webhookUrl: String, title: String, message: String): ResponseEntity<String> {
        val headers = HttpHeaders().apply {
            add("Content-Type", "application/json")
        }
        val notification = TeamsNotification(title, message)

        val body = objectMapper.writeValueAsString(notification)
        val request = HttpEntity(body, headers)

        return restTemplate.exchange(webhookUrl, HttpMethod.POST, request)
    }
}

data class TeamsNotification(
    val title: String,
    val text: String,
)
