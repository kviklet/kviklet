package com.example.executiongate.service

import com.example.executiongate.db.ExecutionRequestEntity
import com.example.executiongate.db.ExecutionRequestRepository
import org.springframework.stereotype.Service

@Service
class ExecutionRequestService(
    val executionRequestRepository: ExecutionRequestRepository
) {

    fun create(databaseId: String, statement: String) {
        val entity = executionRequestRepository.save(
            ExecutionRequestEntity(
                statement = statement,
                databaseId = databaseId,
                state = "PENDING"
            )
        )
    }

    fun execute(executionRequestId: String) {

    }
}
