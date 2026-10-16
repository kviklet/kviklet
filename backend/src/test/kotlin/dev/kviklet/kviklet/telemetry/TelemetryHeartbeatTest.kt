package dev.kviklet.kviklet.telemetry

import dev.kviklet.kviklet.db.ConfigurationAdapter
import dev.kviklet.kviklet.db.ConfigurationRepository
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("test")
class TelemetryHeartbeatTest {

    @Autowired
    private lateinit var heartbeat: TelemetryHeartbeat

    @Autowired
    private lateinit var configurationAdapter: ConfigurationAdapter

    @Autowired
    private lateinit var configurationRepository: ConfigurationRepository

    @AfterEach
    fun tearDown() {
        configurationRepository.deleteAll()
    }

    @Test
    fun `MCP is reported as enabled unless an admin switched it off`() {
        heartbeat.snapshot().mcpEnabled shouldBe true

        configurationAdapter.setConfiguration("mcpEnabled", "false")

        heartbeat.snapshot().mcpEnabled shouldBe false
    }
}
