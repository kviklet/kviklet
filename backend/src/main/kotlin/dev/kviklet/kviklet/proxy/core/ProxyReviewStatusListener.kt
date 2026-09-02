// This file is not MIT licensed
package dev.kviklet.kviklet.proxy.core

import dev.kviklet.kviklet.service.ReviewStatusUpdatedEvent
import dev.kviklet.kviklet.service.dto.ExecutionRequestId
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

// Ends live proxy sessions the moment their request is rejected or closed. Sessions otherwise only end on
// their scheduled expiry -- never, for a request without a duration -- so without this a closed request
// would keep relaying queries until the server restarts.
@Component
class ProxyReviewStatusListener(private val proxyServers: List<ProxyServer>) {
    private val logger = LoggerFactory.getLogger(javaClass)

    // After commit so a session is only torn down once the rejection is durable; fallbackExecution keeps it
    // working when the event is published outside a transaction.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    fun handleReviewStatusUpdated(event: ReviewStatusUpdatedEvent) {
        if (!event.status.isTerminal()) {
            return
        }
        val requestId = ExecutionRequestId(event.requestId)
        proxyServers.forEach { server ->
            try {
                server.expireSessionsForRequest(requestId)
            } catch (e: Exception) {
                logger.error("Failed to end proxy sessions of ${event.status.name.lowercase()} request $requestId", e)
            }
        }
    }
}
