package com.example.executiongate

import com.example.executiongate.controller.CreateDatasourceConnectionRequest
import com.example.executiongate.controller.CreateDatasourceRequest
import com.example.executiongate.controller.CreateExecutionRequestRequest
import com.example.executiongate.controller.ReviewConfigRequest
import com.example.executiongate.controller.UpdateExecutionRequestRequest
import com.example.executiongate.service.dto.DatasourceConnectionId
import com.example.executiongate.service.dto.DatasourceType

object TestFixtures {
    fun createDatasourceRequest(id: String) = CreateDatasourceRequest(
        id = id,
        displayName = "dev db1",
        datasourceType = DatasourceType.MYSQL,
        hostname = "localhost",
        port = 3306,
    )

    fun createDatasourceConnectionRequest(id: String, displayName: String = "display name") =
        CreateDatasourceConnectionRequest(
            id = id,
            displayName = displayName,
            username = "username",
            password = "password",
            description = "description",
            reviewConfig = ReviewConfigRequest(0),
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
