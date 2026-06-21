package dev.kviklet.kviklet.service.notifications
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import java.net.URI

@Component
class SlackApiClient(private val restTemplate: RestTemplate) {

    fun sendMessage(webhookUrl: String, message: String) {
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        val payload = mapOf("text" to message)
        val request = HttpEntity(payload, headers)

        // Pass a parsed URI (not a String) so RestTemplate does not treat the webhook URL as a URI template.
        restTemplate.exchange(URI.create(webhookUrl), HttpMethod.POST, request, String::class.java)
        return
    }
}
