package com.example.executiongate.service

import com.example.executiongate.controller.CreateExecutionRequest
import com.example.executiongate.controller.CreateReviewRequest
import com.example.executiongate.db.DatasourceConnectionRepository
import com.example.executiongate.db.EventEntity
import com.example.executiongate.db.EventRepository
import com.example.executiongate.db.ExecutionRequestEntity
import com.example.executiongate.db.ExecutionRequestRepository
import com.example.executiongate.db.ReviewPayload
import com.example.executiongate.service.dto.Event
import com.example.executiongate.service.dto.EventType
import com.example.executiongate.service.dto.ExecutionRequest
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
            ),
        )
        return entity.toDto()
    }

    fun list(): List<ExecutionRequest> = executionRequestRepository.findAll().map { it.toDto() }

    @Transactional
    fun execute(executionRequestId: String): QueryResult {
        /*
        val executionRequestEntity: ExecutionRequestEntity? = executionRequestRepository.findByIdOrNull(
            executionRequestId
        )

        if (executionRequestEntity != null) {
            val connectionEntity = datasourceConnectionRepository.findByIdOrNull(executionRequestEntity.databaseId)

            if (connectionEntity != null) {
                return ExecutorService().execute(connectionEntity., executionRequestEntity.statement)
            }
        }*/
        throw Exception("Failed to run query")
    }

    fun createReview(id: ExecutionRequestId, request: CreateReviewRequest): Event {
        val executionRequestEntity = getExecutionRequest(id)

        return eventRepository.save(
            EventEntity(
                executionRequest = executionRequestEntity,
                type = EventType.REVIEW,
                payload = ReviewPayload(
                    comment = request.comment,
                    action = request.action,
                ),
            ),
        ).toDto()
    }

    private fun getExecutionRequest(id: ExecutionRequestId): ExecutionRequestEntity =
        executionRequestRepository.findByIdOrNull(id.toString())
            ?: throw EntityNotFound("Execution Request Not Found", "Datasource with id $id does not exist.")

    // fun addReview(requestId: ExecutionRequestId, request: CreateReviewRequest): ExecutionRequest {}
}
