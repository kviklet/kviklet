package dev.kviklet.kviklet.service

import dev.kviklet.kviklet.service.dto.DatasourceType
import net.sf.jsqlparser.parser.CCJSqlParserUtil
import net.sf.jsqlparser.statement.Commit
import net.sf.jsqlparser.statement.RollbackStatement
import net.sf.jsqlparser.statement.Statement
import net.sf.jsqlparser.statement.delete.Delete
import net.sf.jsqlparser.statement.insert.Insert
import net.sf.jsqlparser.statement.select.Select
import net.sf.jsqlparser.statement.update.Update
import org.springframework.stereotype.Component

@Component
class DryRunValidator {

    sealed class ValidationResult {
        object Valid : ValidationResult()
        data class Invalid(val reason: String) : ValidationResult()
    }

    fun validateForDryRun(query: String, databaseType: DatasourceType): ValidationResult {
        // MongoDB should never reach here
        if (databaseType == DatasourceType.MONGODB) {
            return ValidationResult.Invalid("Dry run is not supported for MongoDB")
        }

        try {
            val statements = CCJSqlParserUtil.parseStatements(query)

            for (statement in statements.statements) {
                // Block transaction control statements for all databases
                val transactionControlResult = checkTransactionControl(statement)
                if (transactionControlResult is ValidationResult.Invalid) {
                    return transactionControlResult
                }

                // For MySQL/MariaDB, only allow safe DML statements (DDL causes implicit commit)
                if (databaseType in listOf(DatasourceType.MYSQL, DatasourceType.MARIADB)) {
                    val result = checkMySqlAllowedStatements(statement)
                    if (result is ValidationResult.Invalid) {
                        return result
                    }
                }
            }

            return ValidationResult.Valid
        } catch (e: Exception) {
            return ValidationResult.Invalid("Failed to parse SQL: ${e.message}")
        }
    }

    private fun checkTransactionControl(statement: Statement): ValidationResult = when (statement) {
        is Commit -> ValidationResult.Invalid("COMMIT statements are not allowed in dry run mode")

        is RollbackStatement -> ValidationResult.Invalid(
            "ROLLBACK statements are not allowed in dry run mode",
        )

        else -> {
            // Note: JSqlParser doesn't parse BEGIN and START TRANSACTION well, so they will fail at parse time
            // We can check for SAVEPOINT and RELEASE which do parse
            val stmtString = statement.toString().trim().uppercase()
            when {
                stmtString.startsWith("SAVEPOINT") ->
                    ValidationResult.Invalid("SAVEPOINT statements are not allowed in dry run mode")

                stmtString.startsWith("RELEASE") ->
                    ValidationResult.Invalid("RELEASE SAVEPOINT statements are not allowed in dry run mode")

                else -> ValidationResult.Valid
            }
        }
    }

    /**
     * For MySQL/MariaDB, only allow known-safe DML statements.
     * DDL and other statements cause implicit commits and cannot be rolled back.
     */
    private fun checkMySqlAllowedStatements(statement: Statement): ValidationResult = when (statement) {
        is Select, is Insert, is Update, is Delete -> ValidationResult.Valid
        else -> ValidationResult.Invalid(
            "Only SELECT, INSERT, UPDATE, and DELETE statements are allowed in dry run mode for MySQL/MariaDB. " +
                "Other statements (DDL, GRANT, etc.) cause implicit commits and cannot be rolled back.",
        )
    }
}
