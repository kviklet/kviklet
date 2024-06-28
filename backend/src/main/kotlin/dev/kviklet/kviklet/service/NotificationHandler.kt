package dev.kviklet.kviklet.service

import dev.kviklet.kviklet.controller.ServerUrlInterceptor
import dev.kviklet.kviklet.db.ConfigurationAdapter
import dev.kviklet.kviklet.service.dto.Event
import dev.kviklet.kviklet.service.dto.ExecutionRequestDetails
import dev.kviklet.kviklet.service.dto.ReviewStatus
import dev.kviklet.kviklet.service.notifications.SlackApiClient
import dev.kviklet.kviklet.service.notifications.TeamsApiClient
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service

// Event listeners are wrapped in try catch block to let the normal flow continue even if the notification fails
// This can happen, if a user has configured webhooks wrong or slack/teams is not reachable.
@Service
class NotificationHandler(
    private val configurationAdapter: ConfigurationAdapter,
    private val teamsClient: TeamsApiClient,
    private val slackClient: SlackApiClient,
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    private fun sendNotification(message: Message) {
        val config = configurationAdapter.getConfiguration()
        if (!config.teamsUrl.isNullOrBlank()) {
            teamsClient.sendMessage(config.teamsUrl, message.title, message.text)
        }
        if (!config.slackUrl.isNullOrBlank()) {
            slackClient.sendMessage(config.slackUrl, "*${message.title}*\n${message.text}")
        }
    }

    @EventListener
    fun handleExecutionRequestCreated(event: RequestCreatedEvent) {
        try {
            val host = ServerUrlInterceptor.getServerUrl()
            if (event.necessaryReviews > 0) {
                val message = Message(
                    title = "New Request: \"${event.title}\"",
                    text = "Created by ${event.author}.\n" +
                        "This Request needs ${event.necessaryReviews} approving review(s) to be executed.\n" +
                        "Go to $host/requests/${event.requestId} to review it.",
                )
                sendNotification(message)
            }
        } catch (e: Exception) {
            logger.error("Failed to send notification", e)
        }
    }

    @EventListener
    fun handleReviewStatusUpdated(event: ReviewStatusUpdatedEvent) {
        try {
            val host = ServerUrlInterceptor.getServerUrl()
            if (event.status == ReviewStatus.AWAITING_APPROVAL) {
                val message = Message(
                    title = "Review Status Updated",
                    text = "Request ${event.title} has been reviewed by ${event.reviewer}.\n" +
                        "Status: ${event.status}.\n" +
                        "Approvals: ${event.approvals}/${event.requiredReviews}.\n" +
                        "Go to $host/requests/${event.requestId} to review it.",
                )
                sendNotification(message)
            } else if (event.status == ReviewStatus.APPROVED) {
                val message = Message(
                    title = "Request Approved",
                    text = "Request ${event.title} has been approved by ${event.reviewer}.\n" +
                        "$host/requests/${event.requestId}",
                )
                sendNotification(message)
            }
        } catch (e: Exception) {
            logger.error("Failed to send notification", e)
        }
    }
}

data class Message(val title: String, val text: String)

data class RequestCreatedEvent(
    val requestId: String,
    val title: String,
    val author: String,
    val necessaryReviews: Int,
) : ApplicationEvent(requestId) {

    companion object {
        fun fromRequest(request: ExecutionRequestDetails): RequestCreatedEvent = RequestCreatedEvent(
            requestId = request.request.id.toString(),
            title = request.request.title,
            author = request.request.author.fullName ?: "",
            necessaryReviews = request.request.connection.reviewConfig.numTotalRequired,
        )
    }
}

data class ReviewStatusUpdatedEvent(
    val requestId: String,
    val title: String,
    val status: ReviewStatus,
    val requiredReviews: Int,
    val approvals: Int,
    val reviewer: String,
) : ApplicationEvent(requestId) {
    companion object {
        fun from(request: ExecutionRequestDetails, event: Event): ReviewStatusUpdatedEvent = ReviewStatusUpdatedEvent(
            requestId = event.request.id.toString(),
            title = event.request.title,
            status = request.resolveReviewStatus(),
            requiredReviews = event.request.connection.reviewConfig.numTotalRequired,
            approvals = request.getApprovalCount(),
            reviewer = event.author.fullName ?: "",
        )
    }
}
