package dev.kviklet.kviklet.shell

import org.springframework.stereotype.Service
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler
import java.util.concurrent.ConcurrentHashMap

@Service
class ShellHandler(private val kubernetesApi: KubernetesApi) : TextWebSocketHandler() {

    private val activeSessions = ConcurrentHashMap<String, WebSocketSession>()
    private val processes = ConcurrentHashMap<String, Process>()

    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        val command = message.payload
        val requestId = session.attributes["requestId"] as String

        val process = processes.get(requestId)
        process?.outputStream?.bufferedWriter()?.use { writer ->
            writer.write(command)
            writer.newLine()
            writer.flush()
        }
        session.sendMessage(TextMessage("Command $command sent to process"))
    }

    override fun afterConnectionEstablished(session: WebSocketSession) {
        val requestId = session.attributes["requestId"] as String
        val isFirstSession = processes[requestId] == null
        activeSessions[requestId] = session

        if (!isFirstSession) {
            val namespace = "kube-system"
            val pod = "kindnet-bb6dq"
            val command = "sleep 1 && echo hello && sleep 10 && echo world && bin/sh"
            val process = kubernetesApi.executeCommandOnPodLive(namespace, pod, command, "some-id") { message ->
                session.sendMessage(TextMessage(message))
            }

            processes[requestId] = process
        }

        println("WebSocket connection established for request: $requestId")
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        val sessionId = session.id
        activeSessions.remove(sessionId)
        println("WebSocket connection closed for session: $sessionId")
    }

    fun sendMessageForRequest(requestId: String, message: String) {
        val session = activeSessions[requestId]
        session?.sendMessage(TextMessage(message))
    }
}
