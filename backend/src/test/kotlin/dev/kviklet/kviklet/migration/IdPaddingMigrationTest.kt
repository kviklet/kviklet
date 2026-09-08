package dev.kviklet.kviklet.migration

import liquibase.Contexts
import liquibase.LabelExpression
import liquibase.Liquibase
import liquibase.database.DatabaseFactory
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.ClassLoaderResourceAccessor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.sql.Connection
import java.sql.DriverManager

/**
 * Before changeset 049 ids lived in CHAR(22) columns, so the 21-character ids the generator used
 * to pad with a space came back from the database with that space. The changeset moves them to
 * VARCHAR and must strip the padding everywhere an id is stored, including the VARCHAR columns
 * that copied a padded id as data, without breaking any reference between the rows.
 */
class IdPaddingMigrationTest {

    companion object {
        val db: PostgreSQLContainer<*> = PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("migration_db")

        init {
            db.start()
        }
    }

    private fun padded(id: String): String {
        assertEquals(21, id.length, "test ids must be 21 characters before padding")
        return "$id "
    }

    private val userId = padded("Huiyz7BRyvAJpyALHFXTZ")
    private val roleId = padded("zw3Rjkq8hf6oRQbZbEGmS")
    private val policyId = padded("AbCdEfGhJkLmNpQrStUvW")
    private val requestId = padded("5RQj8YkA1xrbhS9dyz6ne")
    private val eventId = padded("Kw2mVrEZ4nT6ScGqLd8Hp")
    private val apiKeyId = padded("9fXpN3bJaLvWq7RtYe2Um")
    private val liveSessionId = padded("Dh4kCsA8uMxQ2nBrPe6Vt")

    @Test
    fun `migration strips the padding from ids and keeps every reference intact`() {
        DriverManager.getConnection(db.jdbcUrl, db.username, db.password).use { conn ->
            val database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(JdbcConnection(conn))
            val liquibase = Liquibase("changelog/000-changelog.yaml", ClassLoaderResourceAccessor(), database)
            val unrun = liquibase.listUnrunChangeSets(Contexts(), LabelExpression())
            val changesBefore = unrun.indexOfFirst { it.filePath.endsWith("049-store-ids-without-padding.yaml") }
            assertTrue(changesBefore > 0, "changeset 049 not found in ${unrun.map { it.filePath }}")
            liquibase.update(changesBefore, Contexts(), LabelExpression())

            seedPaddedRows(conn)

            liquibase.update(Contexts(), LabelExpression())

            val columnTypes = queryStrings(
                conn,
                """
                select table_name || '.' || column_name || ':' || data_type
                from information_schema.columns
                where table_schema = 'public'
                  and table_name in ('user', 'role', 'policy', 'user_role', 'execution_request', 'event',
                                     'api_keys', 'license', 'role_sync_mapping')
                  and column_name in ('id', 'user_id', 'role_id', 'execution_request_id', 'author_id')
                """.trimIndent(),
            )
            assertTrue(columnTypes.isNotEmpty())
            assertEquals(emptyList<String>(), columnTypes.filterNot { it.endsWith(":character varying") })

            assertEquals(userId.trim(), queryStrings(conn, "select id from \"user\"").single())
            assertEquals(roleId.trim(), queryStrings(conn, "select id from role").single())
            assertEquals(policyId.trim(), queryStrings(conn, "select id from policy").single())
            assertEquals(requestId.trim(), queryStrings(conn, "select id from execution_request").single())
            assertEquals(userId.trim(), queryStrings(conn, "select author_id from execution_request").single())
            assertEquals(eventId.trim(), queryStrings(conn, "select id from event").single())
            assertEquals(userId.trim(), queryStrings(conn, "select author_id from event").single())
            assertEquals(apiKeyId.trim(), queryStrings(conn, "select id from api_keys").single())
            assertEquals(userId.trim(), queryStrings(conn, "select user_id from api_keys").single())
            assertEquals(liveSessionId.trim(), queryStrings(conn, "select id from live_session").single())

            // Exact comparisons now, no CHAR padding semantics to rely on
            assertEquals(
                "1",
                queryStrings(
                    conn,
                    """
                    select count(*) from event e
                      join execution_request r on r.id = e.execution_request_id
                      join "user" u on u.id = e.author_id and u.id = r.author_id
                    """.trimIndent(),
                ).single(),
            )
            assertEquals(
                "1",
                queryStrings(
                    conn,
                    """
                    select count(*) from user_role ur
                      join "user" u on u.id = ur.user_id
                      join role r on r.id = ur.role_id
                      join policy p on p.role_id = r.id
                      join api_keys k on k.user_id = u.id
                    """.trimIndent(),
                ).single(),
            )
            assertEquals(
                "1",
                queryStrings(
                    conn,
                    "select count(*) from live_session s join execution_request r on r.id = s.execution_request_id",
                ).single(),
            )
        }
    }

    private fun seedPaddedRows(conn: Connection) {
        conn.createStatement().use { st ->
            st.execute("insert into \"user\" (id, email) values ('$userId', 'padded@example.com')")
            st.execute("insert into role (id, name, description) values ('$roleId', 'Padded', '')")
            st.execute("insert into user_role (user_id, role_id) values ('$userId', '$roleId')")
            st.execute("insert into policy (id, role_id, action, resource) values ('$policyId', '$roleId', '*', '*')")
            st.execute(
                """
                insert into execution_request (id, datasource_id, author_id, title, execution_type, execution_status)
                values ('$requestId', 'some-connection', '$userId', 'Padded', 'SingleExecution', 'EXECUTABLE')
                """.trimIndent(),
            )
            st.execute(
                """
                insert into event (id, author_id, execution_request_id, type, payload)
                values ('$eventId', '$userId', '$requestId', 'COMMENT', '{}')
                """.trimIndent(),
            )
            st.execute(
                "insert into api_keys (id, name, key_hash, user_id) values ('$apiKeyId', 'key', 'hash', '$userId')",
            )
            st.execute(
                """
                insert into live_session (id, execution_request_id, console_content)
                values ('$liveSessionId', '$requestId', '')
                """.trimIndent(),
            )
        }
    }

    private fun queryStrings(conn: Connection, sql: String): List<String> = conn.createStatement().use { st ->
        st.executeQuery(sql).use { rs ->
            generateSequence { if (rs.next()) rs.getString(1) else null }.toList()
        }
    }
}
