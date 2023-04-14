package com.example.executiongate.service

import com.example.executiongate.controller.CreateCommentRequest
import com.example.executiongate.controller.CreateExecutionRequest
import com.example.executiongate.controller.CreateReviewRequest
import com.example.executiongate.db.CommentPayload
import com.example.executiongate.db.DatasourceConnectionRepository
import com.example.executiongate.db.EventEntity
import com.example.executiongate.db.EventRepository
import com.example.executiongate.db.ExecutionRequestEntity
import com.example.executiongate.db.ExecutionRequestRepository
import com.example.executiongate.db.Payload
import com.example.executiongate.db.ReviewPayload
import com.example.executiongate.service.dto.Event
import com.example.executiongate.service.dto.EventType
import com.example.executiongate.service.dto.ExecutionRequest
import com.example.executiongate.service.dto.ExecutionRequestDetails
import com.example.executiongate.service.dto.ExecutionRequestId
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import javax.transaction.Transactional

@Service
class ExecutionRequestService(
    val executionRequestRepository: ExecutionRequestRepository,
    val eventRepository: EventRepository,
    val datasourceConnectionRepository: DatasourceConnectionRepository,
    val datasourceService: DatasourceService,
) {

    @Transactional
    fun create(request: CreateExecutionRequest): ExecutionRequest {
        val connectionEntity = datasourceService.getDatasourceConnection(request.datasourceConnectionId)

        val entity = executionRequestRepository.save(
            ExecutionRequestEntity(
                connection = connectionEntity,
                title = request.title,
                description = request.description,
                statement = request.statement,
                readOnly = request.readOnly,
                reviewStatus = "PENDING",
                executionStatus = "PENDING",
                events = emptySet(),
            ),
        )
        return entity.toDto()
    }

    fun list(): List<ExecutionRequest> = executionRequestRepository.findAll().map { it.toDto() }

    fun get(id: ExecutionRequestId): ExecutionRequestDetails {
        val executionRequestDetails = getExecutionRequestDetails(id)
            return executionRequestDetails.toDetailDto()
    }

    fun createReview(id: ExecutionRequestId, request: CreateReviewRequest) = saveEvent(
        id,
        ReviewPayload(comment = request.comment, action = request.action)
    )

    fun createComment(id: ExecutionRequestId, request: CreateCommentRequest) = saveEvent(
        id,
        CommentPayload(comment = request.comment)
    )

    private fun saveEvent(
        id: ExecutionRequestId,
        payload: Payload
    ): Event {
        val executionRequestEntity = getExecutionRequest(id)

        return eventRepository.save(
            EventEntity(
                executionRequest = executionRequestEntity,
                type = EventType.REVIEW,
                payload = payload,
            ),
        ).toDto()
    }

    private fun getExecutionRequest(id: ExecutionRequestId): ExecutionRequestEntity =
        executionRequestRepository.findByIdOrNull(id.toString())
            ?: throw EntityNotFound("Execution Request Not Found", "Execution Request with id $id does not exist.")

    private fun getExecutionRequestDetails(id: ExecutionRequestId): ExecutionRequestEntity =
        executionRequestRepository.findByIdWithDetails(id)
            ?: throw EntityNotFound("Execution Request Not Found", "Execution Request with id $id does not exist.")


}
