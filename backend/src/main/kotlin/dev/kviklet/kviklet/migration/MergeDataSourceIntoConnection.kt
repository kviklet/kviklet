package dev.kviklet.kviklet.migration

import liquibase.change.custom.CustomTaskChange
import liquibase.database.Database
import liquibase.database.jvm.JdbcConnection
import liquibase.exception.ValidationErrors
import liquibase.resource.ResourceAccessor

class MergeDataSourceIntoConnection : CustomTaskChange {
    private lateinit var resourceAccessor: ResourceAccessor

    override fun execute(database: Database) {
        // Directly using the Connection object
        val conn = database.connection as JdbcConnection

        conn.prepareStatement("SELECT  port, hostname, type, id FROM datasource").use { selectStmt ->
            val rs = selectStmt.executeQuery()
            while (rs.next()) {
                val port = rs.getString("port")
                val hostname = rs.getString("hostname")
                val type = rs.getString("type")
                val dataSourceId = rs.getString("id")

                conn.prepareStatement(
                    "UPDATE datasource_connection SET port = ?, hostname = ?, type = ? WHERE datasource_id = ?",
                ).use { updateStmt ->
                    updateStmt.setString(1, port)
                    updateStmt.setString(2, hostname)
                    updateStmt.setString(3, type)
                    updateStmt.setString(4, dataSourceId)

                    updateStmt.executeUpdate()
                }
            }
        }
    }

    override fun setFileOpener(resourceAccessor: ResourceAccessor) {
        this.resourceAccessor = resourceAccessor
    }

    override fun getConfirmationMessage(): String = "Merged datasource into connection"

    override fun setUp() {
    }

    override fun validate(database: Database): ValidationErrors {
        // No checks for validation
        return ValidationErrors()
    }
}
