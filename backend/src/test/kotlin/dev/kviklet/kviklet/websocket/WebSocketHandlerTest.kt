package dev.kviklet.kviklet.websocket

import com.fasterxml.jackson.databind.ObjectMapper
import dev.kviklet.kviklet.controller.ErrorResponseMessage
import dev.kviklet.kviklet.controller.StatusMessage
import dev.kviklet.kviklet.db.User
import dev.kviklet.kviklet.helper.ConnectionHelper
import dev.kviklet.kviklet.helper.ExecutionRequestHelper
import dev.kviklet.kviklet.helper.UserHelper
import dev.kviklet.kviklet.service.dto.Connection
import dev.kviklet.kviklet.service.dto.LiveSessionId
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketHttpHeaders
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.client.standard.StandardWebSocketClient
import org.springframework.web.socket.handler.TextWebSocketHandler
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.net.URI
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WebSocketHandlerTest {

    @Autowired
    private lateinit var userHelper: UserHelper

    @Autowired
    private lateinit var connectionHelper: ConnectionHelper

    @Autowired
    private lateinit var executionRequestHelper: ExecutionRequestHelper

    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var mockMvc: MockMvc

    private lateinit var webSocketClient: StandardWebSocketClient
    private lateinit var objectMapper: ObjectMapper
    private lateinit var testUser: User
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
        testUser = userHelper.createUser(permissions = listOf("*"))
        testConnection = connectionHelper.createPostgresConnection(db)
        webSocketClient = StandardWebSocketClient()
        objectMapper = ObjectMapper()
    }

    @AfterEach
    fun tearDown() {
        executionRequestHelper.deleteAll()
        connectionHelper.deleteAll()
        userHelper.deleteAll()
    }

    @Test
    fun testWebSocketConnection() {
        val executionRequest = executionRequestHelper.createExecutionRequest(db, testUser, connection = testConnection)
        val sessionCookie = userHelper.login(testUser.email, "123456", mockMvc)

        val messages = CompletableFuture<List<String>>()
        val session = connectToWebSocket(executionRequest.getId(), sessionCookie.value, messages)

        // Send an update message
        val updateMessage = """
            {
                "type": "update_content",
                "id": "${executionRequest.getId()}",
                "content": "SELECT * FROM users"
            }
        """.trimIndent()
        session.sendMessage(TextMessage(updateMessage))

        // Wait for messages
        val receivedMessages = messages.get(5, TimeUnit.SECONDS)

        // Assert the received messages
        assert(receivedMessages.isNotEmpty())
        val statusMessage = objectMapper.readValue(receivedMessages.last(), StatusMessage::class.java)
        assert(statusMessage.id == LiveSessionId(executionRequest.getId()))
        assert(statusMessage.consoleContent.isNotEmpty())
        assert(statusMessage.observers.isNotEmpty())

        session.close()
    }

    @Test
    fun testInvalidMessage() {
        val executionRequest = executionRequestHelper.createExecutionRequest(db, testUser, connection = testConnection)
        val sessionCookie = userHelper.login(testUser.email, "123456", mockMvc)

        val messages = CompletableFuture<List<String>>()
        val session = connectToWebSocket(executionRequest.getId(), sessionCookie.value, messages)

        // Send an invalid message
        session.sendMessage(TextMessage("Invalid message"))

        // Wait for messages
        val receivedMessages = messages.get(5, TimeUnit.SECONDS)

        // Assert the received error
        assert(receivedMessages.isNotEmpty())
        val errorMessage = objectMapper.readValue(receivedMessages.last(), ErrorResponseMessage::class.java)
        assert(errorMessage.id == LiveSessionId(executionRequest.getId()))
        assert(errorMessage.error.contains("Error processing message"))

        session.close()
    }

    private fun connectToWebSocket(
        executionRequestId: String,
        sessionId: String,
        messages: CompletableFuture<List<String>>,
    ): WebSocketSession {
        val url = "ws://localhost:$port/sql/$executionRequestId"
        val headers = WebSocketHttpHeaders()
        headers.add("Cookie", "SESSION=$sessionId")

        val handler = object : TextWebSocketHandler() {
            private val receivedMessages = mutableListOf<String>()

            override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
                receivedMessages.add(message.payload)
                messages.complete(receivedMessages)
            }
        }

        return webSocketClient.execute(handler, headers, URI(url)).get(5, TimeUnit.SECONDS)
    }
}
