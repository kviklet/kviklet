package dev.kviklet.kviklet.controller

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler

// Data classes for message structures
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = ExecuteQueryMessage::class, name = "execute_query"),
    JsonSubTypes.Type(value = CancelQueryMessage::class, name = "cancel_query"),
    JsonSubTypes.Type(value = HeartbeatMessage::class, name = "heartbeat"),
)
sealed class WebSocketMessage {
    abstract val id: String
}

data class ExecuteQueryMessage(override val id: String, val payload: ExecuteQueryPayload) : WebSocketMessage()

data class CancelQueryMessage(override val id: String) : WebSocketMessage()

data class HeartbeatMessage(override val id: String) : WebSocketMessage()

data class ExecuteQueryPayload(val sql: String, val params: List<Any>?)
sealed class WebSocketResponseMessage {
    abstract val type: String
    abstract val id: String
}

data class QueryResultResponseMessage(override val id: String, val payload: QueryResultPayload) :
    WebSocketResponseMessage() {
    override val type = "query_result"
}

data class ErrorResponseMessage(override val id: String, val payload: ErrorPayload) : WebSocketResponseMessage() {
    override val type = "error"
}

data class HeartbeatResponseMessage(override val id: String) : WebSocketResponseMessage() {
    override val type = "heartbeat"
}

data class QueryResultPayload(
    val columns: List<String>,
    val rows: List<List<Any>>,
    val rowCount: Int,
    val executionTime: Double,
)

data class ErrorPayload(val code: String, val message: String)

@Component
class SessionWebsocketHandler : TextWebSocketHandler() {
    private val logger = LoggerFactory.getLogger(SessionWebsocketHandler::class.java)
    private val objectMapper = ObjectMapper()

    override fun afterConnectionEstablished(session: WebSocketSession) {
        logger.info("New WebSocket connection established: ${session.id}")
    }

    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        try {
            when (val webSocketMessage = objectMapper.readValue(message.payload, WebSocketMessage::class.java)) {
                is ExecuteQueryMessage -> handleExecuteQuery(session, webSocketMessage)
                is CancelQueryMessage -> handleCancelQuery(session, webSocketMessage)
                is HeartbeatMessage -> handleHeartbeat(session, webSocketMessage)
            }
        } catch (e: Exception) {
            logger.error("Error processing message", e)
            sendErrorResponseMessage(session, "INTERNAL_ERROR", "An internal error occurred", "unknown")
        }
    }

    private fun handleExecuteQuery(session: WebSocketSession, message: ExecuteQueryMessage) {
        // TODO: Execute the SQL query using your existing logic
        // For now, we'll just send a mock result
        val result = QueryResultResponseMessage(
            id = message.id,
            payload = QueryResultPayload(
                columns = listOf("id", "name"),
                rows = listOf(listOf(1, "John Doe")),
                rowCount = 1,
                executionTime = 0.023,
            ),
        )
        sendMessage(session, result)
    }

    private fun handleCancelQuery(session: WebSocketSession, message: CancelQueryMessage) {
        // TODO: Implement query cancellation logic
        // sendMessage(session, message)
    }

    private fun handleHeartbeat(session: WebSocketSession, message: HeartbeatMessage) {
        sendMessage(session, HeartbeatResponseMessage(id = message.id))
    }

    private fun handleUnknownMessage(session: WebSocketSession, id: String) {
        sendErrorResponseMessage(session, "UNKNOWN_MESSAGE_TYPE", "Received unknown message type", id)
    }

    private fun sendErrorResponseMessage(session: WebSocketSession, code: String, message: String, id: String) {
        val errorMessage = ErrorResponseMessage(
            id = id,
            payload = ErrorPayload(code = code, message = message),
        )
        sendMessage(session, errorMessage)
    }

    private fun sendMessage(session: WebSocketSession, message: WebSocketResponseMessage) {
        try {
            session.sendMessage(TextMessage(objectMapper.writeValueAsString(message)))
        } catch (e: Exception) {
            logger.error("Error sending message", e)
        }
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        logger.info("WebSocket connection closed: ${session.id}, status: $status")
    }
}
