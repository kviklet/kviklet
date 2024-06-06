package dev.kviklet.kviklet.service

import dev.kviklet.kviklet.db.ConfigurationAdapter
import dev.kviklet.kviklet.service.dto.Event
import dev.kviklet.kviklet.service.dto.ExecutionRequestDetails
import dev.kviklet.kviklet.service.dto.ReviewStatus
import dev.kviklet.kviklet.service.notifications.SlackApiClient
import dev.kviklet.kviklet.service.notifications.TeamsApiClient
import org.springframework.context.ApplicationEvent
import org.springframework.context.event.EventListener

class NotificationService(
    private val configurationAdapter: ConfigurationAdapter,
    private val teamsClient: TeamsApiClient,
    private val slackClient: SlackApiClient,
) {

    @EventListener
    fun handleExecutionRequestCreated(event: RequestCreatedEvent) {
        val config = configurationAdapter.getConfiguration()
        if (config.teamsUrl != null) {
            teamsClient.sendMessage(
                config.teamsUrl,
                "New request created by ${event.author}",
                "Go to ${config.host}/requests/${event.requestId} to view the request.",
            )
        }
        if (config.slackUrl != null) {
            slackClient.sendMessage(
                config.slackUrl,
                "New request created by ${event.author}" +
                    "Go to ${config.host}/requests/${event.requestId} to view the request.",
            )
        }
    }

    @EventListener
    fun handleReviewStatusUpdated(event: ReviewStatusUpdatedEvent) {
        // Send notification
    }
}

data class RequestCreatedEvent(
    val requestId: String,
    val author: String,
) : ApplicationEvent(requestId) {

    companion object {
        fun fromRequest(request: ExecutionRequestDetails): RequestCreatedEvent {
            return RequestCreatedEvent(request.request.id.toString(), request.request.author.fullName ?: "")
        }
    }
}

data class ReviewStatusUpdatedEvent(
    val requestId: String,
    val status: ReviewStatus,
) : ApplicationEvent(requestId) {
    companion object {
        fun fromReviewEvent(event: Event): ReviewStatusUpdatedEvent {
            return ReviewStatusUpdatedEvent(event.request.request.id.toString(), event.request.resolveReviewStatus())
        }
    }
}
