package dev.kviklet.kviklet

import com.example.executiongate.controller.CreateExecutionRequestRequest
import com.example.executiongate.controller.UpdateExecutionRequestRequest
import com.example.executiongate.service.dto.DatasourceConnectionId
import com.example.executiongate.service.dto.DatasourceType
import dev.kviklet.kviklet.controller.CreateDatasourceConnectionRequest
import dev.kviklet.kviklet.controller.CreateDatasourceRequest
import dev.kviklet.kviklet.controller.ReviewConfigRequest

object TestFixtures {
    fun createDatasourceRequest(id: String) = dev.kviklet.kviklet.controller.CreateDatasourceRequest(
        id = id,
        displayName = "dev db1",
        datasourceType = DatasourceType.MYSQL,
        hostname = "localhost",
        port = 3306,
    )

    fun createDatasourceConnectionRequest(id: String, displayName: String = "display name") =
        dev.kviklet.kviklet.controller.CreateDatasourceConnectionRequest(
            id = id,
            displayName = displayName,
            username = "username",
            password = "password",
            description = "description",
            reviewConfig = dev.kviklet.kviklet.controller.ReviewConfigRequest(0),
        )

    fun updateExecutionRequestRequest(statement: String = "select 1") = UpdateExecutionRequestRequest(
        title = "title",
        description = "description",
        statement = statement,
        readOnly = false,
    )

    fun createExecutionRequestRequest(db: String) = CreateExecutionRequestRequest(
        datasourceConnectionId = DatasourceConnectionId(db),
        title = "title",
        description = "description",
        statement = "select 1",
        readOnly = false,
    )
}
