package dev.kviklet.kviklet.telemetry

import dev.kviklet.kviklet.ApplicationProperties
import dev.kviklet.kviklet.db.ConfigurationAdapter
import dev.kviklet.kviklet.security.ApiKeyAuthentication
import dev.kviklet.kviklet.security.UserDetailsWithId
import dev.kviklet.kviklet.service.BaseUrlResolver
import dev.kviklet.kviklet.service.dto.ReviewAction
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.transaction.support.TransactionSynchronizationManager

class TelemetryTest {

    private class RecordingSink : TelemetrySink {
        val sent = mutableListOf<TelemetryPayload>()
        override fun send(payload: TelemetryPayload) {
            sent += payload
        }
    }

    private val sink = RecordingSink()
    private val configurationAdapter = mockk<ConfigurationAdapter>(relaxed = true)
    private val applicationProperties = ApplicationProperties().apply { version = "1.2.3" }

    private fun telemetry(enabled: Boolean = true, key: String = "phc_test"): Telemetry {
        val properties = TelemetryProperties().apply {
            this.enabled = enabled
            posthog.key = key
        }
        every { configurationAdapter.getConfiguration(Telemetry.INSTANCE_ID_KEY) } returns "instance-1"
        return Telemetry(
            properties,
            sink,
            configurationAdapter,
            BaseUrlResolver("https://kviklet.example.com"),
            applicationProperties,
        )
    }

    @AfterEach
    fun cleanup() {
        SecurityContextHolder.clearContext()
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization()
        }
    }

    @Test
    fun `nothing is sent when telemetry is switched off`() {
        telemetry(enabled = false).track(RequestClosed)
        sink.sent.shouldBeEmpty()
    }

    @Test
    fun `nothing is sent without a PostHog key`() {
        val telemetry = telemetry(key = "")
        telemetry.enabled shouldBe false
        telemetry.track(RequestClosed)
        sink.sent.shouldBeEmpty()
    }

    @Test
    fun `events carry the instance id, domain and version but no person profile`() {
        telemetry().track(ReviewSubmitted(ReviewAction.APPROVE))

        val payload = sink.sent.single()
        payload.event shouldBe "review_submitted"
        payload.distinctId shouldBe "instance-1"
        payload.properties["instance_id"] shouldBe "instance-1"
        payload.properties["domain"] shouldBe "https://kviklet.example.com"
        payload.properties["version"] shouldBe "1.2.3"
        payload.properties["review_action"] shouldBe "APPROVE"
        payload.properties["\$process_person_profile"] shouldBe false
    }

    @Test
    fun `the current user is identified by an opaque id scoped to the instance`() {
        val user = UserDetailsWithId("user-42", "someone@example.com", "secret", emptyList())
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(user, null, emptyList())

        telemetry().track(RequestClosed)

        val payload = sink.sent.single()
        payload.distinctId shouldBe "instance-1:user-42"
        payload.properties["client"] shouldBe "WEB"
        payload.properties.values.any { it.toString().contains("someone@example.com") } shouldBe false
    }

    @Test
    fun `an api key caller is reported as such`() {
        val user = UserDetailsWithId("user-42", "someone@example.com", "secret", emptyList())
        SecurityContextHolder.getContext().authentication = ApiKeyAuthentication(user, emptyList())

        telemetry().track(RequestClosed)

        val payload = sink.sent.single()
        payload.distinctId shouldBe "instance-1:user-42"
        payload.properties["client"] shouldBe "API_KEY"
    }

    @Test
    fun `instance level events carry no client`() {
        telemetry().track(RequestClosed)
        sink.sent.single().properties.containsKey("client") shouldBe false
    }

    @Test
    fun `an explicit user id wins over the security context`() {
        telemetry().track(UserLoggedIn(LoginMethod.OIDC), userId = "user-7")
        sink.sent.single().distinctId shouldBe "instance-1:user-7"
    }

    @Test
    fun `inside a transaction the event is delivered only after commit`() {
        TransactionSynchronizationManager.initSynchronization()
        telemetry().track(RequestClosed)
        sink.sent.shouldBeEmpty()

        TransactionSynchronizationManager.getSynchronizations().forEach { it.afterCommit() }
        sink.sent.size shouldBe 1
    }

    @Test
    fun `a new instance id is generated once and stored`() {
        every { configurationAdapter.getConfiguration(Telemetry.INSTANCE_ID_KEY) } returns null
        val properties = TelemetryProperties().apply { posthog.key = "phc_test" }
        val telemetry = Telemetry(
            properties,
            sink,
            configurationAdapter,
            BaseUrlResolver(null),
            applicationProperties,
        )

        val stored = slot<String>()
        val first = telemetry.instanceId()
        verify(exactly = 1) { configurationAdapter.setConfiguration(Telemetry.INSTANCE_ID_KEY, capture(stored)) }
        stored.captured shouldBe first
        telemetry.instanceId() shouldBe first
        first.length shouldBe 36
    }

    @Test
    fun `a sink failure never propagates to the caller`() {
        val failing = object : TelemetrySink {
            override fun send(payload: TelemetryPayload) = throw IllegalStateException("boom")
        }
        val properties = TelemetryProperties().apply { posthog.key = "phc_test" }
        every { configurationAdapter.getConfiguration(Telemetry.INSTANCE_ID_KEY) } returns "instance-1"
        val telemetry =
            Telemetry(properties, failing, configurationAdapter, BaseUrlResolver(null), applicationProperties)

        telemetry.track(RequestClosed)
        telemetry.instanceId() shouldStartWith "instance"
    }
}
