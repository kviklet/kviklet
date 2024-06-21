package dev.kviklet.kviklet

import dev.kviklet.kviklet.controller.CreateDatasourceConnectionRequest
import dev.kviklet.kviklet.controller.CreateDatasourceExecutionRequestRequest
import dev.kviklet.kviklet.controller.ReviewConfigRequest
import dev.kviklet.kviklet.controller.UpdateExecutionRequestRequest
import dev.kviklet.kviklet.service.dto.ConnectionId
import dev.kviklet.kviklet.service.dto.DatasourceType
import dev.kviklet.kviklet.service.dto.RequestType

object TestFixtures {

    fun createDatasourceConnectionRequest(id: String, displayName: String = "display name") =
        CreateDatasourceConnectionRequest(
            id = id,
            displayName = displayName,
            username = "username",
            password = "password",
            description = "description",
            reviewConfig = ReviewConfigRequest(0),
            type = DatasourceType.MYSQL,
            hostname = "localhost",
            port = 3306,
        )

    fun updateExecutionRequestRequest(statement: String = "select 1") = UpdateExecutionRequestRequest(
        title = "title",
        description = "description",
        statement = statement,
    )

    fun createExecutionRequestRequest(db: String) = CreateDatasourceExecutionRequestRequest(
        connectionId = ConnectionId(db),
        title = "title",
        description = "description",
        statement = "select 1",
        type = RequestType.SingleExecution,
    )
}
