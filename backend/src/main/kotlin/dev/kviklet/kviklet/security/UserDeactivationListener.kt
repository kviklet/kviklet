package dev.kviklet.kviklet.security

import dev.kviklet.kviklet.proxy.core.ProxyServer
import dev.kviklet.kviklet.service.UserDeactivatedEvent
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.session.FindByIndexNameSessionRepository
import org.springframework.session.Session
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * Ends the live access of a deactivated user once the deactivation is committed: their stored
 * browser sessions are deleted and their proxy sessions are expired (WebSocket observers are
 * closed by the websocket handler itself, which owns them). Runs after commit so a rolled-back
 * deactivation never kicks anyone out.
 */
@Component
class UserDeactivationListener(
    private val sessionRepository: ObjectProvider<FindByIndexNameSessionRepository<out Session>>,
    private val postgresProxyServer: ProxyServer,
    private val mysqlProxyServer: ProxyServer,
) {
    private val logger = LoggerFactory.getLogger(UserDeactivationListener::class.java)

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onUserDeactivated(event: UserDeactivatedEvent) {
        deleteBrowserSessions(event.email)
        listOf(postgresProxyServer, mysqlProxyServer).forEach { server ->
            try {
                server.expireSessionsForUser(event.userId)
            } catch (e: Exception) {
                logger.error("Failed to end proxy sessions for deactivated user ${event.userId}", e)
            }
        }
    }

    // Sessions are indexed by the authentication name, which is the user's email for every login
    // method (see UserDetailsWithId).
    private fun deleteBrowserSessions(email: String) {
        val repository = sessionRepository.ifAvailable ?: return
        try {
            repository.findByPrincipalName(email).keys.forEach { repository.deleteById(it) }
        } catch (e: Exception) {
            logger.error("Failed to delete sessions of deactivated user $email", e)
        }
    }
}
