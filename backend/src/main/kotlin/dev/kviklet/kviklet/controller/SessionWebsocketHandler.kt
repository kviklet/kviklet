package dev.kviklet.kviklet.controller

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.databind.ObjectMapper
import dev.kviklet.kviklet.db.User
import dev.kviklet.kviklet.db.UserId
import dev.kviklet.kviklet.security.UserDetailsWithId
import dev.kviklet.kviklet.service.UserService
import dev.kviklet.kviklet.service.dto.DBExecutionResult
import dev.kviklet.kviklet.service.dto.ExecutionRequestId
import dev.kviklet.kviklet.service.dto.LiveSession
import dev.kviklet.kviklet.service.dto.LiveSessionId
import dev.kviklet.kviklet.service.websocket.SessionService
import org.slf4j.LoggerFactory
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler
import org.springframework.web.util.UriComponentsBuilder
import java.util.concurrent.ConcurrentHashMap

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = UpdateContentMessage::class, name = "update_content"),
    JsonSubTypes.Type(value = ExecuteMessage::class, name = "execute"),
)
sealed class WebSocketMessage

data class UpdateContentMessage(val content: String) : WebSocketMessage()

data class ExecuteMessage(val statement: String) : WebSocketMessage()

sealed class ResponseMessage(open val sessionId: LiveSessionId)
data class ErrorResponseMessage(val type: String = "error", override val sessionId: LiveSessionId, val error: String) :
    ResponseMessage(sessionId)

data class StatusMessage(
    val type: String = "status",
    override val sessionId: LiveSessionId,
    val consoleContent: String,
    val observers: List<UserResponse>,
) : ResponseMessage(sessionId)

data class ResultMessage(
    val type: String = "result",
    override val sessionId: LiveSessionId,
    val results: List<ExecutionResultResponse>,
) : ResponseMessage(sessionId)

data class SessionObserver(val webSocketSession: WebSocketSession, val user: User)

@Component
class SessionWebsocketHandler(
    private val sessionService: SessionService,
    private val objectMapper: ObjectMapper,
    private val userService: UserService,
) : TextWebSocketHandler() {
    private val logger = LoggerFactory.getLogger(SessionWebsocketHandler::class.java)
    private val sessionObservers = ConcurrentHashMap<LiveSessionId, MutableSet<SessionObserver>>()
    private val sessionToLiveSessionMap = ConcurrentHashMap<String, LiveSessionId>()

    override fun afterConnectionEstablished(session: WebSocketSession) {
        val requestId = extractRequestId(session)
        val securityContext = session.attributes["SPRING_SECURITY_CONTEXT"] as? SecurityContext
        if (securityContext != null) {
            SecurityContextHolder.setContext(securityContext)
        } else {
            throw IllegalStateException("Security context not found in WebSocket session")
        }
        val liveSession = sessionService.createOrConnectToSession(
            ExecutionRequestId(requestId),
        )
        sessionToLiveSessionMap[session.id] = liveSession.id!!
        val userDetailsWithId = SecurityContextHolder.getContext().authentication.principal as UserDetailsWithId
        logger.info("User id: ${userDetailsWithId.id}")
        val user = userService.getUser(UserId(userDetailsWithId.id))

        sessionObservers.computeIfAbsent(liveSession.id!!) { ConcurrentHashMap.newKeySet() }.add(
            SessionObserver(
                webSocketSession = session,
                user = user,
            ),
        )
        broadcastUpdate(liveSession)

        logger.info(
            "New WebSocket connection established: ${session.id}, userId: ${userDetailsWithId.id}, requestId: $requestId",
        )
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        val liveSessionId = sessionToLiveSessionMap[session.id] ?: return
        sessionObservers[liveSessionId]?.removeIf { it.webSocketSession == session }
        if (sessionObservers[liveSessionId]?.isEmpty() == true) {
            sessionObservers.remove(liveSessionId)
        }
        sessionToLiveSessionMap.remove(session.id)
        logger.info("WebSocket connection closed: ${session.id}, status: $status")
    }

    private fun extractRequestId(session: WebSocketSession): String {
        val uri = session.uri ?: throw IllegalStateException("Session URI is null")
        val path = UriComponentsBuilder.fromUri(uri).build().pathSegments
        return path.lastOrNull() ?: throw IllegalArgumentException("RequestId not found in URI")
    }

    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        val securityContext = session.attributes["SPRING_SECURITY_CONTEXT"] as? SecurityContext
        if (securityContext != null) {
            SecurityContextHolder.setContext(securityContext)
        } else {
            throw IllegalStateException("Security context not found in WebSocket session")
        }
        val liveSessionId = sessionToLiveSessionMap[session.id] ?: throw IllegalStateException("LiveSession not found")

        if (message.payload == "CONNECT") {
            val liveSession = sessionService.getSession(liveSessionId)
            broadcastUpdate(liveSession)
            return
        }

        try {
            when (val webSocketMessage = objectMapper.readValue(message.payload, WebSocketMessage::class.java)) {
                is UpdateContentMessage -> {
                    val updatedSession = sessionService.updateContent(
                        liveSessionId,
                        webSocketMessage.content,
                    )
                    broadcastUpdate(updatedSession)
                }
                is ExecuteMessage -> {
                    val executionResult = sessionService.executeStatement(
                        liveSessionId,
                        webSocketMessage.statement,
                    )
                    when (executionResult) {
                        is DBExecutionResult -> {
                            val resultMessage = ResultMessage(
                                sessionId = liveSessionId,
                                results = executionResult.results.map { ExecutionResultResponse.fromDto(it) },
                            )
                            broadcastResultMessage(liveSessionId, resultMessage)
                        }
                        else -> throw IllegalStateException("Unsupported execution result type: $executionResult")
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("Error processing message", e)
            sendErrorResponseMessage(session, "Error processing message", liveSessionId)
        }
    }

    private fun broadcastResultMessage(sessionId: LiveSessionId, resultMessage: ResultMessage) {
        sessionObservers[sessionId]?.forEach { sessionObserver ->
            sendMessage(sessionObserver.webSocketSession, resultMessage)
        }
    }

    private fun broadcastUpdate(updatedSession: LiveSession) {
        val updateMessage = StatusMessage(
            sessionId = updatedSession.id!!,
            consoleContent = updatedSession.consoleContent,
            observers = sessionObservers[updatedSession.id]?.map { UserResponse(it.user) } ?: emptyList(),
        )
        sessionObservers[updatedSession.id]?.forEach { sessionObserver ->
            sendMessage(sessionObserver.webSocketSession, updateMessage)
        }
    }

    private fun sendErrorResponseMessage(session: WebSocketSession, message: String, id: LiveSessionId) {
        val errorMessage = ErrorResponseMessage(
            sessionId = id,
            error = message,
        )
        sendMessage(session, errorMessage)
    }

    private fun sendMessage(session: WebSocketSession, message: ResponseMessage) {
        try {
            logger.info("Sending message to ${session.id}: $message")
            synchronized(session) {
                session.sendMessage(TextMessage(objectMapper.writeValueAsString(message)))
            }
        } catch (e: Exception) {
            logger.error("Error sending message", e)
        }
    }
}
