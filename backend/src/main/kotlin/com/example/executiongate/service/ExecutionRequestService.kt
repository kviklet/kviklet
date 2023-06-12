package com.example.executiongate.service

import com.example.executiongate.controller.CreateCommentRequest
import com.example.executiongate.controller.CreateExecutionRequest
import com.example.executiongate.controller.CreateReviewRequest
import com.example.executiongate.db.CommentPayload
import com.example.executiongate.db.DatasourceConnectionAdapter
import com.example.executiongate.db.DatasourceConnectionRepository
import com.example.executiongate.db.EventRepository
import com.example.executiongate.db.ExecutionRequestAdapter
import com.example.executiongate.db.ExecutionRequestEntity
import com.example.executiongate.db.Payload
import com.example.executiongate.db.ReviewConfig
import com.example.executiongate.db.ReviewPayload
import com.example.executiongate.service.dto.Event
import com.example.executiongate.service.dto.ExecutionRequest
import com.example.executiongate.service.dto.ExecutionRequestDetails
import com.example.executiongate.service.dto.ExecutionRequestId
import com.example.executiongate.service.dto.ReviewStatus
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import javax.transaction.Transactional

@Service
class ExecutionRequestService(
    val executionRequestAdapter: ExecutionRequestAdapter,
    val datasourceConnectionAdapter: DatasourceConnectionAdapter,
) {

    @Transactional
    fun create(request: CreateExecutionRequest, userId: String): ExecutionRequest {
        val datasourceConnection = datasourceConnectionAdapter.getDatasourceConnection(request.datasourceConnectionId)

        return executionRequestAdapter.createExecutionRequest(
            connectionId = request.datasourceConnectionId,
            title = request.title,
            description = request.description,
            statement = request.statement,
            readOnly = request.readOnly,
            reviewStatus = resolveReviewStatus(emptySet(), datasourceConnection.reviewConfig),
            executionStatus = "PENDING",
            authorId = userId,
        )
    }

    fun list(): List<ExecutionRequest> = executionRequestAdapter.listExecutionRequests()

    fun get(id: ExecutionRequestId): ExecutionRequestDetails =
        executionRequestAdapter.getExecutionRequestDetails(id)

    @Transactional
    fun createReview(id: ExecutionRequestId, request: CreateReviewRequest) = saveEvent(
        id,
        ReviewPayload(comment = request.comment, action = request.action),
    )

    @Transactional
    fun createComment(id: ExecutionRequestId, request: CreateCommentRequest) = saveEvent(
        id,
        CommentPayload(comment = request.comment),
    )

    private fun saveEvent(
        id: ExecutionRequestId,
        payload: Payload,
    ): Event {
        val (executionRequest, event) = executionRequestAdapter.addEvent(id, payload)

        val reviewStatus: ReviewStatus = resolveReviewStatus(
            executionRequest.events,
            executionRequest.request.connection.reviewConfig,
        )

        executionRequestAdapter.updateReviewStatus(id, reviewStatus)

        return event
    }

    fun resolveReviewStatus(
        events: Set<Event>,
        reviewConfig: ReviewConfig,
    ): ReviewStatus {
        println("events: $events")
        return ReviewStatus.APPROVED
    }
}
