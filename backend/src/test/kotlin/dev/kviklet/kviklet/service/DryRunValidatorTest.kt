package dev.kviklet.kviklet.service

import dev.kviklet.kviklet.service.dto.DatasourceType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DryRunValidatorTest {

    private val validator = DryRunValidator()

    @Test
    fun `should allow simple SELECT query for PostgreSQL`() {
        val query = "SELECT * FROM users WHERE id = 1"
        val result = validator.validateForDryRun(query, DatasourceType.POSTGRESQL)

        assertTrue(result is DryRunValidator.ValidationResult.Valid)
    }

    @Test
    fun `should allow simple UPDATE query for PostgreSQL`() {
        val query = "UPDATE users SET name = 'John' WHERE id = 1"
        val result = validator.validateForDryRun(query, DatasourceType.POSTGRESQL)

        assertTrue(result is DryRunValidator.ValidationResult.Valid)
    }

    @Test
    fun `should allow simple INSERT query for PostgreSQL`() {
        val query = "INSERT INTO users (name, email) VALUES ('John', 'john@example.com')"
        val result = validator.validateForDryRun(query, DatasourceType.POSTGRESQL)

        assertTrue(result is DryRunValidator.ValidationResult.Valid)
    }

    @Test
    fun `should allow simple DELETE query for PostgreSQL`() {
        val query = "DELETE FROM users WHERE id = 1"
        val result = validator.validateForDryRun(query, DatasourceType.POSTGRESQL)

        assertTrue(result is DryRunValidator.ValidationResult.Valid)
    }

    @Test
    fun `should block COMMIT statement`() {
        val query = "UPDATE users SET name = 'John' WHERE id = 1; COMMIT;"
        val result = validator.validateForDryRun(query, DatasourceType.POSTGRESQL)

        assertTrue(result is DryRunValidator.ValidationResult.Invalid)
        assertEquals(
            "COMMIT statements are not allowed in dry run mode",
            (result as DryRunValidator.ValidationResult.Invalid).reason,
        )
    }

    @Test
    fun `should block ROLLBACK statement`() {
        val query = "UPDATE users SET name = 'John' WHERE id = 1; ROLLBACK;"
        val result = validator.validateForDryRun(query, DatasourceType.POSTGRESQL)

        assertTrue(result is DryRunValidator.ValidationResult.Invalid)
        assertEquals(
            "ROLLBACK statements are not allowed in dry run mode",
            (result as DryRunValidator.ValidationResult.Invalid).reason,
        )
    }

    @Test
    fun `should return parse error for BEGIN statement`() {
        val query = "BEGIN; UPDATE users SET name = 'John' WHERE id = 1;"
        val result = validator.validateForDryRun(query, DatasourceType.POSTGRESQL)

        assertTrue(result is DryRunValidator.ValidationResult.Invalid)
        assertTrue((result as DryRunValidator.ValidationResult.Invalid).reason.contains("Failed to parse SQL"))
    }

    @Test
    fun `should return parse error for START TRANSACTION statement`() {
        val query = "START TRANSACTION; UPDATE users SET name = 'John' WHERE id = 1;"
        val result = validator.validateForDryRun(query, DatasourceType.POSTGRESQL)

        assertTrue(result is DryRunValidator.ValidationResult.Invalid)
        assertTrue((result as DryRunValidator.ValidationResult.Invalid).reason.contains("Failed to parse SQL"))
    }

    @Test
    fun `should block SAVEPOINT statement`() {
        val query = "SAVEPOINT my_savepoint; UPDATE users SET name = 'John' WHERE id = 1;"
        val result = validator.validateForDryRun(query, DatasourceType.POSTGRESQL)

        assertTrue(result is DryRunValidator.ValidationResult.Invalid)
        assertEquals(
            "SAVEPOINT statements are not allowed in dry run mode",
            (result as DryRunValidator.ValidationResult.Invalid).reason,
        )
    }

    @Test
    fun `should allow DDL for PostgreSQL`() {
        val query = "CREATE TABLE test_table (id INT, name VARCHAR(100))"
        val result = validator.validateForDryRun(query, DatasourceType.POSTGRESQL)

        assertTrue(result is DryRunValidator.ValidationResult.Valid)
    }

    @Test
    fun `should allow ALTER for PostgreSQL`() {
        val query = "ALTER TABLE users ADD COLUMN age INT"
        val result = validator.validateForDryRun(query, DatasourceType.POSTGRESQL)

        assertTrue(result is DryRunValidator.ValidationResult.Valid)
    }

    @Test
    fun `should block CREATE TABLE for MySQL`() {
        val query = "CREATE TABLE test_table (id INT, name VARCHAR(100))"
        val result = validator.validateForDryRun(query, DatasourceType.MYSQL)

        assertTrue(result is DryRunValidator.ValidationResult.Invalid)
        assertTrue(
            (result as DryRunValidator.ValidationResult.Invalid).reason.contains(
                "Only SELECT, INSERT, UPDATE, and DELETE",
            ),
        )
    }

    @Test
    fun `should block ALTER for MySQL`() {
        val query = "ALTER TABLE users ADD COLUMN age INT"
        val result = validator.validateForDryRun(query, DatasourceType.MYSQL)

        assertTrue(result is DryRunValidator.ValidationResult.Invalid)
        assertTrue(
            (result as DryRunValidator.ValidationResult.Invalid).reason.contains(
                "Only SELECT, INSERT, UPDATE, and DELETE",
            ),
        )
    }

    @Test
    fun `should block DROP for MySQL`() {
        val query = "DROP TABLE users"
        val result = validator.validateForDryRun(query, DatasourceType.MYSQL)

        assertTrue(result is DryRunValidator.ValidationResult.Invalid)
        assertTrue(
            (result as DryRunValidator.ValidationResult.Invalid).reason.contains(
                "Only SELECT, INSERT, UPDATE, and DELETE",
            ),
        )
    }

    @Test
    fun `should block TRUNCATE for MySQL`() {
        val query = "TRUNCATE TABLE users"
        val result = validator.validateForDryRun(query, DatasourceType.MYSQL)

        assertTrue(result is DryRunValidator.ValidationResult.Invalid)
        assertTrue(
            (result as DryRunValidator.ValidationResult.Invalid).reason.contains(
                "Only SELECT, INSERT, UPDATE, and DELETE",
            ),
        )
    }

    @Test
    fun `should block CREATE TABLE for MariaDB`() {
        val query = "CREATE TABLE test_table (id INT, name VARCHAR(100))"
        val result = validator.validateForDryRun(query, DatasourceType.MARIADB)

        assertTrue(result is DryRunValidator.ValidationResult.Invalid)
        assertTrue(
            (result as DryRunValidator.ValidationResult.Invalid).reason.contains(
                "Only SELECT, INSERT, UPDATE, and DELETE",
            ),
        )
    }

    @Test
    fun `should allow DML for MySQL`() {
        val query = "UPDATE users SET name = 'John' WHERE id = 1"
        val result = validator.validateForDryRun(query, DatasourceType.MYSQL)

        assertTrue(result is DryRunValidator.ValidationResult.Valid)
    }

    @Test
    fun `should block MongoDB`() {
        val query = "db.users.find()"
        val result = validator.validateForDryRun(query, DatasourceType.MONGODB)

        assertTrue(result is DryRunValidator.ValidationResult.Invalid)
        assertEquals(
            "Dry run is not supported for MongoDB",
            (result as DryRunValidator.ValidationResult.Invalid).reason,
        )
    }

    @Test
    fun `should allow multiple DML statements for PostgreSQL`() {
        val query = """
            UPDATE users SET name = 'John' WHERE id = 1;
            INSERT INTO users (name, email) VALUES ('Jane', 'jane@example.com');
            DELETE FROM users WHERE id = 2;
        """.trimIndent()
        val result = validator.validateForDryRun(query, DatasourceType.POSTGRESQL)

        assertTrue(result is DryRunValidator.ValidationResult.Valid)
    }

    @Test
    fun `should block if any statement in batch is invalid`() {
        val query = """
            UPDATE users SET name = 'John' WHERE id = 1;
            COMMIT;
            INSERT INTO users (name, email) VALUES ('Jane', 'jane@example.com');
        """.trimIndent()
        val result = validator.validateForDryRun(query, DatasourceType.POSTGRESQL)

        assertTrue(result is DryRunValidator.ValidationResult.Invalid)
    }

    @Test
    fun `should return invalid for unparseable SQL`() {
        val query = "THIS IS NOT VALID SQL AT ALL"
        val result = validator.validateForDryRun(query, DatasourceType.POSTGRESQL)

        assertTrue(result is DryRunValidator.ValidationResult.Invalid)
        assertTrue((result as DryRunValidator.ValidationResult.Invalid).reason.contains("Failed to parse SQL"))
    }

    @Test
    fun `should allow MSSQL specific syntax`() {
        val query = "SELECT TOP 10 * FROM users"
        val result = validator.validateForDryRun(query, DatasourceType.MSSQL)

        assertTrue(result is DryRunValidator.ValidationResult.Valid)
    }
}
