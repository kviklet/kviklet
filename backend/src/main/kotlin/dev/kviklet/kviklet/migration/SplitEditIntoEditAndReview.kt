package dev.kviklet.kviklet.migration

import dev.kviklet.kviklet.db.util.IdGenerator
import liquibase.change.custom.CustomTaskChange
import liquibase.database.Database
import liquibase.database.jvm.JdbcConnection
import liquibase.exception.CustomChangeException
import liquibase.exception.SetupException
import liquibase.exception.ValidationErrors
import liquibase.resource.ResourceAccessor

class SplitEditIntoEditAndReview : CustomTaskChange {
    private lateinit var resourceAccessor: ResourceAccessor

    @Throws(CustomChangeException::class)
    override fun execute(database: Database) {
        val connection = database.connection as JdbcConnection
        val idGenerator = IdGenerator()

        try {
            // Find all policies with 'execution_request:edit' action
            val selectSql = "SELECT role_id, effect, resource FROM policy WHERE action = 'execution_request:edit'"
            connection.prepareStatement(selectSql).use { selectStmt ->
                val resultSet = selectStmt.executeQuery()

                // Prepare insert statement
                val insertSql = "INSERT INTO policy (id, role_id, effect, action, resource) VALUES (?, ?, ?, ?, ?)"
                connection.prepareStatement(insertSql).use { insertStmt ->
                    while (resultSet.next()) {
                        val roleId = resultSet.getString("role_id")
                        val effect = resultSet.getString("effect")
                        val resource = resultSet.getString("resource")

                        // Generate new ID
                        val newId = idGenerator.generateId() as String

                        // Insert new 'review' policy
                        insertStmt.setString(1, newId)
                        insertStmt.setString(2, roleId)
                        insertStmt.setString(3, effect)
                        insertStmt.setString(4, "execution_request:review")
                        insertStmt.setString(5, resource)
                        insertStmt.executeUpdate()
                    }
                }
            }
        } catch (e: Exception) {
            throw CustomChangeException("Error executing SplitEditIntoEditAndReview", e)
        }
    }

    @Throws(SetupException::class)
    override fun setUp() {
        // No setup needed
    }

    @Throws(SetupException::class)
    override fun setFileOpener(resourceAccessor: ResourceAccessor) {
        this.resourceAccessor = resourceAccessor
    }

    override fun validate(database: Database): ValidationErrors? = null

    override fun getConfirmationMessage(): String =
        "Split 'execution_request:edit' policies into 'edit' and 'review' completed successfully"
}
