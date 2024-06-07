package dev.kviklet.kviklet.service.notifications
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate

@Component
class SlackApiClient(private val restTemplate: RestTemplate) {

    fun sendMessage(webhookUrl: String, message: String) {
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        val payload = mapOf("text" to message)
        val request = HttpEntity(payload, headers)

        restTemplate.exchange(webhookUrl, HttpMethod.POST, request, String::class.java)
        return
    }
}
