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
                liveSessionEnabled = false,
            ),
        )
    }

    @Test
    fun `test serialization and deserialization`() {
        // Trivial right now but if more fields and subfields are added the serialization might become less convenient
        val configuration = Configuration(
            teamsUrl = "https://teams.com",
            slackUrl = "https://slack.com",
            liveSessionEnabled = true,
        )

        val savedConfiguration = configurationAdapter.setConfiguration(configuration)
        val loadedConfiguration = configurationAdapter.getConfiguration()

        assert(savedConfiguration == loadedConfiguration)
        assert(configuration == savedConfiguration)
    }
}
