package com.example.executiongate.service

import com.example.executiongate.db.ConnectionRepository
import com.example.executiongate.db.ExecutionRequestEntity
import com.example.executiongate.db.ExecutionRequestRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import javax.transaction.Transactional

@Service
class ExecutionRequestService(
    val executionRequestRepository: ExecutionRequestRepository,
    val connectionRepository: ConnectionRepository
) {

    fun create(databaseId: String, statement: String): String {
        val entity = executionRequestRepository.save(
            ExecutionRequestEntity(
                statement = statement,
                databaseId = databaseId,
                state = "PENDING"
            )
        )
        return entity.id
    }

    @Transactional
    fun execute(executionRequestId: String): QueryResult {
        val executionRequestEntity: ExecutionRequestEntity? = executionRequestRepository.findByIdOrNull(
            executionRequestId
        )

        if (executionRequestEntity != null) {
            val connectionEntity = connectionRepository.findByIdOrNull(executionRequestEntity.databaseId)

            if (connectionEntity != null) {
                return ExecutorService().execute(connectionEntity.uri, executionRequestEntity.statement)
            }
        }
        throw Exception("Failed to run query")
    }
}
