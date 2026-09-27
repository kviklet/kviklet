package dev.kviklet.kviklet

import dev.kviklet.kviklet.db.User
import dev.kviklet.kviklet.helper.ConnectionHelper
import dev.kviklet.kviklet.helper.ExecutionRequestHelper
import dev.kviklet.kviklet.helper.RoleHelper
import dev.kviklet.kviklet.helper.UserHelper
import dev.kviklet.kviklet.service.dto.Connection
import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CSVExportEncodingTest {

    @Autowired private lateinit var mockMvc: MockMvc

    @Autowired private lateinit var userHelper: UserHelper

    @Autowired private lateinit var roleHelper: RoleHelper

    @Autowired private lateinit var connectionHelper: ConnectionHelper

    @Autowired private lateinit var executionRequestHelper: ExecutionRequestHelper

    private lateinit var testUser: User
    private lateinit var testReviewer: User
    private lateinit var testConnection: Connection

    companion object {
        val db: PostgreSQLContainer<*> = PostgreSQLContainer(DockerImageName.parse("postgres:11.1"))
            .withUsername("root")
            .withPassword("root")
            .withReuse(true)
            .withDatabaseName("test_db")

        init {
            db.start()
        }
    }

    @BeforeEach
    fun setup() {
        // Initialize test database with special characters
        val initScript = """
            DROP SCHEMA IF EXISTS test_encoding CASCADE;
            CREATE SCHEMA test_encoding;
            SET SCHEMA 'test_encoding';
            CREATE TABLE special_chars (
                id INT,
                description VARCHAR(255)
            );
            INSERT INTO special_chars(id, description) VALUES
                (1, 'Normal text'),
                (2, 'Special chars: ñ, é, ä, ö, ü'),
                (3, 'Emoji: 🎉 🚀'),
                (4, 'Chinese: 你好世界'),
                (5, 'Arabic: مرحبا بالعالم'),
                (6, 'Mixed: café ☕ naïve'),
                (7, 'Symbols: ™ © ® € £ ¥')
            ;
        """.trimIndent()

        db.createConnection("").use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(initScript)
            }
        }

        testUser = userHelper.createUser(permissions = listOf("*"))
        testReviewer = userHelper.createUser(permissions = listOf("*"))
        testConnection = connectionHelper.createPostgresConnection(db)
    }

    @AfterEach
    fun tearDown() {
        executionRequestHelper.deleteAll()
        connectionHelper.deleteAll()
        userHelper.deleteAll()
        roleHelper.deleteAll()
    }

    @Test
    fun `CSV export with special characters should use proper UTF-8 encoding`() {
        // Create an approved execution request
        val executionRequest = executionRequestHelper.createApprovedRequest(
            author = testUser,
            approver = testReviewer,
            connection = testConnection,
            sql = "SELECT * FROM test_encoding.special_chars ORDER BY id",
        )

        val userCookie = userHelper.login(email = testUser.email, mockMvc = mockMvc)

        // Download CSV
        val response = downloadCSV(executionRequest.getId(), userCookie)
            .andReturn()

        println("Response status: ${response.response.status}")
        println("Response content: ${response.response.contentAsString}")

        if (response.response.status != 200) {
            throw AssertionError(
                "Expected 200 but got ${response.response.status}: ${response.response.contentAsString}",
            )
        }

        val csvContent = response.response.contentAsString

        // Verify headers
        assert(csvContent.contains("id,description"))

        // Verify each row with special characters
        assert(csvContent.contains("1,Normal text"))
        assert(csvContent.contains("2,\"Special chars: ñ, é, ä, ö, ü\""))
        assert(csvContent.contains("3,Emoji: 🎉 🚀")) // Emoji doesn't need quotes
        assert(csvContent.contains("4,Chinese: 你好世界"))
        assert(csvContent.contains("5,Arabic: مرحبا بالعالم"))
        assert(csvContent.contains("6,Mixed: café ☕ naïve"))
        assert(csvContent.contains("7,Symbols: ™ © ® € £ ¥"))

        // Print for debugging
        println("CSV Content:")
        println(csvContent)
    }

    private fun downloadCSV(executionRequestId: String, cookie: Cookie): ResultActions = mockMvc.perform(
        get("/execution-requests/$executionRequestId/download")
            .cookie(cookie)
            .contentType("application/json"),
    )
}
