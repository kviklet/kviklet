package dev.kviklet.kviklet.websocket

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import dev.kviklet.kviklet.db.User
import dev.kviklet.kviklet.helper.ConnectionHelper
import dev.kviklet.kviklet.helper.ExecutionRequestHelper
import dev.kviklet.kviklet.helper.UserHelper
import dev.kviklet.kviklet.service.dto.Connection
import dev.kviklet.kviklet.service.dto.ExecutionRequestDetails
import dev.kviklet.kviklet.service.dto.RequestType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
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
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.net.URI
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

data class SessionAndMessages(
    val session: WebSocketSession,
    val messages: CompletableFuture<List<String>>,
    val executionRequest: ExecutionRequestDetails,
)

// TODO: Simplify assertions and calling code?
// TODO: Assert that it doesnt work with SingleRequest Sessions
// TODO: Assert that approvals are necessary still to execute
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
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
    private lateinit var testUser: User
    private lateinit var testConnection: Connection

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    companion object {
        @Container
        val db: PostgreSQLContainer<*> = PostgreSQLContainer(DockerImageName.parse("postgres:11.1"))
            .withUsername("root")
            .withPassword("root")
            .withDatabaseName("test_db")
    }

    @BeforeEach
    fun setup() {
        testUser = userHelper.createUser(permissions = listOf("*"))
        testConnection = connectionHelper.createPostgresConnection(db)
        webSocketClient = StandardWebSocketClient()
    }

    @AfterEach
    fun tearDown() {
        executionRequestHelper.deleteAll()
        connectionHelper.deleteAll()
        userHelper.deleteAll()
    }

    fun openSession(approved: Boolean = true): SessionAndMessages {
        val messages = CompletableFuture<List<String>>()
        val testApprover = userHelper.createUser()
        val executionRequest = if (approved) {
            executionRequestHelper.createApprovedRequest(
                author = testUser,
                approver = testApprover,
                connection = testConnection,
                requestType = RequestType.TemporaryAccess,
            )
        } else {
            executionRequestHelper.createExecutionRequest(
                author = testUser,
                connection = testConnection,
                requestType = RequestType.TemporaryAccess,
            )
        }
        val sessionCookie = userHelper.login(testUser.email, "123456", mockMvc)

        val session = connectToWebSocket(executionRequest.getId(), sessionCookie.value, messages)
        return SessionAndMessages(session, messages, executionRequest)
    }

    fun openObserverSession(executionRequest: ExecutionRequestDetails): SessionAndMessages {
        val messages = CompletableFuture<List<String>>()
        val testObserver = userHelper.createUser()
        val sessionCookie = userHelper.login(testObserver.email, "123456", mockMvc)
        val session = connectToWebSocket(executionRequest.getId(), sessionCookie.value, messages)
        return SessionAndMessages(session, messages, executionRequest)
    }

    fun waitForResponses(messages: CompletableFuture<List<String>>, expectedMessages: Int): List<JsonNode> {
        Thread.sleep(1000)
        val receivedMessages = messages.get(5, TimeUnit.SECONDS)
        assert(receivedMessages.isNotEmpty())
        assertEquals(expectedMessages, receivedMessages.size)
        return receivedMessages.map { objectMapper.readTree(it) }
    }

    @Test
    fun testWebSocketConnectionUpdateMessage() {
        val (session, messages, _) = openSession()

        val updateMessage = """
            {
                "type": "update_content",
                "content": "SELECT * FROM users"
            }
        """.trimIndent()
        session.sendMessage(TextMessage(updateMessage))

        val receivedMessages = waitForResponses(messages, 2)
        val statusMessage = receivedMessages.last()
        assertEquals("SELECT * FROM users", statusMessage.get("consoleContent").asText())
        val observer = statusMessage.get("observers").first()
        assertEquals(observer.get("id").asText(), testUser.getId())
        assertEquals(observer.get("email").asText(), testUser.email)
        assertEquals(observer.get("fullName").asText(), testUser.fullName)
        session.close()
    }

    @Test
    fun sendExecuteMessage() {
        val (session, messages, _) = openSession()
        val updateMessage = """
            {
                "type": "update_content",
                "content": "SELECT * FROM users"
            }
        """.trimIndent()
        session.sendMessage(TextMessage(updateMessage))
        val executeMessage = """
            {
                "type": "execute",
                "statement": "SELECT 1 as test;"
            }
        """.trimIndent()
        session.sendMessage(TextMessage(executeMessage))
        // Wait 2 seconds for the message to be processed otherwise the get is too eager
        // and only one updated message is received
        val receivedMessages = waitForResponses(messages, 3)
        val resultMessage = receivedMessages.last()
        assertTrue(resultMessage.has("sessionId"))
        assertTrue(resultMessage.has("results"))

        val results = resultMessage.get("results")
        assertTrue(results.isArray())
        assertTrue(results.size() > 0)

        val firstResult = results[0]
        assertTrue(firstResult.has("type"))
        assertEquals("RECORDS", firstResult.get("type").asText())

        if (firstResult.has("rows") && firstResult.get("rows").isArray()) {
            val firstRow = firstResult.get("rows")[0]
            assertTrue(firstRow.has("test"))
            assertEquals(1, firstRow.get("test").asInt())
        }

        session.close()
    }

    @Test
    fun testInvalidMessage() {
        val (session, messages, _) = openSession()
        // Send an invalid message
        session.sendMessage(TextMessage("Invalid message"))

        val receivedMessages = waitForResponses(messages, 2)
        val errorMessage = receivedMessages.last()
        assert(errorMessage.get("error").asText().contains("Error processing message"))

        session.close()
    }

    @Test
    fun testObserveSession() {
        val (session, messages, executionRequest) = openSession()
        val (observerSession, observerMessages, _) = openObserverSession(executionRequest)
        session.sendMessage(
            TextMessage(
                """
                {
                    "type": "update_content",
                    "content": "SELECT * FROM users"
                }
                """.trimIndent(),
            ),
        )
        val observerResults = waitForResponses(observerMessages, 2)
        val statusMessage = observerResults.last()
        assertEquals("SELECT * FROM users", statusMessage.get("consoleContent").asText())
        session.close()
        observerSession.close()
    }

    @Test
    fun `observer cannot update Content`() {
        val (session, messages, executionRequest) = openSession()
        val (observerSession, observerMessages, _) = openObserverSession(executionRequest)
        observerSession.sendMessage(
            TextMessage(
                """
                {
                    "type": "update_content",
                    "content": "SELECT * FROM users"
                }
                """.trimIndent(),
            ),
        )
        val observerResults = waitForResponses(observerMessages, 2)
        val statusMessage = observerResults.last()
        assertFalse(statusMessage.has("consoleContent"))
        session.close()
        observerSession.close()
    }

    @Test
    fun `observer cannot execute`() {
        val (session, messages, executionRequest) = openSession()
        val (observerSession, observerMessages, _) = openObserverSession(executionRequest)
        observerSession.sendMessage(
            TextMessage(
                """
                {
                    "type": "execute",
                    "statement": "SELECT 1 as test;"
                }
                """.trimIndent(),
            ),
        )
        val observerResults = waitForResponses(observerMessages, 2)
        val resultMessage = observerResults.last()
        assertFalse(resultMessage.has("results"))
        session.close()
        observerSession.close()
    }

    @Test
    fun `unapproved session cannot execute`() {
        val (session, messages, _) = openSession(false)
        session.sendMessage(
            TextMessage(
                """
                {
                    "type": "execute",
                    "statement": "SELECT 1 as test;"
                }
                """.trimIndent(),
            ),
        )
        val receivedMessages = waitForResponses(messages, 2)
        val errorMessage = receivedMessages.last()
        assert(errorMessage.get("error").asText().contains("Error processing message"))
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
