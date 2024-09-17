package dev.kviklet.kviklet.controller

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.databind.ObjectMapper
import dev.kviklet.kviklet.security.UserDetailsWithId
import dev.kviklet.kviklet.service.dto.ExecutionRequestId
import dev.kviklet.kviklet.service.dto.LiveSession
import dev.kviklet.kviklet.service.dto.LiveSessionId
import dev.kviklet.kviklet.service.websocket.SessionService
import dev.kviklet.kviklet.service.websocket.UserRole
import dev.kviklet.kviklet.service.websocket.UserRoleService
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
)
sealed class WebSocketMessage {
    abstract val id: String
}

data class UpdateContentMessage(override val id: String, val content: String) : WebSocketMessage()

sealed class ResponseMessage(open val id: LiveSessionId)
data class ErrorResponseMessage(override val id: LiveSessionId, val error: String) : ResponseMessage(id)

data class StatusMessage(override val id: LiveSessionId, val consoleContent: String, val observers: List<String>) :
    ResponseMessage(id)

@Component
class SessionWebsocketHandler(
    private val sessionService: SessionService,
    private val userRoleService: UserRoleService,
    private val objectMapper: ObjectMapper,
) : TextWebSocketHandler() {
    private val logger = LoggerFactory.getLogger(SessionWebsocketHandler::class.java)
    private val sessionRoleMap = ConcurrentHashMap<String, UserRole>()
    private val sessionObservers = ConcurrentHashMap<LiveSessionId, MutableSet<WebSocketSession>>()
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
        val userRole = userRoleService.determineUserRole(userDetailsWithId.id, ExecutionRequestId(requestId))
        sessionRoleMap[session.id] = userRole

        if (userRole == UserRole.OBSERVER) {
            sessionObservers.computeIfAbsent(liveSession.id!!) { ConcurrentHashMap.newKeySet() }.add(session)
        }
        broadcastUpdate(liveSession)

        logger.info(
            "New WebSocket connection established: ${session.id}, userId: ${userDetailsWithId.id}, requestId: $requestId, role: $userRole",
        )
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
            }
        } catch (e: Exception) {
            logger.error("Error processing message", e)
            sendErrorResponseMessage(session, "INTERNAL_ERROR", liveSessionId)
        }
    }

    private fun broadcastUpdate(updatedSession: LiveSession) {
        val updateMessage = StatusMessage(
            id = updatedSession.id!!,
            consoleContent = updatedSession.consoleContent,
            observers = sessionObservers[updatedSession.id]?.map { it.id } ?: emptyList(),
        )
        sessionObservers[updatedSession.id]?.forEach { observerSession ->
            sendMessage(observerSession, updateMessage)
        }
    }

    private fun sendErrorResponseMessage(session: WebSocketSession, message: String, id: LiveSessionId) {
        val errorMessage = ErrorResponseMessage(
            id = id,
            error = message,
        )
        sendMessage(session, errorMessage)
    }

    private fun sendMessage(session: WebSocketSession, message: ResponseMessage) {
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
