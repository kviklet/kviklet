package dev.kviklet.kviklet.telemetry

import io.kotest.matchers.shouldBe
import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestTemplate
import java.time.Instant

class PostHogSinkTest {

    private val properties = TelemetryProperties().apply {
        posthog.host = "https://eu.i.posthog.com/"
        posthog.key = "phc_test"
        maxQueueSize = 3
    }
    private val restTemplate = RestTemplate()
    private val server = MockRestServiceServer.bindTo(restTemplate).build()
    private val sink = PostHogSink(properties, restTemplate)

    private fun payload(event: String) = TelemetryPayload(
        event = event,
        distinctId = "instance-1",
        properties = mapOf("instance_id" to "instance-1", "required_reviews" to null),
        timestamp = Instant.parse("2026-09-20T10:00:00Z"),
    )

    @Test
    fun `queued events are posted as one batch to the batch endpoint`() {
        server.expect(requestTo("https://eu.i.posthog.com/batch"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.api_key").value("phc_test"))
            .andExpect(jsonPath("$.batch.length()").value(2))
            .andExpect(jsonPath("$.batch[0].event").value("request_created"))
            .andExpect(jsonPath("$.batch[0].distinct_id").value("instance-1"))
            .andExpect(jsonPath("$.batch[0].timestamp").value("2026-09-20T10:00:00Z"))
            .andExpect(jsonPath("$.batch[0].properties.instance_id").value("instance-1"))
            .andExpect(jsonPath("$.batch[1].event").value("review_submitted"))
            .andRespond(withSuccess("{\"status\":1}", MediaType.APPLICATION_JSON))

        sink.send(payload("request_created"))
        sink.send(payload("review_submitted"))
        sink.flush()

        server.verify()
    }

    @Test
    fun `nothing is posted when the queue is empty`() {
        sink.flush()
        server.verify()
    }

    @Test
    fun `a failed post is dropped without throwing`() {
        server.expect(requestTo("https://eu.i.posthog.com/batch")).andRespond(withStatus(HttpStatus.BAD_GATEWAY))

        sink.send(payload("request_created"))
        sink.flush()
        sink.flush()

        server.verify()
    }

    @Test
    fun `events beyond the queue capacity are dropped`() {
        server.expect(requestTo("https://eu.i.posthog.com/batch"))
            .andExpect(jsonPath("$.batch.length()").value(3))
            .andRespond(withSuccess())

        repeat(5) { sink.send(payload("request_created")) }
        sink.flush()

        server.verify()
    }

    @Test
    fun `the properties are sent as given`() {
        server.expect(requestTo("https://eu.i.posthog.com/batch"))
            .andExpect(jsonPath("$.batch[0].properties.required_reviews").value(nullValue()))
            .andRespond(withSuccess())

        sink.send(payload("request_created"))
        sink.flush()

        server.verify()
        properties.posthog.key shouldBe "phc_test"
    }
}
