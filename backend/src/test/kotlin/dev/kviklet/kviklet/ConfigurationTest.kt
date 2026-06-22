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
            ),
        )
    }

    @Test
    fun `test serialization and deserialization`() {
        // Trivial right now but if more fields and subfields are added the serialization might become less convenient
        val configuration = Configuration(
            teamsUrl = "https://teams.com",
            slackUrl = "https://slack.com",
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
}
