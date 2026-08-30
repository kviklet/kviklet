package dev.kviklet.kviklet

import dev.kviklet.kviklet.db.ConfigurationAdapter
import dev.kviklet.kviklet.service.dto.Configuration
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("test")
class ConfigurationTest {

    @Autowired
    private lateinit var configurationAdapter: ConfigurationAdapter

    @AfterEach
    fun tearDown() {
        configurationAdapter.setConfiguration(
            Configuration(
                teamsUrl = "",
                slackUrl = "",
                proxyEnabled = false,
            ),
        )
    }

    @Test
    fun `test serialization and deserialization`() {
        // Trivial right now but if more fields and subfields are added the serialization might become less convenient
        val configuration = Configuration(
            teamsUrl = "https://teams.com",
            slackUrl = "https://slack.com",
            proxyEnabled = true,
        )

        val savedConfiguration = configurationAdapter.setConfiguration(configuration)
        val loadedConfiguration = configurationAdapter.getConfiguration()

        assert(savedConfiguration == loadedConfiguration)
        assert(configuration == savedConfiguration)
    }

    @Test
    fun `stores webhook urls longer than 255 characters`() {
        // Teams Power Automate "Workflows" webhook URLs routinely exceed 255 chars,
        // which the original VARCHAR(255) column rejected.
        val longUrl = "https://default.environment.api.powerplatform.com/powerautomate/automations/direct/" +
            "workflows/${"a".repeat(700)}/triggers/manual/paths/invoke?api-version=1&sig=${"b".repeat(43)}"
        assert(longUrl.length > 255)

        val savedConfiguration = configurationAdapter.setConfiguration(
            Configuration(teamsUrl = longUrl, slackUrl = "https://slack.com"),
        )
        val loadedConfiguration = configurationAdapter.getConfiguration()

        assert(savedConfiguration.teamsUrl == longUrl)
        assert(loadedConfiguration.teamsUrl == longUrl)
    }

    @Test
    fun `partial update leaves other keys unchanged and returns the full configuration`() {
        configurationAdapter.setConfiguration(
            Configuration(teamsUrl = "https://teams.com", slackUrl = "https://slack.com"),
        )

        // Null fields mean "leave unchanged"; the returned configuration must still be complete.
        val savedConfiguration = configurationAdapter.setConfiguration(
            Configuration(teamsUrl = null, slackUrl = null, proxyEnabled = true),
        )

        assert(savedConfiguration.teamsUrl == "https://teams.com")
        assert(savedConfiguration.slackUrl == "https://slack.com")
        assert(savedConfiguration.proxyEnabled == true)
        assert(configurationAdapter.getConfiguration().proxyEnabled == true)
    }
}
