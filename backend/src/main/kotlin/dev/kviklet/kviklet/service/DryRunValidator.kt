package dev.kviklet.kviklet.service

import dev.kviklet.kviklet.service.dto.DatasourceType
import net.sf.jsqlparser.parser.CCJSqlParserUtil
import net.sf.jsqlparser.statement.Commit
import net.sf.jsqlparser.statement.RollbackStatement
import net.sf.jsqlparser.statement.Statement
import net.sf.jsqlparser.statement.alter.Alter
import net.sf.jsqlparser.statement.create.table.CreateTable
import net.sf.jsqlparser.statement.drop.Drop
import net.sf.jsqlparser.statement.truncate.Truncate
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

                // For MySQL/MariaDB, also block DDL (causes implicit commit)
                if (databaseType in listOf(DatasourceType.MYSQL, DatasourceType.MARIADB)) {
                    val ddlResult = checkDDL(statement)
                    if (ddlResult is ValidationResult.Invalid) {
                        return ddlResult
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

    private fun checkDDL(statement: Statement): ValidationResult = when (statement) {
        is CreateTable -> ValidationResult.Invalid(
            "CREATE TABLE statements cause implicit commit in MySQL/MariaDB and are not allowed in dry run mode",
        )

        is Alter -> ValidationResult.Invalid(
            "ALTER statements cause implicit commit in MySQL/MariaDB and are not allowed in dry run mode",
        )

        is Drop -> ValidationResult.Invalid(
            "DROP statements cause implicit commit in MySQL/MariaDB and are not allowed in dry run mode",
        )

        is Truncate -> ValidationResult.Invalid(
            "TRUNCATE statements cause implicit commit in MySQL/MariaDB and are not allowed in dry run mode",
        )

        else -> {
            val stmtString = statement.toString().trim().uppercase()
            when {
                stmtString.startsWith("RENAME") -> ValidationResult.Invalid(
                    "RENAME statements cause implicit commit in MySQL/MariaDB and are not allowed in dry run mode",
                )

                stmtString.startsWith("CREATE INDEX") -> ValidationResult.Invalid(
                    "CREATE INDEX statements cause implicit commit in MySQL/MariaDB " +
                        "and are not allowed in dry run mode",
                )

                stmtString.startsWith("CREATE DATABASE") -> ValidationResult.Invalid(
                    "CREATE DATABASE statements cause implicit commit in MySQL/MariaDB " +
                        "and are not allowed in dry run mode",
                )

                stmtString.startsWith("DROP DATABASE") -> ValidationResult.Invalid(
                    "DROP DATABASE statements cause implicit commit in MySQL/MariaDB " +
                        "and are not allowed in dry run mode",
                )

                stmtString.startsWith("CREATE") -> ValidationResult.Invalid(
                    "CREATE statements cause implicit commit in MySQL/MariaDB and are not allowed in dry run mode",
                )

                else -> ValidationResult.Valid
            }
        }
    }
}
